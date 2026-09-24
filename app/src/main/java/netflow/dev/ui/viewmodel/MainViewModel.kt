package netflow.dev.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import netflow.dev.network.CellularNetworkProvider
import netflow.dev.network.CellularTechMonitor
import netflow.dev.network.NetworkInterfaceLister
import netflow.dev.apn.shell.LibsuShellExecutor
import netflow.dev.apn.api.ApnApi
import netflow.dev.apn.api.CurrentApnResult
import netflow.dev.proxy.ConnectionRegistry
import netflow.dev.proxy.SpeedSampler
import netflow.dev.service.ProxyService
import netflow.dev.ui.theme.ThemeMode
import netflow.dev.util.AntiKillEnforcer
import netflow.dev.util.AntiKillPreferences
import netflow.dev.util.ApnPreferences
import netflow.dev.util.ApnRotation
import netflow.dev.util.AppLog
import netflow.dev.util.ApnRotateMode
import netflow.dev.util.LibsuRootShell
import netflow.dev.util.RateUnit
import netflow.dev.util.RootDaemon
import netflow.dev.geo.GeoInfo
import netflow.dev.geo.GeoIpClient
import netflow.dev.util.RootState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs: SharedPreferences =
        app.getSharedPreferences(ProxyService.PREFS_NAME, Context.MODE_PRIVATE)

    private val _bindAddress = MutableStateFlow(
        prefs.getString(ProxyService.PREF_BIND_ADDRESS, "0.0.0.0") ?: "0.0.0.0"
    )
    val bindAddress: StateFlow<String> = _bindAddress.asStateFlow()

    private val _port = MutableStateFlow(prefs.getInt(ProxyService.PREF_PORT, ProxyService.DEFAULT_PORT))
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _interfaces = MutableStateFlow<List<NetworkInterfaceLister.Candidate>>(emptyList())
    val interfaces: StateFlow<List<NetworkInterfaceLister.Candidate>> = _interfaces.asStateFlow()

    /** Callback perubahan WiFi; dilepas di onCleared. */
    private var wifiCallback: ConnectivityManager.NetworkCallback? = null

    private val _serviceState = MutableStateFlow<ProxyService.State>(ProxyService.State.Stopped)
    val serviceState: StateFlow<ProxyService.State> = _serviceState.asStateFlow()

    private val _devices = MutableStateFlow<List<ConnectionRegistry.DeviceSummary>>(emptyList())
    val devices: StateFlow<List<ConnectionRegistry.DeviceSummary>> = _devices.asStateFlow()

    private val _totals = MutableStateFlow(ConnectionRegistry.Totals(0L, 0L, 0))
    val totals: StateFlow<ConnectionRegistry.Totals> = _totals.asStateFlow()

    private val _rates = MutableStateFlow(SpeedSampler.Rates(0L, 0L))
    val rates: StateFlow<SpeedSampler.Rates> = _rates.asStateFlow()

    private val _cellular =
        MutableStateFlow<CellularNetworkProvider.State>(CellularNetworkProvider.State.Idle)
    val cellular: StateFlow<CellularNetworkProvider.State> = _cellular.asStateFlow()

    private val techMonitor = CellularTechMonitor(app).also { it.start(viewModelScope) }
    val cellularTech: StateFlow<CellularTechMonitor.TechState> = techMonitor.state

    private val _authEnabled = MutableStateFlow(
        prefs.getBoolean(ProxyService.PREF_AUTH_ENABLED, false)
    )
    val authEnabled: StateFlow<Boolean> = _authEnabled.asStateFlow()

    private val _authUsername = MutableStateFlow(
        prefs.getString(ProxyService.PREF_AUTH_USERNAME, "") ?: ""
    )
    val authUsername: StateFlow<String> = _authUsername.asStateFlow()

    private val _authPassword = MutableStateFlow(
        prefs.getString(ProxyService.PREF_AUTH_PASSWORD, "") ?: ""
    )
    val authPassword: StateFlow<String> = _authPassword.asStateFlow()

    private val _themeMode = MutableStateFlow(
        ThemeMode.fromKey(prefs.getString(KEY_THEME_MODE, null))
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _rateUnit = MutableStateFlow(
        RateUnit.fromKey(prefs.getString(ProxyService.PREF_RATE_UNIT, null))
    )
    val rateUnit: StateFlow<RateUnit> = _rateUnit.asStateFlow()

    private val _autoStartOnBoot = MutableStateFlow(AntiKillPreferences.autoStartOnBoot(app))
    val autoStartOnBoot: StateFlow<Boolean> = _autoStartOnBoot.asStateFlow()

    private val _rootEnabled = MutableStateFlow(AntiKillPreferences.rootEnabled(app))
    val rootEnabled: StateFlow<Boolean> = _rootEnabled.asStateFlow()

    // Root grant is process-scoped, never persisted: every process re-checks
    // via requestRoot(). The superuser prompt only ever comes from the user
    // turning the toggle on here, never from the service.
    val rootState: StateFlow<RootState> = LibsuRootShell.state
    val antiKillEnforcement: StateFlow<List<AntiKillEnforcer.ItemState>> =
        AntiKillEnforcer.items

    private val _daemonEnabled = MutableStateFlow(AntiKillPreferences.daemonEnabled(app))
    val daemonEnabled: StateFlow<Boolean> = _daemonEnabled.asStateFlow()
    val daemonStatus: StateFlow<RootDaemon.Status> = RootDaemon.status

    private val _rootAutoStart = MutableStateFlow(AntiKillPreferences.rootAutoStart(app))
    val rootAutoStart: StateFlow<Boolean> = _rootAutoStart.asStateFlow()

    fun setRootAutoStart(enabled: Boolean) {
        AntiKillPreferences.setRootAutoStart(getApplication(), enabled)
        _rootAutoStart.value = enabled
        if (enabled) maybeAutoStart()
    }

    /**
     * "Start after root": begitu root benar-benar tersedia (dan pref-nya
     * aktif), jalankan proxy sekali. Hanya bereaksi pada transisi ke Granted
     * supaya tidak start berulang; toggle dimatikan tidak menghentikan proxy
     * yang sudah berjalan (itu tugas tombol power).
     */
    private fun maybeAutoStart() {
        if (!_rootAutoStart.value) return
        viewModelScope.launch {
            val granted = LibsuRootShell.state.value == RootState.Granted ||
                LibsuRootShell.request() == RootState.Granted
            if (granted && _serviceState.value is ProxyService.State.Stopped) start()
        }
    }

    private var bound: ProxyService? = null
    private val collectors = mutableListOf<Job>()

    init {
        // Self-heal: a fresh process starts at Unknown, so with the daemon
        // switched on it asks for root once (the manager remembers the grant,
        // so this is silent) and then verifies the boot script is still in
        // place, re-creating it if a ROM update wiped service.d. Never runs
        // when the daemon is off, so no prompt for anyone not using it.
        viewModelScope.launch {
            LibsuRootShell.state.collect { state ->
                val daemonOn = AntiKillPreferences.daemonEnabled(getApplication())
                if (!daemonOn) return@collect
                if (state == RootState.Unknown) {
                    LibsuRootShell.request()
                    return@collect
                }
                if (state == RootState.Granted) {
                    val ctx = getApplication<Application>()
                    RootDaemon.reconcile(ctx, LibsuRootShell, ctx.packageName)
                }
            }
        }

        // Start-after-root: root yang baru terkonfirmasi memicu satu kali
        // start kalau pref-nya aktif (lihat [maybeAutoStart]).
        viewModelScope.launch {
            LibsuRootShell.state.collect { state ->
                if (state == RootState.Granted) maybeAutoStart()
            }
        }

        // Endpoint Home mengikuti IP WiFi yang bisa dihubungi klien. Daripada
        // poll (boros CPU/baterai), daftar interface di-refresh saat WiFi
        // naik/turun atau alamatnya berubah: OS push event via NetworkCallback,
        // jadi nol polling. Refresh satu kali di sini supaya render pertama
        // sudah punya data.
        refreshInterfaces()
        val cm = getApplication<Application>()
            .getSystemService(ConnectivityManager::class.java)
        val wifiRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        wifiCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refreshInterfaces()
            override fun onLost(network: Network) = refreshInterfaces()
            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties,
            ) = refreshInterfaces()
        }
        runCatching { cm.registerNetworkCallback(wifiRequest, wifiCallback!!) }
            .onFailure { wifiCallback = null }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? ProxyService.LocalBinder)?.service ?: return
            bound = service
            mirror(service)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = null
            collectors.forEach { it.cancel() }
            collectors.clear()
        }
    }

    private fun mirror(service: ProxyService) {
        collectors.forEach { it.cancel() }
        collectors.clear()
        collectors += service.state.onEach { _serviceState.value = it }.launchIn(viewModelScope)

        // Kartu geo-IP menarik data setiap kali proxy baru naik: itulah
        // momen IP seluler bisa berubah (rotasi APN/radio), jadi angka basi
        // tidak ditampilkan. Kegagalan pool tidak mengganggu apa pun.
        viewModelScope.launch {
            serviceState.collect { s ->
                if (s is ProxyService.State.Running) {
                    refreshGeo()
                    refreshApn()
                }
            }
        }

        // Rotasi APN otomatis jalan di ProxyService (bukan di sini), jadi
        // kartu APN dan geo-IP hanya tahu lewat sinyal akhir rotasi.
        viewModelScope.launch {
            ApnRotation.rotated.collect { at ->
                if (at > 0L) {
                    refreshGeo()
                    refreshApn()
                }
            }
        }
        collectors += service.devices.onEach { _devices.value = it }.launchIn(viewModelScope)
        collectors += service.totals.onEach { _totals.value = it }.launchIn(viewModelScope)
        collectors += service.rates.onEach { _rates.value = it }.launchIn(viewModelScope)
        collectors += service.cellularState.onEach { _cellular.value = it }.launchIn(viewModelScope)
    }

    fun bind() {
        val ctx = getApplication<Application>()
        ctx.bindService(
            Intent(ctx, ProxyService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
    }

    fun unbind() {
        val ctx = getApplication<Application>()
        runCatching { ctx.unbindService(connection) }
        collectors.forEach { it.cancel() }
        collectors.clear()
        bound = null
    }

    fun refreshInterfaces() {
        _interfaces.value = NetworkInterfaceLister.list()
    }

    fun selectBindAddress(addr: String) {
        _bindAddress.value = addr
        prefs.edit().putString(ProxyService.PREF_BIND_ADDRESS, addr).apply()
    }

    fun selectPort(p: Int) {
        _port.value = p
        prefs.edit().putInt(ProxyService.PREF_PORT, p).apply()
    }

    fun setAuthEnabled(enabled: Boolean) {
        _authEnabled.value = enabled
        prefs.edit().putBoolean(ProxyService.PREF_AUTH_ENABLED, enabled).apply()
    }

    fun setAuthUsername(value: String) {
        _authUsername.value = value
        prefs.edit().putString(ProxyService.PREF_AUTH_USERNAME, value).apply()
    }

    fun setAuthPassword(value: String) {
        _authPassword.value = value
        prefs.edit().putString(ProxyService.PREF_AUTH_PASSWORD, value).apply()
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        AntiKillPreferences.setAutoStartOnBoot(getApplication(), enabled)
        _autoStartOnBoot.value = enabled
    }

    fun setRootEnabled(enabled: Boolean) {
        AntiKillPreferences.setRootEnabled(getApplication(), enabled)
        _rootEnabled.value = enabled
        if (!enabled) {
            AntiKillEnforcer.reset()
            // A leftover boot script would keep restarting the proxy after
            // the user turned root off; remove it. Needs one silent root call
            // (the manager remembers the grant); skipped when unavailable.
            if (AntiKillPreferences.daemonEnabled(getApplication())) {
                AntiKillPreferences.setDaemonEnabled(getApplication(), false)
                _daemonEnabled.value = false
                viewModelScope.launch {
                    if (LibsuRootShell.state.value != RootState.Granted) LibsuRootShell.request()
                    RootDaemon.uninstall(LibsuRootShell)
                }
            }
        } else {
            requestRoot()
        }
    }

    /** Prompt for root from the UI path. Never throws; state lands in [rootState]. */
    fun requestRoot() {
        AppLog.i(TAG, "root requested")
        viewModelScope.launch { LibsuRootShell.request() }
    }

    /**
     * Root watchdog daemon: installs/removes the /data/adb/service.d boot
     * script. Root is requested once here (silent when the grant is already
     * remembered), never from the service.
     */
    fun setDaemonEnabled(enabled: Boolean) {
        AppLog.i(TAG, "watchdog daemon ${if (enabled) "install" else "uninstall"} requested")
        AntiKillPreferences.setDaemonEnabled(getApplication(), enabled)
        _daemonEnabled.value = enabled
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            if (LibsuRootShell.state.value != RootState.Granted) LibsuRootShell.request()
            if (enabled) RootDaemon.install(ctx, LibsuRootShell, ctx.packageName)
            else RootDaemon.uninstall(LibsuRootShell)
        }
    }

    fun cycleThemeMode() {
        // Directed cycle Dark -> System -> Light -> Dark, so a fresh install
        // (which defaults to Dark) moves to System on the first tap instead
        // of landing back on Dark, which would look like a no-op.
        val next = when (_themeMode.value) {
            ThemeMode.Dark -> ThemeMode.System
            ThemeMode.System -> ThemeMode.Light
            ThemeMode.Light -> ThemeMode.Dark
        }
        _themeMode.value = next
        prefs.edit().putString(KEY_THEME_MODE, next.key).apply()
        AppLog.i(TAG, "theme -> ${next.key}")
    }

    // ------------------------------------------------------------- geo-IP card

    private val _geoInfo = MutableStateFlow<GeoInfo?>(null)
    val geoInfo: StateFlow<GeoInfo?> = _geoInfo.asStateFlow()

    private val _geoLoading = MutableStateFlow(false)
    val geoLoading: StateFlow<Boolean> = _geoLoading.asStateFlow()

    private val geoClient by lazy { GeoIpClient(getApplication()) }

    /** Ambil geo-IP dari pool; no-op kalau sudah ada permintaan berjalan. */
    fun refreshGeo() {
        if (_geoLoading.value) return
        _geoLoading.value = true
        viewModelScope.launch {
            val g = geoClient.fetch()
            _geoInfo.value = g
            _geoLoading.value = false
            AppLog.d(TAG, "geo lookup: ${g?.ip ?: "failed"}")
        }
    }

    // ------------------------------------------------------------- APN card

    private val _currentApn = MutableStateFlow<CurrentApnResult?>(null)
    val currentApn: StateFlow<CurrentApnResult?> = _currentApn.asStateFlow()

    private val _apnBusy = MutableStateFlow(false)
    val apnBusy: StateFlow<Boolean> = _apnBusy.asStateFlow()

    private val apnApi by lazy { ApnApi(LibsuShellExecutor()) }

    /**
     * Baca APN preferensi aktif. Memerlukan root (query lewat shell); tanpa
     * root hasilnya kegagalan dan kartu menampilkan pesan sesuai.
     */
    fun refreshApn() {
        if (_apnBusy.value) return
        _apnBusy.value = true
        viewModelScope.launch {
            val r = apnApi.currentApn()
            _currentApn.value = r
            _apnBusy.value = false
            if (r.ok) AppLog.i(TAG, "apn current read: ${r.apn?.name ?: "-"}")
            else AppLog.w(TAG, "apn current read failed: ${r.error}")
        }
    }

    /** Rotasi ke APN lain (auto-pick + siklus data), lalu baca ulang hasil. */
    fun switchApn() {
        if (_apnBusy.value) return
        _apnBusy.value = true
        viewModelScope.launch {
            // Satu baris sebelum dan sesudah supaya log menunjukkan aksi yang
            // diminta pengguna (tombol Switch), bukan hanya hasil diam-diam.
            AppLog.i(TAG, "apn switch requested")
            val r = apnApi.autoChangeIp()
            val after = apnApi.currentApn()
            _currentApn.value = after
            _apnBusy.value = false
            if (r.ok) AppLog.i(TAG, "apn switched to ${r.apnName ?: after.apn?.name ?: "-"}")
            else AppLog.w(TAG, "apn switch failed: ${r.error}")
        }
    }

    // ------------------------------------------------- APN auto switch

    private val _apnAutoEnabled = MutableStateFlow(ApnPreferences.autoEnabled(app))
    val apnAutoEnabled: StateFlow<Boolean> = _apnAutoEnabled.asStateFlow()

    private val _apnAutoMode = MutableStateFlow(ApnPreferences.autoMode(app))
    val apnAutoMode: StateFlow<ApnRotateMode> = _apnAutoMode.asStateFlow()

    private val _apnAutoInterval = MutableStateFlow(ApnPreferences.autoIntervalSeconds(app))
    val apnAutoInterval: StateFlow<Int> = _apnAutoInterval.asStateFlow()

    /** Hanya menulis pref; loop-nya milik ProxyService (baca pref per tick). */
    fun setApnAutoEnabled(enabled: Boolean) {
        ApnPreferences.setAutoEnabled(getApplication(), enabled)
        _apnAutoEnabled.value = enabled
    }

    fun setApnAutoMode(mode: ApnRotateMode) {
        ApnPreferences.setAutoMode(getApplication(), mode)
        _apnAutoMode.value = mode
    }

    fun setApnAutoInterval(seconds: Int) {
        ApnPreferences.setAutoIntervalSeconds(getApplication(), seconds)
        _apnAutoInterval.value = ApnPreferences.autoIntervalSeconds(getApplication())
    }

    fun cycleRateUnit() {
        val next = if (_rateUnit.value == RateUnit.BytesPerSecond) RateUnit.Mbps else RateUnit.BytesPerSecond
        _rateUnit.value = next
        prefs.edit().putString(ProxyService.PREF_RATE_UNIT, next.key).apply()
    }

    fun start() {
        runCatching {
            val ctx = getApplication<Application>()
            val intent = ProxyService.startIntent(ctx, _bindAddress.value, _port.value)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }
    }

    fun stop() {
        val ctx = getApplication<Application>()
        ctx.startService(ProxyService.stopIntent(ctx))
    }

    override fun onCleared() {
        super.onCleared()
        techMonitor.stop()
        wifiCallback?.let { cb ->
            getApplication<Application>()
                .getSystemService(ConnectivityManager::class.java)
                .let { cm -> runCatching { cm.unregisterNetworkCallback(cb) } }
        }
        wifiCallback = null
        unbind()
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"

        /** Tag log untuk aksi yang dimulai pengguna dari UI. */
        private const val TAG = "MainVM"
    }
}

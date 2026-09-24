package netflow.dev.geo

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Hasil pembacaan geo-IP: alamat publik, negara, dan ISP/organisasi.
 *
 * [provider] mencatat endpoint yang menjawab supaya kartu UI bisa menampilkan
 * sumbernya dan diagnosis rate-limit lebih mudah.
 */
data class GeoInfo(
    val ip: String,
    val country: String,
    val countryCode: String,
    val org: String,
    val provider: String,
)

/** Satu endpoint dalam pool beserta nama tampilannya. */
private data class GeoProvider(val name: String, val url: String)

/**
 * Klien geo-IP dengan pooling: round-robin memakai endpoint berikutnya tiap
 * pengambilan, dan endpoint yang baru gagal (termasuk 429) masuk cooldown
 * sehingga tidak dibentrok berulang. Permintaan dijalankan di atas jaringan
 * seluler lewat [CellularNetworkProvider], jadi yang terbaca adalah IP
 * publik seluler, bukan IP WiFi perangkat.
 *
 * Semua parser toleran terhadap perbedaan nama field antar provider; nilai
 * yang tidak ada direpresentasikan sebagai string kosong, bukan null, supaya
 * UI tidak perlu percabangan ekstra.
 */
class GeoIpClient(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private val pool = listOf(
        GeoProvider("ipapi.co", "https://ipapi.co/json/"),
        GeoProvider("ipwho.is", "https://ipwho.is/"),
        GeoProvider("ip.sb", "https://api.ip.sb/geoip"),
        GeoProvider("ipinfo.io", "https://ipinfo.io/json"),
        GeoProvider("ipinfo.io #2", "https://ipinfo.io/json?token=6ad76d4f06e977"),
        GeoProvider("ipinfo.io #3", "https://ipinfo.io/json?token=b40d7bd8e6febf"),
        GeoProvider("ipinfo.io #4", "https://ipinfo.io/json?token=37dfdad87a67bb"),
        GeoProvider("getmyip.pro", "https://api.getmyip.pro/v1/json"),
        GeoProvider("freeipapi", "https://free.freeipapi.com/api/v1/json"),
        GeoProvider("hackmyip", "https://hackmyip.com/api/ip"),
    )

    private val cursor = AtomicInteger(0)

    /** Nama provider yang terakhir berhasil (untuk tampilan). */
    @Volatile
    var lastProvider: String? = null
        private set

    /**
     * Ambil geo-IP sekali. Mencoba pool secara round-robin sampai satu
     * berhasil; provider yang gagal dapat cooldown [COOLDOWN_MS]. Mengembalikan
     * null hanya bila semua endpoint gagal atau jaringan seluler mati.
     */
    suspend fun fetch(): GeoInfo? = withContext(Dispatchers.IO) {
        val net = awaitCellular(8_000L) ?: return@withContext null
        val start = cursor.get().coerceIn(0, pool.lastIndex)
        for (offset in pool.indices) {
            val idx = (start + offset) % pool.size
            val p = pool[idx]
            val until = cooldownUntil[p.name] ?: 0L
            if (now() < until) continue
            val info = runCatching { request(net, p) }.getOrNull()
            if (info != null) {
                cursor.set((idx + 1) % pool.size)
                cooldownUntil.remove(p.name)
                lastProvider = p.name
                return@withContext info
            }
            cooldownUntil[p.name] = now() + COOLDOWN_MS
        }
        null
    }

    /**
     * Minta jaringan seluler sekali pakai. Callback dilepas setelah jawaban
     * (sukses maupun timeout) supaya tidak ada request yang menggantung;
     * permintaan dipicu pengguna atau timer, bukan jalur panas.
     */
    private suspend fun awaitCellular(timeoutMs: Long): Network? {
        val cached = cm.allNetworks.firstOrNull { n ->
            runCatching {
                cm.getNetworkCapabilities(n)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                    cm.getNetworkCapabilities(n)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            }.getOrElse { false }
        }
        if (cached != null) return cached
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        runCatching { cm.unregisterNetworkCallback(this) }
                        if (cont.isActive) cont.resume(network)
                    }
                    override fun onUnavailable() {
                        runCatching { cm.unregisterNetworkCallback(this) }
                        if (cont.isActive) cont.resume(null)
                    }
                }
                cont.invokeOnCancellation { runCatching { cm.unregisterNetworkCallback(cb) } }
                runCatching { cm.requestNetwork(request, cb) }
                    .onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
    }

    private fun request(net: android.net.Network, p: GeoProvider): GeoInfo? {
        val conn = net.openConnection(URL(p.url)) as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            // 429 (dan 5xx) adalah sinyal rate-limit/masalah server: keluar
            // sebagai kegagalan supaya provider ini dapat cooldown, bukan
            // dipakai ulang pada permintaan berikutnya.
            if (code !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return parse(body, p.name)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String, provider: String): GeoInfo? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val o = flatten(root)
        val ip = o.firstString("ip", "ipAddress", "address", "query") ?: return null
        // "country" berarti kode (2 huruf) di ipapi/ipinfo/country.is, tapi
        // nama negara di ipwho.is/ip.sb/getmyip. Bedakan dari panjang, jangan
        // urutan key tetap.
        val rawCountry = o.optString("country").takeIf { it.isNotBlank() }
        val code = o.firstString("country_code", "countryCode", "country_code_alpha2")
            ?: rawCountry?.takeIf { it.length == 2 }
        val country = o.firstString("country_name", "countryName")
            ?: rawCountry?.takeIf { it.length > 2 }
            ?: code?.takeIf { it.isNotEmpty() }?.let { localeCountryName(it) }
            ?: ""
        val conn = o.optJSONObject("connection")
        val rawOrg = o.firstString("org", "isp", "ispName", "organization", "asnOrganization", "asn_organization")
            ?: conn?.firstString("isp", "org", "organization")
            ?: ""
        // ipinfo menyertakan nomor AS di depan ("AS24203 PT XL Axiata")
        // sementara provider lain tidak. Buang prefix itu supaya ISP yang
        // tampil konsisten antar provider.
        val org = rawOrg.replace(Regex("^AS\\d+\\s+"), "")
        return GeoInfo(
            ip = ip,
            country = country,
            countryCode = code.orEmpty().uppercase(),
            org = org,
            provider = provider,
        )
    }

    /**
     * Ratakan objek bersarang satu level ke map pencarian: sebagian provider
     * (mis. hackmyip) membungkus semua di `data`, lokasi di `data.location`,
     * dan jaringan di `data.network`. Key dari level atas tetap menang supaya
     * provider datar tidak berubah perilaku.
     */
    private fun flatten(root: JSONObject): JSONObject {
        val merged = JSONObject(root.toString())
        val data = root.optJSONObject("data") ?: return merged
        listOfNotNull(data, data.optJSONObject("location"), data.optJSONObject("network"))
            .forEach { obj ->
                obj.keys().forEach { k ->
                    if (!merged.has(k)) merged.put(k, obj.opt(k))
                }
            }
        return merged
    }

    /** String non-kosong pertama dari daftar [keys], atau null. */
    private fun JSONObject.firstString(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { k -> optString(k).takeIf { it.isNotBlank() } }

    /**
     * Nama negara dari kode 2 huruf lewat [Locale] (bahasa mengikuti locale
     * perangkat). Dipakai provider yang hanya mengirim kode (ipinfo,
     * country.is) supaya kartu tetap menampilkan nama seperti provider lain.
     * Kode tak dikenal menghasilkan string kosong.
     */
    private fun localeCountryName(code: String): String =
        runCatching { java.util.Locale("", code.uppercase()).displayCountry }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != code.uppercase() }
            .orEmpty()

    private companion object {
        const val CONNECT_TIMEOUT_MS = 6_000
        const val READ_TIMEOUT_MS = 6_000
        const val COOLDOWN_MS = 90_000L
        val cooldownUntil = mutableMapOf<String, Long>()
    }
}

package netflow.dev.ui.screens




import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import android.widget.Toast
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import netflow.dev.network.CellularNetworkProvider
import netflow.dev.network.CellularTechMonitor
import netflow.dev.service.ProxyService
import netflow.dev.ui.components.PowerButton
import netflow.dev.ui.components.PowerState
import netflow.dev.ui.components.Eyebrow
import netflow.dev.ui.components.Overline

import netflow.dev.ui.components.ScreenTitle
import netflow.dev.ui.components.StatusTile
import netflow.dev.ui.components.StatusDot
import netflow.dev.ui.theme.PillShape
import netflow.dev.ui.theme.TileRadius
import netflow.dev.ui.theme.CardRadius
import netflow.dev.ui.theme.Danger
import netflow.dev.ui.theme.Info
import netflow.dev.ui.theme.Success
import netflow.dev.ui.theme.StatDownContent
import netflow.dev.ui.theme.StatDownSurface
import netflow.dev.ui.theme.Accent
import netflow.dev.ui.theme.OutlineSoft
import netflow.dev.ui.theme.OutlineStrong
import netflow.dev.ui.theme.SurfaceHigh
import netflow.dev.ui.theme.SurfaceLow
import netflow.dev.ui.theme.TextMuted
import netflow.dev.ui.theme.TextPrimary
import netflow.dev.ui.theme.TextSecondary
import netflow.dev.ui.theme.PotAccent
import netflow.dev.ui.theme.PotAccentDeep
import netflow.dev.ui.theme.SuccessTint
import netflow.dev.ui.theme.cardShadow
import netflow.dev.ui.theme.glowShadow
import netflow.dev.ui.theme.ThemeMode
import netflow.dev.ui.theme.AccentOn
import netflow.dev.ui.theme.SurfaceMid
import netflow.dev.ui.viewmodel.MainViewModel
import netflow.dev.util.ByteFormatter

/**
 * Home follows a profile-overview layout: an eyebrow plus title header, a
 * sage identity hero carrying the power control, a side-by-side APN / ge-IP
 * pair, a dark traffic summary with share bars, a quick-settings card, and a
 * bottom navigation pill. The whole screen is meant to fit without scrolling;
 * if a card is ever added, remove one.
 */
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onToggle: () -> Unit,
    onOpenListen: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenAuth: () -> Unit,
    onOpenAntiKill: () -> Unit,
    onOpenApnAuto: () -> Unit,
    onOpenDocs: () -> Unit,
    onOpenLogs: () -> Unit,
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    onHeaderClick: () -> Unit,
) {
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val totals by viewModel.totals.collectAsStateWithLifecycle()
    val geoInfo by viewModel.geoInfo.collectAsStateWithLifecycle()
    val geoLoading by viewModel.geoLoading.collectAsStateWithLifecycle()
    val currentApn by viewModel.currentApn.collectAsStateWithLifecycle()
    val apnBusy by viewModel.apnBusy.collectAsStateWithLifecycle()
    val cellular by viewModel.cellular.collectAsStateWithLifecycle()
    val cellularTech by viewModel.cellularTech.collectAsStateWithLifecycle()
    val autoStart by viewModel.autoStartOnBoot.collectAsStateWithLifecycle()
    val rootEnabled by viewModel.rootEnabled.collectAsStateWithLifecycle()
    val daemonEnabled by viewModel.daemonEnabled.collectAsStateWithLifecycle()
    val rootAutoStart by viewModel.rootAutoStart.collectAsStateWithLifecycle()
    val interfaces by viewModel.interfaces.collectAsStateWithLifecycle()

    val powerState = when (serviceState) {
        is ProxyService.State.Running -> PowerState.On
        is ProxyService.State.Starting -> PowerState.Starting
        is ProxyService.State.Paused -> PowerState.Paused
        is ProxyService.State.Error -> PowerState.Error
        else -> PowerState.Off
    }
    val stateName = when (serviceState) {
        is ProxyService.State.Running -> "Online"
        is ProxyService.State.Starting -> "Starting"
        is ProxyService.State.Paused -> "Paused"
        is ProxyService.State.Error -> "Error"
        else -> "Offline"
    }
    // IP yang ditampilkan di bawah state: IP WiFi bila WiFi hidup, karena itu
    // alamat yang benar-benar bisa dihubungi klien LAN. List interface sudah
    // memfilter isUp, jadi WiFi mati tidak pernah muncul di sini dan tampilan
    // jatuh ke 0.0.0.0 (bind sebenarnya, semua antarmuka). Hanya IP, tanpa
    // port: port ada di URL yang disalin (lihat [socksUrl] di bawah).
    val wifiIp = interfaces.firstOrNull { it.isWifi }?.address ?: "0.0.0.0"
    // Running / Starting / Paused show the address; Error carries the
    // message, which is the only line worth showing in that state.
    val stateLine = when (val s = serviceState) {
        is ProxyService.State.Running -> wifiIp
        is ProxyService.State.Starting -> wifiIp
        is ProxyService.State.Paused -> wifiIp
        is ProxyService.State.Error -> s.message.take(90)
        else -> "not bound"
    }
    // URL siap-tempel untuk klien SOCKS5; kosong saat tidak ada endpoint.
    // Port diekstrak terpisah: cabang when dengan beberapa tipe tidak
    // melakukan smart-cast ke properti bersama.
    val boundPort = when (val s = serviceState) {
        is ProxyService.State.Running -> s.port
        is ProxyService.State.Starting -> s.port
        is ProxyService.State.Paused -> s.port
        else -> null
    }
    val socksUrl = boundPort?.let { "socks5://$wifiIp:$it" }
    val stateLineIsError = serviceState is ProxyService.State.Error

    // APN kartu menarik data sekali saat layar tampil plus tiap proxy naik
    // (kolektor di ViewModel). Tanpa polling: APN bisa berubah di luar app,
    // jadi refresh terjadi saat app dibuka dan setelah aksi switch.
    LaunchedEffect(Unit) { viewModel.refreshApn() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .padding(top = 8.dp, bottom = 12.dp),
    ) {
        ProfileHeader(
            tech = cellularTech,
            cellular = cellular,
            themeMode = themeMode,
            onCycleTheme = onCycleTheme,
            onNetworkClick = onHeaderClick,
        )
        Spacer(Modifier.height(12.dp))
        // Profile layout: scrollable identity content with a fixed bottom nav
        // pill. The middle takes the slack, the pill never scrolls away.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IdentityHero(
                powerState = powerState,
                stateName = stateName,
                stateLine = stateLine,
                stateLineIsError = stateLineIsError,
                socksUrl = socksUrl,
                onToggle = onToggle,
            )
            // Kartu APN sekarang lebar penuh dengan hierarki jelas: label +
              // nama APN besar, nilai apn (mono) dan tipe (pill), lalu dua
            // aksi. Total uploaded jadi tile lebar penuh di bawahnya.
            // APN (kiri) dan Public IP (kanan) berdampingan dengan bobot sama.
            // Keduanya mode compact: font dikecilkan dan tombol APN ditumpuk
            // supaya tidak ada teks terpotong di kolom ~165dp. IntrinsicSize.
            // Min + fillMaxHeight menyamakan tinggi kedua kartu (fillMaxHeight
            // sendiri tak berlaku di dalam verticalScroll: constraint-nya
            // unbounded, jadi tinggi baris harus dipaku lewat intrinsic).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ApnCard(
                    apn = currentApn?.apn,
                    ok = currentApn?.ok ?: false,
                    error = currentApn?.error,
                    busy = apnBusy,
                    onSwitch = { viewModel.switchApn() },
                    onSettings = onOpenApnAuto,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    compact = true,
                )
                GeoCard(
                    info = geoInfo,
                    loading = geoLoading,
                    onRefresh = { viewModel.refreshGeo() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    compact = true,
                )
            }
            TrafficSummaryCard(
                bytesDown = totals.bytesDown,
                bytesUp = totals.bytesUp,
                activeConnections = totals.active,
            )
            QuickSettingsCard(
                autoStart = autoStart,
                onAutoStartToggle = { viewModel.setAutoStartOnBoot(it) },
                rootEnabled = rootEnabled,
                onRootToggle = { on ->
                    viewModel.setRootEnabled(on)
                    if (on) viewModel.requestRoot()
                },
                daemonEnabled = daemonEnabled,
                onDaemonToggle = viewModel::setDaemonEnabled,
                rootAutoStart = rootAutoStart,
                onRootAutoStartToggle = viewModel::setRootAutoStart,
            )
        }
        Spacer(Modifier.height(10.dp))
        BottomNavPill(
            onOpenListen = onOpenListen,
            onOpenDevices = onOpenDevices,
            onOpenAuth = onOpenAuth,
            onOpenAntiKill = onOpenAntiKill,
            onOpenDocs = onOpenDocs,
            onOpenLogs = onOpenLogs,
        )
    }
}

@Composable
private fun ProfileHeader(
    tech: CellularTechMonitor.TechState,
    cellular: CellularNetworkProvider.State,
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    onNetworkClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Eyebrow(text = "NETFLOW")
            Spacer(Modifier.height(1.dp))
            ScreenTitle(text = "Socks5 Over Cellular")
            // Device-cellular state, tappable to re-open the perms dialog:
            // the tech label needs READ_BASIC_PHONE_STATE (auto-granted normal
            // perm, API 33+) or READ_PHONE_STATE (runtime, pre-33).
            val dotColor = when (tech) {
                is CellularTechMonitor.TechState.Tech -> Success
                is CellularTechMonitor.TechState.OperatorOnly -> Success
                CellularTechMonitor.TechState.DataOff -> Danger
                CellularTechMonitor.TechState.Unknown -> when (cellular) {
                    is CellularNetworkProvider.State.Available -> Success
                    CellularNetworkProvider.State.Requesting -> Info
                    else -> TextMuted
                }
            }
            val label = when (tech) {
                is CellularTechMonitor.TechState.Tech ->
                    if (tech.operator.isBlank()) tech.label
                    else "${tech.operator} ${tech.label}"
                is CellularTechMonitor.TechState.OperatorOnly -> tech.operator
                CellularTechMonitor.TechState.DataOff -> "data off"
                CellularTechMonitor.TechState.Unknown -> when (cellular) {
                    is CellularNetworkProvider.State.Available -> "mobile"
                    CellularNetworkProvider.State.Requesting -> "wait"
                    else -> "-"
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(PillShape)
                    .clickable(onClick = onNetworkClick)
                    .padding(horizontal = 2.dp, vertical = 6.dp),
            ) {
                StatusDot(color = dotColor)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    color = if (tech is CellularTechMonitor.TechState.DataOff) Danger
                    else TextSecondary,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    maxLines = 1,
                )
            }
        }
        ThemeToggleButton(themeMode = themeMode, onClick = onCycleTheme)
    }
}

@Composable
private fun IdentityHero(
    powerState: PowerState,
    stateName: String,
    stateLine: String,
    stateLineIsError: Boolean,
    socksUrl: String?,
    onToggle: () -> Unit,
) {
    // Badge status mengikuti perlakuan chip kartu APN: wash tipis dari
    // StatDownContent + teks StatDownContent (tanpa aksen kuning).
    val statusLabel = when (powerState) {
        PowerState.On -> "CONNECTED"
        PowerState.Starting -> "STARTING"
        PowerState.Paused -> "PAUSED"
        PowerState.Error -> "ERROR"
        PowerState.Off -> "OFFLINE"
    }
    // Dot berdenyut hanya saat benar-benar hidup.
    val pulse by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot-alpha",
    )
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Same card shape as every other card on the screen: 26dp radius,
            // all four corners equal. The plant arch that used to frame the
            // power control was the style's signature geometry, but it read as
            // a notch cut out of the card and made the hero the one surface
            // that did not match the rest, so it is reverted to the shared
            // CardRadius token. Shape.kt no longer carries an arch.
            .cardShadow(RoundedCornerShape(CardRadius))
            .clip(RoundedCornerShape(CardRadius))
            .background(StatDownSurface)
            .border(1.dp, OutlineSoft, RoundedCornerShape(CardRadius))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PowerButton(
                state = powerState,
                statusLabel = stateName,
                onClick = onToggle,
                diameter = 108.dp,
                compact = true,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Satu baris: badge status di kiri, radio + operator di kanan
                // (dipisah titik) supaya kartu tetap pendek.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(
                        label = statusLabel,
                        dotAlpha = if (powerState == PowerState.On) pulse else 1f,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stateName,
                    color = StatDownContent,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                // Saat ada endpoint: IP saja (tanpa port) di pil gelap,
                // ditap untuk menyalin URL lengkap (socks5://ip:port).
                // Toast mengonfirmasi salinan. Pesan error / "not bound"
                // tetap teks polos supaya tidak jadi pil panjang.
                if (socksUrl != null) {
                    // Satu pil: IP di kiri, ikon salin di kanan. Seluruh pil
                    // bisa ditap; ikon menandakan aksinya.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(PillShape)
                            .background(StatDownContent.copy(alpha = 0.10f))
                            .clickable {
                                clipboard.setText(AnnotatedString(socksUrl))
                                Toast.makeText(
                                    context,
                                    "Copied $socksUrl",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = stateLine,
                            color = StatDownContent,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            ),
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy socks5 URL",
                            tint = StatDownContent.copy(alpha = 0.72f),
                            modifier = Modifier.size(13.dp),
                        )
                    }
                } else {
                    Text(
                        text = stateLine,
                        color = if (stateLineIsError) Danger else TextSecondary,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        ),
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

/**
 * Badge status hero: wash tipis + teks StatDownContent, persis perlakuan
 * chip kartu APN. Dot berdenyut saat online.
 */
@Composable
private fun StatusBadge(label: String, dotAlpha: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(PillShape)
            .background(StatDownContent.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(StatDownContent.copy(alpha = dotAlpha)),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            color = StatDownContent,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
            ),
        )
    }
}

@Composable
private fun GeoCard(
    info: netflow.dev.geo.GeoInfo?,
    loading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    // Mode setengah lebar: font dikecilkan supaya IP panjang (mis.
    // 112.215.66.117) tetap utuh dalam kolom ~165dp, tidak dipotong.
    compact: Boolean = false,
) {
    // Warna: permukaan sage yang sama dengan kartu APN di sebelahnya
    // (StatDownSurface + StatDownContent), sehingga kedua kartu tampil satu
    // pasangan. Hijau disimpan untuk aksi, jadi yang membedakan kartu ini
    // adalah tile ikon dan tombol aksinya.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardShadow(RoundedCornerShape(CardRadius))
            .clip(RoundedCornerShape(CardRadius))
            .background(StatDownSurface)
            .clickable(onClick = onRefresh)
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = 13.dp),
    ) {
        // Baris label: tile status kebun (rubrik) + label, sama seperti kartu
        // APN, memakai [StatusTile] bersama supaya ukuran ikon dan geometri
        // lingkaran identik. Tint-nya 12% StatDownContent, bukan warna rubrik
        // baru: keduanya berdiri di atas permukaan sage-nya sendiri.
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusTile(
                tint = StatDownContent.copy(alpha = 0.12f),
                content = StatDownContent,
                icon = Icons.Rounded.Public,
                modifier = Modifier.size(if (compact) 24.dp else 28.dp),
            )
            Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
            Overline(
                text = "PUBLIC IP",
                color = TextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (loading) {
                Text(
                    text = "reading...",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
        Text(
            text = info?.ip ?: "-",
            color = StatDownContent,
            // 18sp di mode compact: 14 karakter IP tetap utuh di kolom
            // separuh; headlineSmall (24sp) akan terpotong.
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 18.sp else 24.sp,
                letterSpacing = (-0.5).sp,
            ),
            maxLines = 1,
        )
        Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (info != null && info.countryCode.isNotEmpty()) {
                Text(
                    text = flagEmoji(info.countryCode),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(6.dp))
            }
            val countryText = when {
                info == null -> if (loading) "looking up..." else "tap to retry"
                info.country.isNotEmpty() -> info.country
                else -> "unknown country"
            }
            Text(
                text = countryText,
                color = StatDownContent,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (compact) 13.sp else 15.sp,
                ),
                maxLines = 1,
            )
            if (info != null && info.countryCode.isNotEmpty()) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = info.countryCode,
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (compact) 10.sp else 11.sp,
                    ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.CellTower,
                contentDescription = null,
                tint = StatDownContent,
                modifier = Modifier.size(if (compact) 14.dp else 16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = info?.org?.takeIf { it.isNotEmpty() } ?: "unknown provider",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = if (compact) 11.sp else 13.sp,
                ),
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        // Dorong bagian bawah ke dasar kartu supaya sejajar dengan tombol
        // kartu APN di sebelahnya (kartu kini selalu setinggi).
        Spacer(Modifier.weight(1f))
        if (info != null) {
            Text(
                text = "via ${info.provider}",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
        }
        // Refresh = aksi utama kartu ini, jadi bentuk dan warnanya sama dengan
        // tombol Switch kartu APN di sebelahnya: pil terisi hijau dengan
        // glow. Kedua kartu lalu terbaca satu pasangan, bukan satu rubrik.
        CardAction(
            label = "Refresh",
            icon = Icons.Rounded.Refresh,
            content = Accent,
            on = AccentOn,
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            filled = true,
            spinning = loading,
            onClick = onRefresh,
        )
    }
}

/** Kode negara dua huruf (mis. "ID") menjadi emoji bendera regional indicator. */
private fun flagEmoji(code: String): String =
    code.uppercase()
        .filter { it in 'A'..'Z' }
        .map { Character.toChars(0x1F1E6 + (it - 'A')) }
        .joinToString("") { String(it) }

/**
 * Kartu APN preferensi aktif, lebar penuh. Permukaan memakai token stat
 * "downloaded" (mint di tema terang) supaya posisinya tetap terbaca sebagai
 * pengganti kartu tersebut. Tombol Switch memutar ke APN lain lewat root,
 * Setting membuka layar APN sistem.
 */
@Composable
private fun ApnCard(
    apn: netflow.dev.apn.model.Apn?,
    ok: Boolean,
    error: String?,
    busy: Boolean,
    onSwitch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    // Mode setengah lebar: nama lebih kecil dan tombol ditumpuk vertikal
    // supaya keduanya tetap penuh label dalam kolom ~165dp.
    compact: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardShadow(RoundedCornerShape(CardRadius))
            .clip(RoundedCornerShape(CardRadius))
            .background(StatDownSurface)
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = 13.dp),
    ) {
        // Baris label: tile status kebun (rubrik) + eyebrow + status di kanan.
        // Sama seperti kartu Public IP, memakai [StatusTile] bersama supaya
        // ukuran ikon dan geometri lingkaran identik di kedua kartu. Tint-nya
        // 12% StatDownContent, bukan warna rubrik baru: kartu ini berdiri di
        // atas permukaan sage-nya sendiri, jadi wash hijau tipis sudah cukup
        // untuk memisahkan tile dari latar tanpa menambah warna asing.
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusTile(
                tint = StatDownContent.copy(alpha = 0.12f),
                content = StatDownContent,
                icon = Icons.Rounded.SimCard,
                modifier = Modifier.size(if (compact) 24.dp else 28.dp),
            )
            Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
            Overline(
                text = "CURRENT APN",
                color = TextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (busy) {
                Text(
                    text = "reading...",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
        if (apn != null) {
            Text(
                text = apn.name,
                color = StatDownContent,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 17.sp else 22.sp,
                ),
                maxLines = 2,
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = apn.apnName,
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (compact) 10.sp else 11.sp,
                    ),
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(StatDownContent.copy(alpha = 0.10f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = apn.apnType,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        } else {
            Text(
                text = when {
                    busy -> "reading..."
                    !ok && error != null -> error
                    !ok -> "Root is required to read the active APN."
                    else -> "No active APN"
                },
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = if (compact) 11.sp else 13.sp,
                ),
                maxLines = 3,
            )
        }
        Spacer(Modifier.height(if (compact) 10.dp else 12.dp))
        // Dorong tombol ke dasar kartu supaya rata dengan baris "via" kartu
        // Public IP di sebelahnya.
        Spacer(Modifier.weight(1f))
        if (compact) {
            // Kolom sempit: tombol ditumpuk supaya label tetap utuh.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CardAction(
                    label = "Switch",
                    icon = Icons.Rounded.Sync,
                    content = Accent,
                    on = AccentOn,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    filled = true,
                    spinning = busy,
                    onClick = onSwitch,
                )
                CardAction(
                    label = "Setting",
                    icon = Icons.Rounded.Settings,
                    content = Accent,
                    on = AccentOn,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = true,
                    filled = false,
                    onClick = onSettings,
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Switch = aksi utama (terisi), Setting = sekunder (outline).
                // Bobot sama supaya lebarnya identik.
                CardAction(
                    label = "Switch",
                    icon = Icons.Rounded.Sync,
                    content = Accent,
                    on = AccentOn,
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    filled = true,
                    spinning = busy,
                    onClick = onSwitch,
                )
                CardAction(
                    label = "Setting",
                    icon = Icons.Rounded.Settings,
                    content = Accent,
                    on = AccentOn,
                    modifier = Modifier.weight(1f),
                    enabled = true,
                    filled = false,
                    onClick = onSettings,
                )
            }
        }
    }
}

/**
 * Tombol aksi kartu. Warna mengikuti konten kartunya melalui [content]
 * (foreground) dan [on] (di atas tombol terisi); [filled] membedakan aksi
 * utama dari sekunder. [spinning] memutar ikon terus-menerus (dipakai tombol
 * Switch saat rotasi APN berjalan).
 */
@Composable
private fun CardAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: Color,
    on: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    filled: Boolean,
    spinning: Boolean = false,
    onClick: () -> Unit,
) {
    // Saat berputar tombol tetap warna penuh (hanya tidak bisa ditap dua
    // kali); redup hanya untuk keadaan disabled biasa.
    val tint = if (enabled || spinning) content else content.copy(alpha = 0.4f)
    val spin = rememberInfiniteTransition(label = "action-spin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
        ),
        label = "action-spin-angle",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(PillShape)
            .then(
                if (filled) {
                    // A filled control is the style's primary action, so it
                    // carries the soft green glow, not the card whisper.
                    Modifier.glowShadow(PillShape).background(tint)
                } else {
                    Modifier.border(1.dp, tint.copy(alpha = 0.5f), PillShape)
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            // Di atas tombol terisi, ikon memakai warna on-fill yang opak
            // (permukaan kartu translusien di tema gelap -> tak terlihat).
            tint = if (filled) on else tint,
            modifier = Modifier
                .size(16.dp)
                .then(if (spinning) Modifier.rotate(angle) else Modifier),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = if (filled) on else tint,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun TrafficSummaryCard(
    bytesDown: Long,
    bytesUp: Long,
    activeConnections: Int,
) {
    val total = bytesDown + bytesUp
    // Canvas draw lambdas are not composable contexts, so capture the theme
    // getter first. Deeper than the card so an empty bar still reads as a
    // track rather than as blank space.
    val trackColor = SurfaceMid
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardShadow(RoundedCornerShape(CardRadius))
            .clip(RoundedCornerShape(CardRadius))
            .background(SurfaceLow)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline(
                text = "TRAFFIC",
                color = TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$activeConnections active",
                color = TextMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.height(12.dp))
        ShareBar(
            label = "Down",
            bytes = bytesDown,
            total = total,
            tint = PotAccentDeep,
            trackColor = trackColor,
        )
        Spacer(Modifier.height(8.dp))
        ShareBar(
            label = "Up",
            bytes = bytesUp,
            total = total,
            tint = PotAccent,
            trackColor = trackColor,
        )
    }
}

@Composable
private fun ShareBar(
    label: String,
    bytes: Long,
    total: Long,
    tint: Color,
    trackColor: Color,
) {
    val fraction = if (total > 0) bytes.toFloat() / total.toFloat() else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(44.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(CircleShape),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val y = size.height / 2
                drawLine(
                    color = trackColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(0f, y),
                    end = Offset(size.width * fraction, y),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = ByteFormatter.bytes(bytes),
            color = TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(72.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun QuickSettingsCard(
    autoStart: Boolean,
    onAutoStartToggle: (Boolean) -> Unit,
    rootEnabled: Boolean,
    onRootToggle: (Boolean) -> Unit,
    daemonEnabled: Boolean,
    onDaemonToggle: (Boolean) -> Unit,
    rootAutoStart: Boolean,
    onRootAutoStartToggle: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardShadow(RoundedCornerShape(CardRadius))
            .clip(RoundedCornerShape(CardRadius))
            .background(SurfaceLow)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Overline(
            text = "QUICK SETTINGS",
            color = TextMuted,
        )
        Spacer(Modifier.height(6.dp))
        // Auth lives on its own screen with the credential fields next to the
        // toggle; duplicating it here without those fields was noise.
        SettingsToggleRow(
            icon = Icons.Rounded.Replay,
            title = "Start after reboot",
            subtitle = "launch on boot",
            checked = autoStart,
            onCheckedChange = onAutoStartToggle,
        )
        HorizontalDivider(color = OutlineSoft)
        // Kelompok root: ketiga toggle bawaan satu permintaan operator,
        // dipisah dari baris boot di atas.
        Overline(
            text = "ROOT",
            color = TextMuted,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
        SettingsToggleRow(
            icon = Icons.Rounded.Shield,
            title = "Automatic Anti kill",
            subtitle = "apply restrictions with root",
            checked = rootEnabled,
            onCheckedChange = onRootToggle,
        )
        SettingsToggleRow(
            icon = Icons.Rounded.RestartAlt,
            title = "Restart after kill",
            subtitle = "root watchdog daemon",
            checked = daemonEnabled,
            onCheckedChange = onDaemonToggle,
        )
        SettingsToggleRow(
            icon = Icons.Rounded.PowerSettingsNew,
            title = "Start after root",
            subtitle = "start proxy once root is granted",
            checked = rootAutoStart,
            onCheckedChange = onRootAutoStartToggle,
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tile status kebun: hijau daun saat aktif, abu netral saat mati.
        // Tanpa tile hijau di sini kartu kehabisan cara mengatakan "sudah
        // dilakukan" tanpa membaca label.
        StatusTile(
            tint = if (checked) SuccessTint else SurfaceMid,
            content = if (checked) Success else TextSecondary,
            icon = icon,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SurfaceLow,
                checkedTrackColor = Accent,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceHigh,
                uncheckedBorderColor = OutlineStrong,
            ),
        )
    }
}

@Composable
private fun BottomNavPill(
    onOpenListen: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenAuth: () -> Unit,
    onOpenAntiKill: () -> Unit,
    onOpenDocs: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardShadow(PillShape)
            .clip(PillShape)
            .background(SurfaceLow)
            .border(1.dp, OutlineSoft, PillShape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        NavPillItem(
            icon = Icons.Rounded.Router,
            label = "Listen",
            onClick = onOpenListen,
            modifier = Modifier.weight(1f),
        )
        NavPillItem(
            icon = Icons.Rounded.Devices,
            label = "Devices",
            onClick = onOpenDevices,
            modifier = Modifier.weight(1f),
        )
        NavPillItem(
            icon = Icons.Rounded.Lock,
            label = "Auth",
            onClick = onOpenAuth,
            modifier = Modifier.weight(1f),
        )
        NavPillItem(
            icon = Icons.Rounded.Shield,
            label = "Anti-Kill",
            onClick = onOpenAntiKill,
            modifier = Modifier.weight(1f),
        )
        NavPillItem(
            icon = Icons.Rounded.Terminal,
            label = "Logs",
            onClick = onOpenLogs,
            modifier = Modifier.weight(1f),
        )
        NavPillItem(
            icon = Icons.Rounded.MenuBook,
            label = "Docs",
            onClick = onOpenDocs,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NavPillItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(TileRadius))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun ThemeToggleButton(themeMode: ThemeMode, onClick: () -> Unit) {
    val icon = when (themeMode) {
        ThemeMode.System -> Icons.Rounded.BrightnessAuto
        ThemeMode.Light -> Icons.Rounded.LightMode
        ThemeMode.Dark -> Icons.Rounded.DarkMode
    }
    val description = when (themeMode) {
        ThemeMode.System -> "Theme: follow system"
        ThemeMode.Light -> "Theme: light"
        ThemeMode.Dark -> "Theme: dark"
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(SurfaceLow)
            .border(1.dp, OutlineSoft, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

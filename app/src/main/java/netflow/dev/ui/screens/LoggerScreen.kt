package netflow.dev.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import netflow.dev.ui.components.ScreenHeader
import netflow.dev.ui.theme.PillShape
import netflow.dev.ui.theme.TileRadius
import netflow.dev.ui.theme.CardRadius
import netflow.dev.ui.theme.Danger
import netflow.dev.ui.theme.SurfaceLow
import netflow.dev.ui.theme.TextMuted
import netflow.dev.ui.theme.TextPrimary
import netflow.dev.ui.theme.TextSecondary
import netflow.dev.ui.theme.Info
import netflow.dev.ui.theme.DangerTint
import netflow.dev.ui.theme.Warning
import netflow.dev.ui.theme.WarningTint
import netflow.dev.ui.theme.Success
import netflow.dev.ui.theme.SuccessTint
import netflow.dev.ui.theme.levelColor
import netflow.dev.ui.theme.SurfaceHigh
import netflow.dev.util.AppLog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Logger in-app: menampilkan ring buffer [AppLog] (semua event penting app:
 * siklus proxy, listener, DNS, enforcement, daemon, rotasi, geo). Daftar
 * auto-scroll ke entri terbaru dan bisa difilter per level.
 *
 * Tombol "Device logcat" mengambil keluaran `logcat -d` penuh lewat shell
 * root (permission `READ_LOGS` adalah signature-level, jadi hanya root yang
 * bisa). Tanpa root, tombol itu tidak muncul.
 */
@Composable
fun LoggerScreen(
    rootGranted: Boolean,
    onFetchDeviceLog: suspend () -> String?,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val entries by AppLog.entries.collectAsStateWithLifecycle()
    var minLevel by remember { mutableStateOf(AppLog.Level.V) }
    var deviceLog by remember { mutableStateOf<String?>(null) }
    var fetching by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT) }

    val filtered = remember(entries, minLevel) {
        entries.filter { it.level.ordinal >= minLevel.ordinal }
    }

    // Auto-scroll ke entri terbaru setiap daftar berubah.
    androidx.compose.runtime.LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty()) listState.animateScrollToItem(filtered.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .padding(top = 4.dp, bottom = 12.dp),
    ) {
        ScreenHeader(
            title = "Logger",
            eyebrow = "LOG",
            onBack = onBack,
            action = {
                androidx.compose.material3.IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(AppLog.asText()))
                    },
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy log",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                androidx.compose.material3.IconButton(onClick = { AppLog.clear() }) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = "Clear log",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            LevelChip("All", AppLog.Level.V, minLevel) { minLevel = it }
            LevelChip("I+", AppLog.Level.I, minLevel) { minLevel = it }
            LevelChip("W+", AppLog.Level.W, minLevel) { minLevel = it }
            LevelChip("E", AppLog.Level.E, minLevel) { minLevel = it }
            Spacer(Modifier.weight(1f))
            if (rootGranted) {
                LogAction(
                    label = if (fetching) "..." else "Device logcat",
                    onClick = {
                        fetching = true
                        scope.launch {
                            deviceLog = onFetchDeviceLog()
                            fetching = false
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty() && deviceLog == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CardRadius))
                    .background(SurfaceLow)
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Belum ada log. Jalankan proxy atau operasi APN, " +
                        "event akan muncul di sini.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CardRadius))
                    .background(SurfaceLow)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                items(filtered) { e ->
                    LogRow(entry = e, timeFmt = timeFmt)
                }
                deviceLog?.let { raw ->
                    item {
                        Text(
                            text = "---- device logcat (root) ----",
                            color = Info,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    // logcat device bisa sangat panjang; batasi tampilan ke
                    // 400 baris terakhir supaya list tetap ringan.
                    items(raw.lineSequence().toList().takeLast(400)) { line ->
                        Text(
                            text = line,
                            color = TextMuted,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelChip(
    label: String,
    level: AppLog.Level,
    current: AppLog.Level,
    onSelect: (AppLog.Level) -> Unit,
) {
    val active = current == level
    // Chip terpilih memakai rubrik levelnya sendiri, supaya memilih "E"
    // terbaca terakota dan "W" terbaca amber, bukan sekadar hijau aksi.
    val activeTint = when (level) {
        AppLog.Level.E -> DangerTint
        AppLog.Level.W -> WarningTint
        AppLog.Level.I, AppLog.Level.D -> SuccessTint
        AppLog.Level.V -> SurfaceHigh
    }
    val activeContent = when (level) {
        AppLog.Level.E -> Danger
        AppLog.Level.W -> Warning
        AppLog.Level.I, AppLog.Level.D -> Success
        AppLog.Level.V -> TextSecondary
    }
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(if (active) activeTint else SurfaceLow)
            .clickable { onSelect(level) }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            color = if (active) activeContent else TextSecondary,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun LogAction(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(PillShape)
            .background(SurfaceLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Rounded.PhoneAndroid,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            color = TextSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * Satu baris log: waktu (mono, muted), chip level berwarna, tag berwarna,
 * pesan. Warna level mengikuti token tema ([levelColor]) supaya W/E langsung
 * terbaca saat scroll. Baris warn/error diberi wash tipis sebagai penanda
 * tambahan yang tidak bergantung pada warna saja.
 */
@Composable
private fun LogRow(entry: AppLog.Entry, timeFmt: SimpleDateFormat) {
    val color = levelColor(entry.level)
    val alert = entry.level == AppLog.Level.W || entry.level == AppLog.Level.E
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TileRadius))
            .background(if (alert) color.copy(alpha = 0.07f) else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = timeFmt.format(Date(entry.timeMs)),
            color = TextMuted,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
            ),
        )
        Spacer(Modifier.width(6.dp))
        // Lebar chip dikunci supaya V/D/I/W/E semuanya sama: huruf satu karak-
        // ter punya advance width berbeda di font proporsional, jadi tanpa
        // kunci lebar "D" akan lebih lebar dari "I".
        Box(
            modifier = Modifier
                .width(20.dp)
                .clip(PillShape)
                .background(color.copy(alpha = 0.18f))
                .padding(horizontal = 4.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = entry.level.name,
                color = color,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                ),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.tag,
                color = color,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                ),
            )
            Text(
                text = entry.msg,
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            )
        }
    }
}

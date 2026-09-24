package netflow.dev.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import netflow.dev.ui.components.ScreenHeader
import netflow.dev.ui.components.StatusTile
import netflow.dev.ui.components.Overline
import netflow.dev.ui.theme.PillShape
import netflow.dev.ui.theme.CardRadius
import netflow.dev.ui.theme.OutlineSoft
import netflow.dev.ui.theme.OutlineStrong
import netflow.dev.ui.theme.SurfaceLow
import netflow.dev.ui.theme.SurfaceMid
import netflow.dev.ui.theme.Success
import netflow.dev.ui.theme.SuccessTint
import netflow.dev.ui.theme.TextMuted
import netflow.dev.ui.theme.TextPrimary
import netflow.dev.ui.theme.TextSecondary
import netflow.dev.ui.theme.Accent
import netflow.dev.ui.theme.TileRadius
import netflow.dev.util.ApnPreferences
import netflow.dev.util.ApnRotateMode
import netflow.dev.ui.viewmodel.MainViewModel

/**
 * Pengaturan rotasi IP otomatis. Loop-nya milik ProxyService, halaman ini
 * hanya menulis pref (enable, mode, interval detik); perubahan berlaku di
 * tick berikutnya tanpa restart proxy.
 */
@Composable
fun ApnAutoScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val enabled by viewModel.apnAutoEnabled.collectAsStateWithLifecycle()
    val mode by viewModel.apnAutoMode.collectAsStateWithLifecycle()
    val interval by viewModel.apnAutoInterval.collectAsStateWithLifecycle()

    var secondsText by remember {
        mutableStateOf(ApnPreferences.autoIntervalSeconds(context).toString())
    }
    // Pref bisa berubah dari tempat lain (mis. chip), jaga field tetap sinkron.
    LaunchedEffect(interval) {
        if (secondsText.toIntOrNull() != interval) secondsText = interval.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = "Auto switch",
            eyebrow = "APN",
            onBack = onBack,
        )
        Spacer(Modifier.height(10.dp))

        // Kartu penjelasan.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardRadius))
                .background(SurfaceMid)
                .border(1.dp, OutlineSoft, RoundedCornerShape(CardRadius))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Overline(
                text = "HOW IT WORKS",
                color = TextMuted,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Rotates the cellular IP on a timer and runs only while the proxy is up.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(10.dp))

        // Kartu enable + mode.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardRadius))
                .background(SurfaceLow)
                .border(1.dp, OutlineSoft, RoundedCornerShape(CardRadius))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusTile(
                    tint = SuccessTint,
                    content = Success,
                    icon = Icons.Rounded.Sync,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto switch",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Text(
                        text = "change the IP on a timer",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = viewModel::setApnAutoEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SurfaceLow,
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = SurfaceLow,
                        uncheckedBorderColor = OutlineStrong,
                    ),
                )
            }

            HorizontalDivider(color = OutlineSoft)
            Spacer(Modifier.height(10.dp))

            Overline(
                text = "METHOD",
                color = TextMuted,
            )
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ApnRotateMode.entries.forEachIndexed { index, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { viewModel.setApnAutoMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ApnRotateMode.entries.size,
                        ),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = Accent.copy(alpha = 0.2f),
                            activeContentColor = TextPrimary,
                            activeBorderColor = Accent,
                            inactiveContainerColor = SurfaceLow,
                            inactiveContentColor = TextSecondary,
                            inactiveBorderColor = OutlineStrong,
                        ),
                    ) {
                        Text(m.label, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = mode.method,
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = mode.description,
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(10.dp))

        // Kartu interval.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardRadius))
                .background(SurfaceLow)
                .border(1.dp, OutlineSoft, RoundedCornerShape(CardRadius))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Overline(
                text = "INTERVAL",
                color = TextMuted,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                shape = RoundedCornerShape(TileRadius),
                value = secondsText,
                onValueChange = { raw ->
                    val digits = raw.filter(Char::isDigit).take(4)
                    secondsText = digits
                    digits.toIntOrNull()?.let(viewModel::setApnAutoInterval)
                },
                label = { Text("Seconds between switches") },
                suffix = { Text("seconds") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    color = TextPrimary,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = OutlineStrong,
                    disabledBorderColor = OutlineSoft,
                    focusedLabelColor = Accent,
                    unfocusedLabelColor = TextSecondary,
                    cursorColor = Accent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    disabledTextColor = TextMuted,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 30, 60).forEach { s ->
                    Text(
                        text = "${s}s",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clip(PillShape)
                            .border(1.dp, OutlineStrong, PillShape)
                            .clickable {
                                secondsText = s.toString()
                                viewModel.setApnAutoInterval(s)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (enabled) {
                    "Switching every ${interval}s while the proxy is up."
                } else {
                    "Off. The proxy will not switch the IP until this is enabled."
                },
                // Status aktif vs mati harus beda warna, kalau tidak keduanya
                // terbaca sama padahal artinya berlawanan.
                color = if (enabled) Success else TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

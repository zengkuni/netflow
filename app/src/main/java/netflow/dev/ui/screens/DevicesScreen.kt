package netflow.dev.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import netflow.dev.proxy.ConnectionRegistry
import netflow.dev.ui.components.HeroCard
import netflow.dev.ui.components.Overline
import netflow.dev.ui.components.StatusDot
import netflow.dev.ui.components.ScreenHeader
import netflow.dev.ui.theme.PillShape
import netflow.dev.ui.theme.TileRadius
import netflow.dev.ui.theme.HeroOnPrimary
import netflow.dev.ui.theme.HeroOnSecondary
import netflow.dev.ui.theme.Success
import netflow.dev.ui.theme.Accent
import netflow.dev.ui.theme.OutlineSoft
import netflow.dev.ui.theme.SurfaceLow
import netflow.dev.ui.theme.SurfaceMid
import netflow.dev.ui.theme.TextMuted
import netflow.dev.ui.theme.TextPrimary
import netflow.dev.ui.theme.TextSecondary
import netflow.dev.ui.viewmodel.MainViewModel
import netflow.dev.util.ByteFormatter

@Composable
fun DevicesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val devices by viewModel.devices.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .padding(top = 4.dp, bottom = 12.dp),
    ) {
        ScreenHeader(
            title = "Connected devices",
            eyebrow = "LAN",
            onBack = onBack,
            action = {
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(SurfaceLow)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = devices.size.toString(),
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        val activeCount = devices.count { it.activeConnections > 0 }
        HeroCard {
            Overline(
                text = "LAN CLIENTS",
                color = HeroOnSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = activeCount.toString(),
                    color = HeroOnPrimary,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (devices.isEmpty()) "no devices seen yet"
                    else "of ${devices.size} online now",
                    color = HeroOnSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Devices appear here once they connect through the proxy.",
                color = HeroOnSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(10.dp))
        if (devices.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(devices, key = { it.clientHost }) { d -> DeviceRow(d) }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Devices,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No clients connected yet",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Point a device's SOCKS5 settings here\nand it'll appear in this list.",
            color = TextMuted,
            style = MaterialTheme.typography.labelMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun DeviceRow(device: ConnectionRegistry.DeviceSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TileRadius))
            .background(SurfaceMid)
            .border(1.dp, OutlineSoft, RoundedCornerShape(TileRadius))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SurfaceLow),
            contentAlignment = Alignment.Center,
        ) {
            StatusDot(
                color = if (device.activeConnections > 0) Success else TextMuted,
                size = 10,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.clientHost,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                ),
            )
            Spacer(Modifier.height(2.dp))
            val state = if (device.activeConnections > 0) "online" else "idle"
            Text(
                text = "$state · ↑${ByteFormatter.bytes(device.bytesUp)} " +
                    "↓${ByteFormatter.bytes(device.bytesDown)}",
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = ByteFormatter.elapsed(device.firstSeenMs),
            color = TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

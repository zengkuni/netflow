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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import netflow.dev.network.NetworkInterfaceLister
import netflow.dev.service.ProxyService
import netflow.dev.ui.components.HeroCard
import netflow.dev.ui.components.Overline
import netflow.dev.ui.components.Pill
import netflow.dev.ui.components.RoundIconButton
import netflow.dev.ui.components.ScreenHeader
import netflow.dev.ui.theme.TileRadius
import netflow.dev.ui.theme.HeroOnPrimary
import netflow.dev.ui.theme.HeroOnSecondary
import netflow.dev.ui.theme.Accent
import netflow.dev.ui.theme.Danger
import netflow.dev.ui.theme.OutlineSoft
import netflow.dev.ui.theme.OutlineStrong
import netflow.dev.ui.theme.SurfaceLow
import netflow.dev.ui.theme.SurfaceMid
import netflow.dev.ui.theme.TextMuted
import netflow.dev.ui.theme.TextPrimary
import netflow.dev.ui.theme.TextSecondary
import netflow.dev.ui.viewmodel.MainViewModel

@Composable
fun ListenAddressScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val candidates by viewModel.interfaces.collectAsStateWithLifecycle()
    val bindAddress by viewModel.bindAddress.collectAsStateWithLifecycle()
    val port by viewModel.port.collectAsStateWithLifecycle()
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()

    val canEdit = when (serviceState) {
        is ProxyService.State.Stopped, is ProxyService.State.Error -> true
        else -> false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .padding(top = 4.dp, bottom = 12.dp),
    ) {
        ScreenHeader(
            title = "Listen address",
            eyebrow = "BIND",
            onBack = onBack,
            action = {
                RoundIconButton(
                    icon = Icons.Rounded.Refresh,
                    description = "Refresh",
                    onClick = { viewModel.refreshInterfaces() },
                    enabled = canEdit,
                )
            },
        )
        Spacer(Modifier.height(10.dp))
        HeroCard {
            Overline(
                text = "CURRENT ENDPOINT",
                color = HeroOnSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "$bindAddress:$port",
                color = HeroOnPrimary,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (canEdit) "Pick an interface below to change it."
                else "Stop the proxy to change the endpoint.",
                color = HeroOnSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (!canEdit) {
            HintBanner(
                "Stop the proxy to change the listen address or port."
            )
            Spacer(Modifier.height(8.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            candidates.forEach { cand ->
                AddressRow(
                    candidate = cand,
                    selected = cand.address == bindAddress,
                    enabled = canEdit,
                    onClick = { if (canEdit) viewModel.selectBindAddress(cand.address) },
                )
            }
            if (candidates.isEmpty()) {
                Text(
                    text = "No interfaces enumerated",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        PortField(
            port = port,
            enabled = canEdit,
            onChange = viewModel::selectPort,
        )
    }
}

@Composable
internal fun HintBanner(message: String, alert: Boolean = false) {
    // Danger is a DPColors field, so it is theme-aware: terracotta on paper,
    // the softened terracotta on moss. As a 1dp hairline on the light theme's
    // white surface it measures 2.77:1, under the 3:1 floor for non-text, so a
    // border alone is close to invisible in daylight. Washing the surface with
    // 10% Danger reads in both themes and still leaves the text far above
    // contrast minimums.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TileRadius))
            .background(SurfaceLow)
            .then(if (alert) Modifier.background(Danger.copy(alpha = 0.10f)) else Modifier)
            .border(
                if (alert) 2.dp else 1.dp,
                if (alert) Danger else OutlineSoft,
                RoundedCornerShape(TileRadius),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = message,
            color = if (alert) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun AddressRow(
    candidate: NetworkInterfaceLister.Candidate,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TileRadius))
            .background(if (selected) Accent.copy(alpha = 0.08f) else SurfaceMid)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) Accent else Color.Transparent,
                shape = RoundedCornerShape(TileRadius),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when {
            candidate.isWildcard -> Icons.Rounded.SettingsEthernet
            candidate.isWifi -> Icons.Rounded.Wifi
            else -> Icons.Rounded.Router
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Accent else TextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.address,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                ),
            )
            Text(
                text = candidate.label,
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (selected) Pill(text = "active", color = Accent)
    }
}

@Composable
private fun PortField(
    port: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(port.toString()) }
    LaunchedEffect(port) { text = port.toString() }

    OutlinedTextField(
        shape = RoundedCornerShape(TileRadius),
        value = text,
        onValueChange = { v ->
            text = v.filter { it.isDigit() }.take(5)
            text.toIntOrNull()?.takeIf { it in 1..65535 }?.let(onChange)
        },
        label = { Text("Port") },
        singleLine = true,
        enabled = enabled,
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
}

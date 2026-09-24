package netflow.dev.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import netflow.dev.ui.components.HeroCard
import netflow.dev.ui.components.Overline
import netflow.dev.ui.components.ScreenHeader
import netflow.dev.ui.theme.TileRadius
import netflow.dev.ui.theme.HeroOnPrimary
import netflow.dev.ui.theme.HeroOnSecondary
import netflow.dev.ui.theme.Accent
import netflow.dev.ui.theme.OutlineSoft
import netflow.dev.ui.theme.OutlineStrong
import netflow.dev.ui.theme.SurfaceLow
import netflow.dev.ui.theme.SurfaceMid
import netflow.dev.ui.theme.TextMuted
import netflow.dev.ui.theme.TextPrimary
import netflow.dev.ui.theme.TextSecondary
import netflow.dev.ui.theme.SurfaceHigh
import netflow.dev.ui.viewmodel.MainViewModel

@Composable
fun AuthScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val enabled by viewModel.authEnabled.collectAsStateWithLifecycle()
    val username by viewModel.authUsername.collectAsStateWithLifecycle()
    val password by viewModel.authPassword.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .padding(top = 4.dp, bottom = 12.dp),
    ) {
        ScreenHeader(
            title = "Authentication",
            eyebrow = "ACCESS",
            onBack = onBack,
        )
        Spacer(Modifier.height(10.dp))
        HeroCard {
            Overline(
                text = "ACCESS",
                color = HeroOnSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (enabled) "Auth required" else "Open to the LAN",
                color = HeroOnPrimary,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (enabled)
                    "Clients must send a username and password during the SOCKS5 handshake."
                else
                    "Anyone on the network can use the proxy without credentials.",
                color = HeroOnSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EnableRow(
                enabled = enabled,
                onToggle = viewModel::setAuthEnabled,
            )

            // Auth is enforced fail-closed: with a blank username or password
            // the server refuses every method rather than matching "" == "".
            // That is the safe behaviour, but it is also invisible from the
            // client side, so say so here, otherwise a half-filled form looks
            // identical to a working one while nothing can connect.
            // isEmpty, not isBlank, it must match the server's own test in
            // Socks5Connection.negotiateMethod exactly. A username of " " is
            // accepted there, and a banner claiming connections are refused
            // when they are not is worse than no banner.
            val incomplete = enabled && (username.isEmpty() || password.isEmpty())
            HintBanner(
                alert = incomplete,
                message = when {
                    incomplete ->
                        "Credentials incomplete. Every connection will be refused. " +
                            "Fill in both fields, or turn authentication off."
                    enabled ->
                        "Clients must send these credentials during the SOCKS5 handshake " +
                            "(RFC 1929 username/password). Changes apply to new connections."
                    else ->
                        "No authentication required. Any client that can reach the listen " +
                            "address can use the proxy."
                },
            )

            OutlinedTextField(
                shape = RoundedCornerShape(TileRadius),
                value = username,
                onValueChange = viewModel::setAuthUsername,
                label = { Text("Username") },
                singleLine = true,
                enabled = enabled,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = if (enabled) TextSecondary else TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    color = TextPrimary,
                ),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                shape = RoundedCornerShape(TileRadius),
                value = password,
                onValueChange = viewModel::setAuthPassword,
                label = { Text("Password") },
                singleLine = true,
                enabled = enabled,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = if (enabled) TextSecondary else TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        enabled = enabled,
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff
                            else Icons.Rounded.Visibility,
                            contentDescription = if (passwordVisible) "Hide password"
                            else "Show password",
                            tint = if (enabled) TextSecondary else TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    color = TextPrimary,
                ),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EnableRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TileRadius))
            .background(SurfaceMid)
            .border(1.dp, OutlineSoft, RoundedCornerShape(TileRadius))
            .clickable { onToggle(!enabled) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Require credentials",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (enabled) "Username + password required" else "No auth required",
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                // Track mati harus lebih gelap dari latarnya (SurfaceMid),
                // jika tidak baris hanya dipegang garis tepinya.
                checkedThumbColor = SurfaceLow,
                checkedTrackColor = Accent,
                uncheckedThumbColor = OutlineStrong,
                uncheckedTrackColor = SurfaceHigh,
                uncheckedBorderColor = OutlineStrong,
            ),
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = OutlineStrong,
    disabledBorderColor = OutlineSoft,
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextSecondary,
    disabledLabelColor = TextMuted,
    cursorColor = Accent,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    disabledTextColor = TextMuted,
)

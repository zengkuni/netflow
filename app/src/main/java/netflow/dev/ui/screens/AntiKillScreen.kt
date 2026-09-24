package netflow.dev.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import netflow.dev.ui.components.HeroCard
import netflow.dev.ui.components.HeroIconBubble
import netflow.dev.ui.components.RoundIconButton
import netflow.dev.ui.components.ScreenHeader
import netflow.dev.ui.components.StatusTile
import netflow.dev.ui.theme.PillShape
import netflow.dev.ui.theme.CardRadius
import netflow.dev.ui.theme.HeroOnPrimary
import netflow.dev.ui.theme.HeroOnSecondary
import netflow.dev.ui.theme.HeroAccent
import netflow.dev.ui.theme.Accent
import netflow.dev.ui.theme.AccentOn
import netflow.dev.ui.theme.Danger
import netflow.dev.ui.theme.OutlineSoft
import netflow.dev.ui.theme.OutlineStrong
import netflow.dev.ui.theme.SurfaceHigh
import netflow.dev.ui.theme.SurfaceLow
import netflow.dev.ui.theme.SurfaceMid
import netflow.dev.ui.theme.TextMuted
import netflow.dev.ui.theme.TextPrimary
import netflow.dev.ui.theme.TextSecondary
import netflow.dev.ui.theme.Success
import netflow.dev.ui.theme.SuccessTint
import netflow.dev.ui.theme.Info
import netflow.dev.ui.theme.InfoTint
import netflow.dev.ui.theme.SandDeep
import netflow.dev.ui.viewmodel.MainViewModel
import netflow.dev.util.AntiKillEnforcer
import netflow.dev.util.AntiKillPreferences
import netflow.dev.util.AntiKillStep
import netflow.dev.util.OemHelper
import netflow.dev.util.RootDaemon
import netflow.dev.util.RootState

@Composable
fun AntiKillScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val autoStart by viewModel.autoStartOnBoot.collectAsStateWithLifecycle()
    val rootEnabled by viewModel.rootEnabled.collectAsStateWithLifecycle()
    val rootState by viewModel.rootState.collectAsStateWithLifecycle()
    val enforcement by viewModel.antiKillEnforcement.collectAsStateWithLifecycle()
    val daemonEnabled by viewModel.daemonEnabled.collectAsStateWithLifecycle()
    val daemonStatus by viewModel.daemonStatus.collectAsStateWithLifecycle()
    var infoExpanded by rememberSaveable { mutableStateOf(false) }

    // Auto-detectable steps are queried live from the system on every resume.
    var notifGranted by remember { mutableStateOf(OemHelper.areNotificationsEnabled(context)) }
    var battGranted by remember {
        mutableStateOf(OemHelper.isIgnoringBatteryOptimizations(context))
    }
    // Manual OEM steps the user marked done, these can't be read back, so we
    // persist the "I've done this" flags.
    val manualSteps = remember {
        mutableStateMapOf<AntiKillStep, Boolean>().apply {
            AntiKillStep.entries.filterNot { it.autoDetectable }.forEach {
                put(it, AntiKillPreferences.stepDone(context, it))
            }
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifGranted = granted }

    fun reSync() {
        notifGranted = OemHelper.areNotificationsEnabled(context)
        battGranted = OemHelper.isIgnoringBatteryOptimizations(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reSync()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun isGranted(step: AntiKillStep): Boolean = when (step) {
        AntiKillStep.Notifications -> notifGranted
        AntiKillStep.BatteryOptimization -> battGranted
        else -> manualSteps[step] == true
    }

    val grantedCount = AntiKillStep.entries.count { isGranted(it) }
    val total = AntiKillStep.entries.size
    val allDone = grantedCount == total
    val pct by animateFloatAsState(
        targetValue = if (total == 0) 0f else grantedCount / total.toFloat(),
        animationSpec = tween(400),
        label = "antikill-progress",
    )

    fun grantStep(step: AntiKillStep) {
        when (step) {
            AntiKillStep.Notifications ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    OemHelper.openNotificationSettings(context)
                }
            AntiKillStep.BatteryOptimization -> OemHelper.openBatteryOptimization(context)
            AntiKillStep.AutoStart -> OemHelper.openAutoStart(context)
            AntiKillStep.BackgroundActivity -> OemHelper.openBackgroundActivity(context)
            AntiKillStep.LockInRecents -> OemHelper.openLockInRecentsGuide(context)
        }
    }

    fun toggleManual(step: AntiKillStep) {
        val next = !(manualSteps[step] ?: false)
        manualSteps[step] = next
        AntiKillPreferences.setStepDone(context, step, next)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .padding(top = 4.dp, bottom = 12.dp),
    ) {
        ScreenHeader(
            title = "Anti-Kill",
            eyebrow = "SURVIVAL",
            onBack = onBack,
            action = {
                RoundIconButton(
                    icon = Icons.Rounded.HelpOutline,
                    description = if (infoExpanded) "Hide explanation" else "What is this?",
                    onClick = { infoExpanded = !infoExpanded },
                )
            },
        )
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WhatIsThisBanner(visible = infoExpanded, onClose = { infoExpanded = false })
            SurvivalHeroCard(pct = pct, granted = grantedCount, total = total, allDone = allDone)
            AutoStartCard(enabled = autoStart, onToggle = viewModel::setAutoStartOnBoot)
            RootCard(
                enabled = rootEnabled,
                rootState = rootState,
                items = enforcement,
                daemonEnabled = daemonEnabled,
                daemonStatus = daemonStatus,
                onToggle = { on ->
                    viewModel.setRootEnabled(on)
                    if (on) viewModel.requestRoot()
                },
                onDaemonToggle = viewModel::setDaemonEnabled,
            )
            AntiKillStep.entries.forEach { step ->
                StepCard(
                    step = step,
                    granted = isGranted(step),
                    onGrant = { grantStep(step) },
                    onToggleManual = { toggleManual(step) },
                )
            }
        }
    }
}

@Composable
private fun WhatIsThisBanner(visible: Boolean, onClose: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardRadius))
                .background(SurfaceLow)
                .border(1.dp, OutlineSoft, RoundedCornerShape(CardRadius))
                .background(Brush.linearGradient(listOf(SandDeep.copy(alpha = 0.10f), Color.Transparent)))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                IconBubble(Icons.Rounded.Lightbulb)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Why does Netflow need these?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 6.dp),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close explanation",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "The proxy runs entirely on your phone. To keep serving clients it has " +
                    "to stay awake in the background and hold the cellular connection, even " +
                    "with the screen off.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Android, especially Samsung, Xiaomi, Huawei, and OnePlus, kills " +
                    "background apps to save battery. Each step below tells your phone " +
                    "\"don't kill Netflow\" through a different channel: notifications, " +
                    "battery Doze, auto-launch, background activity, and the Recents lock.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "These are one-time settings that only affect Netflow. Every other " +
                    "app keeps its normal battery management.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun SurvivalHeroCard(pct: Float, granted: Int, total: Int, allDone: Boolean) {
    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroIconBubble(
                icon = if (allDone) Icons.Rounded.CheckCircle else Icons.Rounded.Shield,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (allDone) "You're set" else "Keep Netflow alive",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = HeroOnPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (allDone) "Survival settings granted. The proxy will ride out Doze, swipe-away, and reboots."
                    else "Android will kill the proxy unless you grant these. Each is a one-time setting.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HeroOnSecondary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = HeroAccent,
            trackColor = HeroOnSecondary.copy(alpha = 0.25f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "$granted of $total complete",
            style = MaterialTheme.typography.labelMedium,
            color = HeroOnSecondary,
        )
    }
}

/**
 * App-level "Start after reboot" toggle. Distinct from the OEM auto-launch step
 * below it: this flips the BootReceiver on/off, the OEM step is the
 * manufacturer's whitelist that lets that receiver actually fire.
 */
@Composable
private fun AutoStartCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(SurfaceLow)
            .border(1.dp, OutlineSoft, RoundedCornerShape(CardRadius))
            .clickable { onToggle(!enabled) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusTile(
            tint = if (enabled) SuccessTint else SurfaceMid,
            content = if (enabled) Success else TextSecondary,
            icon = Icons.Rounded.RestartAlt,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Start after reboot",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (enabled)
                    "Netflow starts automatically when your phone restarts."
                else
                    "Netflow stays off after a reboot until you open the app and tap power.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SurfaceLow,
                checkedTrackColor = Accent,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceLow,
                uncheckedBorderColor = OutlineStrong,
            ),
        )
    }
}

/**
 * Optional root Anti-Kill. Off by default; turning it on asks for root once
 * (superuser prompt), then the service silently applies the generic
 * restrictions on every start.
 *
 * The two OEM steps that have no command surface (OEM auto-launch, lock in
 * Recents) stay manual below either way. Without root this card is inert and
 * everything still works through the manual path.
 */
@Composable
private fun RootCard(
    enabled: Boolean,
    rootState: RootState,
    items: List<AntiKillEnforcer.ItemState>,
    daemonEnabled: Boolean,
    daemonStatus: RootDaemon.Status,
    onToggle: (Boolean) -> Unit,
    onDaemonToggle: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(SurfaceLow)
            .border(1.dp, OutlineSoft, RoundedCornerShape(CardRadius))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusTile(
                tint = if (enabled) SuccessTint else SurfaceMid,
                content = if (enabled) Success else TextSecondary,
                icon = Icons.Rounded.AdminPanelSettings,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Automatic Anti-Kill (root)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "With root, Netflow applies the generic restrictions for you. " +
                        "The OEM steps below stay manual.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SurfaceLow,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = SurfaceLow,
                    uncheckedBorderColor = OutlineStrong,
                ),
            )
        }
        if (enabled) {
            Spacer(Modifier.height(10.dp))
            Text(
                when (rootState) {
                    RootState.Granted ->
                        "Root granted. Restrictions are re-applied silently every time the proxy starts."
                    RootState.Denied ->
                        "Root denied. The manual steps below still work without root."
                    RootState.Unavailable ->
                        "No root shell available right now. The manual steps below still work."
                    RootState.Unknown ->
                        "Waiting for the superuser prompt. Approve it to continue."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (rootState == RootState.Granted) TextPrimary else TextSecondary,
            )
        }
        if (items.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            items.forEach { state -> EnforcementRow(state) }
            Spacer(Modifier.height(6.dp))
            Text(
                "Applied by the service on its last start. Settings already granted " +
                    "stay granted even if you turn root off.",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
        }
        if (enabled) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = OutlineSoft)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Restart after kill (root daemon)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "A root boot script restarts the proxy within seconds of an " +
                            "OEM kill, force-stop, or reboot. It never restarts a proxy " +
                            "you stopped yourself.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = daemonEnabled,
                    onCheckedChange = onDaemonToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SurfaceLow,
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = SurfaceLow,
                        uncheckedBorderColor = OutlineStrong,
                    ),
                )
            }
            if (daemonStatus != RootDaemon.Status.Unknown) {
                Spacer(Modifier.height(6.dp))
                Text(
                    when (daemonStatus) {
                        RootDaemon.Status.Installing -> "Installing the boot script..."
                        RootDaemon.Status.Installed ->
                            "Boot script active in /data/adb/service.d."
                        RootDaemon.Status.Absent -> "Boot script removed."
                        RootDaemon.Status.Unsupported ->
                            "This root manager has no /data/adb/service.d, so the daemon " +
                                "isn't available. Restart-after-kill stays manual."
                        RootDaemon.Status.Failed ->
                            "Installing the boot script failed. Root may have been denied."
                        RootDaemon.Status.Unknown -> ""
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (daemonStatus == RootDaemon.Status.Installed) Success
                    else if (daemonStatus == RootDaemon.Status.Failed ||
                        daemonStatus == RootDaemon.Status.Unsupported
                    ) Danger else TextMuted,
                )
            }
        }
    }
}

@Composable
private fun EnforcementRow(state: AntiKillEnforcer.ItemState) {
    val applied = state.status == AntiKillEnforcer.Status.Applied
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (applied) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (applied) Success else Danger,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = when (state.item) {
                AntiKillEnforcer.Item.Notifications -> "Notifications"
                AntiKillEnforcer.Item.DozeWhitelist -> "Battery optimization exempt"
                AntiKillEnforcer.Item.BackgroundActivity -> "Background activity"
                AntiKillEnforcer.Item.DataSaver -> "Data saver whitelist (best effort)"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (applied) "Applied" else "Failed",
            style = MaterialTheme.typography.labelMedium,
            color = if (applied) TextMuted else Danger,
        )
    }
}

@Composable
private fun StepCard(
    step: AntiKillStep,
    granted: Boolean,
    onGrant: () -> Unit,
    onToggleManual: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(SurfaceLow)
            .border(1.dp, OutlineSoft, RoundedCornerShape(CardRadius))
            .clickable { expanded = !expanded }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Tile dan ikon sama-sama berubah saat granted: hanya mengubah
            // alpha tile tidak terbaca sebagai "sudah dilakukan".
            StatusTile(
                tint = if (granted) SuccessTint else SurfaceMid,
                content = if (granted) Success else TextSecondary,
                icon = if (granted) Icons.Rounded.CheckCircle else step.icon,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    step.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    step.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = TextMuted,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(Modifier.padding(top = 12.dp, start = 52.dp, end = 2.dp)) {
                Text(
                    step.rationale,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                if (step.autoDetectable) {
                    if (!granted) {
                        Button(
                            onClick = onGrant,
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Accent,
                                contentColor = AccentOn,
                            ),
                        ) {
                            Text("Grant", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text(
                            "Granted.",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted,
                        )
                    }
                } else {
                    // OEM step: open settings, then a manual "I've done this" switch.
                    // button on its own row so the label never wraps under the Switch.
                    OutlinedButton(
                        onClick = onGrant,
                        shape = PillShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Open settings")
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (granted) "Done" else "I've done this",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = granted,
                            onCheckedChange = { onToggleManual() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SurfaceLow,
                                checkedTrackColor = Accent,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = SurfaceLow,
                                uncheckedBorderColor = OutlineStrong,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconBubble(icon: ImageVector) {
    // Dipakai hanya penjelas ("Why does Netflow need these?"), jadi rubrik
    // info, bukan sukses: tile ini tidak menandai langkah yang sudah selesai.
    StatusTile(
        tint = InfoTint,
        content = Info,
        icon = icon,
        modifier = Modifier.size(40.dp),
    )
}

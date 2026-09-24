package netflow.dev.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import netflow.dev.ui.theme.Danger
import netflow.dev.ui.theme.OutlineSoft
import netflow.dev.ui.theme.StatDownContent
import netflow.dev.ui.theme.glowShadow
import netflow.dev.ui.theme.SurfaceLow
import netflow.dev.ui.theme.TextMuted
import netflow.dev.ui.theme.TextSecondary

enum class PowerState { Off, Starting, On, Paused, Error }

@Composable
fun PowerButton(
    state: PowerState,
    statusLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 156.dp,
    // Hides the status label and hint lines, for layouts that render their own
    // state text next to the button (the Home identity hero). The circle and
    // its states are unchanged.
    compact: Boolean = false,
) {
    // Aksen "hidup" memakai StatDownContent (keluarga warna kartu stat),
    // bukan amber: hero card sekarang sepenuhnya mengikuti palet kartu APN.
    val accent = when (state) {
        PowerState.On -> StatDownContent
        PowerState.Starting -> StatDownContent.copy(alpha = 0.6f)
        PowerState.Paused -> StatDownContent
        PowerState.Error -> Danger
        PowerState.Off -> TextMuted
    }

    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-alpha",
    )
    val spinPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin-phase",
    )

    val outlineSoftColor = OutlineSoft
    val surfaceLowColor = SurfaceLow
    // Canvas draw lambdas are not composable contexts, so the theme getters
    // must be captured here first.
    val textMutedColor = TextMuted
    val dangerColor = Danger
    // In compact mode the caller owns the width (the button shares a Row with
    // a text column), so the fillMaxWidth that centers it on Home would fight
    // the sibling. Non-compact keeps the old behaviour.
    Column(
        modifier = if (compact) modifier else modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Ruang ekstra di atas/diameter asli untuk memuat glow lingkaran.
        val outerDp = diameter + 28.dp
        val innerDp = diameter * 0.62f
        Box(
            modifier = Modifier
                .size(outerDp)
                // Gaya memakai glow hijau lembut pada setiap aksi utama.
                // Hanya saat Online: saat Off, glow akan mengaku rusak
                // padahal yang tidak ada hanya jaringan.
                .then(if (state == PowerState.On) Modifier.glowShadow(CircleShape) else Modifier)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(diameter)) {
                val stroke = 5.dp.toPx()
                val inset = stroke / 2f
                val arcSize = androidx.compose.ui.geometry.Size(
                    size.width - inset * 2,
                    size.height - inset * 2,
                )
                val topLeft = Offset(inset, inset)
                drawArc(
                    color = outlineSoftColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                when (state) {
                    PowerState.On -> drawArc(
                        color = accent.copy(alpha = pulse),
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    PowerState.Paused -> drawArc(
                        color = accent.copy(alpha = pulse * 0.9f),
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(
                            width = stroke, cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(
                                intervals = floatArrayOf(12f, 10f),
                            ),
                        ),
                    )
                    PowerState.Starting -> drawArc(
                        color = accent,
                        startAngle = spinPhase, sweepAngle = 110f, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    PowerState.Error -> drawArc(
                        color = dangerColor,
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(
                            width = stroke, cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(
                                intervals = floatArrayOf(20f, 14f),
                            ),
                        ),
                    )
                    PowerState.Off -> drawArc(
                        color = textMutedColor.copy(alpha = 0.5f),
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(
                            width = stroke, cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(
                                intervals = floatArrayOf(4f, 14f),
                            ),
                        ),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(innerDp)
                    .clip(CircleShape)
                    .background(surfaceLowColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = "Power",
                    tint = accent,
                    modifier = Modifier.size(diameter * 0.32f),
                )
            }
        }
        if (!compact) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = statusLabel.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when (state) {
                    PowerState.On -> "tap to disable"
                    PowerState.Off -> "tap to enable"
                    PowerState.Starting -> "establishing tunnel..."
                    PowerState.Paused -> "still listening, waiting for mobile data"
                    PowerState.Error -> "tap to retry"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}

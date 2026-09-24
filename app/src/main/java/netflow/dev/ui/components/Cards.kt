package netflow.dev.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import netflow.dev.ui.theme.Accent
import netflow.dev.ui.theme.CardRadius
import netflow.dev.ui.theme.HeroAccent
import netflow.dev.ui.theme.HeroBorder
import netflow.dev.ui.theme.HeroInk
import netflow.dev.ui.theme.OutlineSoft
import netflow.dev.ui.theme.PillShape
import netflow.dev.ui.theme.SurfaceLow
import netflow.dev.ui.theme.TextMuted
import netflow.dev.ui.theme.TextPrimary
import netflow.dev.ui.theme.TextSecondary

@Composable
fun StatusDot(color: Color, size: Int = 8) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * Memory Garden's signature pattern: every status is a **tinted icon tile**, so
 * a row colour-codes at a glance instead of leaning on a generic accent. The
 * wash is the rubric tint, the glyph is the rubric hue, and the two are passed
 * separately because a tint alone cannot carry the meaning on a dark surface.
 */
@Composable
fun StatusTile(
    tint: Color,
    content: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    description: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = content,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(PillShape)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

// ------------------------------------------------------------ Shared type roles
// Every screen renders its header through these so the eyebrow, screen title
// and round icon buttons are identical everywhere. Sizes live here, not at call
// sites, so a change propagates to every subscreen at once.

/**
 * The app's one uppercase micro-label treatment: 10sp / 700 Rubik, tracked.
 * Every section overline in the app routes through here, so the treatment is
 * defined exactly once and cannot drift between one screen and the next.
 *
 * Tracking is 0.8sp, not the style sheet's 2.5sp. The spec's overline is sized
 * for a wide desktop column; measured with the bundled Rubik Bold, "CURRENT APN"
 * is 80.0dp at 0.8sp but 98.7dp at 2.5sp, against a 117.5dp budget in the
 * half-width APN / Public IP card row (393dp page, 18dp gutters, 173.5dp card,
 * 12dp pad, 24dp tile, 8dp gap) and 66.2dp once the "reading..." label shares
 * that row. 2.5sp would clip the label in the idle state; 0.8sp fits. Keeping
 * the proven app value over the spec's is the named deviation.
 */
@Composable
fun Overline(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextSecondary,
) {
    Text(
        text = text.uppercase(),
        color = color,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
        ),
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * Uppercase micro-label above a screen title. Forest green, bold, tracked.
 * The [Overline] treatment in the Accent colour, so the screen-header eyebrow
 * and the section labels inside a screen are the same type, not two kinds.
 */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Overline(text = text, modifier = modifier, color = Accent)
}

/** Screen-level title. 24sp bold, the largest sans on a subscreen. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = TextPrimary,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
        ),
        modifier = modifier,
    )
}

/** Round 36dp icon button on a soft surface. Shared by every screen header. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(SurfaceLow)
            .border(1.dp, OutlineSoft, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) TextSecondary else TextMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Subscreen header: round back button, eyebrow, title, optional trailing
 * action. Listen / Devices / Auth / Anti-Kill / APN auto / Docs / Logger all
 * render through this so the header treatment matches Home.
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            description = "Back",
            onClick = onBack,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (eyebrow != null) {
                Eyebrow(text = eyebrow)
                Spacer(Modifier.height(1.dp))
            }
            ScreenTitle(text = title)
        }
        if (action != null) action()
    }
}

/**
 * Forest-green identity card. This is the ONLY `HeroInk` surface on a
 * subscreen: it holds the summary block and its copy action, so the green is
 * the action anchor, not a background. Home builds its own hero inline on the
 * sage stat surface, which is why it is not built from this composable.
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    radius: Dp = CardRadius,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(radius))
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(HeroInk)
            .border(1.dp, HeroBorder, RoundedCornerShape(radius))
            .padding(16.dp),
        content = content,
    )
}

/** 52dp circular icon tile on a HeroCard, tinted sand against the forest green. */
@Composable
fun HeroIconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(HeroAccent.copy(alpha = 0.20f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = HeroAccent,
            modifier = Modifier.size(26.dp),
        )
    }
}

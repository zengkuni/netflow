package netflow.dev.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import netflow.dev.ui.theme.GlowGreen
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Memory Garden shape tokens. The system is deliberately maximal: 26px cards,
 * 22px inner tiles, and fully rounded buttons, chips and badges. Every corner
 * radius in the app goes through these three tokens, never through an inline
 * dp literal.
 *
 * The style's own arch geometry (fully rounded top, square bottom) is
 * deliberately not adopted: it was tried on the identity hero and read as a
 * notch cut out of the card, making the hero the one surface that did not match
 * the rest. Every card now uses CardRadius.
 */
val CardRadius: Dp = 26.dp
val TileRadius: Dp = 22.dp
val PillShape = RoundedCornerShape(percent = 50)

/**
 * The style's two elevation tiers, kept in one place so a call site cannot mix
 * a card's whisper shadow with an action's green glow. The style specifies both
 * as web box-shadows; Compose has no blur-radius or y-offset on `Modifier`
 * shadows, so each is rendered as its own `elevation` with the tint baked into
 * `ambientColor`/`spotColor`. The offset and blur come from the platform
 * shadow, the tint from this colour, which is the closest honest mapping.
 */

/** Whisper-soft card shadow: `0 2px 10px rgba(31,41,34,0.06)`. */
fun Modifier.cardShadow(shape: Shape): Modifier = this.shadow(
    elevation = 2.dp,
    shape = shape,
    ambientColor = Color(0x0F1F2922),
    spotColor = Color(0x0F1F2922),
)

/** The style's signature soft green glow, for a raised primary action. */
fun Modifier.glowShadow(shape: Shape): Modifier = this.shadow(
    elevation = 6.dp,
    shape = shape,
    ambientColor = GlowGreen,
    spotColor = GlowGreen,
)

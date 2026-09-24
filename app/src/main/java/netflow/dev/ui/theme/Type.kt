package netflow.dev.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import netflow.dev.R

/**
 * Rubik (OFL), the single family Memory Garden is set in, chosen for its softly
 * rounded terminals. Bundled as static weights so there is no
 * downloadable-fonts dependency and no Play Services requirement on the
 * opt-in-root path. Weights 300 does not ship, so nothing asks for Light.
 * Address/port readouts that need aligned digits use FontFamily.Monospace
 * directly at the call site; there is no shared Mono alias.
 */
val Rubik = FontFamily(
    Font(resId = R.font.rubik_regular, weight = FontWeight.Normal),
    Font(resId = R.font.rubik_medium, weight = FontWeight.Medium),
    Font(resId = R.font.rubik_semibold, weight = FontWeight.SemiBold),
    Font(resId = R.font.rubik_bold, weight = FontWeight.Bold),
)

/**
 * Memory Garden type scale, Tracking: Headings Use Display Weight (-0.25sp).
 * Every slot sets lineHeight explicitly, because Rubik's natural line box is
 * looser than the platform default the old scale inherited.
 *
 * NAMED OVERRIDE of the spec: the spec's intermediate sizes (18, 14, 13) are
 * collapsed into this scale's own 15 / 13 / 11 slots, and the spec's Code role
 * maps to FontFamily.Monospace at the call site rather than a bundled mono
 * family. Weights follow the spec's 600-workhorse rule throughout.
 */
val NetflowTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        letterSpacing = (-0.25).sp,
        lineHeight = 38.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Rubik,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Rubik,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        letterSpacing = 0.1.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Rubik,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Rubik,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Rubik,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.2.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.2.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 0.4.sp,
        lineHeight = 14.sp,
    ),
)

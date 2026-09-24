package netflow.dev.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import netflow.dev.util.AppLog

// ---------------------------------------------------------------- Light palette
// Memory Garden, "Garden". The page is warm paper (colors.background) and the
// elevated cards are pure white (colors.card); sage tints sit between them so
// depth comes from tint plus a whisper shadow, not from heavy borders.
private val LightInk = Color(0xFFF9F9F6)
// Spec colors.card: white is reserved for elevated cards, the page stays
// warm paper. Cards must read as raised, not as a second tint.
private val LightSurfaceLow = Color(0xFFFFFFFF)
private val LightSurfaceMid = Color(0xFFF0F4EC)
private val LightSurfaceHigh = Color(0xFFEEF3E9)
// Spec colors.hairline: separation prefers hairlines over visible outlines.
private val LightOutlineSoft = Color(0xFFF0F2EE)
// Spec colors.hairline-strong: reserved for inputs that must assert an
// edge. Stone (#B4BDB4) is the disabled-text token, not a border role.
private val LightOutlineStrong = Color(0xFF86958A)
private val LightTextPrimary = Color(0xFF1F2922)
// Spec colors.body / colors.charcoal.
private val LightTextSecondary = Color(0xFF4B5750)
// Spec colors.mute / colors.ash: timestamps and metadata.
private val LightTextMuted = Color(0xFF67736C)

// ------------------------------------------------- Dark palette (Night Garden)
// A derived palette, not a flip: moss-black surfaces, lifted foliage hovers,
// softened accents. Shadows are the only depth cue once tints flatten.
//
// NAMED OVERRIDE of the spec: colors.dark lists bone #1A201A, which is DARKER
// than surface #1B211B, so cards would sink below the page. surfaceMid is kept
// at #252C25 to preserve a visible lift, and the spec's own hierarchy
// (background #0E120E, canvas #121612, bone #1A201A, sage #2A3D2E) collapses
// into two steps at that lightness.
private val DarkInk = Color(0xFF0E120E)
private val DarkSurfaceLow = Color(0xFF1B211B)
private val DarkSurfaceMid = Color(0xFF252C25)
private val DarkSurfaceHigh = Color(0xFF2A3D2E)
private val DarkOutlineSoft = Color(0xFF243427)
private val DarkOutlineStrong = Color(0xFF76857A)
private val DarkTextPrimary = Color(0xFFFCFCF6)
private val DarkTextSecondary = Color(0xFFC4CCC2)
private val DarkTextMuted = Color(0xFF7D877D)

// ---------------------------------------------------- Theme-independent accents
// The hero, traffic and nav surfaces keep one forest green in both themes so
// the power control and the traffic bars hold their contrast regardless of the
// system theme. Anything drawn ON that green uses the sand and terracotta pair
// from the style's illustration palette; both clear 3:1 against #3A5A40.
val HeroInk = Color(0xFF3A5A40)
val HeroOnPrimary = Color(0xFFFCFCF6)
val HeroOnSecondary = Color(0xFFC4CCC2)
val HeroAccent = Color(0xFFEADFC8)

// Memory Garden's earthy detail: sand tones ground the planters, the
// terracotta-pot accents warm them. Only the values a call site actually uses
// are declared: SandDeep for the warm gradient wash, PotAccent and
// PotAccentDeep for the terracotta share bars.
val SandDeep = Color(0xFFD9C9A8)
val PotAccent = Color(0xFFC97B5A)
val PotAccentDeep = Color(0xFFD9A184)

// The style's soft green glow that pairs with every primary action, from its
// pill buttons to its floating add-plant FAB. Renderer-side, this is the
// colour Compose's Modifier.shadow tints a raised primary action with, so the
// green still carries light even where the ambient shadow is not visible.
val GlowGreen = Color(0x733A5A40)

@Immutable
data class DPColors(
    val ink: Color,
    val surfaceLow: Color,
    val surfaceMid: Color,
    val surfaceHigh: Color,
    val outlineSoft: Color,
    val outlineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    // Theme-aware. The green sits on warm paper with no rim in light; the dark
    // page needs a lifted foliage hairline so the card does not read as a hole.
    val heroBorder: Color,
    // Interactive colour. Forest green in light, lifted foliage in dark.
    val accent: Color,
    val accentOn: Color,
    // Garden status colours: watering blue, fertilizer amber, overdue
    // terracotta, foliage green. Each has an on-colour for the filled case,
    // and a tint that becomes the wash on status icon tiles.
    val success: Color,
    val warning: Color,
    val danger: Color,
    val successOn: Color,
    val warningOn: Color,
    val dangerOn: Color,
    val successTint: Color,
    val warningTint: Color,
    val dangerTint: Color,
    val infoTint: Color,
    val info: Color,
    val infoOn: Color,
    val errorOn: Color,
    // The identity hero and the APN card share one tinted surface, the style's
    // primary badge pairing.
    val statDownSurface: Color,
    val statDownContent: Color,
    // Foreground for a control FILLED with the stat content colour. Must be
    // opaque: the surface token is translucent in the dark theme and would
    // render invisible text.
    val statDownOn: Color,
    // Syntax colours for the JSON-RPC docs screen. Light variants are the
    // dark-enough tones that read on the paper card; dark variants are the
    // lifted tones that read on moss.
    val jsonKey: Color,
    val jsonString: Color,
    val jsonNumber: Color,
    val jsonBool: Color,
    // Per-level colours for the Logger screen. Same treatment as the JSON
    // tokens: dark-enough to read on paper in light, lifted to read on moss in
    // dark.
    val logVerbose: Color,
    val logDebug: Color,
    val logInfo: Color,
    val logWarn: Color,
    val logError: Color,
)

internal val DarkDPColors = DPColors(
    ink = DarkInk,
    surfaceLow = DarkSurfaceLow,
    surfaceMid = DarkSurfaceMid,
    surfaceHigh = DarkSurfaceHigh,
    outlineSoft = DarkOutlineSoft,
    outlineStrong = DarkOutlineStrong,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textMuted = DarkTextMuted,
    // On moss, the green needs a lifted rim to read as a card instead of a
    // smudge, and the stat card drops to a deep moss wash.
    heroBorder = Color(0xFF4E7A57),
    accent = Color(0xFF6B9B74),
    accentOn = Color(0xFF0E120E),
    // Night Garden's softened status set. All four are lifted past 4.5:1 on
    // the moss card, so their on-colours go near-black rather than white.
    success = Color(0xFF6B9B74),
    warning = Color(0xFFEDCE8F),
    danger = Color(0xFFC97B5A),
    successOn = Color(0xFF0E120E),
    warningOn = Color(0xFF0E120E),
    dangerOn = Color(0xFF0E120E),
    // Tints are the same hues dropped to ~12% over the moss card, so a status
    // tile reads as a wash and not as a second surface.
    successTint = Color(0xFF1F2E22),
    warningTint = Color(0xFF2E2A1C),
    dangerTint = Color(0xFF33231D),
    infoTint = Color(0xFF1E2731),
    info = Color(0xFF7FA3C4),
    infoOn = Color(0xFF0E120E),
    errorOn = Color(0xFF0E120E),
    statDownSurface = Color(0xFF243427),
    // Derived from Memory Garden foliage (#6B9B74), lifted so the 10sp labels
    // on the moss stat card clear 4.5:1. The named value measures 4.1:1.
    statDownContent = Color(0xFF86B58F),
    // The lifted foliage reads as light, so the foreground goes near-black.
    statDownOn = Color(0xFF0E120E),
    // On moss, lifted tones so every token stays legible.
    jsonKey = Color(0xFF6B9B74),
    jsonString = Color(0xFFC97B5A),
    jsonNumber = Color(0xFF7FA3C4),
    jsonBool = Color(0xFFEDCE8F),
    // On moss, lifted tones so every level pops.
    logVerbose = Color(0xFF8C9890),
    logDebug = Color(0xFF7FA3C4),
    logInfo = Color(0xFF6B9B74),
    logWarn = Color(0xFFEDCE8F),
    logError = Color(0xFFC97B5A),
)

internal val LightDPColors = DPColors(
    ink = LightInk,
    surfaceLow = LightSurfaceLow,
    surfaceMid = LightSurfaceMid,
    surfaceHigh = LightSurfaceHigh,
    outlineSoft = LightOutlineSoft,
    outlineStrong = LightOutlineStrong,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textMuted = LightTextMuted,
    // Memory Garden exact: the green sits on warm paper with no rim, and the
    // stat card is the style's primary badge pairing (sage surface, forest ink).
    heroBorder = Color.Transparent,
    accent = Color(0xFF3A5A40),
    accentOn = Color.White,
    // Garden rubric, the style's exact values: watering blue, fertilizer
    // amber, overdue terracotta, foliage green. Each is dark enough to take
    // white as its on-colour on paper.
    success = Color(0xFF4E7A57),
    warning = Color(0xFF8A651E),
    danger = Color(0xFFA34A2E),
    successOn = Color.White,
    warningOn = Color.White,
    dangerOn = Color.White,
    // The style's tinted surfaces, which pair with each rubric colour on its
    // icon tile. successTint is derived: the style's #EEF3E9 family deepened
    // to #E6EFE7 so the tile reads against the #FCFCF6 card it sits on.
    successTint = Color(0xFFEEF3E9),
    warningTint = Color(0xFFF6EDDA),
    dangerTint = Color(0xFFF6E5DD),
    infoTint = Color(0xFFE6EEF5),
    info = Color(0xFF3E6B94),
    infoOn = Color.White,
    errorOn = Color.White,
    statDownSurface = Color(0xFFEEF3E9),
    statDownContent = Color(0xFF3A5A40),
    // Forest green fill takes a light foreground.
    statDownOn = Color.White,
    // On paper, the same hues darkened past the 4.5:1 floor.
    jsonKey = Color(0xFF3A5A40),
    jsonString = Color(0xFFA34A2E),
    jsonNumber = Color(0xFF3E6B94),
    jsonBool = Color(0xFF8A651E),
    // On paper, the same hues darkened past the contrast floor. Info and warn
    // deliberately share the rubric hues so the Logger reads like every other
    // status surface in the app.
    logVerbose = Color(0xFF6B7870),
    logDebug = Color(0xFF3E6B94),
    logInfo = Color(0xFF4E7A57),
    logWarn = Color(0xFFB98A2F),
    logError = Color(0xFFBC5B3C),
)

val LocalDPColors = staticCompositionLocalOf { LightDPColors }

// @Composable getters keep call-site syntax `Modifier.background(Ink)` working
// while letting the value swap at runtime when the theme changes. For non-
// composable scopes (Canvas lambdas etc.) capture the value as a local val
// inside the @Composable scope first.
val Ink: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.ink
val SurfaceLow: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.surfaceLow
val SurfaceMid: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.surfaceMid
val SurfaceHigh: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.surfaceHigh
val OutlineSoft: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.outlineSoft
val OutlineStrong: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.outlineStrong
val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.textSecondary
val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.textMuted

// Theme-aware (see DPColors).
val HeroBorder: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.heroBorder
val Accent: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.accent
val AccentOn: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.accentOn
// Garden rubric. Each colour ships with an on-colour for the filled case and a
// tint for the wash behind its icon tile (see StatusTile in Cards.kt).
val Success: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.success
val Warning: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.warning
val Danger: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.danger
val SuccessOn: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.successOn
val WarningOn: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.warningOn
val DangerOn: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.dangerOn
val SuccessTint: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.successTint
val WarningTint: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.warningTint
val DangerTint: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.dangerTint
val InfoTint: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.infoTint
val Info: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.info
val InfoOn: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.infoOn
val StatDownSurface: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.statDownSurface
val StatDownContent: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.statDownContent
val StatDownOn: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.statDownOn

// JSON syntax colours for the docs screen (see DPColors).
val JsonKey: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.jsonKey
val JsonString: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.jsonString
val JsonNumber: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.jsonNumber
val JsonBool: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.jsonBool

// Per-level log colours for the Logger screen (see DPColors).
val LogVerbose: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.logVerbose
val LogDebug: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.logDebug
val LogInfo: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.logInfo
val LogWarn: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.logWarn
val LogError: Color @Composable @ReadOnlyComposable get() = LocalDPColors.current.logError

/** Warna level log sesuai token tema; dipakai chip dan teks tag. */
@Composable
@ReadOnlyComposable
fun levelColor(level: AppLog.Level): Color = when (level) {
    AppLog.Level.V -> LogVerbose
    AppLog.Level.D -> LogDebug
    AppLog.Level.I -> LogInfo
    AppLog.Level.W -> LogWarn
    AppLog.Level.E -> LogError
}

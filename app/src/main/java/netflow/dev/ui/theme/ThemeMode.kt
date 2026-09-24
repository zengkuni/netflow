package netflow.dev.ui.theme

/**
 * User preference for app theme.
 *
 * [System] follows the device dark-mode setting; [Light] / [Dark] override it.
 * The selection is cycled by the theme button in the home header. A fresh
 * install defaults to [Dark]: the operator asked for the moss-black Night
 * Garden as the app's face, and the warm-paper light palette is the opted-in
 * variant rather than the other way round.
 *
 * [Light] is still selectable from the header button; it is kept because the
 * full Memory Garden light palette is implemented and needs a way in.
 */
enum class ThemeMode(val key: String) {
    System("system"),
    Dark("dark"),
    Light("light");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: Dark
    }
}

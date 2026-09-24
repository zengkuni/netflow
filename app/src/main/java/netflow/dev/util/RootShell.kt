package netflow.dev.util

import kotlinx.coroutines.flow.StateFlow

/**
 * Root access behind an app-owned seam, so the backend (libsu today) can be
 * swapped without touching call sites.
 *
 * Every call is total: [exec] never throws, and a missing or denied root shows
 * up as [RootState.Denied] / [RootState.Unavailable], so callers keep their
 * no-root fallback path. Commands are always static strings built by the app
 * from its own constants; nothing derived from network input (SOCKS5 payloads,
 * client hostnames) ever reaches the shell.
 */
interface RootShell {
    val state: StateFlow<RootState>

    /** Ask for root. Triggers the superuser prompt on first call. Never throws. */
    suspend fun request(): RootState

    /** Run static commands as root. Any failure yields code -1, never throws. */
    suspend fun exec(vararg cmd: String): ShellOutcome
}

enum class RootState { Unknown, Granted, Denied, Unavailable }

data class ShellOutcome(val code: Int, val output: String) {
    val success: Boolean get() = code == 0
}

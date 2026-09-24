package netflow.dev.util

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * [RootShell] on top of libsu. The shell itself is a process-wide singleton
 * (libsu keeps one per app process); this wrapper adds the never-throws
 * contract and the root-granted state the rest of the app observes.
 *
 * The shell is only ever created inside [request], i.e. after the user opts
 * in on the Anti-Kill screen, so a fresh install never triggers a superuser
 * prompt.
 */
internal object LibsuRootShell : RootShell {

    private val _state = MutableStateFlow(RootState.Unknown)
    override val state: StateFlow<RootState> = _state.asStateFlow()

    override suspend fun request(): RootState = withContext(Dispatchers.IO) {
        val next = runCatching {
            // getShell() creates the shell on first call, showing the
            // superuser prompt; isRoot is false when the user denied it or
            // the device is not rooted.
            if (Shell.getShell().isRoot) RootState.Granted else RootState.Denied
        }.getOrElse { RootState.Unavailable }
        _state.value = next
        next
    }

    override suspend fun exec(vararg cmd: String): ShellOutcome =
        withContext(Dispatchers.IO) {
            if (_state.value != RootState.Granted) {
                return@withContext ShellOutcome(-1, "root not granted")
            }
            runCatching { Shell.cmd(*cmd).exec() }
                .fold(
                    onSuccess = { r ->
                        ShellOutcome(r.code, r.out.joinToString("\n"))
                    },
                    // Shell.NoShellException (shell died / root revoked
                    // mid-session) and any exec failure land here so the
                    // caller's fallback path runs instead of crashing.
                    onFailure = { e ->
                        _state.value = RootState.Unavailable
                        ShellOutcome(-1, e.message ?: "exec failed")
                    },
                )
        }
}

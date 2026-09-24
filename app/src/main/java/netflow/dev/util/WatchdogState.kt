package netflow.dev.util

import android.content.Context
import java.io.File

/**
 * Liveness marker the root watchdog daemon reads to decide whether the proxy
 * is *supposed* to be running.
 *
 * The file lives in the app's files dir, so the service can update it without
 * root; the daemon script reads it as root. filesDir, not cacheDir: the system
 * evicts cache under storage pressure and the user can "Clear cache" from
 * settings, and a vanished marker would silently disable the daemon while the
 * proxy runs.
 *
 * Without the marker the daemon cannot tell an OEM kill from the user tapping
 * the power button off, and would restart a proxy the user just stopped (or
 * restart-loop on a bind error).
 */
object WatchdogState {

    private const val FILE = "watchdog.state"

    fun write(context: Context, running: Boolean) {
        runCatching {
            File(context.filesDir, FILE).writeText(if (running) "run" else "stop")
        }
    }
}

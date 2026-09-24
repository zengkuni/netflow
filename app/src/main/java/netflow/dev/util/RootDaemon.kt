package netflow.dev.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Root watchdog daemon, installed as a boot script in /data/adb/service.d/.
 *
 * KernelSU and Magisk both execute scripts in that directory at boot, as root,
 * outside any app process. That makes the daemon independent of the app's own
 * autostart permission or a Recents lock, and it also restarts the proxy after
 * a plain force-stop, not just a reboot.
 *
 * The script only acts when the proxy is *supposed* to be running: it reads
 * the [WatchdogState] marker the service maintains, so a proxy the user
 * stopped on purpose, or one that errored out, is never restarted.
 *
 * A root manager without service.d (plain su) reports [Status.Unsupported]
 * from [install]; B1 enforcement still applies in that case.
 */
object RootDaemon {

    private const val DIR = "/data/adb/service.d"
    private const val SCRIPT = "$DIR/99netflow.sh"

    enum class Status { Unknown, Installing, Installed, Absent, Unsupported, Failed }

    private val _status = MutableStateFlow(Status.Unknown)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Install the watchdog. Returns failure (never throws) when unsupported. */
    suspend fun install(context: Context, shell: RootShell, pkg: String): ShellOutcome {
        if (!shell.exec("[ -d $DIR ]").success) {
            _status.value = Status.Unsupported
            return ShellOutcome(-1, "no $DIR: not a KernelSU/Magisk root manager")
        }
        _status.value = Status.Installing
        // Stage the script in the app cache (root can read it), then copy it
        // into place as root. Every path is app-owned; pkg is the package
        // name, an app-owned constant.
        val staged = File(context.cacheDir, "99netflow.sh")
        staged.writeText(script(pkg, context.filesDir.absolutePath))
        val copied = shell.exec("cp ${staged.absolutePath} $SCRIPT")
        if (!copied.success) {
            _status.value = Status.Failed
            return copied
        }
        val chmod = shell.exec("chmod 755 $SCRIPT")
        if (chmod.success) {
            staged.delete()
            _status.value = Status.Installed
        } else {
            _status.value = Status.Failed
        }
        if (chmod.success) {
            // service.d only runs at boot; start the loop now so the toggle
            // takes effect without a reboot. setsid detaches it from the
            // transient shell's session so it survives this exec returning.
            // The pgrep guard keeps a re-install from spawning a second loop.
            shell.exec(
                "pgrep -f 99datap[r]oxy.sh >/dev/null || " +
                    "setsid nohup sh $SCRIPT >/dev/null 2>&1 &"
            )
        }
        return chmod
    }

    /** Remove the watchdog and kill the running loop. Best effort. */
    suspend fun uninstall(shell: RootShell): ShellOutcome {
        val removed = shell.exec("rm -f $SCRIPT")
        // The loop survives rm while it runs; match on the script name. The
        // bracket form keeps the pattern from matching the transient shell
        // this very command runs in.
        shell.exec("pkill -f 99datap[r]oxy.sh")
        _status.value = Status.Absent
        return removed
    }

    suspend fun installed(shell: RootShell): Boolean =
        shell.exec("[ -f $SCRIPT ]").success

    /**
     * Re-create the script on every launch while the daemon is switched on.
     * Always rewrites instead of checking existence: an install from an older
     * build would otherwise keep a stale script body forever (for example one
     * pointing at a marker file that has since moved), and "file exists" would
     * never repair it. The rewrite is cheap, and [install]'s pgrep guard keeps
     * it from spawning duplicate loops.
     *
     * A loop started by an older build keeps running the previous script body
     * (sh holds the old inode after the rename), so it is killed and respawned
     * here to pick up the new content. The loop is stateless; the restart gap
     * is at most one poll interval.
     */
    suspend fun reconcile(context: Context, shell: RootShell, pkg: String) {
        install(context, shell, pkg)
        shell.exec("pkill -f 99datap[r]oxy.sh")
        shell.exec(
            "pgrep -f 99datap[r]oxy.sh >/dev/null || " +
                "setsid nohup sh $SCRIPT >/dev/null 2>&1 &"
        )
        _status.value = Status.Installed
    }

    /**
     * The script body. `$(` and `$?`-style shell dollars are literal in a
     * Kotlin raw string (no identifier follows), only $pkg / $cacheDir
     * interpolate, both app-owned constants.
     */
    private fun script(pkg: String, cacheDir: String) = """
        #!/system/bin/sh
        # Netflow root watchdog. Installed by the app; removed when the user
        # turns the daemon off. Restarts the proxy service whenever the app
        # process is gone (OEM kill, force-stop, reboot) AND the proxy is
        # supposed to be running per the service's own state marker.
        while true; do
          if [ "$(cat $cacheDir/watchdog.state 2>/dev/null)" = "run" ]; then
            if ! pidof $pkg >/dev/null 2>&1; then
              am start-foreground-service -n $pkg/.service.ProxyService -a $pkg.ACTION_START --user 0
              sleep 5
            fi
          fi
          sleep 3
        done
    """.trimIndent()
}

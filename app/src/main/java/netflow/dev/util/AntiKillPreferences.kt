package netflow.dev.util

import android.content.Context
import netflow.dev.service.ProxyService

/**
 * Anti-Kill persistence, kept in the same [ProxyService.PREFS_NAME] file the
 * rest of the app uses.
 *
 * - [autoStartOnBoot] gates whether [netflow.dev.service.BootReceiver]
 *   relaunches the proxy after a reboot.
 * - [rootEnabled] gates the silent root Anti-Kill enforcement and the root
 *   watchdog daemon. Defaults to false so no fresh install ever triggers a
 *   superuser prompt; the app runs fully without root either way.
 * - the per-[AntiKillStep] flags only cover the manual OEM steps, the user
 *   ticks "I've done this" because the system can't report those settings.
 *   Auto-detectable steps are read live and never persisted here.
 */
object AntiKillPreferences {
    private const val KEY_AUTOSTART_ON_BOOT = "autostart_on_boot"
    private const val KEY_ROOT_ENABLED = "root_enabled"
    private const val KEY_DAEMON = "root_daemon_enabled"
    private const val KEY_ROOT_AUTOSTART = "root_autostart"
    private fun stepKey(step: AntiKillStep) = "antikill_step_${step.name}"

    private fun prefs(context: Context) =
        context.getSharedPreferences(ProxyService.PREFS_NAME, Context.MODE_PRIVATE)

    fun autoStartOnBoot(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTOSTART_ON_BOOT, false)

    fun setAutoStartOnBoot(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTOSTART_ON_BOOT, enabled).apply()
    }

    fun rootEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ROOT_ENABLED, false)

    fun setRootEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ROOT_ENABLED, enabled).apply()
    }

    fun daemonEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DAEMON, false)

    fun setDaemonEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DAEMON, enabled).apply()
    }

    /** Mulai proxy otomatis begitu root tersedia (lihat [MainViewModel]). */
    fun rootAutoStart(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ROOT_AUTOSTART, false)

    fun setRootAutoStart(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ROOT_AUTOSTART, enabled).apply()
    }

    fun stepDone(context: Context, step: AntiKillStep): Boolean =
        prefs(context).getBoolean(stepKey(step), false)

    fun setStepDone(context: Context, step: AntiKillStep, done: Boolean) {
        prefs(context).edit().putBoolean(stepKey(step), done).apply()
    }
}

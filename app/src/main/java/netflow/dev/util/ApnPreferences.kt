package netflow.dev.util

import android.content.Context
import netflow.dev.service.ProxyService

/**
 * Pref auto-switch APN: status enable, mode rotasi, dan interval detik.
 * Loop-nya sendiri hidup di [netflow.dev.service.ProxyService], jadi
 * rotasi berjalan selama proxy berjalan.
 */
object ApnPreferences {

    private const val KEY_AUTO_ENABLED = "apn_auto_enabled"
    private const val KEY_AUTO_INTERVAL_SECONDS = "apn_auto_interval_s"
    private const val KEY_AUTO_MODE = "apn_auto_mode"

    /** Interval bawaan saat user belum pernah mengisi. */
    const val DEFAULT_INTERVAL_SECONDS = 10

    /** Batas wajar untuk input: 1 detik sampai 1 jam. */
    val INTERVAL_RANGE = 1..3600

    fun autoEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_ENABLED, false)

    fun setAutoEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_ENABLED, enabled).apply()
    }

    fun autoIntervalSeconds(context: Context): Int {
        val stored = prefs(context).getInt(KEY_AUTO_INTERVAL_SECONDS, DEFAULT_INTERVAL_SECONDS)
        return if (stored in INTERVAL_RANGE) stored else DEFAULT_INTERVAL_SECONDS
    }

    fun setAutoIntervalSeconds(context: Context, seconds: Int) {
        val clamped = seconds.coerceIn(INTERVAL_RANGE)
        prefs(context).edit().putInt(KEY_AUTO_INTERVAL_SECONDS, clamped).apply()
    }

    fun autoMode(context: Context): ApnRotateMode {
        val stored = prefs(context).getString(KEY_AUTO_MODE, null)
        return ApnRotateMode.entries.firstOrNull { it.name == stored } ?: ApnRotateMode.APN
    }

    fun setAutoMode(context: Context, mode: ApnRotateMode) {
        prefs(context).edit().putString(KEY_AUTO_MODE, mode.name).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(ProxyService.PREFS_NAME, Context.MODE_PRIVATE)
}

/**
 * Mode rotasi IP otomatis. [method] memakai nama cara kerja (dipakai di log),
 * [label] nama pendek untuk tab, [description] penjelasan di bawah tab.
 */
enum class ApnRotateMode(
    val label: String,
    val method: String,
    val description: String,
) {
    APN("APN", "auto.changeip.apn", "Switch to another APN, then rebuild the data session."),
    AT("AT", "auto.changeip.at", "Detach and re-attach GPRS through the modem AT port."),
    AIRPLANE("Airplane", "auto.changeip.airplane", "Toggle airplane mode on the cell radio only."),
}

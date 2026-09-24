package netflow.dev.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Log in-app: setiap entri diteruskan ke logcat (perilaku seperti biasa) DAN
 * disimpan ke ring buffer yang bisa dilihat di screen Logger.
 *
 * Dibutuhkan karena aplikasi normal tidak boleh membaca log sistem
 * (`READ_LOGS` adalah permission signature-level), jadi satu-satunya cara
 * menampilkan "semua log" tanpa root adalah mencatat sendiri di titik-titik
 * penting. Dengan root, Logger juga bisa menempelkan keluaran `logcat -d`
 * penuh dari perangkat.
 */
object AppLog {

    enum class Level { V, D, I, W, E }

    data class Entry(
        val timeMs: Long,
        val level: Level,
        val tag: String,
        val msg: String,
    )

    private const val CAPACITY = 1000

    // Bounded ring buffer: entri terlama dibuang saat penuh supaya memori
    // tidak tumbuh tanpa batas pada sesi panjang.
    private val buffer = ArrayDeque<Entry>()
    private val lock = Any()

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun v(tag: String, msg: String) = add(Level.V, tag, msg)
    fun d(tag: String, msg: String) = add(Level.D, tag, msg)
    fun i(tag: String, msg: String) = add(Level.I, tag, msg)
    fun w(tag: String, msg: String) = add(Level.W, tag, msg)
    fun e(tag: String, msg: String) = add(Level.E, tag, msg)

    fun e(tag: String, msg: String, tr: Throwable) = add(Level.E, tag, "$msg: ${tr.message}")

    private fun add(level: Level, tag: String, msg: String) {
        // Teruskan ke logcat lebih dulu supaya perilaku debug existing tetap.
        when (level) {
            Level.V -> Log.v(tag, msg)
            Level.D -> Log.d(tag, msg)
            Level.I -> Log.i(tag, msg)
            Level.W -> Log.w(tag, msg)
            Level.E -> Log.e(tag, msg)
        }
        val entry = Entry(System.currentTimeMillis(), level, tag, msg)
        synchronized(lock) {
            if (buffer.size >= CAPACITY) buffer.removeFirst()
            buffer.addLast(entry)
            _entries.value = buffer.toList()
        }
    }

    /** Kosongkan buffer (logcat perangkat tidak tersentuh). */
    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _entries.value = emptyList()
        }
    }

    /** Semua entri sebagai teks polos, siap ditempel ke clipboard. */
    fun asText(): String = _entries.value.joinToString("\n") { e ->
        val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.ROOT)
            .format(java.util.Date(e.timeMs))
        "$ts ${e.level}/${e.tag}: ${e.msg}"
    }
}

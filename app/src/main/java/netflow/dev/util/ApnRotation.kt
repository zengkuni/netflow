package netflow.dev.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sinyal sekali jalan: rotasi APN otomatis baru saja sukses. [rotated] memuat
 * epoch millis rotasi terakhir (0 = belum pernah); UI yang mengoleksinya
 * memperbarui kartu APN dan geo-IP.
 */
object ApnRotation {
    private val _rotated = MutableStateFlow(0L)
    val rotated: StateFlow<Long> = _rotated.asStateFlow()

    fun notifyRotated() {
        _rotated.value = System.currentTimeMillis()
    }
}

// API rotasi IP seluler lewat jalur modem (AT command) dan jalur framework
// (airplane mode). Keduanya tidak menyentuh preferensi APN: tujuannya
// membangun ulang sesi PDP yang sedang berjalan, bukan berpindah APN.
//
// Eksekusi diserahkan ke ShellExecutor dan parsing ke fungsi murni di bawah
// sehingga API dapat diuji dengan shell fake.

package netflow.dev.apn.api

import netflow.dev.apn.shell.ExecResult
import netflow.dev.apn.shell.LibsuShellExecutor
import netflow.dev.apn.shell.ShellExecutor

/**
 * Hasil rotasi IP non-APN ([IpRotateApi.autoChangeIpAt] dan
 * [IpRotateApi.autoChangeIpAirplane]).
 *
 * [info] berisi JSON mentah `https://ipinfo.io/json` setelah data pulih.
 * [detachSeen] khusus jalur AT: true bila modem benar-benar melaporkan
 * `+CGATT: 0` (detach GPRS terkonfirmasi) sebelum attach ulang. [error] terisi
 * saat [ok] false.
 */
data class IpRotateResult(
    val ok: Boolean,
    val info: String? = null,
    val detachSeen: Boolean? = null,
    val radioRestored: Boolean? = null,
    val error: String? = null,
) {
    companion object {
        fun success(info: String?, detachSeen: Boolean? = null, radioRestored: Boolean? = null) =
            IpRotateResult(true, info, detachSeen, radioRestored)

        fun failure(message: String) =
            IpRotateResult(false, error = message)
    }
}

/**
 * IpRotateApi menyediakan rotasi IP di luar jalur APN: detach/attach GPRS via
 * AT command, dan toggle airplane mode yang hanya menyentuh radio seluler.
 *
 * Konstruktor menerima [ShellExecutor] (default [LibsuShellExecutor]) agar
 * dapat disuntikkan fake pada pengujian.
 */
class IpRotateApi(
    private val shell: ShellExecutor = LibsuShellExecutor(),
) {
    /**
     * Rotasi IP dengan detach/attach GPRS modem (`AT+CGATT=0` lalu `=1`).
     *
     * Alur:
     * 1. `AT+CGATT=0` (detach) lalu poll `AT+CGATT?` hingga modem melaporkan
     *    `+CGATT: 0`, batas [detachMillis]. Bila batas habis tanpa `0`,
     *    dilaporkan sebagai `detachSeen=false` dan siklus dihentikan.
     * 2. `AT+CGATT=1` (attach ulang).
     * 3. Tunggu data benar-benar naik (poll `ipinfo.io`, batas [enableMillis])
     *    dan pakai body JSON-nya sebagai hasil.
     *
     * Port AT default [AtPort] (`/dev/smd8`). Semua perintah AT dijalankan lewat
     * helper in-shell yang menyalakan pembaca `cat` paralel (modem hanya
     * menjawab saat ada pembaca) dengan timeout keras, tanpa mem-bunuh proses
     * lain.
     *
     * [sleepMillis] dan [nowMillis] disuntikkan agar uji deterministik.
     */
    suspend fun autoChangeIpAt(
        detachMillis: Long = AtDetachCapMillis,
        enableMillis: Long = EnableCapMillis,
        pollMillis: Long = 500,
        sleepMillis: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
        nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    ): IpRotateResult {
        val detach = atCommand("AT+CGATT=0")
        if (!detach.ok) {
            return IpRotateResult.failure("AT+CGATT=0 gagal: ${detach.error ?: "tanpa pesan"}")
        }

        val detached = waitForAtGatt(
            wantAttached = false,
            capMillis = detachMillis,
            pollMillis = pollMillis,
            sleepMillis = sleepMillis,
            nowMillis = nowMillis,
        )
        if (!detached) {
            return IpRotateResult.failure("detach GPRS tidak terkonfirmasi (+CGATT: 0 tidak muncul)")
        }

        val attach = atCommand("AT+CGATT=1")
        if (!attach.ok) {
            return IpRotateResult.failure("AT+CGATT=1 gagal: ${attach.error ?: "tanpa pesan"}")
        }

        val info = when (val poll = waitForData(
            wantUp = true,
            capMillis = enableMillis,
            pollMillis = pollMillis,
            sleepMillis = sleepMillis,
            nowMillis = nowMillis,
        )) {
            is DataPoll.Up -> poll.body
            DataPoll.Timeout ->
                return IpRotateResult.failure("data tidak pulih setelah attach dalam ${enableMillis} ms")
            DataPoll.Down ->
                return IpRotateResult.failure("data tidak pulih setelah attach (ipinfo tidak terbaca)")
        }

        return IpRotateResult.success(info, detachSeen = true)
    }

    /**
     * Rotasi IP lewat toggle airplane mode, **hanya menyentuh radio seluler**.
     *
     * `cmd connectivity airplane-mode enable` mematikan seluruh radio yang
     * terdaftar di `airplane_mode_radios` (default termasuk bluetooth/wifi).
     * Agar WiFi dan radio lain tetap hidup - "hanya data internet yang
     * diganggu" - daftar itu di-set sementara ke `cell` saja, lalu dipulihkan
     * ke nilai semula pada `finally`.
     *
     * Alur:
     * 1. Simpan `airplane_mode_radios`, set ke `cell`.
     * 2. Airplane ON -> tunggu data benar-benar turun. Bila data tidak turun
     *    dalam [disableMillis], siklus dibatalkan dan dilaporkan gagal:
     *    toggle tanpa detach tidak melepas PDP, jadi IP tidak berotasi.
     * 3. Airplane OFF (selalu dieksekusi lebih dulu, walau langkah 2 gagal).
     * 4. Tunggu data naik (poll `ipinfo.io`), ambil body JSON.
     * 5. Pulihkan `airplane_mode_radios` (selalu, walau gagal).
     *
     * [radioRestored] true bila daftar radio kembali ke nilai semula.
     */
    suspend fun autoChangeIpAirplane(
        disableMillis: Long = DisableCapMillis,
        enableMillis: Long = EnableCapMillis,
        pollMillis: Long = 500,
        sleepMillis: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
        nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    ): IpRotateResult {
        val saved = shell.exec("settings get global airplane_mode_radios")
        if (!saved.ok) {
            return IpRotateResult.failure("baca airplane_mode_radios gagal: ${saved.error ?: "tanpa pesan"}")
        }
        val previous = saved.stdout.trim()

        // Set hanya radio `cell` yang ikut airplane; WiFi/blob tetap hidup.
        val setRadios = shell.exec("settings put global airplane_mode_radios cell")
        if (!setRadios.ok) {
            return IpRotateResult.failure("set airplane_mode_radios gagal: ${setRadios.error ?: "tanpa pesan"}")
        }

        val on = shell.exec("cmd connectivity airplane-mode enable")
        if (!on.ok) {
            restoreRadios(previous)
            return IpRotateResult.failure("airplane ON gagal: ${on.error ?: "tanpa pesan"}")
        }
        val down = waitForData(
            wantUp = false, capMillis = disableMillis, pollMillis = pollMillis,
            sleepMillis = sleepMillis, nowMillis = nowMillis,
        )

        val off = shell.exec("cmd connectivity airplane-mode disable")
        if (!off.ok) {
            restoreRadios(previous)
            return IpRotateResult.failure("airplane OFF gagal: ${off.error ?: "tanpa pesan"}")
        }

        // Airplane sudah dimatikan di atas; baru aman melaporkan kegagalan.
        // Tanpa pemeriksaan ini, siklus yang data tak pernah turun akan
        // dilaporkan sukses padahal tidak ada PDP yang dilepas.
        if (down != DataPoll.Down) {
            restoreRadios(previous)
            val batas = if (down == DataPoll.Timeout) " dalam ${disableMillis} ms" else ""
            return IpRotateResult.failure("data tidak turun setelah airplane ON$batas; rotasi dibatalkan")
        }

        val poll = waitForData(
            wantUp = true, capMillis = enableMillis, pollMillis = pollMillis,
            sleepMillis = sleepMillis, nowMillis = nowMillis,
        )
        // Pulihkan daftar radio apa pun hasil poll data.
        val restored = restoreRadios(previous)
        return when (poll) {
            is DataPoll.Up -> IpRotateResult.success(poll.body, radioRestored = restored)
            DataPoll.Timeout ->
                IpRotateResult.failure("data tidak pulih setelah airplane OFF dalam ${enableMillis} ms")
            DataPoll.Down ->
                IpRotateResult.failure("data tidak pulih setelah airplane OFF (ipinfo tidak terbaca)")
        }
    }

    /**
     * Pulihkan `airplane_mode_radios` ke [previous]. Mengembalikan true bila
     * nilai kosong (tak ada yang perlu dipulihkan) atau penulisan berhasil.
     */
    private suspend fun restoreRadios(previous: String): Boolean {
        if (previous.isEmpty()) return true
        return shell.exec("settings put global airplane_mode_radios '$previous'").ok
    }

    /**
     * Jalankan satu perintah AT lewat helper in-shell dan kembalikan hasilnya.
     *
     * Port modem hanya menjawab bila ada pembaca, jadi port dibuka sebagai fd
     * read-write (`exec 3<>`), perintah ditulis, lalu isi port dibaca oleh satu
     * `cat` yang **dibunuh eksplisit by PID**. Tidak memakai `timeout`:
     * `timeout` toybox di perangkat ini tidak membunuh child-nya, sehingga
     * pembaca nyangkut menumpuk dan mencuri respons modem (pemicu kegagalan
     * `AT+CGATT?` saat E2E). Tidak ada `kill` luas - hanya PID `cat` sendiri.
     */
    private suspend fun atCommand(cmd: String): ExecResult {
        if (cmd.any { it == '\n' || it == '\r' || it == '\'' }) {
            return ExecResult.failure("perintah AT tidak valid: '$cmd'")
        }
        // Helper dijalankan langsung di shell (bukan sebagai file) supaya tidak
        // butuh Context/assets.
        val script = buildString {
            append("P=").append(AtPort).append("; O=").append(AtOutFile).append("; :> \"\$O\"; ")
            append("exec 3<>\"\$P\"; ")
            append("printf '%s\\r' '").append(cmd).append("' >&3; ")
            append("cat <&3 >> \"\$O\" 2>/dev/null & C=\$!; ")
            append("sleep ").append(AtReadMillis / 1000).append("; ")
            append("kill -9 \$C 2>/dev/null; wait \$C 2>/dev/null; ")
            append("exec 3<&- 3>&-; cat \"\$O\" 2>/dev/null")
        }
        return shell.exec(script)
    }

    /**
     * Poll `AT+CGATT?` sampai status attach GPRS sesuai [wantAttached], dengan
     * batas atas jam-dinding [capMillis]. Mengembalikan true bila status
     * tercapai sebelum batas.
     */
    private suspend fun waitForAtGatt(
        wantAttached: Boolean,
        capMillis: Long,
        pollMillis: Long,
        sleepMillis: suspend (Long) -> Unit,
        nowMillis: () -> Long,
    ): Boolean {
        val deadline = nowMillis() + capMillis
        while (true) {
            val r = atCommand("AT+CGATT?")
            if (r.ok) {
                val state = parseGattState(r.stdout)
                if (state != null && state == wantAttached) return true
            }
            if (nowMillis() >= deadline) return false
            sleepMillis(pollMillis)
        }
    }

    /**
     * Uji keterjangkauan data dengan prasyarat eksak (GET `ipinfo.io/json`),
     * sama seperti jalur APN: data attach + DHCP + DNS + route siap.
     * Mengembalikan body JSON bila HTTP 2xx dan memuat field `ip`.
     *
     * Transport (curl/fallback) dan pengikatan ke interface seluler ditangani
     * [probeIpInfo]; tanpa jalur seluler yang terlihat, data dianggap turun.
     */
    private suspend fun probeData(): String? =
        probeIpInfo(shell, cellularIface(shell), ProbeTimeoutMillis)

    /**
     * Tunggu sampai data terjangkau ([wantUp] true) atau turun ([wantUp]
     * false), dengan batas atas jam-dinding [capMillis]. Bila [wantUp] true dan
     * berhasil, mengembalikan body JSON ipinfo dari percobaan itu.
     */
    private suspend fun waitForData(
        wantUp: Boolean,
        capMillis: Long,
        pollMillis: Long,
        sleepMillis: suspend (Long) -> Unit,
        nowMillis: () -> Long,
    ): DataPoll {
        val deadline = nowMillis() + capMillis
        while (true) {
            val body = probeData()
            if (wantUp) {
                if (body != null) return DataPoll.Up(body)
            } else if (body == null) {
                return DataPoll.Down
            }
            if (nowMillis() >= deadline) return DataPoll.Timeout
            sleepMillis(pollMillis)
        }
    }
}

/**
 * Hasil polling koneksi data. Dipisah dari sekadar `String?` agar pemanggil
 * tidak menyamakan "data terkonfirmasi turun" dengan "cap waktu habis",
 * keduanya dulu `null`, sehingga siklus yang gagal bisa dilaporkan sukses.
 */
private sealed interface DataPoll {
    /** Data terkonfirmasi naik, beserta body JSON ipinfo. */
    data class Up(val body: String) : DataPoll

    /** Data terkonfirmasi turun (probe IP publik gagal). */
    data object Down : DataPoll

    /** Cap waktu habis sebelum kondisi yang diminta terpenuhi. */
    data object Timeout : DataPoll
}

/** Port AT modem Qualcomm (SDM660/whyred): jalur AT utama. */
const val AtPort: String = "/dev/smd8"

/**
 * Batas waktu satu probe IP publik (milidetik). Saat data mati, `curl` gagal
 * hampir instan (kode `000`) dan fallback `nc` gagal connect cepat; saat siap
 * sukses < 1 s. 2 s longgar dan menjaga tiap poll tetap murah sehingga
 * cap-waktu tetap bermakna.
 */
const val ProbeTimeoutMillis: Long = 2_000

/** File sementara untuk menampung balasan modem; dipotong tiap perintah. */
const val AtOutFile: String = "/data/local/tmp/at.out"

/**
 * Jendela baca balasan modem (milidetik). Modem membalas dalam < 1 s pada
 * perangkat uji; 2 s adalah pengaman. Nilai ini **bukan** `timeout` proses,
 * pembaca dibunuh eksplisit by PID, jadi tak ada proses nyangkut.
 */
const val AtReadMillis: Long = 2_000

/**
 * Batas atas menunggu detach GPRS terkonfirmasi (`+CGATT: 0`). Detach
 * biasanya selesai dalam 1-2 detik; nilai ini pengaman.
 */
const val AtDetachCapMillis: Long = 6_000

/**
 * Baca status attach GPRS dari keluaran `AT+CGATT?`.
 *
 * Mencari baris `+CGATT: <n>` dan mengembalikan true bila `n` = 1, false bila
 * `0`. Mengembalikan null bila baris status tidak ditemukan (balasan kosong /
 * belum siap), sehingga pemanggil dapat mengulang poll.
 */
fun parseGattState(output: String): Boolean? {
    val m = Regex("""\+CGATT:\s*(\d)""").find(output) ?: return null
    return when (m.groupValues[1]) {
        "1" -> true
        "0" -> false
        else -> null
    }
}

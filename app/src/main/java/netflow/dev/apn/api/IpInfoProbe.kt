// Probe IP publik lewat HTTP dengan transport yang tersedia.
//
// Dipakai bersama oleh jalur APN (`ApnApi`) dan jalur rotasi non-APN
// (`IpRotateApi`) supaya hanya ada SATU konvensi transport di modul ini.

package netflow.dev.apn.api

import netflow.dev.apn.parse.parseIpInfoIp
import netflow.dev.apn.shell.ExecResult
import netflow.dev.apn.shell.ShellExecutor

/** Host yang dipakai untuk membaca IP publik. */
const val IpInfoHost: String = "ipinfo.io"

/** Path JSON yang memuat field `ip`. */
const val IpInfoPath: String = "/json"

/** File sementara untuk request fallback `nc`. */
const val ProbeReqFile: String = "/data/local/tmp/netflow.req"

/** File sementara untuk respons fallback `nc`. */
const val ProbeOutFile: String = "/data/local/tmp/netflow.out"

/**
 * Cari interface data seluler dari tabel routing.
 *
 * Diperlukan karena `curl` polos memakai default route - pada perangkat uji
 * itu `wlan0` (WiFi), sehingga IP yang terbaca adalah IP WiFi dan bukan bukti
 * rotasi IP seluler. Baris `default ... dev rmnet*` adalah jalur seluler;
 * mengembalikan null bila tidak ada (mis. GPRS detached, data mati).
 */
suspend fun cellularIface(shell: ShellExecutor): String? {
    val r = shell.exec("ip -4 route show table all")
    if (!r.ok) return null
    for (raw in r.stdout.lineSequence()) {
        val line = raw.trim()
        if (!line.startsWith("default")) continue
        val m = Regex("""\bdev\s+(\S+)""").find(line) ?: continue
        val dev = m.groupValues[1]
        if (dev.startsWith("rmnet")) return dev
    }
    return null
}

/** Cari IPv4 sebuah interface; null bila interface belum punya alamat. */
private suspend fun ifaceIpv4(shell: ShellExecutor, iface: String): String? {
    val r = shell.exec("ip -4 -o addr show $iface")
    if (!r.ok) return null
    val m = Regex("""\binet\s+(\d+(?:\.\d+){3})""").find(r.stdout) ?: return null
    return m.groupValues[1]
}

/**
 * Apakah binary `curl` tersedia.
 *
 * Dicek **sebelum** dipakai: sebagian build/ROM Android tidak menyertakan
 * `curl`, sehingga probe harus punya fallback alih-alih gagal senyap.
 */
private suspend fun hasCurl(shell: ShellExecutor): Boolean {
    val r = shell.exec("command -v curl")
    return r.ok && r.stdout.trim().isNotEmpty()
}

/**
 * Baca IP publik dari ipinfo.io.
 *
 * Mengembalikan body JSON hanya bila HTTP 2xx dan memuat field `ip`; null untuk
 * semua kegagalan lain (data turun, DNS belum siap, tak ada transport, route
 * seluler belum ada). Dengan begitu pemanggil memperlakukan "tak terbaca"
 * sebagai data belum siap, bukan sebagai error yang perlu dibedakan.
 *
 * Transport dipilih dengan mengecek ketersediaan `curl` lebih dulu; bila tidak
 * ada, jatuh ke `toybox nc` (`/system/bin/toybox` selalu ada di Android) yang
 * mengirim HTTP/1.0 polos. Kedua transport mengukur prasyarat eksak yang sama:
 * data attach + DHCP + DNS + route siap.
 *
 * [iface] wajib interface seluler (`rmnet*`). Bila null - data turun, route
 * seluler hilang - probe dilewatkan dan hasilnya null. Probe **tidak pernah**
 * dijalankan tanpa pengikatan: default route perangkat ini WiFi, sehingga hasil
 * tanpa pengikatan adalah IP WiFi, bukan IP seluler.
 * [maxMillis] membatasi waktu tunggu jaringan.
 */
suspend fun probeIpInfo(shell: ShellExecutor, iface: String?, maxMillis: Long): String? {
    // Tanpa interface seluler yang dikenal, probe TIDAK dijalankan sama sekali.
    // Default route perangkat ini adalah WiFi; request tanpa pengikatan akan
    // mengembalikan IP WiFi dan terbaca sebagai "IP seluler baru" - persis
    // kesalahan yang membuat jalur rotasi melaporkan sukses tanpa rotasi.
    // Saat data turun route `rmnet` hilang, jadi null di sini berarti
    // "data belum siap", bukan "pakai jalur lain".
    if (iface == null) return null
    val secs = ((maxMillis + 999) / 1000).coerceAtLeast(1)
    if (hasCurl(shell)) {
        val r = shell.exec(
            "curl -s --interface $iface -w '\\n%{http_code}' --max-time $secs https://$IpInfoHost$IpInfoPath",
        )
        if (!r.ok) return null
        val out = r.stdout.trim()
        val nl = out.lastIndexOf('\n')
        if (nl < 0) return null
        val body = out.substring(0, nl).trim()
        val code = out.substring(nl + 1).trim()
        if (!code.startsWith("2")) return null
        if (parseIpInfoIp(body) == null) return null
        return body
    }
    val src = ifaceIpv4(shell, iface) ?: return null
    val script = buildString {
        append("printf 'GET ").append(IpInfoPath)
        append(" HTTP/1.0\\r\\nHost: ").append(IpInfoHost).append("\\r\\n\\r\\n' > '")
        append(ProbeReqFile).append("'; ")
        append("toybox nc -4 -s ").append(src).append(" -w ").append(secs)
        append(" -q 1 ").append(IpInfoHost).append(" 80 < '").append(ProbeReqFile)
        append("' > '").append(ProbeOutFile).append("' 2>/dev/null; cat '")
        append(ProbeOutFile).append("' 2>/dev/null")
    }
    val r = shell.exec(script)
    if (!r.ok) return null
    val body = parseHttpResponse(r.stdout) ?: return null
    if (parseIpInfoIp(body) == null) return null
    return body
}

/**
 * Ambil body dari respons HTTP mentah (respons fallback `nc`).
 *
 * Menerima CRLF maupun LF (libsu menggabungkan baris stdout dengan `\n`),
 * menormalkan keduanya, lalu memisahkan header dan body pada baris kosong.
 * Mengembalikan null bila baris status bukan `HTTP/` atau kode bukan 2xx.
 */
internal fun parseHttpResponse(raw: String): String? {
    val text = raw.replace("\r\n", "\n").trim('\n')
    if (text.isEmpty()) return null
    val sep = text.indexOf("\n\n")
    if (sep < 0) return null
    val status = text.substring(0, sep).lineSequence().firstOrNull()?.trim()
    if (status == null || !status.startsWith("HTTP/")) return null
    val code = status.split(' ', limit = 3).getOrNull(1) ?: return null
    if (!code.startsWith("2")) return null
    return text.substring(sep + 2).trim().ifEmpty { null }
}

// Parser output `content query` Android menjadi daftar Apn. Murni (tanpa
// I/O) sehingga dapat diuji di JVM host tanpa perangkat.
//
// Parser ini generik untuk proyeksi kolom APN (lihat ShellProjection): satu
// tempat menyerap kedua format output `content query` (satu-baris dan
// banyak-baris) sehingga tidak ada parsing ad-hoc di lapisan lain.

package netflow.dev.apn.parse

import netflow.dev.apn.model.Apn
import netflow.dev.apn.model.sanitizeNumeric

/**
 * Regex yang memecah output `content query` menjadi baris ternormalisasi.
 *
 * Urutan grup HARUS sama dengan `ShellProjection`; `.*?` (non-greedy) untuk
 * name/apn/type menyerap koma di dalam nilai (mis. `type=default,supl`) dan
 * berhenti pada pemisah ", key=" berikutnya.
 */
private val ROW_HEADER_RE = Regex("""^Row:\s*\d+\s*(.*)$""")
private val KV_LINE_RE = Regex("""^\s*[A-Za-z_][A-Za-z0-9_]*=""")
private val SHELL_ROW_RE = Regex(
    """^_id=(?<id>[^,]+), """ +
        """name=(?<name>.*?), apn=(?<apn>.*?), type=(?<type>.*?), """ +
        """numeric=(?<numeric>[^,]+), current=(?<current>[^,]+), """ +
        """carrier_enabled=(?<ce>[^,]+)$""",
)

/**
 * Satukan kedua format output `content query` (modern satu-baris dan lama
 * banyak-baris) menjadi daftar string `"k=v, k=v, ..."` siap diparse.
 */
internal fun normalizeRows(out: String): List<String> {
    val rows = mutableListOf<String>()
    val cur = mutableListOf<String>()
    fun flush() {
        if (cur.isNotEmpty()) {
            rows.add(cur.joinToString(", "))
            cur.clear()
        }
    }
    for (line in out.split("\n")) {
        val header = ROW_HEADER_RE.find(line)
        if (header != null) {
            flush()
            val rest = header.groupValues[1].trim()
            if (rest.isNotEmpty()) cur.add(rest)
            continue
        }
        if (KV_LINE_RE.containsMatchIn(line)) {
            cur.add(line.trim())
        }
    }
    flush()
    return rows
}

/**
 * Pecah satu baris ternormalisasi menjadi [Apn]. Mengembalikan null bila
 * format tak dikenali (mis. proyeksi berbeda).
 */
internal fun parseApnRow(row: String): Apn? {
    val m = SHELL_ROW_RE.find(row) ?: return null
    val g = m.groups
    fun v(name: String): String = g[name]!!.value.trim()

    // numeric = MCC+MNC disambung; pecah kembali jadi dua kolom.
    val numeric = sanitizeNumeric(v("numeric"))
    val mcc = if (numeric.length > 3) numeric.substring(0, 3) else numeric
    val mnc = if (numeric.length > 3) numeric.substring(3) else ""

    return Apn(
        id = v("id").toLongOrNull() ?: 0L,
        name = v("name"),
        apnName = v("apn"),
        apnType = v("type"),
        mcc = mcc,
        mnc = mnc,
        numeric = numeric,
        current = v("current").toLongOrNull() ?: 0L,
        carrierEnabled = v("ce").toLongOrNull() ?: 0L,
    )
}

/**
 * Parse output tool `content query` menjadi daftar [Apn]. Baris yang tak
 * dikenali (mis. noise SecurityException) diabaikan; input kosong -> list
 * kosong.
 */
fun parseContentQuery(out: String): List<Apn> =
    normalizeRows(out).mapNotNull(::parseApnRow)

/** Ambil `_id` dari baris `content query` provider preferapn. */
private val PREFERRED_ID_RE = Regex("""(?:^|[,\s])_id=(\d+)""")

/**
 * Parse `_id` APN preferensi dari output `content query` provider
 * `preferapn`. Mengembalikan null bila tidak ada baris atau `_id` hilang.
 */
fun parsePreferredApnId(out: String): Long? =
    PREFERRED_ID_RE.find(out)?.groupValues?.get(1)?.toLongOrNull()

/** Ambil nilai field `"ip"` dari JSON ipinfo.io tanpa parser JSON penuh. */
private val IPINFO_IP_RE = Regex(""""ip"\s*:\s*"([^"]+)"""")

/**
 * Ambil alamat IP dari JSON ipinfo.io (mis. dari
 * `curl -s https://ipinfo.io/json`). Mengembalikan null bila field `ip`
 * tidak ada.
 */
fun parseIpInfoIp(json: String): String? =
    IPINFO_IP_RE.find(json)?.groupValues?.get(1)?.trim()?.ifEmpty { null }

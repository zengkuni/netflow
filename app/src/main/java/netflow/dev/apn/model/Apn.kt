// Model APN + logika murni (tanpa I/O) dari tabel Telephony.Carriers.
// Paket ini tidak bergantung pada Android maupun libsu sehingga bisa diuji
// penuh di JVM host.

package netflow.dev.apn.model

/**
 * Apn merepresentasikan satu baris tabel Telephony.Carriers.
 *
 * Nama field mengikuti kolom `android.provider.Telephony.Carriers` yang
 * dipakai proyeksi query (lihat [ShellProjection]).
 */
data class Apn(
    val id: Long,
    val name: String,
    val apnName: String,
    val apnType: String,
    val proxy: String = "",
    val port: String = "",
    val mcc: String,
    val mnc: String,
    val numeric: String,
    val current: Long,
    val carrierEnabled: Long,
)

/** Apakah baris ini APN preferensi aktif (kolom `current` == 1). */
val Apn.isPreferred: Boolean get() = current == 1L

/** Apakah baris ini boleh dipilih operator (kolom `carrier_enabled` != 0). */
val Apn.isEnabled: Boolean get() = carrierEnabled != 0L

/** Apakah baris ini membentuk PDN data default. Lihat [formsDefaultPdn]. */
val Apn.formsDefaultPdn: Boolean get() = formsDefaultPdn(apnType)

/**
 * Format satu baris ringkas untuk log/stdout. Baris preferensi ditandai "*"
 * pada kolom pertama.
 */
fun Apn.format(): String {
    val mark = if (isPreferred) "*" else " "
    val status = if (isEnabled) "enabled" else "disabled"
    val apn = if (apnName.isEmpty()) "-" else apnName
    return "$mark #$id ${ellipsize(name, 24)} apn=$apn " +
        "type=${if (apnType.isEmpty()) "-" else apnType} " +
        "oper=$mcc/$mnc $status"
}

/** Potong [s] ke maksimal [n] karakter + "…" bila terpotong. */
internal fun ellipsize(s: String, n: Int): String =
    if (s.length > n) s.substring(0, n) + "…" else s

/**
 * Apakah [apnType] membentuk PDN data default. Mengikuti semantik AOSP
 * `ApnSetting.getApnTypesBitmaskFromString`:
 *
 * - Kolom tipe KOSONG ("") dipetakan ke `TYPE_ALL` yang memuat DEFAULT,
 *   sehingga dianggap default.
 * - Selain itu, dianggap default bila daftar memuat token `default`
 *   (case-insensitive, spasi di sekitar token diabaikan).
 * - String yang hanya berisi spasi BUKAN empty menurut `TextUtils.isEmpty`
 *   (yang hanya cek panjang 0); AOSP mem-split(",") -> token " " tak dikenal
 *   -> bitmask 0 (TYPE_NONE). Karena itu spasi murni TIDAK dianggap default.
 */
fun formsDefaultPdn(apnType: String): Boolean {
    if (apnType.isEmpty()) return true
    return apnType.split(",").any { it.trim().equals("default", ignoreCase = true) }
}

/** Kembalikan baris dengan [id] tertentu, atau null bila tidak ada. */
fun List<Apn>.findByID(id: Long): Apn? = firstOrNull { it.id == id }

/**
 * Pastikan [numeric] (MCC+MNC) hanya berisi digit sehingga tidak bisa
 * menyuntik perintah ke klausa selection provider.
 */
fun sanitizeNumeric(numeric: String): String = numeric.filter { it.isDigit() }

// -- Kontrak data operasi APN ------------------------------------------------

/** URI content provider tabel APN Android (Telephony.Carriers.CONTENT_URI). */
const val CarriersURI = "content://telephony/carriers"

/** URI provider preferensi APN (Telephony.Carriers.PREFERRED_APN_URI). */
const val PreferredApnURI = "content://telephony/carriers/preferapn"

/**
 * Daftar kolom yang diminta ke `content query`. Urutannya HARUS sama dengan
 * grup regex di parser `content query`; dengan urutan tetap, pemisahan baris
 * tetap andal meski suatu nilai kolom mengandung koma (mis. type=default,supl).
 */
const val ShellProjection = "_id:name:apn:type:numeric:current:carrier_enabled"

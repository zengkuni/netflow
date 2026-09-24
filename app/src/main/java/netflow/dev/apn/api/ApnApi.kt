// API method untuk operasi APN. Lapisan ini hanya menyusun perintah dan
// menerjemahkan hasil; eksekusi diserahkan ke ShellExecutor (shell/) dan
// parsing ke parse/. Dengan begitu API bisa diuji dengan ShellExecutor fake.

package netflow.dev.apn.api

import netflow.dev.apn.model.Apn
import netflow.dev.apn.model.CarriersURI
import netflow.dev.apn.model.PreferredApnURI
import netflow.dev.apn.model.ShellProjection
import netflow.dev.apn.model.sanitizeNumeric
import netflow.dev.apn.parse.parseContentQuery
import netflow.dev.apn.parse.parseIpInfoIp
import netflow.dev.apn.parse.parsePreferredApnId
import netflow.dev.apn.shell.ExecResult
import netflow.dev.apn.shell.LibsuShellExecutor
import netflow.dev.apn.shell.ShellExecutor

/**
 * Hasil pembacaan daftar APN dari perangkat.
 *
 * [apns] hanya terisi bila [ok] true. [error] berisi pesan kegagalan shell
 * (transport / non-zero exit) sehingga pemanggil API tidak perlu melempar
 * exception.
 */
data class ApnListResult(
    val ok: Boolean,
    val apns: List<Apn>,
    val error: String? = null,
) {
    companion object {
        fun success(apns: List<Apn>) = ApnListResult(true, apns)
        fun failure(message: String) = ApnListResult(false, emptyList(), message)
    }
}

/**
 * Identitas SIM aktif: MCC, MNC, dan numeric (MCC+MNC) SIM di slot aktif.
 *
 * [ok] false bila shell gagal atau properti kosong (tidak ada SIM / tidak
 * terbaca) - pada kondisi itu [numeric], [mcc], dan [mnc] kosong dan [error]
 * memuat alasannya.
 */
data class ActiveSimResult(
    val ok: Boolean,
    val numeric: String,
    val mcc: String,
    val mnc: String,
    val error: String? = null,
) {
    companion object {
        /** [numeric] harus sudah disanitasi (digit saja). */
        fun success(numeric: String) = ActiveSimResult(
            ok = true,
            numeric = numeric,
            mcc = numeric.take(3),
            mnc = numeric.drop(3),
        )

        fun failure(message: String) =
            ActiveSimResult(false, "", "", "", message)
    }
}

/**
 * APN preferensi aktif (baris yang ditunjuk `carriers/preferapn`).
 *
 * [ok] false bila preferensi belum diset, provider gagal dibaca, atau id
 * preferensi tidak ada di tabel `carriers` - pada kondisi itu [apn] null dan
 * [error] memuat alasannya.
 */
data class CurrentApnResult(
    val ok: Boolean,
    val apn: Apn?,
    val error: String? = null,
) {
    companion object {
        fun success(apn: Apn) = CurrentApnResult(true, apn)
        fun failure(message: String) = CurrentApnResult(false, null, message)
    }
}

/**
 * Spesifikasi baris APN baru untuk [ApnApi.addApn].
 *
 * Kolom `current` tidak ada di sini karena provider menentukannya sendiri
 * (insert dengan `current` eksplisit diabaikan, lihat catatan di [ApnApi.addApn]).
 */
data class ApnDraft(
    val name: String,
    val apnName: String,
    val apnType: String,
    val mcc: String,
    val mnc: String,
    val numeric: String,
    val carrierEnabled: Long = 1,
)

/**
 * Hasil operasi tulis (insert/delete) APN.
 *
 * [ok] false bila shell gagal (transport/non-zero exit) atau validasi argumen
 * gagal; [error] memuat alasannya.
 */
data class WriteResult(
    val ok: Boolean,
    val error: String? = null,
) {
    companion object {
        fun success() = WriteResult(true)
        fun failure(message: String) = WriteResult(false, message)
    }
}

/**
 * Hasil [ApnApi.autoChangeIp].
 *
 * [apnName] nama APN yang dipakai setelah rotasi, [info] JSON mentah dari
 * `https://ipinfo.io/json`. [switchedTo] berisi id APN yang dipilih (null bila
 * tidak ada rotasi karena hanya satu kandidat). [error] terisi saat [ok] false.
 */
data class AutoChangeIpResult(
    val ok: Boolean,
    val apnName: String? = null,
    val info: String? = null,
    val switchedTo: Long? = null,
    val error: String? = null,
) {
    companion object {
        fun success(apnName: String?, info: String?, switchedTo: Long?) =
            AutoChangeIpResult(true, apnName, info, switchedTo)

        fun failure(message: String) = AutoChangeIpResult(false, error = message)
    }
}

/**
 * ApnApi menyediakan operasi APN lewat [ShellExecutor].
 *
 * Konstruktor menerima [ShellExecutor] sehingga pemanggil dapat
 * menyuntikkan implementasi (mis. fake untuk uji), dengan default
 * [LibsuShellExecutor] yang memakai shell root libsu.
 */
class ApnApi(
    private val shell: ShellExecutor = LibsuShellExecutor(),
) {
    /**
     * Ambil baris APN dari content provider.
     *
     * Menjalankan `content query` (opsional dengan `--where`) lewat
     * [ShellExecutor], lalu mem-parse outputnya dengan [parseContentQuery].
     * Bila [where] null, tidak ada klausa `--where` (seluruh baris).
     *
     * [where] disisipkan apa adanya ke perintah shell, jadi pemanggil yang
     * menerima nilai dari luar (mis. JSON-RPC) WAJIB memvalidasinya lebih
     * dulu, lihat [isSafeWhere].
     *
     * Tidak melempar untuk kegagalan shell/command: kegagalan dikembalikan
     * sebagai [ApnListResult.failure].
     */
    suspend fun getListApn(where: String? = DefaultApnWhere): ApnListResult {
        val cmd = buildString {
            append("content query --uri ").append(CarriersURI)
            append(" --projection ").append(ShellProjection)
            if (!where.isNullOrBlank()) append(" --where \"").append(where).append('"')
        }
        val r = shell.exec(cmd)
        if (!r.ok) {
            return ApnListResult.failure(r.error ?: "content query gagal")
        }
        return ApnListResult.success(parseContentQuery(r.stdout))
    }

    /**
     * Baca identitas SIM aktif (MCC, MNC, numeric) dari properti sistem
     * `gsm.sim.operator.numeric`. Mengembalikan [ActiveSimResult] dengan
     * `ok=false` bila shell gagal, properti kosong, atau numeric kurang dari
     * 5 digit (tidak ada SIM / belum terdaftar).
     */
    suspend fun activeSim(): ActiveSimResult {
        val r: ExecResult = shell.exec("getprop gsm.sim.operator.numeric")
        if (!r.ok) {
            return ActiveSimResult.failure(r.error ?: "getprop gsm.sim.operator.numeric gagal")
        }
        val numeric = sanitizeNumeric(r.lines.lastOrNull().orEmpty().trim())
        if (numeric.length < 5) {
            return ActiveSimResult.failure("numeric SIM tidak tersedia: '$numeric'")
        }
        return ActiveSimResult.success(numeric)
    }

    /** Apakah shell root tersedia (bounded, tidak pernah menggantung). */
    suspend fun isRootGranted(): Boolean = shell.isRootGranted()

    // -- Operasi preferensi + siklus data ------------------------------------

    /**
     * Baca id APN preferensi aktif dari `content://telephony/carriers/preferapn`.
     *
     * Mengembalikan null bila provider tidak mengembalikan baris (umum: belum
     * ada preferensi eksplisit) atau bila `_id` tidak dapat diparsing.
     */
    suspend fun preferredApnId(): Long? {
        val r = shell.exec("content query --uri $PreferredApnURI")
        if (!r.ok) return null
        return parsePreferredApnId(r.stdout)
    }

    /**
     * Baca APN yang ditunjuk preferensi saat ini (`carriers/preferapn`) sebagai
     * satu objek [Apn].
     *
     * Berbeda dari [getListApn] yang mengembalikan semua baris `current=1`,
     * method ini hanya mengembalikan baris yang benar-benar dipakai provider
     * sebagai preferensi. Mengembalikan [CurrentApnResult] dengan `ok=false`
     * bila preferensi belum diset, pembacaan preferensi gagal, atau id
     * preferensi tidak ditemukan di tabel `carriers`.
     */
    suspend fun currentApn(): CurrentApnResult {
        val prefer = shell.exec("content query --uri $PreferredApnURI")
        if (!prefer.ok) {
            return CurrentApnResult.failure(prefer.error ?: "query preferapn gagal")
        }
        val id = parsePreferredApnId(prefer.stdout)
            ?: return CurrentApnResult.failure("preferensi APN belum diset")

        // Kueri baris berdasarkan id preferensi, lewat jalur --where yang sama
        // dengan getListApn (proyeksi kolom identik).
        val row = shell.exec(
            "content query --uri $CarriersURI --projection $ShellProjection" +
                " --where \"_id=$id\"",
        )
        if (!row.ok) {
            return CurrentApnResult.failure(row.error ?: "query carriers gagal")
        }
        val apn = parseContentQuery(row.stdout).firstOrNull()
            ?: return CurrentApnResult.failure("APN preferensi id=$id tidak ditemukan")

        return CurrentApnResult.success(apn)
    }

    /**
     * Tetapkan APN [id] sebagai preferensi aktif.
     *
     * [id] divalidasi sebagai angka oleh tipe [Long]; karena itu nilainya
     * tidak mungkin menyuntik perintah ke argumen `--bind`. `sub_id` tidak
     * diikutsertakan agar provider memakai SIM default.
     */
    suspend fun setPreferredApn(id: Long): ExecResult =
        shell.exec("content insert --uri $PreferredApnURI --bind apn_id:i:$id")

    /**
     * Sisipkan satu baris APN baru ke tabel `carriers`.
     *
     * Nilai string (`name`, `apn`, `type`) di-quote untuk shell lewat
     * [quoteShellArg] sehingga spasi dan karakter lain aman (mis. name
     * "Indosat MMS"). Kolom numerik (`mcc`, `mnc`, `numeric`) wajib digit;
     * `numeric` dinormalkan dengan [sanitizeNumeric].
     *
     * Kolom `current` TIDAK di-bind: provider menetapkannya sendiri, dan insert
     * `current` eksplisit diabaikan (terbukti di perangkat - bind `current:i:0`
     * tetap tersimpan `current=1`).
     *
     * `content insert` tidak mencetak id baris baru, jadi [WriteResult] hanya
     * melaporkan sukses/gagal; id baris baru dibaca lewat [getListApn].
     */
    suspend fun addApn(draft: ApnDraft): WriteResult {
        if (draft.name.isEmpty()) return WriteResult.failure("name tidak boleh kosong")
        if (draft.apnName.isEmpty()) return WriteResult.failure("apn tidak boleh kosong")

        // Kolom numerik wajib digit agar tidak menyuntik/memecah argumen.
        if (!isNumericValue(draft.mcc)) return WriteResult.failure("mcc harus angka: '${draft.mcc}'")
        if (!isNumericValue(draft.mnc)) return WriteResult.failure("mnc harus angka: '${draft.mnc}'")
        val numeric = sanitizeNumeric(draft.numeric)
        if (numeric.isEmpty()) return WriteResult.failure("numeric harus angka: '${draft.numeric}'")

        // Setiap nilai string di-quote untuk shell; spasi/kutip/tanda lain aman.
        val name = quoteShellArg(draft.name)
            ?: return WriteResult.failure("name tidak valid: '${draft.name}'")
        val apn = quoteShellArg(draft.apnName)
            ?: return WriteResult.failure("apn tidak valid: '${draft.apnName}'")
        val type = quoteShellArg(draft.apnType)
            ?: return WriteResult.failure("type tidak valid: '${draft.apnType}'")

        val cmd = buildString {
            append("content insert --uri ").append(CarriersURI)
            append(" --bind name:s:").append(name)
            append(" --bind apn:s:").append(apn)
            append(" --bind type:s:").append(type)
            append(" --bind mcc:s:").append(draft.mcc)
            append(" --bind mnc:s:").append(draft.mnc)
            append(" --bind numeric:s:").append(numeric)
            append(" --bind carrier_enabled:i:").append(draft.carrierEnabled)
        }
        val r = shell.exec(cmd)
        return if (r.ok) WriteResult.success() else WriteResult.failure(r.error ?: "insert APN gagal")
    }

    /**
     * Hapus baris APN berdasarkan [id].
     *
     * [id] bertipe [Long] sehingga klausa `--where` hanya berisi angka dan
     * tidak dapat menyuntik perintah.
     */
    suspend fun deleteApn(id: Long): WriteResult {
        val r = shell.exec("content delete --uri $CarriersURI --where \"_id=$id\"")
        return if (r.ok) WriteResult.success() else WriteResult.failure(r.error ?: "delete APN gagal")
    }

    /**
     * Hapus baris APN berdasarkan nilai kolom `apn`.
     *
     * Klausa `--where` dibangun oleh [apnWhereClause] (escape SQL + quote
     * shell), sehingga nilai dengan spasi/kutip aman. Menghapus berdasarkan
     * nilai bisa mengenai lebih dari satu baris (kolom `apn` tidak unik), dan
     * tetap berlaku pada baris yang id-nya tidak diketahui.
     */
    suspend fun deleteApnByValue(apnValue: String): WriteResult {
        if (apnValue.isEmpty()) return WriteResult.failure("apn tidak boleh kosong")
        val where = apnWhereClause(apnValue)
            ?: return WriteResult.failure("nilai apn tidak valid: '$apnValue'")
        val r = shell.exec("content delete --uri $CarriersURI --where $where")
        return if (r.ok) WriteResult.success() else WriteResult.failure(r.error ?: "delete APN gagal")
    }

    /** Aktifkan koneksi data seluler. */
    suspend fun enableData(): ExecResult = shell.exec("svc data enable")

    /** Matikan koneksi data seluler. */
    suspend fun disableData(): ExecResult = shell.exec("svc data disable")

    /**
     * Baca IP publik saat ini dari `https://ipinfo.io/json`.
     *
     * Mengembalikan null bila transport gagal, JSON tidak memuat field `ip`,
     * **atau data seluler sedang turun** (tak ada route `rmnet`, jadi tak ada
     * interface untuk diikat). Transport memilih `curl` bila tersedia dan jatuh
     * ke `toybox nc` bila tidak - lihat [probeIpInfo]. Request diikat ke
     * interface seluler supaya IP yang terbaca bukan IP WiFi yang memegang
     * default route.
     */
    suspend fun publicIp(): String? =
        probeIpInfo(shell, cellularIface(shell), PublicIpTimeoutMillis)?.let { parseIpInfoIp(it) }

    /**
     * Ambil JSON mentah dari `https://ipinfo.io/json`.
     *
     * Mengembalikan null bila transport gagal atau keluarannya tidak memuat
     * field `ip` (indikasi respons bukan JSON yang diharapkan).
     */
    suspend fun ipInfoJson(): String? =
        probeIpInfo(shell, cellularIface(shell), PublicIpTimeoutMillis)

    /**
     * Uji keterjangkauan data dengan prasyarat eksak: GET `ipinfo.io/json`.
     *
     * Itu kondisi yang benar-benar dibutuhkan [ipInfoJson] (data attach + DHCP
     * + DNS + route siap), bukan proxy seperti `getprop`/`dumpsys` yang bisa
     * tampak "LTE" walau PDP belum aktif. Mengembalikan body JSON bila HTTP
     * 2xx dan memuat field `ip`, null bila belum siap.
     *
     * Batas waktu 2 s: saat data mati transport gagal hampir instan, saat siap
     * sukses < 1 s - 2 s longgar dan menjaga tiap poll tetap murah sehingga
     * cap-waktu tetap bermakna.
     */
    suspend fun probeData(): String? =
        probeIpInfo(shell, cellularIface(shell), ProbeTimeoutMillis)

    /**
     * Tunggu sampai data terjangkau ([wantUp] true) atau benar-benar turun
     * ([wantUp] false), dengan **batas atas jam-dinding** [capMillis].
     *
     * Setiap iterasi memanggil [probeData] (yang sekaligus menguji prasyarat
     * eksak `ipinfo`); bila tujuannya tercapai sebelum batas, kembali lebih
     * awal. Cap dihitung dari durasi nyata (`System.nanoTime`), bukan
     * akumulasi jeda, sehingga satu probe yang lambat pun tidak melewati cap.
     *
     * Bila [wantUp] true dan berhasil, mengembalikan body JSON ipinfo dari
     * percobaan itu (dipakai ulang oleh pemanggil → tanpa GET kedua); selain
     * itu null. [nowMillis] disuntikkan agar uji dapat memakai waktu palsu.
     */
    suspend fun waitForData(
        wantUp: Boolean,
        capMillis: Long,
        pollMillis: Long = 500,
        sleepMillis: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
        nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    ): String? {
        val deadline = nowMillis() + capMillis
        while (true) {
            val body = probeData()
            if (wantUp) {
                if (body != null) return body
            } else if (body == null) {
                return null
            }
            if (nowMillis() >= deadline) return null
            sleepMillis(pollMillis)
        }
    }

    /**
     * Siklus data bersama: set preferensi ke [id] -> disable -> tunggu data
     * turun (poll, batas [disableMillis]) -> enable -> tunggu data naik
     * (poll, batas [enableMillis]).
     *
     * Mengembalikan [ExecResult] kegagalan tahap, atau `ok=true` dengan body
     * JSON ipinfo hasil poll naik di [RotateOutcome.info] (null bila cap habis
     * sebelum data siap). Dipakai [switchPreferredApn] dan [autoChangeIp] agar
     * urutan dan batas atas konsisten.
     */
    private suspend fun rotateDataCycle(
        id: Long,
        disableMillis: Long,
        enableMillis: Long,
        pollMillis: Long,
        sleepMillis: suspend (Long) -> Unit,
        nowMillis: () -> Long,
    ): RotateOutcome {
        val set = setPreferredApn(id)
        if (!set.ok) return RotateOutcome.failed(set.error ?: "set preferensi gagal")

        val down = disableData()
        if (!down.ok) return RotateOutcome.failed(down.error ?: "matikan data gagal")
        waitForData(
            wantUp = false, capMillis = disableMillis, pollMillis = pollMillis,
            sleepMillis = sleepMillis, nowMillis = nowMillis,
        )

        val up = enableData()
        if (!up.ok) return RotateOutcome.failed(up.error ?: "nyalakan data gagal")
        val info = waitForData(
            wantUp = true, capMillis = enableMillis, pollMillis = pollMillis,
            sleepMillis = sleepMillis, nowMillis = nowMillis,
        )
        return RotateOutcome(true, info)
    }

    /**
     * Pindah ke APN [id] lalu paksa koneksi data menyambung ulang.
     *
     * Urutan: set preferensi -> matikan data -> tunggu data benar-benar turun
     * (poll, batas [disableMillis]) -> nyalakan data -> tunggu data benar-benar
     * naik (poll, batas [enableMillis]). Delay tetap lama digantikan polling
     * adaptif: siklus selesai begitu kondisi nyata tercapai, bukan menunggu
     * durasi buta. [sleepMillis] disuntikkan agar dapat dipalsukan di uji.
     */
    suspend fun switchPreferredApn(
        id: Long,
        disableMillis: Long = DisableCapMillis,
        enableMillis: Long = EnableCapMillis,
        pollMillis: Long = 500,
        sleepMillis: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
        nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    ): ExecResult {
        val r = rotateDataCycle(
            id, disableMillis, enableMillis, pollMillis, sleepMillis, nowMillis,
        )
        return if (r.ok) ExecResult.success("") else ExecResult.failure(r.error ?: "siklus data gagal")
    }

    /**
     * Rotasi APN lalu laporkan APN aktif beserta info IP publik.
     *
     * Alur:
     * 1. Baca daftar APN aktif ([getListApn], `current=1` non-MMS).
     * 2. Bila daftar berisi <= 1 baris, tambahkan APN `aha` dari identitas SIM
     *    aktif ([activeSim]) agar ada kandidat alternatif, lalu baca ulang
     *    daftar.
     * 3. Buang APN yang sedang jadi preferensi ([currentApn]) dari kandidat.
     * 4. Ambil [randomCount] kandidat acak; pilih satu.
     * 5. Pindah ke kandidat terpilih lalu siklus data adaptif: tunggu data
     *    benar-benar turun (poll, batas [DisableCapMillis]), nyalakan, lalu
     *    tunggu `ipinfo.io` berhasil (poll, batas [EnableCapMillis]). Body IP
     *    dari polling itu dipakai langsung, tanpa GET kedua.
     *
     * [random] disuntikkan untuk pengujian deterministik. Bila tidak ada
     * kandidat setelah rotasi (mis. hanya satu APN dan gagal menambah `aha`),
     * hasilnya `ok=false`.
     */
    suspend fun autoChangeIp(
        randomCount: Int = 3,
        sleepMillis: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
        random: () -> Int = { kotlin.random.Random.nextInt() },
        nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    ): AutoChangeIpResult {
        // 1. Daftar APN aktif saat ini.
        val first = getListApn()
        if (!first.ok) {
            return AutoChangeIpResult.failure("apn.list gagal: ${first.error ?: "tanpa pesan"}")
        }

        var candidates = first.apns
        if (candidates.size <= 1) {
            // 2. Pastikan ada APN `aha` dari identitas SIM agar ada alternatif.
            if (first.apns.none { it.name == AhaApnName }) {
                val sim = activeSim()
                if (!sim.ok) {
                    return AutoChangeIpResult.failure("sim.active gagal: ${sim.error ?: "tanpa pesan"}")
                }
                val add = addApn(
                    ApnDraft(
                        name = AhaApnName,
                        apnName = AhaApnName,
                        apnType = "default,supl",
                        mcc = sim.mcc,
                        mnc = sim.mnc,
                        numeric = sim.numeric,
                    ),
                )
                if (!add.ok) {
                    return AutoChangeIpResult.failure("apn.add aha gagal: ${add.error ?: "tanpa pesan"}")
                }
            }
            val refetch = getListApn()
            if (!refetch.ok) {
                return AutoChangeIpResult.failure("apn.list ulang gagal: ${refetch.error ?: "tanpa pesan"}")
            }
            candidates = refetch.apns
        }

        // 3. Buang APN yang sedang menjadi preferensi.
        val currentId = currentApn().apn?.id
        val pool = candidates.filter { it.id != currentId }
            .ifEmpty { candidates }

        val picked = pickApn(pool, randomCount, random)
            ?: return AutoChangeIpResult.failure("tidak ada kandidat APN untuk rotasi")

        // 4. Pindah + siklus data adaptif (helper bersama dengan
        //    switchPreferredApn), lalu pakai body IP dari polling yang sudah
        //    berhasil (tanpa GET kedua).
        val cycle = rotateDataCycle(
            picked.id,
            disableMillis = DisableCapMillis,
            enableMillis = EnableCapMillis,
            pollMillis = 500,
            sleepMillis = sleepMillis,
            nowMillis = nowMillis,
        )
        if (!cycle.ok) {
            return AutoChangeIpResult.failure(cycle.error ?: "siklus data gagal")
        }
        val info = cycle.info
            ?: return AutoChangeIpResult.failure("gagal baca ipinfo.io")
        return AutoChangeIpResult.success(picked.name, info, picked.id)
    }
}

/**
 * Hasil [ApnApi.rotateDataCycle]: [ok] true bila seluruh tahap siklus data
 * berhasil dijalankan. [info] berisi body JSON ipinfo dari poll naik bila
 * data siap sebelum batas; null bila cap habis atau tahap sebelumnya gagal.
 * [error] terisi saat [ok] false.
 */
private data class RotateOutcome(
    val ok: Boolean,
    val info: String? = null,
    val error: String? = null,
) {
    companion object {
        fun failed(message: String) = RotateOutcome(false, error = message)
    }
}

/** Nama APN cadangan yang dibuat [ApnApi.autoChangeIp] bila kandidat minim. */
const val AhaApnName: String = "aha"

/**
 * Batas atas menunggu data benar-benar turun setelah `svc data disable`.
 * Saat data mati, `curl` gagal hampir instan sehingga biasanya jauh lebih
 * cepat; nilai ini hanya pengaman.
 */
const val DisableCapMillis: Long = 4_000

/**
 * Batas atas menunggu data naik + IP terbaca setelah `svc data enable`.
 * Polling berhenti begitu `ipinfo.io` berhasil, biasanya < 5 s; nilai ini
 * pengaman untuk kondisi lambat.
 */
const val EnableCapMillis: Long = 14_000

/**
 * Batas waktu satu probe IP publik untuk pembacaan tunggal (`publicIp` /
 * `ipInfoJson`), bukan polling. Lebih longgar daripada [ProbeTimeoutMillis]
 * karena tidak diulang dalam loop.
 */
const val PublicIpTimeoutMillis: Long = 15_000

/**
 * Ambil satu APN acak dari [pool].
 *
 * Bila ukuran pool <= 1, ambil satu-satunya. Bila lebih, batasi ke
 * [randomCount] baris pertama lalu pilih satu dengan [random]. Mengembalikan
 * null bila [pool] kosong.
 */
internal fun pickApn(
    pool: List<Apn>,
    randomCount: Int,
    random: () -> Int,
): Apn? {
    if (pool.isEmpty()) return null
    if (pool.size == 1) return pool.first()
    val limit = randomCount.coerceIn(1, pool.size)
    val window = pool.take(limit)
    val idx = ((random() % window.size) + window.size) % window.size
    return window[idx]
}

/**
 * Klausa `--where` default untuk [ApnApi.getListApn]: hanya APN yang sedang
 * aktif (preferensi berjalan) dan bukan tipe MMS. Penyaringan dilakukan di
 * sisi content provider, bukan setelah data diambil.
 */
const val DefaultApnWhere: String = "current=1 AND type != 'mms'"

/**
 * Validasi longgar untuk klausa `--where` sebelum disisipkan ke perintah
 * shell. Hanya mengizinkan karakter yang lazim pada ekspresi kolom SQLite
 * (`_id`, `name`, `type`, `current`, operator, tanda kutip, koma, spasi).
 * Menolak `;`, backtick, `$`, `&`, `|`, `<`, `>`, backslash, dan baris baru
 * yang dapat keluar dari perintah `content query`.
 */
fun isSafeWhere(where: String): Boolean {
    if (where.isBlank() || where.length > 256) return false
    return where.all { c ->
        c.isLetterOrDigit() || c == '_' || c == ' ' || c == '=' ||
            c == '!' || c == '\'' || c == ',' || c == '.' || c == ':' ||
            c == '(' || c == ')'
    }
}

/**
 * Bungkus [value] menjadi kata shell berkutip tunggal yang aman untuk disisipkan
 * ke perintah `content` (dikirim sebagai teks ke shell root persisten libsu).
 *
 * Di dalam kutip tunggal shell POSIX semua karakter bersifat literal kecuali
 * kutip tunggal itu sendiri; kutip tunggal di-escape dengan pola `'\''`.
 * Mengembalikan null bila [value] memuat karakter yang tidak dapat diwakili:
 * baris baru, carriage return, atau NUL (baris baru adalah terminator perintah
 * pada shell yang dipakai, jadi harus ditolak, bukan di-quote).
 */
fun quoteShellArg(value: String): String? {
    if (value.length > 256) return null
    if (value.any { it == '\n' || it == '\r' || it == '\u0000' }) return null
    return "'" + value.replace("'", "'\\''") + "'"
}

/**
 * Nilai kolom numerik yang aman: hanya digit (memanfaatkan [sanitizeNumeric]).
 * Dipakai untuk `mcc`, `mnc`, dan `numeric` pada [ApnApi.addApn].
 */
fun isNumericValue(value: String): Boolean =
    value.isNotEmpty() && value.all { it.isDigit() }

/**
 * Bangun klausa `--where` untuk pencocokan nilai kolom `apn` secara aman.
 *
 * Nilai di-escape untuk SQLite (kutip tunggal ganda `''`), lalu seluruh klausa
 * dibungkus kutip tunggal shell lewat [quoteShellArg] sehingga shell menerima
 * `apn='<nilai>'` sebagai satu argumen. Mengembalikan null bila nilai memuat
 * karakter yang ditolak [quoteShellArg].
 */
fun apnWhereClause(apnValue: String): String? {
    val sqlEscaped = apnValue.replace("'", "''")
    return quoteShellArg("apn='$sqlEscaped'")
}

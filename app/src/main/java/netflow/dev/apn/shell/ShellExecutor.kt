// Lapisan eksekusi shell root. Menyembunyikan libsu di balik satu interface
// kecil sehingga modul lain (api/, jsonrpc/, service) tidak mengimpor libsu
// secara langsung dan mudah diganti/di-fake saat pengujian.

package netflow.dev.apn.shell

import com.topjohnwu.superuser.Shell
import netflow.dev.util.LibsuRootShell
import netflow.dev.util.RootShell
import netflow.dev.util.RootState

/**
 * Hasil satu perintah shell yang sudah dinormalkan.
 *
 * [ok] true hanya bila perintah benar-benar dieksekusi dan keluar dengan
 * kode 0. [error] berisi pesan kegagalan siap-tampil (stdout/stderr), null
 * bila sukses.
 */
data class ExecResult(
    val ok: Boolean,
    val stdout: String,
    val error: String? = null,
) {
    /** Baris stdout tanpa baris kosong di ujung. */
    val lines: List<String> get() = stdout.split("\n").filter { it.isNotEmpty() }

    companion object {
        fun success(stdout: String) = ExecResult(true, stdout)
        fun failure(message: String) = ExecResult(false, "", message)
    }
}

/**
 * ShellExecutor menjalankan perintah shell root.
 *
 * Implementasi default [LibsuShellExecutor] memakai shell root persisten
 * libsu (lewat [RootShell] milik app); implementasi lain (mis. fake) dapat
 * disuntikkan untuk pengujian.
 */
interface ShellExecutor {
    /** Jalankan [command] secara sinkron dan kembalikan hasilnya. */
    suspend fun exec(command: String): ExecResult

    /** Apakah shell root tersedia (bounded, tidak pernah menggantung). */
    suspend fun isRootGranted(): Boolean
}

/**
 * Implementasi [ShellExecutor] di atas libsu, melalui seam [RootShell] milik
 * app (lihat `netflow.dev.util.LibsuRootShell`).
 *
 * Seam itu memegang satu shell root persisten per proses app dan sudah
 * menjamin kontrak never-throws: root yang belum di-grant, shell mati, atau
 * kegagalan exec dipetakan ke [ExecResult.failure], sehingga pemanggil di
 * api/ tetap berjalan pada perangkat tanpa root.
 */
class LibsuShellExecutor(
    private val shell: RootShell = LibsuRootShell,
) : ShellExecutor {

    override suspend fun exec(command: String): ExecResult {
        // Shell persistent libsu dibuat saat request(); pada proses fresh
        // (mis. di-start daemon) state masih Unknown, jadi minta dulu.
        // Manajer superuser mengingat grant, jadi ini sunyi setelah approval
        // pertama.
        if (shell.state.value != RootState.Granted) shell.request()
        val r = shell.exec(command)
        return if (r.success) {
            ExecResult.success(r.output)
        } else {
            ExecResult.failure("perintah gagal (code=${r.code}): ${r.output}")
        }
    }

    override suspend fun isRootGranted(): Boolean =
        shell.state.value == RootState.Granted || shell.request() == RootState.Granted
}

/**
 * Jalur `Shell.cmd(...)` libsu untuk aliran keluaran (mis. `logcat -d`).
 * Disediakan agar pemanggil tidak mengimpor libsu secara langsung.
 */
fun streamCommand(command: String): Shell.Job = Shell.cmd(command)

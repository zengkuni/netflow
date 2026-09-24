package netflow.dev.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import netflow.dev.util.AppLog
import netflow.dev.apn.api.ApnApi
import netflow.dev.apn.api.IpRotateApi
import netflow.dev.apn.jsonrpc.JsonRpcExecutor
import netflow.dev.apn.jsonrpc.JsonRpcRegistry
import netflow.dev.apn.jsonrpc.JsonRpcServer
import netflow.dev.apn.jsonrpc.JsonRpcServerConfig
import netflow.dev.apn.jsonrpc.registerApnMethods
import netflow.dev.apn.jsonrpc.registerIpMethods
import netflow.dev.apn.shell.LibsuShellExecutor

/**
 * Host untuk [JsonRpcServer]: API JSON-RPC 2.0 untuk operasi APN dan rotasi
 * IP (port 9060). Hidup bersama siklus proxy: diminta start oleh
 * [ProxyService] saat proxy naik, diminta stop pada [ProxyService.fullCleanup].
 *
 * Sengaja BUKAN foreground service dan tidak punya notifikasi sendiri:
 * ProxyService sudah memegang satu notifikasi yang merangkum keduanya, dan
 * startService dari konteks foreground service aman dari background-start
 * limits. Kegagalan bind port cukup di-log lalu service mati sendiri; proxy
 * tidak ikut jatuh.
 */
class JsonRpcService : Service() {

    private var server: JsonRpcServer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (server != null) return START_STICKY
        val shell = LibsuShellExecutor()
        val registry = JsonRpcRegistry()
        registerApnMethods(registry, ApnApi(shell))
        registerIpMethods(registry, IpRotateApi(shell))
        val srv = JsonRpcServer(
            executor = JsonRpcExecutor(
                registry,
                onCall = { method, ok, detail ->
                    // Satu baris per panggilan API, apa pun method-nya, supaya
                    // menu Logger menunjukkan siapa melakukan apa dan apa yang
                    // gagal. Keberhasilan di-D supaya log tetap hidup tanpa
                    // membebani daftar; kegagalan di-W karena itu yang perlu
                    // diperhatikan.
                    if (ok) {
                        AppLog.d(TAG, "api $method ok")
                    } else {
                        AppLog.w(TAG, "api $method failed: $detail")
                    }
                },
            ),
            config = JsonRpcServerConfig(bindHost = BIND_HOST, port = PORT),
            onLog = { line -> AppLog.i(TAG, line) },
        )
        server = srv
        // start() melempar bila port tak bisa di-bind; jangan jatuhkan proses,
        // cukup matikan service ini (proxy tetap jalan).
        runCatching { srv.start() }.onFailure { e ->
            AppLog.w(TAG, "jsonrpc server gagal start: ${e.message}")
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { server?.stop() }
        server = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "JsonRpcService"

        /**
         * Bind ke semua antarmuka (0.0.0.0) atas permintaan operator agar API
         * dapat dipanggil dari host/LAN, bukan hanya dari perangkat sendiri.
         *
         CATATAN KEAMANAN (kesadaran operator): method JSON-RPC menjalankan
         perintah root (tulis tabel APN, toggle airplane mode) dan API ini
         TIDAK punya autentikasi. Dengan bind 0.0.0.0, setiap klien yang bisa
         menjangkau port ini memperoleh kendali radio perangkat. Aman hanya
         pada jaringan yang dipercaya. Kalau nanti perlu dibuka ke jaringan
         yang tidak dipercaya, tambahkan autentikasi sebelum melonggarkan ini.
         */
        const val BIND_HOST = "0.0.0.0"

        /** Port API JSON-RPC. */
        const val PORT = 9060

        fun startIntent(context: Context): Intent =
            Intent(context, JsonRpcService::class.java)

        fun stopIntent(context: Context): Intent =
            Intent(context, JsonRpcService::class.java)
    }
}

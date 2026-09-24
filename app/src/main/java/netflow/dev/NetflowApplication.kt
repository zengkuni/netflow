package netflow.dev

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import java.security.Security

class NetflowApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Disable JVM-level positive + negative DNS caching. Most of our
        // resolves go through Network#getAllByName on the cellular Network,
        // but any JVM fallback (e.g. java.net.InetAddress.getByName from a
        // library, or a future code path) would otherwise cache a poisoned
        // answer for the whole process lifetime, only force-stopping the
        // app would clear it. Cellular carriers in censorship regions
        // occasionally serve hijacked DNS, so a stale poisoned entry would
        // make TLS look broken to every client.
        Security.setProperty("networkaddress.cache.ttl", "0")
        Security.setProperty("networkaddress.cache.negative.ttl", "0")

        val mgr = getSystemService(NotificationManager::class.java)
        // Channel JSON-RPC lama (dari versi yang memakai service foreground
        // terpisah) dihapus: notifikasi keduanya sudah digabung ke satu
        // channel ini. Channel persist setelah update, jadi dibersihkan di
        // sini sekali per proses.
        mgr.deleteNotificationChannel("netflow.jsonrpc")
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Netflow",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Ongoing proxy status"
                    setShowBadge(false)
                }
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "netflow.status"
    }
}

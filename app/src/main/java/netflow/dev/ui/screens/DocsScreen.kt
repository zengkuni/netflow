package netflow.dev.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import netflow.dev.ui.components.HeroCard
import netflow.dev.ui.components.Overline
import netflow.dev.ui.components.ScreenHeader
import netflow.dev.ui.theme.TileRadius
import netflow.dev.ui.theme.CardRadius
import netflow.dev.ui.theme.HeroOnPrimary
import netflow.dev.ui.theme.HeroOnSecondary
import netflow.dev.ui.theme.JsonBool
import netflow.dev.ui.theme.JsonKey
import netflow.dev.ui.theme.JsonNumber
import netflow.dev.ui.theme.JsonString
import netflow.dev.ui.theme.OutlineSoft
import netflow.dev.ui.theme.SurfaceLow
import netflow.dev.ui.theme.TextMuted
import netflow.dev.ui.theme.TextPrimary
import netflow.dev.ui.theme.Info
import netflow.dev.ui.theme.TextSecondary
import netflow.dev.ui.theme.HeroAccent
import netflow.dev.ui.theme.Accent
import netflow.dev.ui.theme.SurfaceMid

/**
 * Dokumentasi API JSON-RPC: alamat server, dua bentuk transport, dan setiap
 * method dengan contoh request/response nyata (diambil dari keluaran device).
 * Isinya statis, tidak menyentuh jaringan, supaya aman dibaca kapan saja.
 */
@Composable
fun DocsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .padding(top = 4.dp, bottom = 12.dp),
    ) {
        ScreenHeader(
            title = "JSON-RPC docs",
            eyebrow = "API",
            onBack = onBack,
        )
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ServerCard()
            methods.forEach { m -> MethodCard(m) }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Satu entry dokumentasi method. */
private data class MethodDoc(
    val name: String,
    val summary: String,
    val params: String?,
    val request: String,
    val response: String,
)

private val methods = listOf(
    MethodDoc(
        name = "ping",
        summary = "Cek server hidup. Tanpa parameter.",
        params = null,
        request = """{"jsonrpc":"2.0","id":1,"method":"ping"}""",
        response = """{"jsonrpc":"2.0","id":1,"result":{"ok":true,"error":null,"data":"pong"}}""",
    ),
    MethodDoc(
        name = "root.granted",
        summary = "Apakah shell root tersedia untuk perintah APN.",
        params = null,
        request = """{"jsonrpc":"2.0","id":2,"method":"root.granted"}""",
        response = """{"jsonrpc":"2.0","id":2,"result":{"ok":true,"error":null,"data":true}}""",
    ),
    MethodDoc(
        name = "apn.list",
        summary = "Daftar APN. Tanpa where memakai filter default " +
            "(current=1 dan bukan mms); where kosong menampilkan semua baris.",
        params = "where (string, opsional)",
        request = """{"jsonrpc":"2.0","id":3,"method":"apn.list"}""",
        response = """{"jsonrpc":"2.0","id":3,"result":{"ok":true,"error":null,"data":[{"id":3485,"name":"XL Unlimited","apn":"xlunlimited","type":"default,supl","mcc":"510","mnc":"11","numeric":"51011","current":1,"carrierEnabled":1}]}}""",
    ),
    MethodDoc(
        name = "sim.active",
        summary = "MCC/MNC SIM di slot data aktif.",
        params = null,
        request = """{"jsonrpc":"2.0","id":4,"method":"sim.active"}""",
        response = """{"jsonrpc":"2.0","id":4,"result":{"ok":true,"error":null,"data":{"mnc":"11","numeric":"51011"}}}""",
    ),
    MethodDoc(
        name = "apn.current",
        summary = "APN preferensi yang sedang aktif.",
        params = null,
        request = """{"jsonrpc":"2.0","id":5,"method":"apn.current"}""",
        response = """{"jsonrpc":"2.0","id":5,"result":{"ok":true,"error":null,"data":{"id":3485,"name":"XL Unlimited","apn":"xlunlimited","type":"default,supl","mcc":"510","mnc":"11","numeric":"51011","current":1,"carrierEnabled":1}}}""",
    ),
    MethodDoc(
        name = "apn.switch",
        summary = "Jadikan APN dengan id tertentu sebagai preferensi aktif.",
        params = "id (number, wajib)",
        request = """{"jsonrpc":"2.0","id":6,"method":"apn.switch","params":{"id":3486}}""",
        response = """{"jsonrpc":"2.0","id":6,"result":{"ok":true,"error":null,"data":null}}""",
    ),
    MethodDoc(
        name = "apn.add",
        summary = "Tambah baris APN baru ke tabel carrier.",
        params = "name, apn, mcc, mnc, numeric (wajib); " +
            "type (default \"default,supl\"), carrierEnabled (default 1) opsional",
        request = """{"jsonrpc":"2.0","id":7,"method":"apn.add","params":{"name":"XL Backup","apn":"xlbackup","type":"default,supl","mcc":"510","mnc":"11","numeric":"51011"}}""",
        response = """{"jsonrpc":"2.0","id":7,"result":{"ok":true,"error":null,"data":null}}""",
    ),
    MethodDoc(
        name = "apn.delete",
        summary = "Hapus APN berdasarkan id atau nilai apn.",
        params = "id (number) atau apn (string), salah satu wajib",
        request = """{"jsonrpc":"2.0","id":8,"method":"apn.delete","params":{"id":3490}}""",
        response = """{"jsonrpc":"2.0","id":8,"result":{"ok":true,"error":null,"data":null}}""",
    ),
    MethodDoc(
        name = "auto.changeip.apn",
        summary = "Rotasi IP lewat jalur APN (siklus data svc). " +
            "memilih APN kandidat lalu memulihkan data.",
        params = null,
        request = """{"jsonrpc":"2.0","id":9,"method":"auto.changeip.apn"}""",
        response = """{"jsonrpc":"2.0","id":9,"result":{"ok":true,"error":null,"data":{"apn_active":"xlunlimited","apn_id":3485,"info":{"ip":"112.215.66.74","country":"ID","org":"AS24203 PT XL Axiata"}}}}""",
    ),
    MethodDoc(
        name = "auto.changeip.at",
        summary = "Rotasi IP lewat AT command modem (detach/attach GPRS).",
        params = null,
        request = """{"jsonrpc":"2.0","id":10,"method":"auto.changeip.at"}""",
        response = """{"jsonrpc":"2.0","id":10,"result":{"ok":true,"error":null,"data":{"info":{"ip":"112.215.66.74"},"detach_seen":true,"radio_restored":true}}}""",
    ),
    MethodDoc(
        name = "auto.changeip.airplane",
        summary = "Rotasi IP lewat toggle airplane mode (hanya radio seluler).",
        params = null,
        request = """{"jsonrpc":"2.0","id":11,"method":"auto.changeip.airplane"}""",
        response = """{"jsonrpc":"2.0","id":11,"result":{"ok":true,"error":null,"data":{"info":{"ip":"112.215.66.74"},"detach_seen":null,"radio_restored":true}}}""",
    ),
)

@Composable
private fun ServerCard() {
    val clipboard = LocalClipboardManager.current
    HeroCard {
        Overline(
            text = "SERVER",
            color = HeroOnSecondary,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "127.0.0.1:9060",
                color = HeroOnPrimary,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString("127.0.0.1:9060"))
                },
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy address",
                    tint = HeroAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Loopback saja. API menjalankan perintah root dan tidak " +
                "punya autentikasi, jadi tidak dibuka ke LAN.",
            color = HeroOnSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Bullet("HTTP POST (curl):", "curl -X POST http://127.0.0.1:9060 -d '{...}'")
        Spacer(Modifier.height(4.dp))
        Bullet("JSON per baris (nc):", "echo '{...}' | nc 127.0.0.1 9060")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Server hidup saat proxy menyala (JsonRpcService di-start " +
                "oleh ProxyService).",
            color = HeroOnSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun MethodCard(m: MethodDoc) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(SurfaceLow)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = m.name,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.IconButton(
                onClick = { clipboard.setText(AnnotatedString(m.request)) },
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy request",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = m.summary,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (m.params != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Params: ${m.params}",
                color = Info,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.height(10.dp))
        CodeBlock(label = "REQUEST", code = m.request)
        Spacer(Modifier.height(8.dp))
        CodeBlock(label = "RESPONSE", code = m.response)
    }
}

@Composable
private fun CodeBlock(label: String, code: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TileRadius))
            .background(SurfaceMid)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Overline(text = label, color = TextMuted)
        Spacer(Modifier.height(4.dp))
        // Pretty-print + syntax colour, gaya dokumentasi API: kunci amber,
        // string hijau, angka oranye, boolean/null ungu. Kalau input bukan
        // JSON valid, teks mentah ditampilkan apa adanya.
        val pretty = remember(code) { prettyJson(code) }
        val colored = colorizeJson(pretty)
        Text(
            text = colored,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            ),
        )
    }
}

/** Indentasi 2 spasi lewat org.json; input tak valid dikembalikan utuh. */
private fun prettyJson(code: String): String = runCatching {
    JSONObject(code).toString(2)
}.getOrDefault(code)

/**
 * Warnai token JSON. Urutan regex penting: kunci (string yang diikuti ":")
 * harus dicek sebelum string biasa, dan kata kunci sebelum identifier lain.
 */
@Composable
private fun colorizeJson(json: String): AnnotatedString {
    val keyColor = JsonKey
    val stringColor = JsonString
    val numberColor = JsonNumber
    val boolColor = JsonBool
    val baseColor = TextPrimary
    return remember(json, keyColor, stringColor, numberColor, boolColor) {
        val pattern = Regex(
            """(?<key>"(?:[^"\\]|\\.)*"\s*:)|(?<str>"(?:[^"\\]|\\.)*")|""" +
                """(?<num>-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|(?<bool>\b(?:true|false|null)\b)""",
        )
        val builder = AnnotatedString.Builder()
        var cursor = 0
        pattern.findAll(json).forEach { m ->
            if (m.range.first > cursor) {
                builder.append(json.substring(cursor, m.range.first))
            }
            val groups = m.groups as MatchNamedGroupCollection
            val color = when {
                groups["key"] != null -> keyColor
                groups["str"] != null -> stringColor
                groups["num"] != null -> numberColor
                else -> boolColor
            }
            val start = builder.length
            builder.append(m.value)
            builder.addStyle(SpanStyle(color = color), start, builder.length)
            cursor = m.range.last + 1
        }
        if (cursor < json.length) builder.append(json.substring(cursor))
        builder.toAnnotatedString()
    }
}

@Composable
private fun Bullet(label: String, code: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "-",
            color = HeroOnSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                text = label,
                color = HeroOnPrimary,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = code,
                color = HeroOnSecondary,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                ),
            )
        }
    }
}

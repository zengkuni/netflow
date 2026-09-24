// Transport JSON-RPC di atas TCP loopback. Server menerima DUA bentuk pada
// port yang sama, dideteksi dari byte pertama:
//
// 1. HTTP/1.1 POST  (untuk `curl -X POST` / Python `requests`)
//      POST / HTTP/1.1
//      Content-Length: 46
//      {"jsonrpc":"2.0","id":1,"method":"apn.list"}
//
// 2. JSON per baris (untuk `nc`)
//      {"jsonrpc":"2.0","id":1,"method":"apn.list"}\n
//
// Kelas ini murni java.net (tanpa Android Context) sehingga dapat dijalankan
// dari mana saja dan diuji tanpa perangkat.

package netflow.dev.apn.jsonrpc

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Opsi server JSON-RPC. */
data class JsonRpcServerConfig(
    /**
     * Alamat bind; default `0.0.0.0` (semua antarmuka) atas permintaan
     * operator agar server dapat dipanggil dari host/LAN, bukan hanya
     * loopback. Batasi ke `127.0.0.1` bila hanya pemakaian lokal.
     */
    val bindHost: String = "0.0.0.0",
    val port: Int = 9060,
)

/**
 * Server JSON-RPC TCP. Setiap koneksi ditangani di thread sendiri (blocking
 * I/O sederhana, mudah dibaca); request diproses berurutan per koneksi.
 *
 * [onLog] menerima baris log ringkas (opsional) agar pemanggil dapat
 * meneruskannya ke logcat tanpa membuat kelas ini bergantung pada Android.
 */
class JsonRpcServer(
    private val executor: JsonRpcExecutor,
    private val config: JsonRpcServerConfig = JsonRpcServerConfig(),
    private val onLog: (String) -> Unit = {},
) {
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val connections = AtomicInteger(0)

    /** Port efektif (berguna bila [JsonRpcServerConfig.port] = 0). */
    val boundPort: Int get() = serverSocket?.localPort ?: -1

    /** Mulai server; melempar bila port tak bisa di-bind. */
    fun start() {
        val s = ServerSocket(config.port, 16, InetAddress.getByName(config.bindHost))
        serverSocket = s
        onLog("jsonrpc server mendengarkan di ${config.bindHost}:${s.localPort}")

        acceptThread = thread(name = "jsonrpc-accept", isDaemon = true) {
            while (!s.isClosed) {
                val client = try {
                    s.accept()
                } catch (e: Exception) {
                    if (s.isClosed) break else continue
                }
                thread(name = "jsonrpc-client", isDaemon = true) { serve(client) }
            }
        }
    }

    private fun serve(client: Socket) {
        connections.incrementAndGet()
        // Tampilkan kedua sisi: alamat client DAN endpoint lokal yang
        // dihubungi (mis. 192.168.1.101:9060). Dengan bind 0.0.0.0, sisi lokal
        // memberi tahu antarmuka mana yang menerima koneksi.
        onLog(
            "koneksi masuk ${fmtAddr(client.remoteSocketAddress)} " +
                "-> ${fmtAddr(client.localSocketAddress)}",
        )
        client.use { c ->
            val input = BufferedInputStream(c.getInputStream())
            val output = BufferedOutputStream(c.getOutputStream())
            try {
                if (looksLikeHttp(input)) {
                    serveHttp(input, output)
                } else {
                    serveLines(input, output)
                }
            } catch (e: Exception) {
                onLog("koneksi error: ${e.message}")
            }
        }
        connections.decrementAndGet()
        onLog("koneksi ditutup")
    }

    /** SocketAddress.toString() memakai prefix "/"; buang agar ip:port bersih. */
    private fun fmtAddr(addr: java.net.SocketAddress?): String =
        addr?.toString()?.removePrefix("/") ?: "-"

    /**
     * Deteksi HTTP tanpa memakan byte: intip 4 byte awal ("POST"/"GET " dst).
     * Seluruh stream di-buffer, jadi mark/reset aman.
     */
    private fun looksLikeHttp(input: BufferedInputStream): Boolean {
        input.mark(8)
        val head = ByteArray(4)
        var n = 0
        while (n < 4) {
            val r = input.read(head, n, 4 - n)
            if (r < 0) break
            n += r
        }
        input.reset()
        if (n < 4) return false
        val text = String(head, 0, 4, StandardCharsets.US_ASCII)
        return text.startsWith("POST") || text.startsWith("GET ") ||
            text.startsWith("HEAD") || text.startsWith("OPTI")
    }

    // -- Mode baris JSON (nc) -------------------------------------------------

    private fun serveLines(input: BufferedInputStream, output: BufferedOutputStream) {
        readLines(input) { line ->
            if (line.isNotBlank()) {
                val response = runBlockingExecute(line)
                if (response != null) {
                    output.write((response + "\n").toByteArray(StandardCharsets.UTF_8))
                    output.flush()
                }
            }
        }
    }

    private fun readLines(input: BufferedInputStream, onLine: (String) -> Unit) {
        val buf = ByteArrayOutputStream()
        val one = ByteArray(1)
        while (true) {
            val r = input.read(one)
            if (r < 0) break
            if (one[0] == '\n'.code.toByte()) {
                onLine(String(buf.toByteArray(), StandardCharsets.UTF_8))
                buf.reset()
            } else {
                buf.write(one[0].toInt())
            }
        }
        if (buf.size() > 0) onLine(String(buf.toByteArray(), StandardCharsets.UTF_8))
    }

    // -- Mode HTTP POST -------------------------------------------------------

    private fun serveHttp(input: BufferedInputStream, output: BufferedOutputStream) {
        // Baca header sampai baris kosong (\r\n\r\n atau \n\n).
        val headerBuf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) break
            headerBuf.write(b)
            val a = headerBuf.toByteArray()
            val n = a.size
            val crlfcrlf = n >= 4 && a[n - 4] == '\r'.code.toByte() &&
                a[n - 3] == '\n'.code.toByte() && a[n - 2] == '\r'.code.toByte() &&
                a[n - 1] == '\n'.code.toByte()
            val lflf = n >= 2 && a[n - 2] == '\n'.code.toByte() && a[n - 1] == '\n'.code.toByte()
            if (crlfcrlf || lflf) break
        }

        val headerText = String(headerBuf.toByteArray(), StandardCharsets.ISO_8859_1)
        val lines = headerText.split("\r\n", "\n").filter { it.isNotEmpty() }
        val requestLine = lines.firstOrNull() ?: ""
        val parts = requestLine.split(" ")
        val method = parts.getOrNull(0) ?: "POST"

        val contentLength = lines
            .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0

        val body = ByteArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val r = input.read(body, read, contentLength - read)
            if (r < 0) break
            read += r
        }
        val bodyText = String(body, 0, read, StandardCharsets.UTF_8)

        if (method.equals("OPTIONS", ignoreCase = true)) {
            writeHttp(output, 204, "", contentType = "application/json")
            return
        }
        if (!method.equals("POST", ignoreCase = true)) {
            writeHttp(
                output, 405,
                """{"jsonrpc":"2.0","id":null,"error":{"code":-32600,"message":"hanya POST"},"data":null}""",
            )
            return
        }

        val response = runBlockingExecute(bodyText)
            ?: """{"jsonrpc":"2.0","id":null,"data":null}"""
        writeHttp(output, 200, response)
    }

    private fun writeHttp(
        output: BufferedOutputStream,
        status: Int,
        body: String,
        contentType: String = "application/json",
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val reason = if (status == 200) "OK" else if (status == 204) "No Content" else "Error"
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n" +
            "\r\n"
        output.write(head.toByteArray(StandardCharsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }

    /** Jalankan [text] secara sinkron (executor suspend) dan kembalikan hasil. */
    private fun runBlockingExecute(text: String): String? =
        kotlinx.coroutines.runBlocking { executor.execute(text) }

    /** Hentikan server dan tutup semua koneksi. */
    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        onLog("jsonrpc server berhenti")
    }
}

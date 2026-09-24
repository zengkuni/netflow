// Inti JSON-RPC 2.0: parse request, dispatch ke method, susun response.
//
// Lapisan ini murni (tanpa Android/libsu) dan tidak melakukan I/O jaringan;
// transport (TCP/local socket) disediakan lapisan lain. Method didaftarkan
// sebagai handler suspend sehingga bisa memanggil ApnApi langsung.

package netflow.dev.apn.jsonrpc

/** Kode error JSON-RPC 2.0 standar. */
object JsonRpcError {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
}

/** Handler satu method: terima params, kembalikan nilai hasil (bisa null). */
typealias JsonRpcHandler = suspend (params: JsonValue?) -> JsonValue

/**
 * Registry method JSON-RPC. Nama method dipetakan ke handler; permintaan ke
 * method yang tak terdaftar menghasilkan error METHOD_NOT_FOUND.
 */
class JsonRpcRegistry {
    private val handlers = LinkedHashMap<String, JsonRpcHandler>()

    /** Daftarkan [name]; menimpa bila sudah ada. */
    fun register(name: String, handler: JsonRpcHandler): JsonRpcRegistry {
        handlers[name] = handler
        return this
    }

    fun has(name: String): Boolean = handlers.containsKey(name)

    /** Daftar nama method terdaftar (untuk introspeksi/diagnostik). */
    fun names(): List<String> = handlers.keys.toList()

    internal fun handler(name: String): JsonRpcHandler? = handlers[name]
}

/**
 * Eksekutor JSON-RPC: mengubah teks request menjadi teks response.
 *
 * Envelope response: sukses memuat `result` berisi objek hasil handler
 * (`{ok, error, data}`), sedangkan error memuat objek `error` dengan
 * `data` bernilai `null`.
 *
 * `execute` tidak pernah melempar; seluruh kegagalan dipetakan ke objek
 * error JSON-RPC sehingga satu request buruk tidak mematikan transport.
 * Notifikasi (request tanpa `id`) menghasilkan response `null` yang oleh
 * pemanggil berarti "tidak ada balasan".
 */
class JsonRpcExecutor(
    private val registry: JsonRpcRegistry,
    /**
     * Dipanggil tepat sekali per request dengan nama method, hasil, dan alasan
     * gagal (`null` bila sukses). Diberi nilai default kosong agar kelas ini
     * tetap murni dan bisa diuji tanpa Android; pemasangan log dilakukan di
     * [netflow.dev.service.JsonRpcService].
     *
     * Tidak boleh melempar: kegagalan logging tidak boleh menjatuhkan request.
     */
    private val onCall: (method: String, ok: Boolean, detail: String?) -> Unit = { _, _, _ -> },
) {

    suspend fun execute(requestText: String): String? {
        val method = peekMethod(requestText)
        val response = executeInternal(requestText)
        runCatching { onCall(method, okOf(response), detailOf(response)) }
        return response
    }

    /** Nama method dari teks request; `-` bila tidak terbaca. */
    private fun peekMethod(text: String): String =
        (runCatching { parseJson(text) }.getOrNull() as? JsonValue.Obj)
            ?.get("method")?.asString() ?: "-"

    /**
     * Notifikasi (request tanpa `id`) tidak menghasilkan balasan, jadi `null`
     * berarti "diproses, tak ada yang dibalas", bukan kegagalan.
     */
    private fun okOf(response: String?): Boolean = detailOf(response) == null

    /**
     * Alasan gagal dari teks response: pesan error JSON-RPC (transport /
     * method tak dikenal / params) diutamakan atas `error` di dalam envelope
     * `{ok, error, data}`, karena yang kedua adalah kegagalan yang dilaporkan
     * handler perangkat dan biasanya lebih spesifik.
     */
    private fun detailOf(response: String?): String? {
        if (response == null) return null
        val obj = runCatching { parseJson(response) }.getOrNull() as? JsonValue.Obj
            ?: return null
        (obj["error"] as? JsonValue.Obj)?.let { rpc ->
            return rpc["message"]?.asString() ?: "jsonrpc error"
        }
        (obj["result"] as? JsonValue.Obj)?.let { result ->
            if (result["ok"]?.asBool() == false) {
                return result["error"]?.asString() ?: "gagal"
            }
        }
        return null
    }

    private suspend fun executeInternal(requestText: String): String? {
        val parsed = try {
            parseJson(requestText)
        } catch (e: JsonParseException) {
            return errorResponse(null, JsonRpcError.PARSE_ERROR, "parse error: ${e.message}")
        }

        val obj = parsed as? JsonValue.Obj
            ?: return errorResponse(null, JsonRpcError.INVALID_REQUEST, "request harus objek")

        val id = obj["id"]
        val method = obj["method"]?.asString()
            ?: return errorResponse(id, JsonRpcError.INVALID_REQUEST, "field 'method' hilang")
        val params = obj["params"]

        val handler = registry.handler(method)
            ?: return errorResponse(id, JsonRpcError.METHOD_NOT_FOUND, "method tidak dikenal: $method")

        return try {
            val result = handler(params)
            successResponse(id, result)
        } catch (e: JsonRpcException) {
            errorResponse(id, e.code, e.message ?: "error")
        } catch (e: Exception) {
            errorResponse(id, JsonRpcError.INTERNAL_ERROR, e.message ?: "internal error")
        }
    }

    private fun successResponse(id: JsonValue?, result: JsonValue): String =
        jsonObject(
            "jsonrpc" to jsonString("2.0"),
            "id" to (id ?: JsonValue.Null),
            "result" to result,
        ).toJson()

    private fun errorResponse(id: JsonValue?, code: Int, message: String): String =
        jsonObject(
            "jsonrpc" to jsonString("2.0"),
            "id" to (id ?: JsonValue.Null),
            "error" to jsonObject(
                "code" to jsonLong(code.toLong()),
                "message" to jsonString(message),
            ),
            "data" to JsonValue.Null,
        ).toJson()
}

/** Exception yang membawa kode error JSON-RPC eksplisit dari handler. */
class JsonRpcException(val code: Int, message: String) : Exception(message) {
    companion object {
        fun invalidParams(message: String) = JsonRpcException(JsonRpcError.INVALID_PARAMS, message)
    }
}

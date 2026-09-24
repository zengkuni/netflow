// Parser + serializer JSON minimal (dangkal, tanpa dependency).
//
// Cukup untuk payload JSON-RPC 2.0 yang bentuknya dangkal:
//   {"jsonrpc":"2.0","id":1,"method":"apn.list","params":{...}}
//   {"jsonrpc":"2.0","id":1,"result":{ok,error,data}}  /  {"jsonrpc":"2.0","id":1,"error":{...},"data":null}
//
// Tidak mendukung tipe berlapis dalam, tapi mendukung objek/array bersarang
// pada `result.data` karena nilai hasil dari handler bisa berupa objek.
//
// Murni (tanpa Android) sehingga teruji penuh di JVM.

package netflow.dev.apn.jsonrpc

/** Nilai JSON: null, boolean, angka, string, array, atau objek. */
sealed interface JsonValue {
    data class Str(val value: String) : JsonValue
    data class Num(val value: Double) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue

    /** Akses field objek; null bila bukan objek atau field tidak ada. */
    operator fun get(key: String): JsonValue? = (this as? Obj)?.fields?.get(key)

    /** String bila bertipe string, selain itu null. */
    fun asString(): String? = (this as? Str)?.value

    /** Long bila bertipe angka, selain itu null. */
    fun asLong(): Long? = (this as? Num)?.value?.toLong()

    /** Boolean bila bertipe boolean, selain itu null. */
    fun asBool(): Boolean? = (this as? Bool)?.value
}

/** Error saat parsing JSON. */
class JsonParseException(message: String) : Exception(message)

/**
 * Parse [text] menjadi [JsonValue]. Melempar [JsonParseException] bila input
 * bukan JSON valid.
 */
fun parseJson(text: String): JsonValue {
    val p = JsonParser(text)
    p.skipWs()
    val v = p.parseValue()
    p.skipWs()
    if (!p.atEnd()) throw JsonParseException("karakter sisa pada offset ${p.pos}")
    return v
}

private class JsonParser(private val s: String) {
    var pos = 0

    fun atEnd(): Boolean = pos >= s.length

    fun skipWs() {
        while (pos < s.length && s[pos].isWhitespace()) pos++
    }

    fun parseValue(): JsonValue {
        if (atEnd()) throw JsonParseException("input habis, nilai diharapkan")
        return when (val c = s[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonValue.Str(parseString())
            't', 'f' -> parseBool()
            'n' -> parseNull()
            else -> if (c == '-' || c.isDigit()) parseNumber()
            else throw JsonParseException("karakter tak terduga '$c' pada offset $pos")
        }
    }

    private fun parseObject(): JsonValue {
        expect('{')
        val fields = LinkedHashMap<String, JsonValue>()
        skipWs()
        if (peek() == '}') { pos++; return JsonValue.Obj(fields) }
        while (true) {
            skipWs()
            if (peek() != '"') throw JsonParseException("kunci objek harus string pada offset $pos")
            val key = parseString()
            skipWs()
            expect(':')
            skipWs()
            fields[key] = parseValue()
            skipWs()
            when (peek()) {
                ',' -> { pos++; continue }
                '}' -> { pos++; break }
                else -> throw JsonParseException("',' atau '}' diharapkan pada offset $pos")
            }
        }
        return JsonValue.Obj(fields)
    }

    private fun parseArray(): JsonValue {
        expect('[')
        val items = mutableListOf<JsonValue>()
        skipWs()
        if (peek() == ']') { pos++; return JsonValue.Arr(items) }
        while (true) {
            skipWs()
            items.add(parseValue())
            skipWs()
            when (peek()) {
                ',' -> { pos++; continue }
                ']' -> { pos++; break }
                else -> throw JsonParseException("',' atau ']' diharapkan pada offset $pos")
            }
        }
        return JsonValue.Arr(items)
    }

    private fun parseString(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            if (atEnd()) throw JsonParseException("string tidak ditutup")
            when (val c = s[pos++]) {
                '"' -> return sb.toString()
                '\\' -> sb.append(parseEscape())
                else -> sb.append(c)
            }
        }
    }

    private fun parseEscape(): Char {
        if (atEnd()) throw JsonParseException("escape tidak lengkap")
        return when (val e = s[pos++]) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (pos + 4 > s.length) throw JsonParseException("escape unicode tidak lengkap")
                val hex = s.substring(pos, pos + 4)
                pos += 4
                hex.toIntOrNull(16)?.toChar()
                    ?: throw JsonParseException("escape unicode invalid: $hex")
            }
            else -> throw JsonParseException("escape tak dikenal '\\$e'")
        }
    }

    private fun parseNumber(): JsonValue {
        val start = pos
        if (peek() == '-') pos++
        while (!atEnd() && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
        val text = s.substring(start, pos)
        val v = text.toDoubleOrNull() ?: throw JsonParseException("angka invalid: $text")
        return JsonValue.Num(v)
    }

    private fun parseBool(): JsonValue =
        if (s.startsWith("true", pos)) { pos += 4; JsonValue.Bool(true) }
        else if (s.startsWith("false", pos)) { pos += 5; JsonValue.Bool(false) }
        else throw JsonParseException("literal boolean invalid pada offset $pos")

    private fun parseNull(): JsonValue =
        if (s.startsWith("null", pos)) { pos += 4; JsonValue.Null }
        else throw JsonParseException("literal null invalid pada offset $pos")

    private fun peek(): Char = if (atEnd()) '\u0000' else s[pos]

    private fun expect(c: Char) {
        if (atEnd() || s[pos] != c) throw JsonParseException("'$c' diharapkan pada offset $pos")
        pos++
    }
}

/** Serialisasi [JsonValue] ke teks JSON. */
fun JsonValue.toJson(): String = StringBuilder().also { writeJson(it, this) }.toString()

private fun writeJson(sb: StringBuilder, v: JsonValue) {
    when (v) {
        is JsonValue.Str -> sb.append(quote(v.value))
        is JsonValue.Num -> sb.append(formatNumber(v.value))
        is JsonValue.Bool -> sb.append(if (v.value) "true" else "false")
        JsonValue.Null -> sb.append("null")
        is JsonValue.Arr -> {
            sb.append('[')
            v.items.forEachIndexed { i, item ->
                if (i > 0) sb.append(',')
                writeJson(sb, item)
            }
            sb.append(']')
        }
        is JsonValue.Obj -> {
            sb.append('{')
            var first = true
            for ((k, value) in v.fields) {
                if (!first) sb.append(',')
                first = false
                sb.append(quote(k)).append(':')
                writeJson(sb, value)
            }
            sb.append('}')
        }
    }
}

/** Angka bulat ditulis tanpa ".0" agar `id` tetap 1, bukan 1.0. */
private fun formatNumber(d: Double): String =
    if (d.isFinite() && d == kotlin.math.floor(d) && kotlin.math.abs(d) < 1e15) {
        d.toLong().toString()
    } else {
        d.toString()
    }

/** Bungkus [s] sebagai string JSON dengan escape lengkap. */
fun quote(s: String): String {
    val sb = StringBuilder(s.length + 2)
    sb.append('"')
    for (c in s) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
        }
    }
    sb.append('"')
    return sb.toString()
}

// -- Pembantu penulisan nilai ------------------------------------------------

fun jsonObject(vararg pairs: Pair<String, JsonValue>): JsonValue.Obj =
    JsonValue.Obj(linkedMapOf(*pairs))

fun jsonArray(items: List<JsonValue>): JsonValue.Arr = JsonValue.Arr(items)

fun jsonString(value: String): JsonValue.Str = JsonValue.Str(value)
fun jsonLong(value: Long): JsonValue.Num = JsonValue.Num(value.toDouble())
fun jsonBool(value: Boolean): JsonValue.Bool = JsonValue.Bool(value)

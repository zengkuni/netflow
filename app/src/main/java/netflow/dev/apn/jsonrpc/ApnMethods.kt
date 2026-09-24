// Method JSON-RPC untuk operasi APN. Menjembatani ApnApi <-> JsonValue.
//
// Setiap handler mengembalikan objek {ok, error, data}. Bentuk ini sengaja
// memakai "ok/error" (bukan melempar) mengikuti result type di api/, supaya
// client tidak perlu membedakan kegagalan transport vs kegagalan perangkat.

package netflow.dev.apn.jsonrpc

import netflow.dev.apn.api.ApnApi
import netflow.dev.apn.api.ApnDraft
import netflow.dev.apn.api.ApnListResult
import netflow.dev.apn.api.DefaultApnWhere
import netflow.dev.apn.api.isSafeWhere
import netflow.dev.apn.model.Apn

/** Ubah satu [Apn] menjadi objek JSON. */
fun apnToJson(a: Apn): JsonValue = jsonObject(
    "id" to jsonLong(a.id),
    "name" to jsonString(a.name),
    "apn" to jsonString(a.apnName),
    "type" to jsonString(a.apnType),
    "mcc" to jsonString(a.mcc),
    "mnc" to jsonString(a.mnc),
    "numeric" to jsonString(a.numeric),
    "current" to jsonLong(a.current),
    "carrierEnabled" to jsonLong(a.carrierEnabled),
)

/** Bungkus [ApnListResult] menjadi `{ok, error, data:[...apn]}`. */
private fun listApnResult(r: ApnListResult): JsonValue = jsonObject(
    "ok" to jsonBool(r.ok),
    "error" to (r.error?.let { jsonString(it) } ?: JsonValue.Null),
    "data" to jsonArray(r.apns.map(::apnToJson)),
)

/** Hasil sukses generik: data apa pun pada field `data`. */
private fun dataResult(data: JsonValue): JsonValue = jsonObject(
    "ok" to jsonBool(true),
    "error" to JsonValue.Null,
    "data" to data,
)

/** Daftarkan seluruh method APN ke [registry]. */
fun registerApnMethods(registry: JsonRpcRegistry, api: ApnApi): JsonRpcRegistry {
    registry.register("ping") {
        dataResult(jsonString("pong"))
    }

    registry.register("apn.list") { params ->
        // `get` pada JsonValue? perlu safe-call. `where` null -> default;
        // string kosong eksplisit -> tanpa --where (semua baris).
        val whereParam = params?.get("where")?.asString()
        when {
            whereParam == null -> listApnResult(api.getListApn(where = DefaultApnWhere))
            whereParam.isEmpty() -> listApnResult(api.getListApn(where = null))
            !isSafeWhere(whereParam) ->
                throw JsonRpcException.invalidParams("where tidak valid: $whereParam")
            else -> listApnResult(api.getListApn(where = whereParam))
        }
    }

    registry.register("sim.active") {
        val s = api.activeSim()
        jsonObject(
            "ok" to jsonBool(s.ok),
            "error" to (s.error?.let { jsonString(it) } ?: JsonValue.Null),
            "data" to if (s.ok) {
                jsonObject(
                    "mnc" to jsonString(s.mnc),
                    "numeric" to jsonString(s.numeric),
                )
            } else {
                JsonValue.Null
            },
        )
    }

    registry.register("apn.current") {
        val c = api.currentApn()
        jsonObject(
            "ok" to jsonBool(c.ok),
            "error" to (c.error?.let { jsonString(it) } ?: JsonValue.Null),
            "data" to (c.apn?.let(::apnToJson) ?: JsonValue.Null),
        )
    }

    registry.register("apn.add") { params ->
        fun str(key: String): String =
            params?.get(key)?.asString() ?: throw JsonRpcException.invalidParams("field '$key' wajib string")

        val draft = ApnDraft(
            name = str("name"),
            apnName = str("apn"),
            apnType = params?.get("type")?.asString() ?: "default,supl",
            mcc = str("mcc"),
            mnc = str("mnc"),
            numeric = str("numeric"),
            carrierEnabled = params?.get("carrierEnabled")?.asLong() ?: 1L,
        )
        val w = api.addApn(draft)
        jsonObject(
            "ok" to jsonBool(w.ok),
            "error" to (w.error?.let { jsonString(it) } ?: JsonValue.Null),
            "data" to JsonValue.Null,
        )
    }

    registry.register("apn.delete") { params ->
        val byId = params?.get("id")?.asLong()
        val byApn = params?.get("apn")?.asString()
        val w = when {
            byId != null -> api.deleteApn(byId)
            byApn != null -> api.deleteApnByValue(byApn)
            else -> throw JsonRpcException.invalidParams("field 'id' atau 'apn' wajib diisi")
        }
        jsonObject(
            "ok" to jsonBool(w.ok),
            "error" to (w.error?.let { jsonString(it) } ?: JsonValue.Null),
            "data" to JsonValue.Null,
        )
    }

    registry.register("auto.changeip.apn") { _ ->
        val r = api.autoChangeIp()
        val info = r.info?.let { parseJson(it) } ?: JsonValue.Null
        jsonObject(
            "ok" to jsonBool(r.ok),
            "error" to (r.error?.let { jsonString(it) } ?: JsonValue.Null),
            "data" to if (r.ok) {
                jsonObject(
                    "apn_active" to (r.apnName?.let { jsonString(it) } ?: JsonValue.Null),
                    "apn_id" to (r.switchedTo?.let { jsonLong(it) } ?: JsonValue.Null),
                    "info" to info,
                )
            } else {
                JsonValue.Null
            },
        )
    }

    registry.register("apn.switch") { params ->
        val id = params?.get("id")?.asLong()
            ?: throw JsonRpcException.invalidParams("field 'id' wajib diisi")
        val r = api.switchPreferredApn(id)
        jsonObject(
            "ok" to jsonBool(r.ok),
            "error" to (r.error?.let { jsonString(it) } ?: JsonValue.Null),
            "data" to JsonValue.Null,
        )
    }

    registry.register("root.granted") {
        dataResult(jsonBool(api.isRootGranted()))
    }

    return registry
}

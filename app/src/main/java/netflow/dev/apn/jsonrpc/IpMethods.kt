// Method JSON-RPC untuk rotasi IP non-APN. Menjembatani IpRotateApi <-> JsonValue.
//
// Bentuk envelope sama dengan method APN: {ok, error, data} agar client tidak
// perlu membedakan kegagalan transport vs kegagalan perangkat.

package netflow.dev.apn.jsonrpc

import netflow.dev.apn.api.IpRotateApi

/**
 * Bungkus [netflow.apn.api.IpRotateResult] menjadi `{ok, error, data}`.
 *
 * `data` memuat `info` (objek JSON ipinfo), `detach_seen` (jalur AT), dan
 * `radio_restored` (jalur airplane) agar client tahu apakah detach terkonfirmasi
 * dan apakah daftar radio kembali utuh.
 */
private fun ipRotateResult(
    ok: Boolean,
    info: String?,
    detachSeen: Boolean?,
    radioRestored: Boolean?,
    error: String?,
): JsonValue = jsonObject(
    "ok" to jsonBool(ok),
    "error" to (error?.let { jsonString(it) } ?: JsonValue.Null),
    "data" to if (ok) {
        jsonObject(
            "info" to (info?.let { parseJson(it) } ?: JsonValue.Null),
            "detach_seen" to (detachSeen?.let { jsonBool(it) } ?: JsonValue.Null),
            "radio_restored" to (radioRestored?.let { jsonBool(it) } ?: JsonValue.Null),
        )
    } else {
        JsonValue.Null
    },
)

/** Daftarkan method rotasi IP non-APN ke [registry]. */
fun registerIpMethods(registry: JsonRpcRegistry, api: IpRotateApi): JsonRpcRegistry {
    registry.register("auto.changeip.at") { _ ->
        val r = api.autoChangeIpAt()
        ipRotateResult(r.ok, r.info, r.detachSeen, r.radioRestored, r.error)
    }

    registry.register("auto.changeip.airplane") { _ ->
        val r = api.autoChangeIpAirplane()
        ipRotateResult(r.ok, r.info, r.detachSeen, r.radioRestored, r.error)
    }

    return registry
}

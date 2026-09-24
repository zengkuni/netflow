package netflow.dev.util

import android.content.Context
import android.os.Build
import netflow.dev.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Silent Anti-Kill enforcement for the restrictions root can flip without the
 * user touching any settings screen: notification permission, Doze
 * whitelist (battery optimization), background execution, data saver.
 *
 * Commands are static; the only interpolated values are the app's own package
 * name ([BuildConfig.APPLICATION_ID]) and uid ([Context.applicationInfo]),
 * both app-owned constants, never network input.
 *
 * The two OEM steps that cannot be flipped from root (OEM auto-launch, lock
 * in Recents) are deliberately NOT here: they stay manual guidance on the
 * Anti-Kill screen. Do not pretend root covers them.
 *
 * Per-item outcomes are published in [items] so the UI shows what actually
 * got applied. Failures are surfaced, never hidden, and never block the
 * proxy start: the caller keeps the existing guidance fallback.
 */
object AntiKillEnforcer {

    enum class Item { Notifications, DozeWhitelist, BackgroundActivity, DataSaver }

    enum class Status { Pending, Applied, Failed }

    data class ItemState(val item: Item, val status: Status, val detail: String)

    private val _items = MutableStateFlow<List<ItemState>>(emptyList())
    val items: StateFlow<List<ItemState>> = _items.asStateFlow()

    /**
     * Apply every static command. Idempotent, safe to run on every proxy
     * start. [shell] must already be [RootState.Granted]; this never prompts.
     */
    suspend fun apply(context: Context, shell: RootShell) {
        val pkg = BuildConfig.APPLICATION_ID
        val uid = context.applicationInfo.uid
        val commands = buildList {
            add(
                Item.Notifications to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Android 13+ gates on the runtime permission, not the
                    // appop, so grant the permission directly. Unverifiable
                    // on the current Android 11 test device.
                    "pm grant $pkg android.permission.POST_NOTIFICATIONS"
                } else {
                    "cmd appops set $pkg POST_NOTIFICATION allow"
                }
            )
            add(Item.DozeWhitelist to "cmd deviceidle whitelist +$pkg")
            add(Item.BackgroundActivity to "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow")
            // Best-effort: netpolicy's subcommand set varies by ROM. A failure
            // here is reported but does not matter, the other three carry the
            // weight.
            add(Item.DataSaver to "cmd netpolicy add restrict-background-whitelist $uid")
        }
        _items.value = commands.map { (item, cmd) ->
            val out = shell.exec(cmd)
            ItemState(
                item = item,
                status = if (out.success) Status.Applied else Status.Failed,
                detail = out.output,
            )
        }
    }

    /** Called when the user turns the root toggle off. */
    fun reset() {
        _items.value = emptyList()
    }
}

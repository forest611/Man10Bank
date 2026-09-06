package red.man10.man10bank.service

import kotlinx.coroutines.CompletableDeferred
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.command.balance.BalanceRegistry
import red.man10.man10bank.service.vault.VaultService
import red.man10.man10bank.util.BalanceFormats

/**
 * 電子マネー参照の互換ファサード（VaultProvider 11.1）。
 * - 旧実装は外部 Economy Provider を取得する Vault Consumer だったが、新設計では Man10Bank 自身が
 *   Provider のため、[VaultService]（ローカル台帳）へ委譲する。
 * - `ServicesManager.getRegistration(Economy)` で自分自身の Provider を取得して叩く実装は禁止
 *   （VaultProvider 11.1）。本クラスがその代替となる。
 * - 既存呼び出し側（残高表示・ATM UI・資産スナップショット）の改修を最小化するため名前と
 *   シグネチャを維持する。読み取りはメインスレッドから同期で行える（台帳参照のみ）。
 */
class VaultManager(
    private val plugin: JavaPlugin,
    private val vaultService: VaultService,
) {

    /** 利用可能かどうか（Provider 登録済みかつ有効）。 */
    fun isAvailable(): Boolean = vaultService.isProviderActive()

    /** 残高取得（未在席・未 claim は 0.0）。ローカル台帳の参照のみで完結する。 */
    fun getBalance(player: OfflinePlayer): Double = vaultService.getBalanceSync(player.uniqueId)

    /** 残高取得（メインスレッドへディスパッチして実行）。コルーチンからはこちらを使う。 */
    suspend fun getBalanceOnMain(player: OfflinePlayer): Double = onMainThread { getBalance(player) }

    private suspend fun <T> onMainThread(block: () -> T): T {
        if (plugin.server.isPrimaryThread) return block()
        val deferred = CompletableDeferred<T>()
        plugin.server.scheduler.runTask(plugin, Runnable {
            try {
                deferred.complete(block())
            } catch (t: Throwable) {
                deferred.completeExceptionally(t)
            }
        })
        return deferred.await()
    }

    /** 残高表示プロバイダの登録（電子マネー/Vault）。 */
    fun registerBalanceProvider() {
        // Vault 残高はメインスレッドで収集済みの context から取得する（DESIGN 3.5）。
        BalanceRegistry.register(
            id = "vault",
            order = 10,
            provider = { _, context ->
                "§b§l電子マネー: ${BalanceFormats.coloredYen(context.vaultBalance)}§r"
            }
        )
    }
}

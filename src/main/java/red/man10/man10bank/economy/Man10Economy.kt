package red.man10.man10bank.economy

import net.milkbowl.vault.economy.Economy
import net.milkbowl.vault.economy.EconomyResponse
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.service.vault.VaultService
import red.man10.man10bank.util.BalanceFormats
import java.util.concurrent.TimeUnit

/**
 * Man10Bank を Vault(Economy) の Provider 化する実装（VaultProvider 6.2/6.3）。
 * - [VaultService] への薄いアダプタ。確定残高は user_vault が真実で、本実装はローカル台帳を同期参照する。
 * - 旧 Vault（単一通貨・double・整数円）のみ対応。bank 系・多通貨は非対応。
 * - [isEnabled] は「Provider 登録済みかつ設定上有効」だけを表し、サービス一時障害では true を維持する。
 *   障害中の拒否は各取引メソッドが書き込み健全性を検査して行う（VaultProvider 5.6）。
 * - 全メソッドはメインスレッドで実行する。off-main から呼ばれた場合はメインスレッドへ
 *   同期ディスパッチして結果を返す（HTTP/DB は待たない。VaultProvider 10.2）。
 * - 文字列/OfflinePlayer 指定は「このサーバーで現在オンライン」のプレイヤーだけを解決する。
 *   任意の名前からオフライン UUID は生成しない（VaultProvider 6.3）。
 */
class Man10Economy(
    private val plugin: JavaPlugin,
    private val vault: VaultService,
    private val currencySingular: String,
    private val currencyPlural: String,
) : Economy {

    override fun isEnabled(): Boolean = vault.isProviderActive()

    override fun getName(): String = "Man10Bank"

    override fun hasBankSupport(): Boolean = false

    // 円・整数。小数桁は持たない。
    override fun fractionalDigits(): Int = 0

    override fun format(amount: Double): String = "${BalanceFormats.amount(amount)}$currencySingular"

    override fun currencyNamePlural(): String = currencyPlural

    override fun currencyNameSingular(): String = currencySingular

    // === 口座 ===
    // 同一サーバーでオンラインかつローカル台帳にエントリ（LOADING/READY）がある場合だけ true（VaultProvider 6.2）。
    override fun hasAccount(player: OfflinePlayer): Boolean = onMain(false) {
        isEnabled && plugin.server.getPlayer(player.uniqueId) != null && vault.hasEntry(player.uniqueId)
    }

    override fun hasAccount(player: OfflinePlayer, worldName: String?): Boolean = hasAccount(player)

    // オンライン対象の claim/load 要求を受理できれば true。DB コミット済みを意味しない（READY まで金銭操作は失敗する）。
    override fun createPlayerAccount(player: OfflinePlayer): Boolean = onMain(false) {
        vault.requestEnsure(player.uniqueId, player.name.orEmpty())
    }

    override fun createPlayerAccount(player: OfflinePlayer, worldName: String?): Boolean =
        createPlayerAccount(player)

    // === 残高 ===
    override fun getBalance(player: OfflinePlayer): Double = onMain(0.0) {
        if (!isEnabled) 0.0 else vault.getBalanceSync(player.uniqueId)
    }

    override fun getBalance(player: OfflinePlayer, world: String?): Double = getBalance(player)

    override fun has(player: OfflinePlayer, amount: Double): Boolean = onMain(false) {
        isEnabled && vault.hasSync(player.uniqueId, amount)
    }

    override fun has(player: OfflinePlayer, worldName: String?, amount: Double): Boolean = has(player, amount)

    // === 入出金 ===
    override fun withdrawPlayer(player: OfflinePlayer, amount: Double): EconomyResponse = onMain(dispatchFailure()) {
        if (!isEnabled) disabledFailure() else vault.providerWithdraw(player.uniqueId, amount)
    }

    override fun withdrawPlayer(player: OfflinePlayer, worldName: String?, amount: Double): EconomyResponse =
        withdrawPlayer(player, amount)

    override fun depositPlayer(player: OfflinePlayer, amount: Double): EconomyResponse = onMain(dispatchFailure()) {
        if (!isEnabled) disabledFailure() else vault.providerDeposit(player.uniqueId, amount)
    }

    override fun depositPlayer(player: OfflinePlayer, worldName: String?, amount: Double): EconomyResponse =
        depositPlayer(player, amount)

    // === 非推奨: 文字列(プレイヤー名)版はオンラインプレイヤーのみ解決して委譲 ===
    // 任意名からのオフライン UUID 生成（getOfflinePlayer(name)）は行わない（VaultProvider 6.3）。
    private fun online(name: String): OfflinePlayer? = plugin.server.getPlayerExact(name)

    @Deprecated("Use OfflinePlayer", ReplaceWith(""))
    override fun hasAccount(playerName: String): Boolean = online(playerName)?.let { hasAccount(it) } ?: false

    @Deprecated("Use OfflinePlayer", ReplaceWith(""))
    override fun hasAccount(playerName: String, worldName: String?): Boolean = hasAccount(playerName)

    @Deprecated("Use OfflinePlayer", ReplaceWith(""))
    override fun getBalance(playerName: String): Double = online(playerName)?.let { getBalance(it) } ?: 0.0

    @Deprecated("Use OfflinePlayer", ReplaceWith(""))
    override fun getBalance(playerName: String, world: String?): Double = getBalance(playerName)

    @Deprecated("Use OfflinePlayer", ReplaceWith(""))
    override fun has(playerName: String, amount: Double): Boolean = online(playerName)?.let { has(it, amount) } ?: false

    @Deprecated("Use OfflinePlayer", ReplaceWith(""))
    override fun has(playerName: String, worldName: String?, amount: Double): Boolean = has(playerName, amount)

    @Deprecated("Use OfflinePlayer", ReplaceWith(""))
    override fun withdrawPlayer(playerName: String, amount: Double): EconomyResponse =
        online(playerName)?.let { withdrawPlayer(it, amount) } ?: offlineFailure()

    @Deprecated("Use OfflinePlayer", ReplaceWith(""))
    override fun withdrawPlayer(playerName: String, worldName: String?, amount: Double): EconomyResponse =
        withdrawPlayer(playerName, amount)

    @Deprecated("Use OfflinePlayer", ReplaceWith(""))
    override fun depositPlayer(playerName: String, amount: Double): EconomyResponse =
        online(playerName)?.let { depositPlayer(it, amount) } ?: offlineFailure()

    @Deprecated("Use OfflinePlayer", ReplaceWith(""))
    override fun depositPlayer(playerName: String, worldName: String?, amount: Double): EconomyResponse =
        depositPlayer(playerName, amount)

    @Deprecated("Use OfflinePlayer", ReplaceWith(""))
    override fun createPlayerAccount(playerName: String): Boolean =
        online(playerName)?.let { createPlayerAccount(it) } ?: false

    @Deprecated("Use OfflinePlayer", ReplaceWith(""))
    override fun createPlayerAccount(playerName: String, worldName: String?): Boolean =
        createPlayerAccount(playerName)

    // === bank 系: すべて未対応（VaultProvider 6.3） ===
    override fun createBank(name: String?, player: OfflinePlayer?): EconomyResponse = notImplemented()

    @Deprecated("Use OfflinePlayer", ReplaceWith(""))
    override fun createBank(name: String?, player: String?): EconomyResponse = notImplemented()

    override fun deleteBank(name: String?): EconomyResponse = notImplemented()

    override fun bankBalance(name: String?): EconomyResponse = notImplemented()

    override fun bankHas(name: String?, amount: Double): EconomyResponse = notImplemented()

    override fun bankWithdraw(name: String?, amount: Double): EconomyResponse = notImplemented()

    override fun bankDeposit(name: String?, amount: Double): EconomyResponse = notImplemented()

    override fun isBankOwner(name: String?, player: OfflinePlayer?): EconomyResponse = notImplemented()

    @Deprecated("Use OfflinePlayer", ReplaceWith(""))
    override fun isBankOwner(name: String?, playerName: String?): EconomyResponse = notImplemented()

    override fun isBankMember(name: String?, player: OfflinePlayer?): EconomyResponse = notImplemented()

    @Deprecated("Use OfflinePlayer", ReplaceWith(""))
    override fun isBankMember(name: String?, playerName: String?): EconomyResponse = notImplemented()

    override fun getBanks(): MutableList<String> = mutableListOf()

    // === 内部 ===

    /**
     * off-main 呼び出しをメインスレッドへ同期ディスパッチする（VaultProvider 10.2）。
     * 待つのはローカル台帳とキュー登録の同期処理のみ（HTTP/DB は待たない）。
     * プラグイン無効化中やタイムアウト時は [fallback] を返す（fail-closed）。
     */
    private fun <T> onMain(fallback: T, block: () -> T): T {
        val server = plugin.server
        if (server.isPrimaryThread) return block()
        if (!plugin.isEnabled) return fallback
        return try {
            server.scheduler.callSyncMethod(plugin, block).get(5, TimeUnit.SECONDS)
        } catch (t: Throwable) {
            plugin.logger.warning("Economy呼び出しのメインスレッドディスパッチに失敗しました: ${t.message}")
            fallback
        }
    }

    private fun disabledFailure(): EconomyResponse =
        EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "電子マネーは無効化されています。")

    private fun offlineFailure(): EconomyResponse =
        EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "対象がこのサーバーにオンラインではありません。")

    private fun dispatchFailure(): EconomyResponse =
        EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "電子マネーの処理をディスパッチできませんでした。")

    private fun notImplemented(): EconomyResponse =
        EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "銀行APIは未対応です。")
}

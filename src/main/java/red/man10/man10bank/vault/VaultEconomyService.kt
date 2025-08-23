package red.man10.man10bank.vault

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import red.man10.man10bank.shared.OperationResult
import red.man10.man10bank.shared.ResultCode
import red.man10.man10bank.util.BigDecimalConverter
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Vault の Economy を BigDecimal ベースで扱うための薄いアダプタ。
 * - プロジェクト内は BigDecimal を使用し、Vault 呼び出し時のみ double へ変換する。
 * - 金額は整数運用（scale=0, RoundingMode.DOWN）。
 */
class VaultEconomyService(
    private val economy: Economy
) {

    companion object {
        fun create(): VaultEconomyService? {
            val econ = setupEconomy() ?: return null
            return VaultEconomyService(econ)
        }

        private fun setupEconomy(): Economy? {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null
            val reg = Bukkit.getServer().servicesManager.getRegistration(Economy::class.java) ?: return null
            return reg.provider
        }

        private fun offline(uuid: UUID): OfflinePlayer = Bukkit.getOfflinePlayer(uuid)
    }

    // 残高取得
    fun getBalance(uuid: UUID): BigDecimal {
        val bal = economy.getBalance(offline(uuid))
        return BigDecimalConverter.fromDouble(bal)
    }

    // 入金（成功後の新残高を返す）
    fun deposit(uuid: UUID, amount: BigDecimal): OperationResult {
        if (amount <= BigDecimal.ZERO) {
            return OperationResult(ResultCode.INVALID_AMOUNT)
        }
        return try {
            val res = economy.depositPlayer(offline(uuid), BigDecimalConverter.toDouble(amount))
            if (!res.transactionSuccess()) {
                OperationResult(ResultCode.FAILURE)
            } else {
                OperationResult(ResultCode.SUCCESS, getBalance(uuid))
            }
        } catch (t: IllegalArgumentException) {
            OperationResult(ResultCode.OVERFLOW)
        } catch (t: Throwable) {
            OperationResult(ResultCode.FAILURE)
        }
    }

    // 出金（成功後の新残高を返す）
    fun withdraw(uuid: UUID, amount: BigDecimal): OperationResult {
        if (amount <= BigDecimal.ZERO) {
            return OperationResult(ResultCode.INVALID_AMOUNT)
        }
        val current = getBalance(uuid)
        if (current < amount) {
            return OperationResult(ResultCode.INSUFFICIENT_FUNDS)
        }
        return try {
            val res = economy.withdrawPlayer(offline(uuid), BigDecimalConverter.toDouble(amount))
            if (!res.transactionSuccess()) {
                OperationResult(ResultCode.FAILURE)
            } else {
                OperationResult(ResultCode.SUCCESS, getBalance(uuid))
            }
        } catch (t: IllegalArgumentException) {
            OperationResult(ResultCode.OVERFLOW)
        } catch (t: Throwable) {
            OperationResult(ResultCode.FAILURE)
        }
    }

    // 指定額以上を保持しているか
    fun has(uuid: UUID, amount: BigDecimal): Boolean {
        if (amount <= BigDecimal.ZERO) return true
        val current = getBalance(uuid)
        return current >= amount
    }
}

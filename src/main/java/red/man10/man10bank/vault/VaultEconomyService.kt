package red.man10.man10bank.vault

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import red.man10.man10bank.shared.OperationResult
import red.man10.man10bank.shared.ResultCode
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
        fun resolveEconomy(): Economy? {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null
            val reg = Bukkit.getServer().servicesManager.getRegistration(Economy::class.java) ?: return null
            return reg.provider
        }

        private fun toIntScale(amount: BigDecimal): BigDecimal = amount.setScale(0, RoundingMode.DOWN)
        private fun toDoubleExact(amount: BigDecimal): Double {
            // double 変換時の安全側対応（整数運用かつ Double の整数精度上限 2^53-1 を超える場合は拒否）
            val v = toIntScale(amount)
            val abs = v.abs()
            val maxExact = BigDecimal.valueOf(9007199254740991L) // 2^53-1
            require(abs <= maxExact) { "金額が大きすぎます（Double で正確に表現不可）: $amount" }
            return v.toDouble()
        }

        private fun offline(uuid: UUID): OfflinePlayer = Bukkit.getOfflinePlayer(uuid)
    }

    // 残高取得
    fun getBalance(uuid: UUID): BigDecimal {
        val bal = economy.getBalance(offline(uuid))
        return BigDecimal.valueOf(bal).setScale(0, RoundingMode.DOWN)
    }

    // 入金（成功後の新残高を返す）
    fun deposit(uuid: UUID, amount: BigDecimal): OperationResult {
        if (amount <= BigDecimal.ZERO) {
            return OperationResult(ResultCode.INVALID_AMOUNT)
        }
        return try {
            val amt = toDoubleExact(amount)
            val res = economy.depositPlayer(offline(uuid), amt)
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
        val need = toIntScale(amount)
        if (current < need) {
            return OperationResult(ResultCode.INSUFFICIENT_FUNDS)
        }
        return try {
            val amt = toDoubleExact(amount)
            val res = economy.withdrawPlayer(offline(uuid), amt)
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
        return current >= toIntScale(amount)
    }

    // Vault 側のフォーマットを利用（UI向け）。内部は BigDecimal を維持。
    fun format(amount: BigDecimal): String = economy.format(toDoubleExact(amount))
}

package red.man10.man10bank.bank

import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank.Companion.coroutineScope
import red.man10.man10bank.Man10Bank.Companion.instance
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.api.APIBank
import red.man10.man10bank.status.StatusManager
import red.man10.man10bank.util.Utility
import red.man10.man10bank.util.Utility.msg
import kotlin.math.floor

/**
 * Vaultと銀行のお金をやり取りするコマンドを追加するクラス
 * /deposit /withdrawなど
 */
object DealCommand : CommandExecutor{

    val labels = arrayOf("d","deposit","w","withdraw")
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {

        if (sender !is Player)return true

        if (!StatusManager.status.enableDealBank){
            msg(sender,"§c§l現在銀行はメンテナンス中です")
            return true
        }

        if (label == "d" || label == "deposit"){

            if (args.isNullOrEmpty()){
                msg(sender,"§a§l/deposit <金額/all> : 銀行に電子マネーを入れる")
                return true
            }

            val amount = if (args[0] == "all") vault.getBalance(sender.uniqueId) else Utility.fixedPerse(args[0])

            if (amount==null || amount < 1){
                msg(sender,"§c§l数字で1円以上を入力してください！")
                return true
            }

            val fixedAmount = floor(amount)

            if (!vault.withdraw(sender.uniqueId,fixedAmount)){
                msg(sender,"§c§l電子マネーが足りません！")
                return true
            }

            coroutineScope.launch {
                val result = APIBank.addBalance(APIBank.TransactionData(
                    sender.uniqueId.toString(),
                    fixedAmount,
                    instance.name,
                    "PlayerDepositOnCommand",
                    "/depositによる入金"
                ))

                if (result != APIBank.BankResult.SUCCESSFUL){
                    msg(sender,"§c入金エラーが発生しました")
                    Bukkit.getScheduler().runTask(instance, Runnable { vault.deposit(sender.uniqueId,fixedAmount) })
                    return@launch
                }
                msg(sender,"§e入金できました！")
            }
        }

        if (label == "withdraw"){
            if (args.isNullOrEmpty()){
                msg(sender,"§c§l/withdraw <金額/all> : 銀行から電子マネーを引き出す")
                return true
            }

            coroutineScope.launch {
                val amount = if (args[0] == "all") APIBank.getBalance(sender.uniqueId) else Utility.fixedPerse(args[0])

                if (amount==null || amount < 1){
                    msg(sender,"§c§l数字で1円以上を入力してください！")
                    return@launch
                }

                val fixedAmount = floor(amount)

                val result = APIBank.takeBalance(APIBank.TransactionData(
                    sender.uniqueId.toString(),
                    fixedAmount,
                    instance.name,
                    "PlayerWithdrawOnCommand",
                    "/withdrawによる出金"
                ))

                if (result == APIBank.BankResult.LACK_OF_MONEY){
                    msg(sender,"§c所持金が足りません")
                    return@launch
                }

                if (result != APIBank.BankResult.SUCCESSFUL){
                    msg(sender,"§c出金エラーが発生しました")
                    vault.deposit(sender.uniqueId,fixedAmount)
                    return@launch
                }

                Bukkit.getScheduler().runTask(instance, Runnable {
                    vault.deposit(sender.uniqueId,fixedAmount)
                    msg(sender,"§e出金できました！")
                })
            }
        }

        return true
    }
}
package red.man10.man10bank.commands

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.shared.ResultCode
import red.man10.man10bank.util.StringFormat
import java.math.BigDecimal

class DepositCommand(private val plugin: Man10Bank) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("プレイヤーのみ実行できます。")
            return true
        }
        if (args.isEmpty()) {
            sender.sendMessage("使用方法: /deposit <金額>")
            return true
        }
        val amount = args[0].toBigDecimalOrNull()
        if (amount == null || amount.signum() <= 0) {
            sender.sendMessage("金額は正の数で指定してください。")
            return true
        }
        val uuid = sender.uniqueId
        GlobalScope.launch {
            val res = plugin.bankService.deposit(uuid, amount, "Command", "Deposit", "入金")
            Bukkit.getScheduler().runTask(plugin, Runnable {
                when (res.code) {
                    ResultCode.SUCCESS -> sender.sendMessage("入金しました。残高: ${StringFormat.money(res.balance!!)}")
                    ResultCode.INVALID_AMOUNT -> sender.sendMessage("入金額が不正です。")
                    else -> sender.sendMessage("入金に失敗しました。")
                }
            })
        }
        return true
    }
}

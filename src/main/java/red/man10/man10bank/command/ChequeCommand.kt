package red.man10.man10bank.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.ISSUE_CHEQUE
import red.man10.man10bank.Man10Bank.Companion.OP
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.cheque.Cheque
import kotlin.math.floor
import java.text.Normalizer

class ChequeCommand(private val plugin: Man10Bank) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when(label){
            "mchequeop" -> {
                if (sender !is Player) return false
                if (!sender.hasPermission(OP)) return false
                if (args.isEmpty()) return false

                val amount = args[0].toDoubleOrNull() ?: return false
                val note = if (args.size > 1) args[1] else null

                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                    sendMsg(sender, "§a§l銀行に問い合わせ中...§k§lXX")
                    Cheque.createCheque(sender, amount, note, true)
                })
                return true
            }
            "mcheque" -> {
                if (sender !is Player) return false
                if (!sender.hasPermission(ISSUE_CHEQUE)) {
                    sendMsg(sender, "§cあなたは小切手を発行する権限がありません")
                    return false
                }
                if (args.isEmpty()) {
                    sendMsg(sender, "§e§l/mcheque <金額> <メモ>")
                    return true
                }
                val amount = floor(zenkakuToHankaku(args[0]))
                if (amount <= 0.0) {
                    sendMsg(sender, "金額を1以上にしてください")
                    return true
                }
                val note = if (args.size > 1) args[1] else null
                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                    sendMsg(sender, "§a§l銀行に問い合わせ中...§k§lXX")
                    Cheque.createCheque(sender, amount, note, false)
                })
                return true
            }
        }
        return false
    }

    private fun zenkakuToHankaku(number: String): Double {
        val normalize = Normalizer.normalize(number, Normalizer.Form.NFKC)
        return normalize.toDoubleOrNull() ?: -1.0
    }
}

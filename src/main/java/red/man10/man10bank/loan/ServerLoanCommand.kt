package red.man10.man10bank.loan

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank.Companion.OP
import red.man10.man10bank.Man10Bank.Companion.format
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.prefix
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ServerLoanCommand : CommandExecutor {

    private val REVO_PERM = "man10bank.revo"
    private val processingPlayers = ConcurrentHashMap<UUID, Boolean>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return false
        if (label != "mrevo") return false

        if (args.isEmpty()) {
            showUsage(sender)
            return false
        }

        when (args[0]) {
            "check" -> async { ServerLoan.checkServerLoan(sender) }
            "checkop" -> if (sender.hasPermission(OP) && args.size >= 2) checkOp(sender, args[1])
            "share" -> share(sender)
            "borrow" -> if (args.size == 2) borrow(sender, args[1].toDoubleOrNull())
            "confirm" -> if (args.size == 2) confirm(sender, args[1].toDoubleOrNull())
            "payment" -> if (args.size == 2) payment(sender, args[1].toDoubleOrNull())
            "payall" -> async { ServerLoan.paymentAll(sender) }
            "addtime" -> if (sender.hasPermission(OP) && args.size == 3) addTime(sender, args[1], args[2].toInt())
            "on" -> if (sender.hasPermission(OP)) toggle(true, sender)
            "off" -> if (sender.hasPermission(OP)) toggle(false, sender)
        }
        return true
    }

    private fun showUsage(p: Player) {
        sendMsg(p, """
                       Man10リボ
                /mrevo check : 借りれる上限額を確かめる
                /mrevo borrow <金額>: お金を借りる(確認画面を挟みます)
                /mrevo payment <金額> : リボの支払い額を決める
                /mrevo payall : 一括返済する
            """.trimIndent())
    }

    private inline fun async(crossinline block: () -> Unit) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { block() })
    }

    private fun checkOp(sender: Player, name: String) {
        val p = Bukkit.getPlayer(name)
        if (p == null) {
            sendMsg(sender, "ユーザーがオフラインです")
            return
        }
        async { ServerLoan.checkServerLoan(sender, p) }
    }

    private fun share(sender: Player) {
        val amount = ServerLoan.shareMap[sender] ?: -1.0
        if (amount == -1.0) {
            sender.sendMessage("あなたは貸し出し可能金額の審査をしておりません！")
            return
        }
        Bukkit.broadcast(Component.text("${prefix}§b§l${sender.name}§a§lさんの公的ローン貸し出し可能金額は" +
                "・・・§e§l${format(amount)}円§a§lです！"))
        ServerLoan.shareMap.remove(sender)
    }

    private fun borrow(sender: Player, amount: Double?) {
        if (amount == null) return
        if (!sender.hasPermission(REVO_PERM)) {
            sendMsg(sender, "あなたはまだMan10リボを使うことができません")
            return
        }
        if (!ServerLoan.isEnable) {
            sendMsg(sender, "現在新規貸し出しはできません。返済は可能です。")
            return
        }
        async { ServerLoan.showBorrowMessage(sender, amount) }
    }

    private fun confirm(sender: Player, amount: Double?) {
        if (amount == null) return
        if (!ServerLoan.commandList.contains(sender)) return
        if (!ServerLoan.isEnable) {
            sendMsg(sender, "現在新規貸し出しはできません。返済は可能です。")
            return
        }
        ServerLoan.commandList.remove(sender)
        if (processingPlayers.containsKey(sender.uniqueId)) {
            sendMsg(sender, "§c§l処理中です。しばらくお待ちください。")
            return
        }
        processingPlayers[sender.uniqueId] = true
        sendMsg(sender, "Man10Bankシステムに問い合わせ中・・・§l§kXX")
        async {
            Thread.sleep(2000)
            ServerLoan.borrow(sender, amount)
            processingPlayers.remove(sender.uniqueId)
        }
    }

    private fun payment(sender: Player, amount: Double?) {
        if (amount == null) return
        async { ServerLoan.setPaymentAmount(sender, amount) }
    }

    private fun addTime(sender: Player, who: String, hour: Int) {
        async {
            when (ServerLoan.addLastPayTime(who, hour)) {
                0 -> sendMsg(sender, "設定完了！${hour}時間追加しました")
                1 -> sendMsg(sender, "存在しないプレイヤーです")
            }
        }
    }

    private fun toggle(enable: Boolean, sender: Player) {
        ServerLoan.isEnable = enable
        if (enable) {
            sendMsg(sender, "Man10リボを有効にしました")
        } else {
            sendMsg(sender, "Man10リボを無効にしました")
        }
        plugin.config.set("revolving.enable", enable)
        plugin.saveConfig()
    }
}

package red.man10.man10bank.command

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent.runCommand
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.Bank
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.format
import red.man10.man10bank.Man10Bank.Companion.loanFee
import red.man10.man10bank.Man10Bank.Companion.loanRate
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.prefix
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.loan.LoanData
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

class LocalLoanCommand : CommandExecutor {

    private val cacheMap = ConcurrentHashMap<Player, Cache>()
    private val USER = "man10lend.user"
    private val OP = "man10lend.op"

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (label != "mlend") return false
        if (sender !is Player) return true

        if (!sender.hasPermission(OP) &&
            (!Man10Bank.enableLocalLoan || Man10Bank.localLoanDisableWorlds.contains(sender.world.name))) {
            sendMsg(sender, "§c§lこのエリアでは個人間借金の取引を行うことはできません。")
            return true
        }

        if (args.isEmpty()) {
            showUsage(sender)
            return true
        }

        return when (args[0]) {
            "off" -> { if (sender.hasPermission(OP)) {
                Man10Bank.enableLocalLoan = false
                plugin.config.set("enableLocalLoan",true)
                plugin.saveConfig()
                sendMsg(sender, "§c§l個人間借金の取引を無効化しました")
            } ; true }
            "on" -> { if (sender.hasPermission(OP)) {
                Man10Bank.enableLocalLoan = true
                plugin.config.set("enableLocalLoan",true)
                plugin.saveConfig()
                sendMsg(sender, "§a§l個人間借金の取引を有効化しました")
            }; true }
            "allow" -> { allow(sender); true }
            "deny" -> { deny(sender); true }
            "userdata" -> { if (args.size >= 2) userData(sender, args[1]); true }
            "reissue" -> {
                if (args.size >= 2) {
                    args[1].toIntOrNull()?.let { reissue(sender, it) }
                        ?: sendMsg(sender, "数字を入力してください")
                }
                true
            }
            else -> propose(sender, args)
        }
    }

    private fun showUsage(p: Player) {
        sendMsg(p, "§a/mlend <プレイヤー> <貸出金額> <返済金額> <期間(日)>")
        sendMsg(p, "§a貸出金額の${loanFee * 100}%を手数料としていただきます")
    }

    private fun allow(sender: Player) {
        if (!sender.hasPermission(USER)) return
        val cache = cacheMap[sender] ?: run {
            sendMsg(sender, "§cあなたに借金の提案は来ていません！")
            return
        }
        if (!cache.lend.isOnline) {
            sendMsg(sender, "§c§l提案者がログアウトしました")
            return
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            sendMsg(sender, "Man10Bankシステムに問い合わせ中・・・§l§kXX")
            val data = LoanData()
            if (!data.create(cache.lend, cache.borrow, cache.amount, cache.paybackAmount, cache.day)) return@Runnable
            cache.lend.inventory.addItem(data.getNote())
            sendMsg(sender, "§a§l借金の契約が成立しました！")
            sendMsg(cache.lend, "§a§l借金の契約が成立しました！")
            cacheMap.remove(sender)
        })
    }

    private fun deny(sender: Player) {
        val cache = cacheMap[sender] ?: run {
            sendMsg(sender, "§cあなたに借金の提案は来ていません！")
            return
        }
        sendMsg(sender, "§c借金の提案を断りました！")
        cache.lend.sendMessage("§c相手が借金の提案を拒否しました！")
        cacheMap.remove(sender)
    }

    private fun userData(sender: Player, name: String) {
        if (!sender.hasPermission(OP)) return
        Thread {
            val uuid = Bank.getUUID(name)
            if (uuid == null) {
                sendMsg(sender, "存在しないユーザーです")
                return@Thread
            }
            sendMsg(sender, name)
            sendMsg(sender, "手形の再発行用ID/金額")
            val data = LoanData.getLoanData(uuid)
            data.forEach { sendMsg(sender, "§c§l${it.first}/${format(it.second)}") }
        }.start()
    }

    private fun reissue(sender: Player, id: Int) {
        if (!sender.hasPermission(OP)) return
        Thread {
            val data = LoanData().load(id) ?: run {
                sendMsg(sender, "借金情報が見つかりません")
                return@Thread
            }
            Bukkit.getScheduler().runTask(plugin, Runnable { sender.inventory.addItem(data.getNote()) })
        }.start()
    }

    private fun propose(sender: Player, args: Array<out String>): Boolean {
        if (!sender.hasPermission(USER)) {
            sendMsg(sender, "§4お金を貸す権限がありません！")
            return true
        }

        val borrow = Bukkit.getPlayer(args[0]) ?: run {
            sendMsg(sender, "§c相手はオフラインです")
            return true
        }

        if (sender.name == borrow.name && !sender.hasPermission(OP)) {
            sendMsg(sender, "§c自分に借金はできません")
            return true
        }

        if (!borrow.hasPermission(USER)) {
            sendMsg(sender, "§4貸し出す相手に権限がありません")
            return true
        }

        val amount: Double
        val paybackAmount: Double
        val day: Int
        try {
            amount = floor(args[1].toDouble())
            paybackAmount = floor(args[2].toDouble())
            day = args[3].toInt()
            if (day > 365 || day <= 0) {
                sendMsg(sender, "§c返済期限は１日以上、一年以内にしてください！")
                return true
            }
            if (amount > Man10Bank.loanMax || amount < 1) {
                sendMsg(sender, "§c貸出金額は1円以上、${format(Man10Bank.loanMax)}円以下に設定してください！")
                return true
            }
            if (paybackAmount < amount) {
                sendMsg(sender, "§c返済金額は貸出金額以上に設定してください！")
                return true
            }
        } catch (e: Exception) {
            sendMsg(sender, "§c入力に問題があります！")
            return true
        }

        val allowOrDeny = text("${prefix}§b§l§n[借りる] ").clickEvent(runCommand("/mlend allow"))
            .append(text("§c§l§n[借りない]").clickEvent(runCommand("/mlend deny")))
        val sdf = SimpleDateFormat("yyyy-MM-dd")
        sendMsg(sender, "§a§l借金の提案を相手に提示しました")
        sendMsg(borrow, "§e§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")
        sendMsg(borrow, "§e§kXX§b§l借金の提案§e§kXX")
        sendMsg(borrow, "§e貸し出す人:${sender.name}")
        sendMsg(borrow, "§e貸し出される金額:${format(amount)}")
        sendMsg(borrow, "§e返す金額:${format(paybackAmount)}")
        sendMsg(borrow, "§e返す日:${sdf.format(LoanData.calcDate(day))}")
        borrow.sendMessage(allowOrDeny)
        sendMsg(borrow, "§e§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")
        val cache = Cache().apply {
            this.amount = amount
            this.paybackAmount = paybackAmount
            this.day = day
            this.borrow = borrow
            this.lend = sender
        }
        cacheMap[borrow] = cache
        return true
    }

    class Cache {
        var day = 0
        var amount = 0.0
        var paybackAmount = 0.0
        lateinit var lend: Player
        lateinit var borrow: Player
    }
}

package red.man10.man10bank.loan

import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import org.apache.commons.lang.math.NumberUtils
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

class LoanCommand : CommandExecutor{

    private val cacheMap = HashMap<Player,Cache>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (label!="mlend")return false

        if (sender !is Player)return true

        if (args.size!=4 && args.size != 1) {

            sendMsg(sender,"§a/mlend <プレイヤー> <金額> <期間(日)> <金利(0.0〜0.5)>")
            sendMsg(sender,"§a金額の10%を手数料としていただきます")
            return true
        }

        //mlend player amount day rate
        if (args[0] == "allow"){

            val cache = cacheMap[sender]

            if (cache == null){
                sendMsg(sender,"§cあなたに借金の提案は来ていません！")
                return true
            }

            if (!cache.lend.isOnline){
                sendMsg(sender,"§c§l提案者がログアウトしました")
                return true
            }

            Thread{

                val data = LoanData()
                val id = data.create(cache.lend,cache.borrow,cache.amount,cache.rate,cache.day)

                if (id == -1){
                    sendMsg(sender,"§c§l相手のお金が足りませんでした")
                    sendMsg(cache.lend,"§c§lお金が足りません！Man10Bankにお金を入れてください！")
                    return@Thread
                }

                cache.lend.inventory.addItem(data.getNote())

            }.start()

            sendMsg(sender,"§a§l借金の契約が成立しました！")
            sendMsg(cache.lend,"§a§l借金の契約が成立しました！")

            cacheMap.remove(sender)

            return true
        }

        if (args[0] == "deny"){

            val cache = cacheMap[sender]

            if (cache == null){
                sendMsg(sender,"§cあなたに借金の提案は来ていません！")
                return true
            }

            sendMsg(sender,"§c借金の提案を拒否しました！")
            cache.lend.sendMessage("§c相手が借金の提案を拒否しました！")

            cacheMap.remove(sender)

            return true
        }

        //////////////////////////////////////////

        val borrow = Bukkit.getPlayer(args[0])

        if (borrow == null){
            sendMsg(sender,"§cそのプレイヤーはオフラインです")
            return true
        }

        val amount : Double
        val day : Int
        val rate : Double

        try {
            amount = args[1].toDouble()
            day = args[2].toInt()
            rate = args[3].toDouble()

            if (rate > 0.5){
                sendMsg(sender,"§c金利は0.5以下にしてください！")
                return true
            }

            if (amount < 1 || day < 0){
                sendMsg(sender,"§c数値エラー")
                return true
            }
        }catch (e:Exception){
            sendMsg(sender,"§c入力に問題があります！")
            return true
        }

        val sdf = SimpleDateFormat("yyyy/MM/dd")

        sendMsg(sender,"§a§l借金の提案を相手に提示しました")

        sendMsg(borrow,"§e§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")
        sendMsg(borrow,"§e§kXX§b§l借金の提案§e§kXX")
        sendMsg(borrow,"§e貸し出される金額:${Man10Bank.format(amount)}")
        sendMsg(borrow,"§e返済する金額:${Man10Bank.format(LoanData.calcRate(amount,day,rate))}")
        sendMsg(borrow,"§e返済日:$${sdf.format(LoanData.calcDate(day))}")
        sendHoverText(borrow,"§b§l§n[借りる]","§c借りたら必ず返しましょう！","/mlend allow")
        sendHoverText(borrow,"§c§l§n[拒否する]","§c正しい判断かもしれない","/mlend deny")
        sendMsg(borrow,"§e§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")

        val cache = Cache()
        cache.amount = amount
        cache.rate = rate
        cache.day = day
        cache.borrow = borrow
        cache.lend = sender

        cacheMap[borrow] = cache

        return false
    }

    fun sendHoverText(p: Player, text: String?, hoverText: String?, command: String?) {
        //////////////////////////////////////////
        //      ホバーテキストとイベントを作成する
        var hoverEvent: HoverEvent? = null
        if (hoverText != null) {
            val hover = ComponentBuilder(hoverText).create()
            hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)
        }

        //////////////////////////////////////////
        //   クリックイベントを作成する
        var clickEvent: ClickEvent? = null
        if (command != null) {
            clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, command)
        }
        val message = ComponentBuilder(text).event(hoverEvent).event(clickEvent).create()
        p.spigot().sendMessage(*message)
    }

    class Cache{
        var day = 0
        var amount = 0.0
        var rate = 0.0
        lateinit var lend : Player
        lateinit var borrow : Player
    }

}
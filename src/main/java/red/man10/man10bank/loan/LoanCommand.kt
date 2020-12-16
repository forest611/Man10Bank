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
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

class LoanCommand : CommandExecutor{

    private val cacheMap = HashMap<Player,Cache>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (label!="mlend")return false

        if (sender !is Player)return true

        //mlend player amount day rate
        if (args[0] == "success"){

            val cache = cacheMap[sender]

            if (cache == null){
                sender.sendMessage("§cあなたに借金の提案は来ていません！")
                return true
            }

            if (!cache.lend.isOnline){
                sender.sendMessage("§c§l提案者がログアウトしました")
                return true
            }

            Thread{

                val data = LoanData()
                data.create(cache.lend,cache.borrow,cache.amount,cache.rate,cache.day)

                cache.lend.inventory.addItem(data.getNote())

            }.start()

            sender.sendMessage("§a§l借金の契約が成立しました！")
            cache.borrow.sendMessage("§a§l借金の契約が成立しました！")

            cacheMap.remove(sender)

            return true
        }

        //////////////////////////////////////////

        val borrow = Bukkit.getPlayer(args[0])

        if (borrow == null){
            sender.sendMessage("§cそのプレイヤーはオフラインです")
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
                sender.sendMessage("§c金利は0.5以下にしてください！")
                return true
            }

            if (amount < 1 || day <1){
                sender.sendMessage("§c数値エラー")
                return true
            }
        }catch (e:Exception){
            sender.sendMessage("§c入力に問題があります！")
            return true
        }

        val sdf = SimpleDateFormat("yyyy/MM/dd")
        sdf.format(LoanData.calcDate(day))

        borrow.sendMessage("§e§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")
        borrow.sendMessage("§e§kXX§b§l借金の提案§e§kXX")
        borrow.sendMessage("§e貸し出される金額:${Man10Bank.format(amount)}")
        borrow.sendMessage("§e返済する金額:${Man10Bank.format(LoanData.calcRate(amount,day,rate))}")
        borrow.sendMessage("§e返済日:$sdf")
        sendHoverText(borrow,"§b§l§n[借りる]","§c借りたら必ず返しましょう！","/mlend success")
        borrow.sendMessage("§e§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")

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
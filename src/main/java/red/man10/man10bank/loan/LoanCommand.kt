package red.man10.man10bank.loan

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent.runCommand
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.es
import red.man10.man10bank.Man10Bank.Companion.format
import red.man10.man10bank.Man10Bank.Companion.loanFee
import red.man10.man10bank.Man10Bank.Companion.loanRate
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.prefix
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

class LoanCommand : CommandExecutor{

    private val cacheMap = ConcurrentHashMap<Player,Cache>()

    private val USER = "man10lend.user"
    private val OP = "man10lend.op"

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (label!="mlend")return false

        if (sender !is Player)return true

        if (args.size!=4 && args.size != 1) {

            sendMsg(sender,"§a/mlend <プレイヤー> <金額> <期間(日)> <金利(0.0〜${loanRate})>")
            sendMsg(sender,"§a金額の10%を手数料としていただきます")
            return true

        }

        if (args[0] == "off"){
            if (!sender.hasPermission(OP))return false
            LoanData.enable = true
            return true
        }

        if (args[0] == "on"){

            if (!sender.hasPermission(OP))return false
            LoanData.enable = true
            return true

        }

        if (!LoanData.enable){

            sendMsg(sender,"§a現在借金の貸し出しなどはできません！")
            return true

        }

        //mlend player amount day rate
        if (args[0] == "allow"){

            if (!sender.hasPermission(USER)){ return false}

            val cache = cacheMap[sender]

            if (cache == null){
                sendMsg(sender,"§cあなたに借金の提案は来ていません！")
                return true
            }

            if (!cache.lend.isOnline){
                sendMsg(sender,"§c§l提案者がログアウトしました")
                return true
            }

            es.execute{

                val data = LoanData()
                val id = data.create(cache.lend,cache.borrow,cache.amount,cache.rate,cache.day)

                if (id == -1){

                    sendMsg(sender,"§c§l相手の銀行のお金が足りませんでした")
                    sendMsg(cache.lend,"§c§l銀行のお金が足りません！${format(cache.amount* loanFee)}円入れてください！")
                    return@execute

                }

                cache.lend.inventory.addItem(data.getNote())

                sendMsg(sender,"§a§l借金の契約が成立しました！")
                sendMsg(cache.lend,"§a§l借金の契約が成立しました！")

                cacheMap.remove(sender)

            }

            return true
        }

        if (args[0] == "deny"){

            val cache = cacheMap[sender]

            if (cache == null){
                sendMsg(sender,"§cあなたに借金の提案は来ていません！")
                return true
            }

            sendMsg(sender,"§c借金の提案を断りました！")
            cache.lend.sendMessage("§c相手が借金の提案を拒否しました！")

            cacheMap.remove(sender)

            return true
        }

        /////////////////貸し出しコマンド/////////////////////////

        if (!sender.hasPermission(USER)){
            sendMsg(sender,"§4お金を貸す権限がありません！")
            return true
        }

        if (sender.name == args[0]){
            sendMsg(sender,"§c自分に借金はできません")
            return true
        }

        val borrow = Bukkit.getPlayer(args[0])

        if (borrow == null){
            sendMsg(sender,"§c相手はオフラインです")
            return true
        }

        if (!borrow.hasPermission(USER)){
            sendMsg(sender,"§4貸し出す相手に権限がありません")
            return true
        }

        val amount : Double
        val day : Int
        val rate : Double

        try {

            amount = floor(args[1].toDouble())
            day = args[2].toInt()
            rate = args[3].toDouble()

            if (rate > loanRate){
                sendMsg(sender,"§c金利は${loanRate}以下にしてください！")
                return true
            }

            if (amount < 1 || day < 0 || rate<0.0){
                sendMsg(sender,"§c数値に問題があります")
                return true
            }

            if (day>365){
                sendMsg(sender,"§c返済期限は一年以内にしてください！")
                return true
            }

            if (amount>Man10Bank.loanMax){
                sendMsg(sender,"§貸出金額は${format(Man10Bank.loanMax)}円以下に設定してください！")
                return true
            }

        }catch (e:Exception){
            sendMsg(sender,"§c入力に問題があります！")
            return true
        }

        val allowOrDeny = Component.text("${prefix}§b§l§n[借りる] ").clickEvent(runCommand("/mlend allow"))
            .append(Component.text("§c§l§n[借りない]").clickEvent(runCommand("/mlend deny")))

        val sdf = SimpleDateFormat("yyyy/MM/dd")

        sendMsg(sender,"§a§l借金の提案を相手に提示しました")

        sendMsg(borrow,"§e§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")
        sendMsg(borrow,"§e§kXX§b§l借金の提案§e§kXX")
        sendMsg(borrow,"§e提案者:${sender.name}")
        sendMsg(borrow,"§e貸し出される金額:${format(amount)}")
        sendMsg(borrow,"§e返す金額:${format(LoanData.calcRate(amount,day,rate))}")
        sendMsg(borrow,"§e返済日:$${sdf.format(LoanData.calcDate(day))}")
        borrow.sendMessage(allowOrDeny)
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


    class Cache{
        var day = 0
        var amount = 0.0
        var rate = 0.0
        lateinit var lend : Player
        lateinit var borrow : Player
    }

}
package red.man10.man10bank.loan

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
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

class LoanCommand : CommandExecutor{

    private val cacheMap = ConcurrentHashMap<Player,Cache>()

    private val USER = "man10lend.user"
    private val OP = "man10lend.op"

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (label!="mlend")return false

        if (sender !is Player)return true

        if (!sender.hasPermission(OP) && !Man10Bank.enableLocalLoan){
            sendMsg(sender,"§c§lこのエリアでは個人間借金の取引を行うことはできません。")
            return true
        }

        if (args.isEmpty()) {

            sendMsg(sender,"§a/mlend <プレイヤー> <金額> <期間(日)> <金利(0.0〜${loanRate})>")
            sendMsg(sender,"§a金額の${loanFee*100}％を手数料としていただきます")
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

            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {

                sendMsg(sender,"Man10Bankシステムに問い合わせ中・・・§l§kXX")

                val data = LoanData()

                data.create(cache.lend,cache.borrow,cache.amount,cache.rate,cache.day)

                cache.lend.inventory.addItem(data.getNote())

                sendMsg(sender,"§a§l借金の契約が成立しました！")
                sendMsg(cache.lend,"§a§l借金の契約が成立しました！")

                cacheMap.remove(sender)

            })

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

        if (args[0] == "userdata"){
            if (!sender.hasPermission(OP))return true

            Thread{
                val uuid = Bank.getUUID(args[1])
                if (uuid == null){
                    sendMsg(sender,"存在しないユーザーです")
                    return@Thread
                }

                sendMsg(sender,args[1])
                sendMsg(sender,"手形の再発行用ID/金額")

                val data = LoanData.getLoanData(uuid)
                data.forEach { sendMsg(sender,"§c§l${it.first}/${format(it.second)}") }
            }.start()

            return true

        }

        if (args[0] == "reissue"){

            if (!sender.hasPermission(OP))return true

            val id = args[1].toIntOrNull()

            if (id==null){
                sendMsg(sender,"数字を入力してください")
                return true
            }

            Thread{
                val data = LoanData().load(id)

                if (data==null){
                    sendMsg(sender,"借金情報が見つかりません")
                    return@Thread
                }

                Bukkit.getScheduler().runTask(plugin,Runnable{ sender.inventory.addItem(data.getNote()) })

            }.start()

            return true
        }

        /////////////////貸し出しコマンド/////////////////////////

        if (!sender.hasPermission(USER)){
            sendMsg(sender,"§4お金を貸す権限がありません！")
            return true
        }

//        if (sender.name == args[0]){
//            sendMsg(sender,"§c自分に借金はできません")
//            return true
//        }

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

            if (rate > loanRate || rate<0.0){
                sendMsg(sender,"§c金利は0以上、${loanRate}以下にしてください！")
                return true
            }

            if (day>365 || day<=0){
                sendMsg(sender,"§c返済期限は１日以上、一年以内にしてください！")
                return true
            }

            if (amount>Man10Bank.loanMax || amount < 1){
                sendMsg(sender,"§貸出金額は1円以上、${format(Man10Bank.loanMax)}円以下に設定してください！")
                return true
            }

        }catch (e:Exception){
            sendMsg(sender,"§c入力に問題があります！")
            return true
        }

        val allowOrDeny = text("${prefix}§b§l§n[借りる] ").clickEvent(runCommand("/mlend allow"))
            .append(text("§c§l§n[借りない]").clickEvent(runCommand("/mlend deny")))

        val sdf = SimpleDateFormat("yyyy-MM-dd")

        sendMsg(sender,"§a§l借金の提案を相手に提示しました")

        sendMsg(borrow,"§e§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")
        sendMsg(borrow,"§e§kXX§b§l借金の提案§e§kXX")
        sendMsg(borrow,"§e貸し出す人:${sender.name}")
        sendMsg(borrow,"§e貸し出される金額:${format(amount)}")
        sendMsg(borrow,"§e返す金額:${format(LoanData.calcRate(amount,day,rate))}")
        sendMsg(borrow,"§e返す日:$${sdf.format(LoanData.calcDate(day))}")
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
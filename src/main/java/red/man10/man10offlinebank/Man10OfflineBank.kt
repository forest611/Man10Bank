package red.man10.man10offlinebank

import org.apache.commons.lang.math.NumberUtils
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.HashMap

class Man10OfflineBank : JavaPlugin(),Listener {

    companion object{
        val prefix = "§l[§e§lMan10Bank§f§l]"

        lateinit var bank : Bank
        lateinit var vault : VaultManager

        fun sendMsg(p:Player,msg:String){
            p.sendMessage(prefix+msg)
        }
    }

    val OP = "man10bank.op"
    val USER = "man10bank.user"

    val checking = HashMap<Player,Command>()


    lateinit var es : ExecutorService

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (label == "mbaltop"){

            if (sender !is Player)return false

            sendMsg(sender,"§e§l現在取得中....")

            es.execute{

                val list = bank.balanceTop()?:return@execute

                for (data in list){
                    sendMsg(sender,"§b§l${data.first.name} : §e§l$ ${String.format("%,.1f",data.second)}")
                }

                sendMsg(sender,"§e§l合計口座残高")

                sendMsg(sender,"§b§kXX§e§l${String.format("%,.1f",bank.totalBalance())}§b§kXX")

                sendMsg(sender,"§e§l平均口座残高")

                sendMsg(sender,"§b§kXX§e§l${String.format("%,.1f",bank.average())}§b§kXX")

            }

        }

        if (label == "mbal"){

            if (args.isEmpty()){

                if (sender !is Player)return false

                sendMsg(sender,"現在取得中....")

                es.execute{
                    sendMsg(sender,"§e§l==========現在の銀行口座残高==========")
                    sendMsg(sender,"§b§kXX§e§l${String.format("%,.1f",bank.getBalance(sender.uniqueId))}§b§kXX")

                }

                return true
            }

            val cmd = args[0]

            if (cmd == "user"&& args.size == 2){
                if (sender !is Player)return false
                if (!sender.hasPermission(OP))return true

                sendMsg(sender,"現在取得中....")

                es.execute{
                    val uuid = bank.getUUID(args[1])

                    if (uuid == null){
                        sendMsg(sender,"まだ口座が解説されていない可能性があります")
                        return@execute
                    }

                    sendMsg(sender,"§e§l==========現在の銀行口座残高==========")
                    sendMsg(sender,"§b§kXX§e§l${String.format("%,.1f",bank.getBalance(uuid))}§b§kXX")

                }

            }

            if (!sender.hasPermission(USER))return true

            if (cmd == "help"){

                if (sender !is Player)return false
                sendMsg(sender,"§eMan10Bank")
                sendMsg(sender,"§e/mbal : 口座残高を確認する")
                sendMsg(sender,"§e/mbal deposit(d) <金額>: 所持金のいくらかを、口座に入金する")
                sendMsg(sender,"§e/mbal withdraw(w) <金額>: 口座のお金を、出金する")
            }

            //deposit withdraw
            if ((cmd == "deposit" || cmd == "d")&& args.size == 2){

                if (sender !is Player)return false

                if (!NumberUtils.isNumber(args[1])){
                    sendMsg(sender,"§c§l入金する額を半角数字で入力してください！")
                    return true
                }

                val amount = args[1].toDouble()

                if (amount < 1){
                    sendMsg(sender,"§c§l1未満の値は入金出来ません！")
                    return true
                }

                if (vault.getBalance(sender.uniqueId)<amount){
                    sendMsg(sender,"§c§l入金失敗！、所持金が足りません！")
                    return true

                }

                !vault.withdraw(sender.uniqueId,amount)

                es.execute {
                    bank.deposit(sender.uniqueId,amount,this,"PlayerDepositOnCommand")

                    sendMsg(sender,"§a§l入金成功！")
                }


                return true
            }

            if ((cmd == "withdraw" || cmd == "w") && args.size == 2){
                if (sender !is Player)return false

                if (!NumberUtils.isNumber(args[1])){
                    sendMsg(sender,"§c§l出金する額を半角数字で入力してください！")
                    return true
                }

                val amount = args[1].toDouble()

                if (amount < 1){
                    sendMsg(sender,"§c§l1未満の値は出金出来ません！")
                    return true
                }

                es.execute {
                    if (!bank.withdraw(sender.uniqueId,amount,this,"PlayerWithdrawOnCommand")){
                        sendMsg(sender,"§c§l出金失敗！口座残高が足りません！")
                        return@execute
                    }

                    vault.deposit(sender.uniqueId,amount)

                    sendMsg(sender,"§a§l出金成功！")

                }

                return true
            }


        }


        if (sender !is Player)return false

        if (label == "mpay"){

            if (!sender.hasPermission(USER))return true

            if (args.size != 2)return false

            if (!NumberUtils.isNumber(args[1])){
                sendMsg(sender,"§c§l/mpay <ユーザー名> <金額>")
                return true
            }

            val amount = args[1].toDouble()

            if (amount <0.1){
                sendMsg(sender,"§c§l0.1未満の額は送金できません！")
                return true
            }

            if (checking[sender] == null||checking[sender]!! != command){

                sendMsg(sender,"§7§l送金金額:${String.format("%,.1f",amount)}")
                sendMsg(sender,"§7§l送金相手:${args[0]}")
                sendMsg(sender,"§7§l確認のため、もう一度入力してください")

                checking[sender] = command

                return true
            }

            checking.remove(sender)

            sendMsg(sender,"§e§l現在入金中・・・")


            es.execute {
                val uuid = bank.getUUID(args[0])

                if (uuid == null){
                    sendMsg(sender,"§c§l存在しないユーザ、もしくは口座がありません！")
                    return@execute
                }

                if (vault.getBalance(sender.uniqueId)<amount){
                    sendMsg(sender,"§c§l送金する残高が足りません！")
                    return@execute

                }

                !vault.withdraw(sender.uniqueId,amount)

                bank.deposit(uuid,amount,this,"RemittanceFrom${sender.name}")

                sendMsg(sender,"§a§l送金成功！")
            }
        }


        return false
    }

    override fun onEnable() {
        // Plugin startup logic

        saveDefaultConfig()

        es = Executors.newCachedThreadPool()

        bank = Bank(this)

        bank.mysqlQueue()

        vault = VaultManager(this)

        server.pluginManager.registerEvents(this,this)

    }

    override fun onDisable() {
        // Plugin shutdown logic
        es.shutdown()
    }

    @EventHandler
    fun login(e:PlayerJoinEvent){
        e.player.performCommand("mbal")
    }
}
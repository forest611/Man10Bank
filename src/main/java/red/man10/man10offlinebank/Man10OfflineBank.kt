package red.man10.man10offlinebank

import org.apache.commons.lang.math.NumberUtils
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import kotlin.collections.HashMap

class Man10OfflineBank : JavaPlugin() {

    companion object{
        val prefix = "§l[§e§lMan10Bank§f§l]"

        lateinit var bank : Bank

        fun sendMsg(p:Player,msg:String){
            p.sendMessage(prefix+msg)
        }
    }

    val OP = "man10bank.op"
    val USER = "man10bank.user"

    val checking = HashMap<Player,Command>()

    lateinit var vault : VaultManager

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (label == "mbal"){



            if (args.isEmpty()){

                if (sender !is Player)return false

                sendMsg(sender,"現在取得中....")

                Thread(Runnable {
                    sendMsg(sender,"§e§l==========現在の銀行口座残高==========")
                    sendMsg(sender,"§b§kXX§e§l${String.format("%,.1f",bank.getBalance(sender.uniqueId))}§b§kXX")
                }).start()

                return true
            }

            val cmd = args[0]

            //プラグインテスト用
            if (cmd == "give"){

                if (!sender.hasPermission(OP))return true

                bank.deposit(UUID.fromString(args[1]),args[2].toDouble(),this,"GivePluginCommand")

                return true
            }

            //プラグインテスト用
            if (cmd == "take"){

                if (!sender.hasPermission(OP))return true

                bank.withdraw(UUID.fromString(args[1]),args[2].toDouble(),this,"TakePluginCommand")

                return true
            }

            if (!sender.hasPermission(USER))return true

            //deposit withdraw
            if (cmd == "deposit" && args.size == 2){

                if (sender !is Player)return false

                if (!NumberUtils.isNumber(args[1])){
                    sendMsg(sender,"§c§l入金する額を半角数字で入力してください！")
                    return true
                }

                val amount = args[1].toDouble()

                if (!vault.withdraw(sender.uniqueId,amount)){
                    sendMsg(sender,"§c§l入金失敗！、所持金が足りません！")
                    return true
                }

                Thread(Runnable {
                    bank.deposit(sender.uniqueId,amount,this,"PlayerDepositOnCommand")

                    sendMsg(sender,"§a§l入金成功！")
                }).start()


                return true
            }

            if (cmd == "withdraw" && args.size == 2){
                if (sender !is Player)return false

                if (!NumberUtils.isNumber(args[1])){
                    sendMsg(sender,"§c§l出金する額を半角数字で入力してください！")
                    return true
                }

                val amount = args[1].toDouble()

                Thread(Runnable {
                    if (!bank.withdraw(sender.uniqueId,amount,this,"PlayerWithdrawOnCommand")){
                        sendMsg(sender,"§c§l出金失敗！口座残高が足りません！")
                        return@Runnable
                    }

                    vault.deposit(sender.uniqueId,amount)

                    sendMsg(sender,"§a§l出金成功！")
                }).start()


                return true
            }


        }


        if (sender !is Player)return false

        if (label == "mpay"){

            if (!sender.hasPermission(USER))return true

            if (args.size != 2)return false

            if (!NumberUtils.isNumber(args[2])){
                sendMsg(sender,"§c§l/mpay <ユーザー名> <金額>")
                return true
            }

            if (!checking.keys.contains(sender)&&checking[sender]!! == command){


                sendMsg(sender,"§7§l送金金額:${String.format("%,.1s",args[2])}")
                sendMsg(sender,"§7§l送金相手:${args[1]}")
                sendMsg(sender,"§7§l確認のため、もう一度入力してください")

                checking[sender] = command

                return true
            }

            checking.remove(sender)

            sendMsg(sender,"§e§l現在入金中・・・")

            Thread(Runnable {
                val uuid = bank.getUUID(args[1])

                val amount = args[2].toDouble()

                if (uuid == null){
                    sendMsg(sender,"§c§l存在しないユーザ、もしくは口座がありません！")
                    return@Runnable
                }

                if (!vault.withdraw(sender.uniqueId,amount)){
                    sendMsg(sender,"§c§l送金する残高が足りません！")
                    return@Runnable
                }

                bank.deposit(uuid,amount,this,"RemittanceFrom${sender.name}")

                sendMsg(sender,"§a§l送金成功！")

            }).start()
        }


        return false
    }

    override fun onEnable() {
        // Plugin startup logic

        saveDefaultConfig()

        bank = Bank(this)

        bank.mysqlQueue()

        vault = VaultManager(this)

    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
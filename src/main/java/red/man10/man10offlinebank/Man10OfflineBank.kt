package red.man10.man10offlinebank

import org.apache.commons.lang.math.NumberUtils
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import red.man10.realestate.VaultManager

class Man10OfflineBank : JavaPlugin() {

    companion object{
        val prefix = "§l[§e§lMan10OfflineBank&f§l]"

        lateinit var bank : Bank

        fun sendMsg(p:Player,msg:String){
            p.sendMessage(prefix+msg)
        }
    }

    lateinit var vault : VaultManager

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (sender !is Player)return false

        if (label == "mbal"){

            if (args.isEmpty()){

                sendMsg(sender,"現在取得中....")

                Thread(Runnable {
                    sendMsg(sender,"==========§e§l現在の銀行口座残高==========")
                    sendMsg(sender,"§b§kXX§e§l${String.format("%,.1f",bank.getBalance(sender.uniqueId))}§b§kXX")
                }).start()

                return true
            }

            val cmd = args[0]

            if (cmd == "deposit" && args.size == 2){

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
                }).start()


                return true
            }

            if (cmd == "withdraw" && args.size == 2){

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

                }).start()


                return true
            }


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
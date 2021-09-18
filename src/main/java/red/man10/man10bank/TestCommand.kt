package red.man10.man10bank

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.Man10Bank.Companion.vault
import java.util.concurrent.ConcurrentHashMap

class TestCommand : CommandExecutor{

    private val amount = 100.0

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (label!="baltest"){return false}

        if (sender !is Player)return true

        if (args.isEmpty()){

            sendMsg(sender,"/baltest single <count>")
            sendMsg(sender,"/baltest multi <count>")
            return true
        }

        val count = args[1].toIntOrNull()?:return true
        val uuid = sender.uniqueId

        if (args[0] == "single"){


            vault.withdraw(uuid, vault.getBalance(uuid))

            vault.deposit(uuid, (count*amount))

            val depositRets = HashMap<Int,Int>()
            val withdrawRets = HashMap<Int,Int>()

            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {

                sender.sendMessage("StartDeposit")

                for (i in 0 until count){
                    val ret = Bank.deposit(uuid,amount, plugin,"BankTest","Man10Bankのテスト").first
                    depositRets[ret] = (depositRets[ret]?:0)+1
                }

                for (ret in depositRets){
                    sender.sendMessage("結果:${ret.key} 数:${ret.value}")
                }

                sender.sendMessage("StartWithdraw")

                for (i in 0 until count){
                    val ret = Bank.withdraw(uuid,amount, plugin,"BankTest","Man10Bankのテスト").first
                    withdrawRets[ret] = (withdrawRets[ret]?:0)+1
                }

                for (ret in withdrawRets){
                    sender.sendMessage("結果:${ret.key} 数:${ret.value}")
                }

                sender.sendMessage("Finish")
            })


            return true
        }

        if (args[0] == "multi"){

            vault.withdraw(uuid, vault.getBalance(uuid))

            vault.deposit(uuid, (count*amount))

            val depositRets = ConcurrentHashMap<Int,Int>()
            val withdrawRets = ConcurrentHashMap<Int,Int>()

            val core = Runtime.getRuntime().availableProcessors()

            val oneCount = count/core

            var thread = core

            sender.sendMessage("論理プロセッサ数:${core}")
            sender.sendMessage("StartDeposit")

            for (c in 0 until core){
                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                    for (i in 0 until oneCount){
                        val ret = Bank.deposit(uuid,amount, plugin,"BankTest","Man10Bankのテスト").first
                        depositRets[ret] = (depositRets[ret]?:0)+1
                    }
                    thread--
                })
            }

            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {

                while (thread!=0){Thread.sleep(1)}

                for (ret in depositRets){
                    sender.sendMessage("結果:${ret.key} 数:${ret.value}")
                }

                thread = core

                for (c in 0 until core){
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                        for (i in 0 until oneCount){
                            val ret = Bank.withdraw(uuid,amount, plugin,"BankTest","Man10Bankのテスト").first
                            withdrawRets[ret] = (withdrawRets[ret]?:0)+1
                        }
                        thread--
                    })
                }

                while (thread!=0){Thread.sleep(1)}

                for (ret in withdrawRets){
                    sender.sendMessage("結果:${ret.key} 数:${ret.value}")
                }

                sender.sendMessage("Finish")
            })

            return true


        }

        return false
    }


}
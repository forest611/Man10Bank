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

class ServerLoanCommand : CommandExecutor{

    private val REVO_PERM = "man10bank.revo"

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (sender !is Player)return false

        if (label != "mrevo")return false

        if (args.isEmpty()) {

            sendMsg(sender,"""
                       Man10リボ
                /mrevo check : 借りれる上限額を確かめる
                /mrevo borrow <金額>: お金を借りる(確認画面を挟みます)
                /mrevo payment <金額> : リボの支払い額を決める
                /mrevo payall : 一括返済する
            """.trimIndent())

            return false

        }

        when(args[0]){

            "check" ->{
                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { ServerLoan.checkServerLoan(sender) })
            }

            "checkop" ->{

                if (!sender.hasPermission(OP))return true

                val p = Bukkit.getPlayer(args[1])

                if (p==null){
                    sendMsg(sender,"ユーザーがオフラインです")
                    return true
                }

                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { ServerLoan.checkServerLoan(sender,p) })
            }

            "share" ->{

                val amount = ServerLoan.shareMap[sender]?:-1.0

                if (amount == -1.0){
                    sender.sendMessage("あなたは貸し出し可能金額の審査をしておりません！")
                    return true
                }

                Bukkit.broadcast(Component.text("${prefix}§b§l${sender.name}§a§lさんの公的ローン貸し出し可能金額は" +
                        "・・・§e§l${format(amount)}円§a§lです！"))

                ServerLoan.shareMap.remove(sender)
            }

            "borrow" ->{

                if (args.size != 2)return true

                if (!sender.hasPermission(REVO_PERM)){
                    sendMsg(sender,"あなたはまだMan10リボを使うことができません")
                    return true
                }

                if (!ServerLoan.isEnable){
                    sendMsg(sender,"現在新規貸し出しはできません。返済は可能です。")
                    return true

                }

                val amount = args[1].toDoubleOrNull()?:return true

                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                    ServerLoan.showBorrowMessage(sender,amount)
                })

            }

            "confirm" ->{

                if (!ServerLoan.commandList.contains(sender))return false

                if (!ServerLoan.isEnable){
                    sendMsg(sender,"現在新規貸し出しはできません。返済は可能です。")
                    return true

                }

                ServerLoan.commandList.remove(sender)

                val amount = args[1].toDoubleOrNull()?:return true

                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                    sendMsg(sender,"Man10Bankシステムに問い合わせ中・・・§l§kXX")
                    ServerLoan.borrow(sender,amount)
                })

            }

            "payment" ->{

                if (args.size != 2)return true

                val amount = args[1].toDoubleOrNull()?:return true

                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                    ServerLoan.setPaymentAmount(sender,amount)
                })

            }

            "payall" ->{
                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { ServerLoan.paymentAll(sender) })
            }

            "addtime" ->{//mrevo addtime <player/all> <hour>

                if (!sender.hasPermission(OP))return true

                if (args.size != 3){
                    sendMsg(sender,"/mrevo addtime <player/all> <hour>")
                    return true
                }

                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {

                    when(ServerLoan.addLastPayTime(args[1],args[2].toInt())){
                        0 ->{ sendMsg(sender,"設定完了！${args[2]}時間追加しました") }
                        1 ->{ sendMsg(sender,"存在しないプレイヤーです")}
                    }

                })
            }

            "on" ->{

                if (!sender.hasPermission(OP))return true

                ServerLoan.isEnable = true
                plugin.config.set("revolving.enable",true)
                plugin.saveConfig()
            }

            "off" ->{
                if (!sender.hasPermission(OP))return true

                ServerLoan.isEnable = false
                plugin.config.set("revolving.enable",false)
                plugin.saveConfig()

            }


        }



        return false
    }
}
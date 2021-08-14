package red.man10.man10bank.loan

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.OP
import red.man10.man10bank.Man10Bank.Companion.es
import red.man10.man10bank.Man10Bank.Companion.format
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
            """.trimIndent())

            return false

        }

        when(args[0]){

            "check" ->{
                ServerLoan.checkServerLoan(sender)
            }

            "checkop" ->{

                if (!sender.hasPermission(OP))return true

                val p = Bukkit.getPlayer(args[1])

                if (p==null){
                    sendMsg(sender,"ユーザーがオフラインです")
                    return true
                }

                ServerLoan.checkServerLoan(sender,p)
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

                val amount = args[1].toDoubleOrNull()?:return true

                es.execute {
                    ServerLoan.showBorrowMessage(sender,amount)
                }

            }

            "confirm" ->{

                if (!ServerLoan.commandList.contains(sender))return false

                ServerLoan.commandList.remove(sender)

                val amount = args[1].toDoubleOrNull()?:return true

                es.execute {
                    ServerLoan.borrow(sender,amount)
                }

            }

            "payment" ->{

                if (args.size != 2)return true

                val amount = args[1].toDoubleOrNull()?:return true

                es.execute {
                    ServerLoan.setPaymentAmount(sender,amount)
                }

            }

            "addtime" ->{//mrevo addtime <player/all> <hour>

                if (!sender.hasPermission(OP))return true

                if (args.size != 3){
                    sendMsg(sender,"/mrevo addtime <player/all> <hour>")
                    return true
                }

                es.execute {

                    when(ServerLoan.addLastPayTime(args[1],args[2].toInt())){
                        0 ->{ sendMsg(sender,"設定完了！${args[2]}時間追加しました") }
                        1 ->{ sendMsg(sender,"存在しないプレイヤーです")}
                    }

                }



            }


        }



        return false
    }
}
package red.man10.man10bank.loan

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.Config
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Permissions
import red.man10.man10bank.api.APIServerLoan
import red.man10.man10bank.util.BlockingQueue
import red.man10.man10bank.util.Utility.format
import red.man10.man10bank.util.Utility.msg
import red.man10.man10bank.util.Utility.prefix
import java.lang.Math.floor

object ServerLoan : CommandExecutor{

    private var serverProperty : APIServerLoan.ServerLoanProperty = APIServerLoan.property()
    var isEnable = true

    private fun checkAmount(p:Player){
        val maxLoan = APIServerLoan.getBorrowableAmount(p.uniqueId)

        msg(p,"§f§l貸し出し可能上限額:§e§l${format(maxLoan)}円(最大:${format(serverProperty.MaximumAmount)}円)")
    }
    private fun setPayment(p:Player,amount: Double){
        val data = APIServerLoan.getInfo(p.uniqueId)
        if (data == null){
            msg(p,"§a§lあなたはリボを利用したことがありません")
            return
        }
        data.payment_amount = amount

        val ret = APIServerLoan.setInfo(data)

        if (ret == "Successful"){
            msg(p,"§e§l支払額を${format(amount)}円に変更しました")
            return
        }
        msg(p,"§e§l変更失敗。時間をおいてやり直してください")
    }

    private fun showBorrowMessage(p:Player,amount:Double){
        if (amount <= 0.0){
            msg(p,"1円以上を入力してください")
            return
        }

        val max = APIServerLoan.getBorrowableAmount(p.uniqueId)
        val borrowing = APIServerLoan.getInfo(p.uniqueId)?.borrow_amount?:0.0

        val borrowableAmount = max - borrowing

        if (borrowableAmount<0.0){
            msg(p,"§cあなたはもうお金を借りることができません！")
            return
        }

        if (borrowableAmount<amount){
            msg(p,"§cあなたが借りることができる金額は${format(borrowableAmount)}円までです")
            p.sendMessage(
                Component.text("${prefix}§e§l§n[${format(borrowableAmount)}円借りる]").
            clickEvent(ClickEvent.runCommand("/mrevo borrow $borrowableAmount")))
            return
        }

        val button = Component.text("${prefix}§c§l§n[借りる] ")
            .clickEvent(ClickEvent.runCommand("/mrevo confirm ${floor(amount)}"))

        val minPaymentAmount = (borrowing + amount )* serverProperty.PaymentInterval * serverProperty.DailyInterest

        msg(p,"§b§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")
        msg(p,"§e§kXX§b§lMan10リボ§e§kXX")
        msg(p,"§b貸し出される金額:${format(amount)}")
        msg(p,"§b現在の利用額:${format(borrowing)}")
        msg(p,"§c利息の計算方法:§l<利用額>x<金利>x<最後に支払ってからの日数>")
        msg(p,"§c※支払額から利息を引いた額が返済に充てられます")
        msg(p,"§b${serverProperty.PaymentInterval}日ごとに最低${format(minPaymentAmount)}円支払う必要があります")
        p.sendMessage(button)
        msg(p,"§b§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")

    }

    private fun borrow(p: Player, amount:Double){
        val ret = APIServerLoan.borrow(p.uniqueId,amount)

        if (ret != "Successful"){
            msg(p,"§c§lリボを借りるのに失敗しました！")
            return
        }

        msg(p,"§a§l${format(amount)}円借りました！")
        msg(p,"§a§l銀行に支払額を入れておいてください")
    }

    private fun pay(p:Player,amount:Double){
        val ret = APIServerLoan.pay(p.uniqueId,amount)

        if (!ret){
            msg(p,"§c§l支払い失敗！銀行の残高が足りない可能性があります")
            return
        }

        msg(p,"§a§l支払い成功！")
    }

    private fun payAll(p:Player){
        pay(p,APIServerLoan.getInfo(p.uniqueId)?.borrow_amount?:0.0)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {

        if (sender !is Player){
            return true
        }

        if (args.isNullOrEmpty()){

            msg(sender,"""
                       Man10リボ
                /mrevo check : 借りれる上限額を確かめる
                /mrevo borrow <金額>: お金を借りる(確認画面を挟みます)
                /mrevo payment <金額> : リボの支払い額を決める
                /mrevo payall : 一括返済する
            """.trimIndent())

            return true
        }

        when(args[0]){

            "check" ->{
                BlockingQueue.addTask {
                    checkAmount(sender)
                }
            }

            "checkop" ->{

                if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return true
                //TODO:

            }

//            "share" ->{
//
//                val amount = ServerLoan.shareMap[sender]?:-1.0
//
//                if (amount == -1.0){
//                    sender.sendMessage("あなたは貸し出し可能金額の審査をしておりません！")
//                    return true
//                }
//
//                Bukkit.broadcast(Component.text("${prefix}§b§l${sender.name}§a§lさんの公的ローン貸し出し可能金額は" +
//                        "・・・§e§l${format(amount)}円§a§lです！"))
//
//                ServerLoan.shareMap.remove(sender)
//            }

            "borrow" ->{

                if (args.size != 2)return true

                if (!sender.hasPermission(Permissions.SERVER_LOAN_USER)){
                    msg(sender,"あなたはまだMan10リボを使うことができません")
                    return true
                }

                if (!isEnable){
                    msg(sender,"現在新規貸し出しはできません。返済は可能です。")
                    return true
                }

                val amount = args[1].toDoubleOrNull()?:return true

                BlockingQueue.addTask {
                    showBorrowMessage(sender,amount)
                }
            }

            "confirm" ->{
                if (!isEnable){
                    msg(sender,"現在新規貸し出しはできません。返済は可能です。")
                    return true
                }

                val amount = args[1].toDoubleOrNull()?:return true

                BlockingQueue.addTask { borrow(sender,amount) }
            }

            "payment" ->{

                if (args.size != 2)return true

                val amount = args[1].toDoubleOrNull()?:return true

                BlockingQueue.addTask {
                    setPayment(sender,amount)
                }

            }

            "pay" ->{
                if (args.size != 2)return true

                val amount = args[1].toDoubleOrNull()?:return true

                BlockingQueue.addTask {
                    pay(sender,amount)
                }
            }

            "payall" ->{
                if (args.size != 2)return true

                BlockingQueue.addTask {
                    payAll(sender)
                }
            }

//            "addtime" ->{//mrevo addtime <player/all> <hour>
//
//                if (!sender.hasPermission(OP))return true
//
//                if (args.size != 3){
//                    sendMsg(sender,"/mrevo addtime <player/all> <hour>")
//                    return true
//                }
//
//                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
//
//                    when(ServerLoan.addLastPayTime(args[1],args[2].toInt())){
//                        0 ->{ sendMsg(sender,"設定完了！${args[2]}時間追加しました") }
//                        1 ->{ sendMsg(sender,"存在しないプレイヤーです")}
//                    }
//
//                })
//            }

            "on" ->{
                if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return true

                isEnable = true

                Man10Bank.instance.config.set(Config.SERVER_LOAN_ENABLE,true)
                Man10Bank.instance.saveConfig()
            }

            "off" ->{
                if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return true

                Man10Bank.instance.config.set(Config.SERVER_LOAN_ENABLE,false)
                Man10Bank.instance.saveConfig()
            }


        }
        return false
    }

}
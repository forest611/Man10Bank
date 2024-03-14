package red.man10.man10bank.loan

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank.Companion.coroutineScope
import red.man10.man10bank.Permissions
import red.man10.man10bank.api.APIServerLoan
import red.man10.man10bank.api.APIServerLoan.ServerLoanProperty
import red.man10.man10bank.status.StatusManager
import red.man10.man10bank.util.Utility.format
import red.man10.man10bank.util.Utility.loggerInfo
import red.man10.man10bank.util.Utility.msg
import red.man10.man10bank.util.Utility.prefix
import java.lang.Math.floor

object ServerLoan : CommandExecutor{

    private lateinit var property : ServerLoanProperty

    suspend fun setup(){
        property = APIServerLoan.property()
        loggerInfo("リボの設定値を読み込みました")
        loggerInfo("支払い期間:${property.paymentInterval}")
        loggerInfo("一日あたりの利息:${property.dailyInterest}")
        loggerInfo("最小貸出可能額:${property.minimumAmount}")
        loggerInfo("最大貸出可能額:${property.maximumAmount}")
    }

    private suspend fun checkAmount(p:Player){
        val maxLoan = APIServerLoan.getBorrowableAmount(p.uniqueId)
        msg(p,"§f§l貸し出し可能上限額:§e§l${format(maxLoan)}円(最大:${format(property.maximumAmount)}円)")
    }
    private suspend fun setPayment(p:Player,amount: Double){
        val data = APIServerLoan.getInfo(p.uniqueId)
        if (data == null){
            msg(p,"§a§lあなたはリボを利用したことがありません")
            return
        }
        val result = APIServerLoan.setPaymentAmount(p.uniqueId,amount)

        if (result){
            msg(p,"§e§l支払額を${format(data.payment_amount)}円に変更しました")
            return
        }
        msg(p,"§e§l変更失敗。時間をおいてやり直してください")
    }

    private suspend fun addPaymentDay(p:Player,day:Int){
        val result = APIServerLoan.addPaymentDay(day)
        if (result){
            msg(p,"設定完了")
            return
        }
        msg(p,"設定失敗")
    }

    private suspend fun showBorrowMessage(p:Player,amount:Double){

        if (!StatusManager.status.enableServerLoan){
            msg(p,"現在新規貸し出しは行っておりません。")
            return
        }
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

        val minPaymentAmount = (borrowing + amount )* property.paymentInterval * property.dailyInterest

        msg(p,"§b§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")
        msg(p,"§e§kXX§b§lMan10リボ§e§kXX")
        msg(p,"§b貸し出される金額:${format(amount)}")
        msg(p,"§b現在の利用額:${format(borrowing)}")
        msg(p,"§c利息の計算方法:§l<利用額>x<金利>x<最後に支払ってからの日数>")
        msg(p,"§c※支払額から利息を引いた額が返済に充てられます")
        msg(p,"§b${property.paymentInterval}日ごとに最低${format(minPaymentAmount)}円支払う必要があります")
        p.sendMessage(button)
        msg(p,"§b§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")

    }

    private suspend fun borrow(p: Player, amount:Double){

        if (!StatusManager.status.enableServerLoan){
            msg(p,"現在新規貸し出しは行っておりません。")
            return
        }

        val ret = APIServerLoan.borrow(p.uniqueId,amount)

        if (ret == APIServerLoan.BorrowResult.Failed){
            msg(p,"§c§lリボの借入に失敗しました！")
            return
        }

        if (ret == APIServerLoan.BorrowResult.FirstSuccess){
            msg(p,"""
                §e§l[重要] 返済について
                §c§lMan10リボは、借りた日から${property.paymentInterval}日ずつ銀行から引き落とされます
                §c§lお支払いができなかった場合、スコアの減少などのペナルティがあるので、
                §c§l必ず銀行にお金を入れておくようにしましょう。
                §c§lまた、/mrevo payment <金額>で引き落とす額を設定できます。
            """.trimIndent())
        }

        msg(p,"§a§l${format(amount)}円借りました！")
        msg(p,"§a§l銀行に支払額を入れておいてください")
    }

    private suspend fun pay(p:Player,amount:Double){
        val result = APIServerLoan.pay(p.uniqueId,amount)

        when(result){
            APIServerLoan.PaymentResult.NotLoan -> {
                msg(p,"あなたは借金をしていません")

            }
            APIServerLoan.PaymentResult.Success -> {
                msg(p,"§a§l支払い成功！")
            }
            APIServerLoan.PaymentResult.NotEnoughMoney ->{
                msg(p,"§c§l支払い失敗！銀行の残高が足りない可能性があります")
            }
        }
    }

    private suspend fun payAll(p:Player){
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
                /mrevo pay : 返済する
                /mrevo payall : 一括返済する
            """.trimIndent())

            if (sender.hasPermission(Permissions.SERVER_LOAN_OP)){
                msg(sender,"""
                /mrevo addtime <day> : 全プレイヤーの支払日を指定日数遅らせる
                =============================================
                §c支払い期間:${property.paymentInterval}
                §c一日あたりの利息:${property.dailyInterest}
                §c最小貸出可能額:${property.minimumAmount}
                §c最大貸出可能額:${property.maximumAmount}                    
                """.trimIndent())
            }

            return true
        }

        when(args[0]){

            "check" ->{
                coroutineScope.launch {
                    checkAmount(sender)
                }
            }

            "borrow" ->{

                if (args.size != 2)return true

                if (!sender.hasPermission(Permissions.SERVER_LOAN_USER)){
                    msg(sender,"あなたはまだMan10リボを使うことができません")
                    return true
                }

                val amount = args[1].toDoubleOrNull()?:return true

                coroutineScope.launch {
                    showBorrowMessage(sender,amount)
                }
            }

            "confirm" ->{

                val amount = args[1].toDoubleOrNull()?:return true

                coroutineScope.launch { borrow(sender,amount) }
            }

            "payment" ->{

                if (args.size != 2)return true

                val amount = args[1].toDoubleOrNull()?:return true

                coroutineScope.launch {
                    setPayment(sender,amount)
                }

            }

            //一部支払い
            "pay" ->{
                if (args.size != 2)return true

                val amount = args[1].toDoubleOrNull()?:return true

                coroutineScope.launch {
                    pay(sender,amount)
                }
            }

            //全額支払い
            "payall" ->{
                if (args.size != 1)return true

                coroutineScope.launch {
                    payAll(sender)
                }
            }

            "addtime" ->{//mrevo addtime <hour>

                if (!sender.hasPermission(Permissions.SERVER_LOAN_OP))return true

                if (args.size != 2){
                    msg(sender,"/mrevo addtime <day>")
                    return true
                }
                val time = args[1].toInt()

                coroutineScope.launch {
                    addPaymentDay(sender,time)
                }
            }
        }
        return false
    }

}
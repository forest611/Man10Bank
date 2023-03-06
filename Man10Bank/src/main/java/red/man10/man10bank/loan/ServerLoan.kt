package red.man10.man10bank.loan

import org.bukkit.entity.Player
import red.man10.man10bank.api.APIServerLoan
import red.man10.man10bank.util.Utility
import java.util.Calendar
import java.util.Date
import java.util.UUID

object ServerLoan {
    fun setPayment(p:Player,amount: Double){
        val data = APIServerLoan.getInfo(p.uniqueId)
        if (data == null){
            Utility.msg(p,"§a§lあなたはリボを利用したことがありません")
            return
        }
        data.payment_amount = amount

        val ret = APIServerLoan.setInfo(data)

        if (ret == "Successful"){
            Utility.msg(p,"§e§l支払額を${Utility.format(amount)}円に変更しました")
            return
        }
        Utility.msg(p,"§e§l変更失敗。時間をおいてやり直してください")
    }

    fun borrow(p: Player, amount:Double){
        val ret = APIServerLoan.borrow(p.uniqueId,amount)

        if (ret != "Successful"){
            Utility.msg(p,"§c§lリボを借りるのに失敗しました！")
            return
        }

        Utility.msg(p,"§a§l${Utility.format(amount)}円借りました！")
        Utility.msg(p,"§a§l銀行に支払額を入れておいてください")
    }

    fun pay(p:Player,amount:Double){
        val ret = APIServerLoan.pay(p.uniqueId,amount)

        if (!ret){
            Utility.msg(p,"§c§l支払い失敗！銀行の残高が足りない可能性があります")
            return
        }

        Utility.msg(p,"§a§l支払い成功！")
    }

}
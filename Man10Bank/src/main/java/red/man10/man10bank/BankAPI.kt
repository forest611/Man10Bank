package red.man10.man10bank

import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.api.APIBank
import red.man10.man10bank.api.APIServerLoan
import java.util.*

class BankAPI(private val plugin: JavaPlugin) {

    /**
     * 出金リクエストを送る
     */
    fun withdraw(uuid: UUID,amount:Double,note:String,displayNote:String):Boolean{
        val result = APIBank.takeBank(APIBank.TransactionData(
            uuid.toString(),
            amount,
            plugin.name,
            note,
            displayNote
        ))

        return result == APIBank.BankResult.SUCCESSFUL
    }

    /**
     * 入金リクエストを送る
     */
    fun deposit(uuid: UUID,amount:Double,note:String,displayNote:String):Boolean{
        val result = APIBank.addBank(APIBank.TransactionData(
            uuid.toString(),
            amount,
            plugin.name,
            note,
            displayNote
        ))

        return result == APIBank.BankResult.SUCCESSFUL
    }

    /**
     * 銀行金額を取得する
     */
    fun getBank(uuid: UUID):Double{
        return APIBank.getBalance(uuid)
    }

    /**
     * リボの金額を取得する
     */
    fun getServerLoan(uuid: UUID):Double{
        return APIServerLoan.getInfo(uuid)?.borrow_amount?:0.0
    }

    fun asyncDeposit(uuid: UUID,amount: Double,note: String,displayNote: String,callback:(Boolean)->Unit){
        Man10Bank.async.execute {
            val ret = deposit(uuid, amount, note, displayNote)
            callback.invoke(ret)
        }
    }

    fun asyncWithdraw(uuid: UUID,amount: Double,note: String,displayNote: String,callback:(Boolean)->Unit){
        Man10Bank.async.execute {
            val ret = withdraw(uuid, amount, note, displayNote)
            callback.invoke(ret)
        }
    }

    fun asyncGetBalance(uuid:UUID,callback: (Double)->Unit){
        Man10Bank.async.execute {
            callback.invoke(getBank(uuid))
        }
    }


//    @Deprecated("displayNoteが設定できない", ReplaceWith("DisplayNoteつき"),DeprecationLevel.WARNING)
//    fun deposit(uuid: UUID,amount: Double,note: String){
//        deposit(uuid,amount, note,note)
//    }
//
//    @Deprecated("displayNoteが設定できない", ReplaceWith("DisplayNoteつき"),DeprecationLevel.WARNING)
//    fun withdraw(uuid:UUID,amount:Double,note:String):Boolean{
//        return withdraw(uuid,amount, note,note)
//    }

}
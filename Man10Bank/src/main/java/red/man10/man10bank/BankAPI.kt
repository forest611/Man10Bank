package red.man10.man10bank

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.Man10Bank.Companion.coroutineScope
import red.man10.man10bank.api.APIBank
import red.man10.man10bank.api.APIServerLoan
import java.util.*

class BankAPI(private val plugin: JavaPlugin) {

    /**
     * 出金リクエストを送る
     */
    fun withdraw(uuid: UUID,amount:Double,note:String,displayNote:String):Boolean {
        val result  = runBlocking {
            APIBank.takeBalance(APIBank.TransactionData(
                uuid.toString(),
                amount,
                plugin.name,
                note,
                displayNote
            ))
        }

        return result == APIBank.BankResult.Successful
    }

    /**
     * 入金リクエストを送る
     * @param ver オーバーロードのための仮引数
     */
    fun deposit(uuid: UUID,amount:Double,note:String,displayNote:String,ver:Int = 1):Boolean{
        val result = runBlocking {
            APIBank.addBalance(APIBank.TransactionData(
                uuid.toString(),
                amount,
                plugin.name,
                note,
                displayNote
            ))
        }

        return result == APIBank.BankResult.Successful
    }

    /**
     * 旧バージョン対応モデル
     */
    @Deprecated("リターン値のあるバージョンを使用すること")
    fun deposit(uuid: UUID,amount:Double,note:String,displayNote:String){
        coroutineScope.launch {
            APIBank.addBalance(APIBank.TransactionData(
                uuid.toString(),
                amount,
                plugin.name,
                note,
                displayNote
            ))
        }
    }


    /**
     * 銀行金額を取得する
     */
    fun getBank(uuid: UUID):Double = runBlocking { APIBank.getBalance(uuid) }


    /**
     * リボの金額を取得する
     */
    fun getServerLoan(uuid: UUID):Double = runBlocking { APIServerLoan.getInfo(uuid)?.borrow_amount?:0.0 }

    /**
     * 非同期で入金する
     * 入金完了するとコールバックが呼ばれる
     */
    fun asyncDeposit(uuid: UUID,amount: Double,note: String,displayNote: String,callback:(Boolean)->Unit) = coroutineScope.launch {
        val ret = deposit(uuid, amount, note, displayNote,1)
        callback.invoke(ret)
    }

    /**
     * 非同期で出勤する
     * 出金完了するとコールバックが呼ばれる
     */
    fun asyncWithdraw(uuid: UUID,amount: Double,note: String,displayNote: String,callback:(Boolean)->Unit) = coroutineScope.launch {
        val ret = withdraw(uuid, amount, note, displayNote)
        callback.invoke(ret)
    }


    /**
     * 非同期で所持金を取得
     * 取得した結果はコールバック関数で取得
     */
    fun asyncGetBalance(uuid:UUID,callback: (Double)->Unit) = coroutineScope.launch { callback.invoke(getBank(uuid)) }



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
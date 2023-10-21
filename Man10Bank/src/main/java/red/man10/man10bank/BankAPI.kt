package red.man10.man10bank

import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.api.APIBank
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
    fun deposit(uuid: UUID,amount:Double,note:String,displayNote:String){
        val result = APIBank.addBank(APIBank.TransactionData(
            uuid.toString(),
            amount,
            plugin.name,
            note,
            displayNote
        ))

//        return result == APIBank.BankResult.SUCCESSFUL
    }

    @Deprecated("displayNoteが設定できない", ReplaceWith("DisplayNoteつき"),DeprecationLevel.WARNING)
    fun deposit(uuid: UUID,amount: Double,note: String){
        deposit(uuid,amount, note,note)
    }

    @Deprecated("displayNoteが設定できない", ReplaceWith("DisplayNoteつき"),DeprecationLevel.WARNING)
    fun withdraw(uuid:UUID,amount:Double,note:String):Boolean{
        return withdraw(uuid,amount, note,note)
    }

}
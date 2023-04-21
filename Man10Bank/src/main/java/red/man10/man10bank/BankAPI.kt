package red.man10.man10bank

import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.api.APIBank
import java.util.*

class BankAPI(private val plugin: JavaPlugin) {

    /**
     * 出金リクエストを送る
     */
    fun withdraw(uuid: UUID,amount:Double,note:String,displayNote:String):Boolean{
        val ret = APIBank.takeBank(APIBank.TransactionData(
            uuid.toString(),
            amount,
            plugin.name,
            note,
            displayNote
        ))

        return Result.valueOf(ret) == Result.Successful
    }

    /**
     * 入金リクエストを送る
     */
    fun deposit(uuid: UUID,amount:Double,note:String,displayNote:String) : Boolean{
        val ret = APIBank.addBank(APIBank.TransactionData(
            uuid.toString(),
            amount,
            plugin.name,
            note,
            displayNote
        ))

        return Result.valueOf(ret) == Result.Successful
    }


    enum class Result{
        Successful,
        NotEnoughMoney,
        NotFoundBank,
    }

}
package red.man10.man10bank

import org.bukkit.Bukkit
import red.man10.man10bank.api.APIBank

object Status {

    var enableDealBank = false
    var enableATM = false
    var enableCheque = false
    var enableLocalLoan = false
    var enableServerLoan = false

    private var timerThread = Thread()
    private const val checkSpanSecond = 10
    fun asyncSendStatus(){
        val data = StatusData()
        //TODO:動作確認必須
        data::class.java.fields.forEach { value ->
            data::class.java.getField(value.name).set(Boolean::class.java,
                Status::class.java.getField(value.name).get(Boolean::class.java))
        }

        Man10Bank.async.execute {
            APIBank.setStatus(data)
        }
    }

    private fun getStatus(){
        val data = APIBank.getStatus()
        //TODO:動作確認必須
        data::class.java.fields.forEach { value ->
            Status::class.java.getField(value.name).set(Boolean::class.java,value.get(Boolean::class.java))
        }
    }

    fun startStatusTimer(){
        timerThread = Thread{
            Bukkit.getLogger().info("ステータスチェク処理を走らせます")
            try {
                getStatus()
                Thread.sleep(1000L * checkSpanSecond)
            }catch (e:InterruptedException){
                Bukkit.getLogger().info("ステータスチェック処理を終了")
                return@Thread
            }
        }
        timerThread.start()
    }

    fun stopStatusTimer(){
        timerThread.interrupt()
    }

    data class StatusData(
        var enableDealBank:Boolean = false,
        var enableATM:Boolean = false,
        var enableCheque:Boolean = false,
        var enableLocalLoan:Boolean = false,
        var enableServerLoan:Boolean = false,
    )

}
package red.man10.man10bank

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import red.man10.man10bank.api.APIBank
import red.man10.man10bank.util.Utility.msg

//サーバーの接続状況や各機能のon/offを確認するクラス
object Status : CommandExecutor{

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
        //TODO:鯖への接続確認もここでする
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

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (label != "bankstatus")return false
        if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return false
        if (args.isNullOrEmpty()){
            msg(sender,"現在の稼働状況")
            msg(sender,"Man10BankServer:${Man10Bank.isEnableServer()}")
            Status::class.java.fields.forEach { value ->
                msg(sender,"${value.name}:${value.get(value.type)}")
            }
            return true
        }

        if (args[0] == "set" && args.size == 3){
            try {
                Status::class.java.getField(args[1]).set(Boolean::class.java,args[2].toBoolean())
                asyncSendStatus()
                msg(sender,"設定完了")
            }catch (e:Exception){
                msg(sender,"引数に問題あり")
            }
        }

        return true
    }

}
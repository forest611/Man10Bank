package red.man10.man10bank.status

import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import red.man10.man10bank.Config
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Permissions
import red.man10.man10bank.api.APIBase
import red.man10.man10bank.api.APIStatus
import red.man10.man10bank.api.Status
import red.man10.man10bank.util.Utility.msg

//サーバーの接続状況や各機能のon/offを確認するクラス

object StatusManager : CommandExecutor{

    var status = Status()

    private val scope = CoroutineScope(Dispatchers.IO)

    private fun asyncSendStatus(){
        scope.launch{
            APIStatus.setStatus(status)
        }
    }

    private suspend fun getStatus(){
        status = APIStatus.getStatus()
    }

    fun startStatusTask(){

        scope.launch {
            Bukkit.getLogger().info("ステータスチェク処理を走らせます")
            while (isActive){
                try {
                    delay(1000L * Config.statusCheckSeconds)
                    getStatus()
                }catch (e:CancellationException){
                    Bukkit.getLogger().info("ステータスチェック処理を中断")
                    return@launch
                }catch (_:Exception){}
            }
            Bukkit.getLogger().info("ステータスチェック処理の終了")
        }
    }

    fun cancelScope(){
        scope.cancel()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (label != "bankstatus")return false
        if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return false
        if (args.isNullOrEmpty()){
            msg(sender,"現在の稼働状況")
            msg(sender,"BankServer:${status.enableAccessUserServer}")
            msg(sender,"ネットワーク接続:${APIBase.enable}")
            msg(sender,"===================================")
            msg(sender,"${StatusName.DealBank.name}:${status.enableDealBank}")
            msg(sender,"${StatusName.Atm.name}:${status.enableATM}")
            msg(sender,"${StatusName.Cheque.name}:${status.enableCheque}")
            msg(sender,"${StatusName.ServerLoan.name}:${status.enableServerLoan}")
            msg(sender,"${StatusName.LocalLoan.name}:${status.enableLocalLoan}")
            msg(sender,"===================================")
            msg(sender,"APIServerは/bankstatus reload で再接続")
            msg(sender,"各機能は/bankstatus set <上記識別名/All> <true/false> でon/off切り替え")
            msg(sender,"")
            msg(sender,"GitHub: https://github.com/forest611/Man10Bank")
            msg(sender,"Author:Jin Morikawa")

            return true
        }

        if (args[0] == "reload"){
            if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return true

            msg(sender,"§c§lリロードを開始します")

            CoroutineScope(Dispatchers.Default).launch {
                msg(sender,"§c§lシステム終了・・・")
                Man10Bank.systemClose()
                msg(sender,"§c§lシステム起動・・・")
                Man10Bank.systemSetup()
                msg(sender,"§c§lシステムリロード完了")
            }

        }

        if (args[0] == "set" && args.size == 3){
            try {

                val value = args[2].toBoolean()

                when(StatusName.valueOf(args[1])){
                    StatusName.All -> {
                        if (value) status.allTrue() else status.allFalse()
                    }
                    StatusName.DealBank -> status.enableDealBank = value
                    StatusName.Atm -> status.enableATM = value
                    StatusName.Cheque -> status.enableCheque = value
                    StatusName.LocalLoan -> status.enableLocalLoan = value
                    StatusName.ServerLoan -> status.enableServerLoan = value
//                    else ->{
//                        msg(sender,"無効なステータス")
//                    }
                }

                asyncSendStatus()
                msg(sender,"設定完了")
            }catch (e:Exception){
                msg(sender,e.message?:"")
                msg(sender,"引数に問題あり")
            }
        }

        return true
    }

    enum class StatusName{
        All,
        DealBank,
        Atm,
        Cheque,
        LocalLoan,
        ServerLoan,
    }

}
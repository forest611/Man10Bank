package red.man10.man10bank

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import red.man10.man10bank.api.APIBank
import red.man10.man10bank.util.Utility.msg

//サーバーの接続状況や各機能のon/offを確認するクラス
class Status : CommandExecutor{

    var enableDealBank = false
    var enableATM = false
    var enableCheque = false
    var enableLocalLoan = false
    var enableServerLoan = false

    companion object{
        var status = Status()

        private var timerThread = Thread()

        private fun asyncSendStatus(){
            Man10Bank.threadPool.execute {
                APIBank.setStatus(status)
            }
        }

        private fun getStatus(){
            status = APIBank.getStatus()
        }

        fun startStatusTimer(){
            timerThread = Thread{
                Bukkit.getLogger().info("ステータスチェク処理を走らせます")
                while (true){
                    try {
                        getStatus()
                        Thread.sleep(1000L * Config.statusCheckSeconds)
                    }catch (e:InterruptedException){
                        Bukkit.getLogger().info("ステータスチェック処理を中断")
                        return@Thread
                    }
                }
            }
            timerThread.start()
        }

        fun stopStatusTimer(){
            timerThread.interrupt()
        }

    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (label != "bankstatus")return false
        if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return false
        if (args.isNullOrEmpty()){
            msg(sender,"現在の稼働状況")
            msg(sender,"APIServer:${Man10Bank.isEnableServer()}")
            msg(sender,"===================================")
            msg(sender,"${StatusName.DEAL_BANK.name}:$enableDealBank")
            msg(sender,"${StatusName.ATM.name}:$enableATM")
            msg(sender,"${StatusName.CHEQUE.name}:$enableCheque")
            msg(sender,"${StatusName.SERVER_LOAN.name}:$enableServerLoan")
            msg(sender,"${StatusName.LOCAL_LOAN.name}:$enableLocalLoan")
            msg(sender,"===================================")
            msg(sender,"APIServerは/man10bank reload で再接続")
            msg(sender,"各機能は/bankstatus set <上記名> <true/false> でon/off切り替え")

            return true
        }

        if (args[0] == "reload"){
            if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return true

            Thread{
                msg(sender,"§c§lシステム終了・・・")
                Man10Bank.systemClose()
                msg(sender,"§c§lシステム起動・・・")
                if (!Man10Bank.systemSetup()){
                    msg(sender,"§c§l§nAPIサーバーへの接続に失敗")
                }
                msg(sender,"§c§lシステムリロード完了")
//                    Man10Bank.open()
            }.start()
        }

        if (args[0] == "set" && args.size == 3){
            try {

                val value = args[2].toBoolean()

                when(StatusName.valueOf(args[1])){
                    StatusName.ALL -> {
                        enableDealBank = value
                        enableATM = value
                        enableCheque = value
                        enableLocalLoan = value
                        enableServerLoan = value
                    }
                    StatusName.DEAL_BANK -> enableDealBank = value
                    StatusName.ATM -> enableATM = value
                    StatusName.CHEQUE -> enableCheque = value
                    StatusName.LOCAL_LOAN -> enableLocalLoan = value
                    StatusName.SERVER_LOAN -> enableServerLoan = value
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
        ALL,
        DEAL_BANK,
        ATM,
        CHEQUE,
        LOCAL_LOAN,
        SERVER_LOAN
    }

}
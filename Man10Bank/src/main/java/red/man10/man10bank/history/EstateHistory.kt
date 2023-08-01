package red.man10.man10bank.history

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.api.APIHistory
import red.man10.man10bank.api.APIServerLoan
import red.man10.man10bank.bank.ATM
import red.man10.man10bank.cheque.Cheque
import red.man10.man10bank.util.Utility.format
import red.man10.man10bank.util.Utility.msg
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

object EstateHistory {

    fun asyncShowBalanceTop(p: CommandSender, page:Int){

        Man10Bank.async.execute {
            val array = APIHistory.getBalanceTop(10,page*10)

            msg(p,"§6§k§lXX§e§l富豪トップ${(page+1)*10}§6§k§lXX")

            var i = (page+1)*10-9
            array.forEach {data ->
                msg(p,"§7§l${i}.§b§l${data.player} : §e§l${format(data.total)}円")
                i++
            }

        }
    }

    fun asyncShowLoanTop(p:CommandSender,page:Int){

        Man10Bank.async.execute {
            val array = APIHistory.getLoanTop(10,page*10)

            msg(p,"§6§k§lXX§c§l借金トップ${(page+1)*10}§6§k§lXX")

            var i = (page+1)*10-9
            array.forEach {data ->
                msg(p,"§7§l${i}.§c§l${data.player} : §4§l${format(data.borrow_amount)}円")
                i++
            }
        }
    }

    fun asyncShowEstate(p:CommandSender,uuid:UUID){

        Man10Bank.async.execute {
            val data = APIHistory.getUserEstate(uuid)
            val nextDate = APIServerLoan.nextPayDate(uuid)
            val revoInfo = APIServerLoan.getInfo(uuid)
            val isLoser = APIServerLoan.isLoser(uuid)

            if (data==null){
                msg(p,"§cプレイヤーが見つかりません")
                return@execute
            }

            msg(p,"§c§l==========${data.player}のお金==========")
            msg(p," §c§l電子マネー:  §e§l${format(data.vault)}")
            msg(p," §c§l銀行:  §e§l${format(data.bank)}")
            msg(p," §c§l現金:  §e§l${format(data.cash)}")
            msg(p," §c§l小切手など:  §e§l${format(data.estete)}")
            if (revoInfo != null){
                msg(p," §c§lMan10リボ:  §e§l${format(data.loan)}")
                msg(p," §c§l支払額:  §e§l${format(revoInfo.payment_amount)}")
                msg(p," §c§l失敗回数:  §e§l${revoInfo.failed_payment}回")
                msg(p," §c§lルーザー:  §e§l${isLoser}")
                msg(p," §c§l次回支払日:  §e§l${nextDate!!.format(DateTimeFormatter.ISO_LOCAL_DATE)}")
            }
        }
    }

    fun asyncShowServerEstate(p:CommandSender){

        Man10Bank.async.execute {
            val data = APIHistory.getServerEstate()

            if (data == null){
                msg(p,"取得失敗")
                return@execute
            }

            msg(p,"§e§l========サーバー資産状況========")
            msg(p,"§b§l電子マネー:  §e§l${format(data.vault)}")
            msg(p,"§b§l銀行:  §e§l${format(data.bank)}")
            msg(p,"§b§l現金:  §e§l${format(data.cash)}")
            msg(p,"§b§lその他資産:  §e§l${format(data.estete)}")
            msg(p,"§b§l合計:  §e§l${format(data.total)}")
            msg(p,"§c§lまんじゅうリボ:  §e§l${format(data.loan)}")
        }
    }

    fun asyncAddEstate(p:Player){

        Man10Bank.async.execute {
            val uuid = p.uniqueId
            //銀行、ローン、トータルは鯖側で計算する
            val data = APIHistory.EstateTable(
                0,
                p.name,
                uuid.toString(),
                LocalDateTime.now(),
                Man10Bank.vault.getBalance(uuid),
                0.0,
                ATM.getCash(p),
                Cheque.getChequeInInventory(p),
                0.0,
                0.0,
                0.0,
                0.0
            )

            APIHistory.addUserEstate(data)

        }
    }

    fun addVaultLog(){


    }


}
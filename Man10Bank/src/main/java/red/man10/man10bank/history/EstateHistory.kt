package red.man10.man10bank.history

import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.api.APIHistory
import red.man10.man10bank.bank.ATM
import red.man10.man10bank.cheque.Cheque
import java.time.LocalDateTime
import java.util.*

object EstateHistory {

    fun showBalanceTop(p: Player, page:Int){

        val array = APIHistory.getBalanceTop(page)

    }

    fun showEstate(uuid:UUID){

        val data = APIHistory.getUserEstate(uuid)


    }

    fun asyncAddEstate(p:Player){

        Man10Bank.thread.execute {
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
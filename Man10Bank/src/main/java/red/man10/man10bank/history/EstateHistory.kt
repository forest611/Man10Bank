package red.man10.man10bank.history

import org.bukkit.entity.Player
import red.man10.man10bank.api.History
import java.util.UUID

object EstateHistory {

    fun showBalanceTop(p: Player, page:Int){

        val array = History.getBalanceTop(page)

    }

    fun showEstate(uuid:UUID){

        val data = History.getUserEstate(uuid)


    }

    fun addVaultLog(){


    }


}
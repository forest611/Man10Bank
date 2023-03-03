package red.man10.man10bank.history

import org.bukkit.entity.Player
import red.man10.man10bank.api.APIHistory
import java.util.UUID

object EstateHistory {

    fun showBalanceTop(p: Player, page:Int){

        val array = APIHistory.getBalanceTop(page)

    }

    fun showEstate(uuid:UUID){

        val data = APIHistory.getUserEstate(uuid)


    }

    fun addVaultLog(){


    }


}
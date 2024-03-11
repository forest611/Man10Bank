package red.man10.man10bank.api

import org.bukkit.entity.Player
import java.util.*

object APICheque {

    private const val PATH = "/cheque/"

    suspend fun create(uuid: UUID, amount: Double, note: String): Int {
        APIBase.get("${PATH}create?uuid=${uuid}&amount=$amount&note=${note}").use{
            if (it.code != 200){
                return 0
            }
            return it.body?.string()?.toIntOrNull()?:0
        }
    }

    suspend fun use(id: Int,p:Player):Double{
        APIBase.get("${PATH}use?uuid=${p.uniqueId}&id=${id}").use{
            if (it.code != 200){
                return 0.0
            }
            return it.body?.string()?.toDoubleOrNull()?:0.0
        }
    }

    suspend fun amount(id:Int):Double{
        APIBase.get("${PATH}amount?id=${id}").use{
            return it.body?.string()?.toDoubleOrNull()?:0.0
        }
    }

}
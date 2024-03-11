package red.man10.man10bank.api

import org.bukkit.entity.Player
import java.util.UUID

object APICheque {

    private const val PATH = "/cheque/"

    fun create(uuid: UUID, amount: Double, note: String): Int {
        var id = 0
        APIBase.get("${PATH}create?uuid=${uuid}&amount=$amount&note=${note}"){
            if (it.code != 200){
                return@get
            }
            id = it.body?.string()?.toIntOrNull()?:0
        }
        return id
    }

    fun use(id: Int,p:Player):Double{
        var amount = 0.0
        APIBase.get("${PATH}use?uuid=${p.uniqueId}&id=${id}"){
            if (it.code != 200){
                return@get
            }
            amount = it.body?.string()?.toDoubleOrNull()?:0.0
        }
        return amount
    }

    fun amount(id:Int):Double{
        var amount = 0.0
        APIBase.get("${PATH}amount?id=${id}"){
            amount = it.body?.string()?.toDoubleOrNull()?:0.0
        }
        return amount
    }

}
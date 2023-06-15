package red.man10.man10bank.api

import red.man10.man10bank.api.APIBase.getRequest
import java.util.UUID

object APICheque {

    private const val apiRoute = "/cheque/"

    fun create(uuid: UUID, amount: Double, note: String, isOp: Boolean = false): Int {

        val result = getRequest("${apiRoute}create?uuid=${uuid}&amount=${amount}&note=${note}&isOp=${isOp}")
        return result?.toIntOrNull() ?: -1
    }

    fun use(id: Int):Double{

        val result = getRequest("${apiRoute}try-use?id=${id}")
        return result?.toDoubleOrNull()?:-1.0
    }

    fun amount(id:Int):Double{
        val result = getRequest("${apiRoute}amount?id=${id}")
        return result?.toDoubleOrNull()?:0.0
    }

}
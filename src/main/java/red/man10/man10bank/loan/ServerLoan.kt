package red.man10.man10bank.loan

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.MySQLManager

object ServerLoan {

    private val mysql = MySQLManager(Man10Bank.plugin,"Man10ServerLoan")

    var scoreMultiplier = 1.0
    var timeMultiplier = 1.0
    var medianMultiplier = 1.0

    fun checkServerLoan(p: Player){

        val list = mutableListOf<Double>()

        val rs = mysql.query("select total from estate_history_tbl where uuid='${p.uniqueId}';")?:return

        while (rs.next()){
            list.add(rs.getDouble("total"))
        }

        rs.close()
        mysql.close()

        list.sort()
        val m = list.size/2

        val median : Double = if (list.size%2 == 0){
            list[m]+list[m+1]/2.0
        }else{
            list[m]
        }

        Bukkit.getLogger().info("median:${median} m:${m}")

        Bukkit.getLogger().info("scoreMultiplier${scoreMultiplier}")
        Bukkit.getLogger().info("timeMultiplier${timeMultiplier}")
        Bukkit.getLogger().info("medianMultiplier${medianMultiplier}")


    }

}
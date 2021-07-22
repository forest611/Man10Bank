package red.man10.man10bank.history

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import red.man10.man10bank.Bank
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.MySQLManager
import red.man10.man10bank.atm.ATMData
import java.util.*
import kotlin.collections.HashMap

object EstateData {

    private val mysql = MySQLManager(Man10Bank.plugin,"Man10BankEstateHistory")

    //ヒストリーに追加
    private fun addEstateHistory(p:Player, vault:Double, bank:Double, estate:Double){

        val uuid = p.uniqueId

        val rs = mysql.query("SELECT * FROM estate_history_tbl WHERE uuid='${p.uniqueId}' ORDER BY date DESC LIMIT 1")

        if (rs==null || !rs.next()){
            mysql.execute("INSERT INTO estate_history_tbl (uuid, date, player, vault, bank, estate, total) " +
                    "VALUES ('${uuid}', now(), '${p.name}', ${vault}, ${bank}, ${estate}, ${vault+bank+estate})")

            return
        }

        val lastVault = rs.getDouble("vault")
        val lastBank = rs.getDouble("bank")
        val lastEstate = rs.getDouble("estate")

        mysql.close()
        rs.close()

        if (vault != lastVault || bank != lastBank || estate != lastEstate){
            mysql.execute("INSERT INTO estate_history_tbl (uuid, date, player, vault, bank, estate, total) " +
                    "VALUES ('${uuid}', now(), '${p.name}', ${vault}, ${bank}, ${estate}, ${vault+bank+estate})")

        }


//        MySQLManager.mysqlQueue.add("INSERT INTO estate_history_tbl (uuid, date, player, vault, bank, estate, total) " +
//                "VALUES ('${uuid}', now(), '${p.name}', ${vault}, ${bank}, ${estate}, ${vault+bank+estate})")
//
    }

    fun createEstateData(p:Player){

        val rs = mysql.query("SELECT player from estate_tbl where uuid='${p.uniqueId}'")

        if (rs== null || !rs.next()){
            mysql.execute("INSERT INTO estate_tbl (uuid, date, player, vault, bank, estate, total) " +
                    "VALUES ('${p.uniqueId}', now(), '${p.name}', 0, 0, 0, 0)")

        }
    }

    //現在の資産を保存(オンラインのユーザー)
    private fun saveCurrentEstate(){

        val rs = mysql.query("SELECT * FROM estate_tbl ORDER BY date DESC LIMIT 1")?:return

        while (rs.next()){
            val p = Bukkit.getPlayer(UUID.fromString(rs.getString("uuid")))?:continue
            if (!p.isOnline)continue

            val uuid = p.uniqueId

            val vault = Man10Bank.vault.getBalance(uuid)
            val bank = Bank.getBalance(uuid)
            val estate = ATMData.getEnderChestMoney(p) + ATMData.getInventoryMoney(p)

            mysql.execute("UPDATE estate_tbl SET " +
                    "date=now(), player='${p.name}', vault=${vault}, bank=${bank}, estate=${estate}, total=${vault+bank+estate} WHERE uuid='${uuid}'")

            addEstateHistory(p, vault, bank, estate)

        }
    }

    //現在の資産を保存(特定のプレイヤーだけ)
    fun saveCurrentEstate(p:Player){

        val uuid = p.uniqueId

        val vault = Man10Bank.vault.getBalance(uuid)
        val bank = Bank.getBalance(uuid)
        val estate = ATMData.getEnderChestMoney(p) + ATMData.getInventoryMoney(p)

        mysql.execute("UPDATE estate_tbl SET " +
                "date=now(), player='${p.name}', vault=${vault}, bank=${bank}, estate=${estate}, total=${vault+bank+estate} WHERE uuid='${uuid}'")

        addEstateHistory(p, vault, bank, estate)

    }


    private fun addServerHistory(){

        val calender = Calendar.getInstance()
        calender.time = Date()

        val year = calender.get(Calendar.YEAR)
        val month = calender.get(Calendar.MONTH)+1
        val day = calender.get(Calendar.DAY_OF_MONTH)
        val hour = calender.get(Calendar.HOUR_OF_DAY)

        val rs1 = mysql.query("select * from server_estate_history where " +
                "year=$year and month=$month and day=$day and hour=$hour;")

        if (rs1 != null&&rs1.next()){
            rs1.close()
            mysql.close()
            return
        }

        val rs = mysql.query("select sum(vault),sum(bank),sum(estate) from estate_tbl")?:return

        rs.next()

        val vaultSum = rs.getDouble(1)
        val bankSum = rs.getDouble(2)
        val estateSum = rs.getDouble(3)
        val total = vaultSum+bankSum+estateSum

        rs.close()
        mysql.close()

        mysql.execute("INSERT INTO server_estate_history (vault, bank, estate, total,year,month,day,hour, date) " +
                "VALUES ($vaultSum, $bankSum, $estateSum, $total,$year,$month,$day,$hour, now())")

    }

    fun getBalanceTotal():HashMap<String,Double>?{

        val map = HashMap<String,Double>()

        val rs = mysql.query("SELECT vault,bank,estate,total from server_estate_history ORDER BY date DESC LIMIT 1")?:return null
        rs.next()
        map["vault"] = rs.getDouble(1)
        map["bank"] = rs.getDouble(2)
        map["estate"] = rs.getDouble(3)
        map["total"] = rs.getDouble(4)

        rs.close()
        mysql.close()
        return map
    }

    fun getBalanceTop(): MutableList<Pair<String, Double>> {

        val list = mutableListOf<Pair<String,Double>>()

        val rs = mysql.query("SELECT player,total FROM estate_tbl order by total desc limit 10;")?:return list

        while (rs.next()){

            val p = rs.getString("player")
            val total = rs.getDouble("total")

            list.add(Pair(p,total))
        }

        rs.close()
        mysql.close()

        return list
    }

    fun historyThread(){

        while (true){
//            saveCurrentEstate()

            if (Man10Bank.loggingServerHistory){
                addServerHistory()
            }
            Bukkit.getLogger().info("SavedServerEstateHistory")

            Thread.sleep(600000)
        }
    }
}
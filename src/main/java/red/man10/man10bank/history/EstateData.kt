package red.man10.man10bank.history

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import red.man10.man10bank.Bank
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.format
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.MySQLManager
import red.man10.man10bank.MySQLManager.Companion.mysqlQueue
import red.man10.man10bank.atm.ATMData
import red.man10.man10bank.cheque.Cheque
import java.util.*
import kotlin.collections.HashMap

object EstateData {

    private val mysql = MySQLManager(Man10Bank.plugin,"Man10BankEstateHistory")

    //ヒストリーに追加
    private fun addEstateHistory(p:Player, vault:Double, bank:Double,cash:Double, estate:Double){

        val uuid = p.uniqueId

        val rs = mysql.query("SELECT * FROM estate_history_tbl WHERE uuid='${p.uniqueId}' ORDER BY date DESC LIMIT 1")

        val total = vault+bank+estate + cash

        if (rs==null || !rs.next()){
            mysqlQueue.add("INSERT INTO estate_history_tbl (uuid, date, player, vault, bank, cash, estate, total) " +
                    "VALUES ('${uuid}', now(), '${p.name}', ${vault}, ${bank},${cash}, ${estate}, ${total})")
            return
        }

        val lastVault = rs.getDouble("vault")
        val lastBank = rs.getDouble("bank")
        val lastCash = rs.getDouble("cash")
        val lastEstate = rs.getDouble("estate")

        mysql.close()
        rs.close()

        if (vault != lastVault || bank != lastBank || estate != lastEstate ||cash!=lastCash){
            mysqlQueue.add("INSERT INTO estate_history_tbl (uuid, date, player, vault, bank, cash, estate, total) " +
                    "VALUES ('${uuid}', now(), '${p.name}', ${vault}, ${bank},${cash}, ${estate}, ${total})")
        }


//        MySQLManager.mysqlQueue.add("INSERT INTO estate_history_tbl (uuid, date, player, vault, bank, estate, total) " +
//                "VALUES ('${uuid}', now(), '${p.name}', ${vault}, ${bank}, ${estate}, ${vault+bank+estate})")
//
    }

    fun createEstateData(p:Player){

        val rs = mysql.query("SELECT player from estate_tbl where uuid='${p.uniqueId}'")

        if (rs== null || !rs.next()){
            mysql.execute("INSERT INTO estate_tbl (uuid, date, player, vault, bank, cash, estate, total) " +
                    "VALUES ('${p.uniqueId}', now(), '${p.name}', 0, 0, 0, 0, 0)")

        }
    }

//    //現在の資産を保存(オンラインのユーザー)
//    private fun saveCurrentEstate(){
//
//        val rs = mysql.query("SELECT * FROM estate_tbl ORDER BY date DESC LIMIT 1")?:return
//
//        while (rs.next()){
//            val p = Bukkit.getPlayer(UUID.fromString(rs.getString("uuid")))?:continue
//            if (!p.isOnline)continue
//
//            val uuid = p.uniqueId
//
//            val vault = Man10Bank.vault.getBalance(uuid)
//            val bank = Bank.getBalance(uuid)
//            val estate = ATMData.getEnderChestMoney(p) + ATMData.getInventoryMoney(p)
//
//            mysql.execute("UPDATE estate_tbl SET " +
//                    "date=now(), player='${p.name}', vault=${vault}, bank=${bank}, estate=${estate}, total=${vault+bank+estate} WHERE uuid='${uuid}'")
//
//            addEstateHistory(p, vault, bank, estate)
//
//        }
//    }

    //現在の資産を保存(特定のプレイヤーだけ)
    fun saveCurrentEstate(p:Player){

        val uuid = p.uniqueId

        val vault = Man10Bank.vault.getBalance(uuid)
        val bank = Bank.getBalance(uuid)
        val cash = ATMData.getEnderChestMoney(p) + ATMData.getInventoryMoney(p)
        val estate = getEstate(p)
        val total = vault+bank+estate + cash

        mysqlQueue.add("UPDATE estate_tbl SET " +
                "date=now(), player='${p.name}', vault=${vault}, bank=${bank}, cash=${cash}, estate=${estate}, total=${total} WHERE uuid='${uuid}'")

        addEstateHistory(p, vault, bank,cash, estate)

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

        val rs = mysql.query("select sum(vault),sum(bank),sum(cash),sum(estate) from estate_tbl")?:return

        rs.next()

        val vaultSum = rs.getDouble(1)
        val bankSum = rs.getDouble(2)
        val cashSum = rs.getDouble(3)
        val estateSum = rs.getDouble(4)
        val total = vaultSum+bankSum+cashSum+estateSum

        rs.close()
        mysql.close()

        mysql.execute("INSERT INTO server_estate_history (vault, bank, cash, estate, total,year,month,day,hour, date) " +
                "VALUES ($vaultSum, $bankSum,$cashSum, $estateSum, $total,$year,$month,$day,$hour, now())")

    }

    fun getBalanceTotal():HashMap<String,Double>?{

        val map = HashMap<String,Double>()

        val rs = mysql.query("SELECT vault,bank,cash,estate,total from server_estate_history ORDER BY date DESC LIMIT 1")?:return null
        if (!rs.next())return null
        map["vault"] = rs.getDouble(1)
        map["bank"] = rs.getDouble(2)
        map["cash"] = rs.getDouble(3)
        map["estate"] = rs.getDouble(4)
        map["total"] = rs.getDouble(5)

        rs.close()
        mysql.close()
        return map
    }

    fun getBalanceTop(page:Int): MutableList<Pair<String, Double>> {

        val list = mutableListOf<Pair<String,Double>>()

        val rs = mysql.query("SELECT player,total FROM estate_tbl order by total desc limit 10 offset ${(page*10)-10};")?:return list

        while (rs.next()){

            val p = rs.getString("player")
            val total = rs.getDouble("total")

            list.add(Pair(p,total))
        }

        rs.close()
        mysql.close()

        return list
    }

    //TODO:ローンなどもみれるようにする
    fun showOfflineUserEstate(show:Player,p:String){

        val rs  = mysql.query("select * from estate_tbl where player='$p';")?:return

        if (!rs.next())return

        val vault = rs.getDouble("vault")
        val bank = rs.getDouble("bank")
        val cash = rs.getDouble("cash")
        val estate = rs.getDouble("estate")

        sendMsg(show, "§e§l==========${p}のお金(オフライン)==========")

        sendMsg(show, " §b§l電子マネー:  §e§l${format(vault)}円")
        sendMsg(show, " §b§l現金:  §e§l${format(cash)}円")
        sendMsg(show, " §b§l銀行:  §e§l${format(bank)}円")
        sendMsg(show, " §b§lその他の資産:  §e§l${format(estate)}円")

        mysql.close()
        rs.close()

    }

    //その他の資産を返す
    fun getEstate(p:Player):Double{

        var cheque = 0.0

        for (item in p.inventory.contents){
            if (item ==null ||item.type == Material.AIR)continue
            cheque+=Cheque.getChequeAmount(item)
        }

        for (item in p.enderChest.contents){
            if (item ==null ||item.type == Material.AIR)continue
            cheque+=Cheque.getChequeAmount(item)
        }

        return cheque

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
package red.man10.man10bank.history

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import red.man10.man10bank.Bank
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.format
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.MySQLManager
import red.man10.man10bank.MySQLManager.Companion.mysqlQueue
import red.man10.man10bank.atm.ATMData
import red.man10.man10bank.cheque.Cheque
import red.man10.man10bank.history.EstateData.historyThread
import red.man10.man10bank.loan.ServerLoan
import red.man10.man10score.ScoreDatabase
import java.util.*

object EstateData {

    init {
        Bukkit.getLogger().info("StartHistoryThread")
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { historyThread() })
    }
    //ヒストリーに追加
    private fun addEstateHistory(p:Player,struct:EstateStruct){

        val uuid = p.uniqueId

        val mysql = MySQLManager(plugin,"Man10BankEstateHistory")
        val rs = mysql.query("SELECT * FROM estate_history_tbl WHERE uuid='${p.uniqueId}' ORDER BY date DESC LIMIT 1")

        val total = struct.total()

        if (rs==null || !rs.next()){
            mysqlQueue.add("INSERT INTO estate_history_tbl (uuid, date, player, vault, bank, cash, estate,loan, total) " +
                    "VALUES ('${uuid}', now(), '${p.name}', ${struct.vault}, ${struct.bank},${struct.cash}, ${struct.estate},${struct.loan} ,${total})")
            return
        }

        val lastVault = rs.getDouble("vault")
        val lastBank = rs.getDouble("bank")
        val lastCash = rs.getDouble("cash")
        val lastEstate = rs.getDouble("estate")

        mysql.close()
        rs.close()

        if (struct.vault != lastVault || struct.bank != lastBank || struct.estate != lastEstate ||struct.cash!=lastCash){
            mysqlQueue.add("INSERT INTO estate_history_tbl (uuid, date, player, vault, bank, cash, estate,loan, total) " +
                    "VALUES ('${uuid}', now(), '${p.name}', ${struct.vault}, ${struct.bank},${struct.cash}, ${struct.estate},${struct.loan} , ${total})")
        }


//        MySQLManager.mysqlQueue.add("INSERT INTO estate_history_tbl (uuid, date, player, vault, bank, estate, total) " +
//                "VALUES ('${uuid}', now(), '${p.name}', ${vault}, ${bank}, ${estate}, ${vault+bank+estate})")
//
    }

    fun createEstateData(p:Player){

        val mysql = MySQLManager(plugin,"Man10BankEstateHistory")

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

        val struct = EstateStruct()

        struct.vault = Man10Bank.vault.getBalance(uuid)
        struct.bank = Bank.getBalance(uuid)
        struct.cash = ATMData.getEnderChestMoney(p) + ATMData.getInventoryMoney(p)
        struct.estate = getEstate(p)
        struct.loan = ServerLoan.getBorrowingAmount(p)

        mysqlQueue.add("UPDATE estate_tbl SET " +
                "date=now(), player='${p.name}', vault=${struct.vault}, bank=${struct.bank}, cash=${struct.cash}," +
                " estate=${struct.estate},loan=${struct.loan}, total=${struct.total()} WHERE uuid='${uuid}'")

        addEstateHistory(p,struct)

    }


    private fun addServerHistory(){

        val calender = Calendar.getInstance()
        calender.time = Date()

        val year = calender.get(Calendar.YEAR)
        val month = calender.get(Calendar.MONTH)+1
        val day = calender.get(Calendar.DAY_OF_MONTH)
        val hour = calender.get(Calendar.HOUR_OF_DAY)

        val mysql = MySQLManager(plugin,"Man10BankEstateHistory")

        val rs1 = mysql.query("select * from server_estate_history where " +
                "year=$year and month=$month and day=$day and hour=$hour;")

        if (rs1 != null&&rs1.next()){
            rs1.close()
            mysql.close()
            return
        }

        val rs = mysql.query("select sum(vault),sum(bank),sum(cash),sum(estate),sum(loan) from estate_tbl")?:return

        rs.next()

        val vaultSum = rs.getDouble(1)
        val bankSum = rs.getDouble(2)
        val cashSum = rs.getDouble(3)
        val estateSum = rs.getDouble(4)
        val loanSum = rs.getDouble(5)
        val total = vaultSum+bankSum+cashSum+estateSum

        rs.close()
        mysql.close()

        mysqlQueue.add("INSERT INTO server_estate_history (vault, bank, cash, estate,loan, total,year,month,day,hour, date) " +
                "VALUES ($vaultSum, $bankSum,$cashSum, $estateSum,${loanSum}, $total,$year,$month,$day,$hour, now())")

        Bukkit.getLogger().info("SavedServerEstateHistory")

    }

    fun getBalanceTotal():EstateStruct{

        val mysql = MySQLManager(plugin,"Man10BankEstateHistory")

        val struct = EstateStruct()

        val rs = mysql.query("SELECT vault,bank,cash,estate,loan from server_estate_history ORDER BY date DESC LIMIT 1")?:return struct
        if (!rs.next())return struct
        struct.vault = rs.getDouble(1)
        struct.bank = rs.getDouble(2)
        struct.cash = rs.getDouble(3)
        struct.estate = rs.getDouble(4)
        struct.loan = rs.getDouble(5)

        rs.close()
        mysql.close()
        return struct
    }

    fun getBalanceTop(page:Int): MutableList<Pair<String, Double>> {

        val mysql = MySQLManager(plugin,"Man10BankEstateHistory")

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

    fun showOfflineUserEstate(show:Player,p:String){

        val uuid = Bank.getUUID(p)?:return

        val mysql = MySQLManager(plugin,"Man10BankEstateHistory")

        val rs  = mysql.query("select * from estate_tbl where uuid='$uuid';")?:return

        if (!rs.next()){
            mysql.close()
            rs.close()
            return
        }

        val vault = rs.getDouble("vault")
        val bank = rs.getDouble("bank")
        val cash = rs.getDouble("cash")
        val estate = rs.getDouble("estate")
        val serverLoan = rs.getDouble("loan")
        val score = ScoreDatabase.getScore(uuid)
//        val serverLoan = ServerLoan.getBorrowingAmount(uuid)

        sendMsg(show, "§e§l==========${p}のお金(オフライン)==========")

        sendMsg(show, " §b§l電子マネー:  §e§l${format(vault)}円")
        sendMsg(show, " §b§l現金:  §e§l${format(cash)}円")
        sendMsg(show, " §b§l銀行:  §e§l${format(bank)}円")
        sendMsg(show, " §b§lその他の資産:  §e§l${format(estate)}円")
        sendMsg(show, " §b§lスコア:  §a§l${score}")
        sendMsg(show, " §c§lMan10リボ:  §e§l${format(serverLoan)}円")

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

            Thread.sleep(600000)
        }
    }
}

class EstateStruct{

    var vault = 0.0
    var bank = 0.0
    var cash = 0.0
    var estate = 0.0
    var crypto = 0.0
    var loan = 0.0

    fun total():Double{
        return vault+bank+cash+estate+crypto//+loan
    }

}
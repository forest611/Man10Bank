package red.man10.man10bank.history

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import red.man10.man10bank.Bank
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.MySQLManager
import red.man10.man10bank.atm.ATMData

object EstateData {

    private const val interval = 36000000L
    private val mysql = MySQLManager(Man10Bank.plugin,"Man10BankEstateHistory")

    private fun addEstateHistory(p:Player, vault:Double, bank:Double, estate:Double){

        val uuid = p.uniqueId

        mysql.execute("INSERT INTO estate_history_tbl (uuid, date, player, vault, bank, estate, total) " +
                "VALUES ('${uuid}', now(), '${p.name}', ${vault}, ${bank}, ${estate}, ${vault+bank+estate})")

    }

    fun createEstateData(p:Player){

        val rs = mysql.query("SELECT player from estate_tbl where uuid='${p.uniqueId}'")

        if (rs== null || !rs.next()){
            mysql.execute("INSERT INTO estate_tbl (uuid, date, player, vault, bank, estate, total) " +
                    "VALUES ('${p.uniqueId}', now(), '${p.name}', 0, 0, 0, 0)")

        }
    }

    private fun saveCurrentEstate(p:Player){

        val uuid = p.uniqueId

        val vault = Man10Bank.vault.getBalance(uuid)
        val bank = Bank.getBalance(uuid)
        val estate = ATMData.getEnderChestMoney(p) + ATMData.getInventoryMoney(p)

        mysql.execute("UPDATE estate_tbl SET " +
                "date=now(), player='${p.name}', vault=${vault}, bank=${bank}, estate=${estate}, total=${vault+bank+estate} WHERE id=1")


        addEstateHistory(p, vault, bank, estate)

    }

    private fun addServerHistory(){

        val rs = mysql.query("select sum(vault),sum(bank),sum(estate) from estate_tbl")?:return

        rs.next()

        val vaultSum = rs.getDouble(1)
        val bankSum = rs.getDouble(2)
        val estateSum = rs.getDouble(3)
        val total = vaultSum+bankSum+estateSum

        rs.close()
        mysql.close()

        mysql.execute("INSERT INTO server_estate_history (vault, bank, estate, date, total) " +
                "VALUES ($vaultSum, $bankSum, $estateSum, $total, now())")

    }

    fun historyThread(){

        while (true){
            for (p in Bukkit.getOnlinePlayers()){
                saveCurrentEstate(p)
            }

            addServerHistory()

            Thread.sleep(interval)
        }


    }


}
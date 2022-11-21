package red.man10.man10bank.history

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.inventory.meta.BundleMeta
import org.bukkit.scheduler.BukkitTask
import red.man10.man10bank.Bank
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.format
import red.man10.man10bank.Man10Bank.Companion.isInstalledShop
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.MySQLManager
import red.man10.man10bank.MySQLManager.Companion.mysqlQueue
import red.man10.man10bank.atm.ATMData
import red.man10.man10bank.cheque.Cheque
import red.man10.man10bank.loan.ServerLoan
import red.man10.man10score.ScoreDatabase
import java.util.*

object EstateData {

    private var shopCacheUpdateTask : BukkitTask? = null //ショップキャッシュ更新タスク

    init {
        Bukkit.getLogger().info("StartHistoryThread")
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { historyThread() })
        if (isInstalledShop){
            if(shopCacheUpdateTask == null) shopCacheUpdateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable { cacheShopTotal() }, 0, 20*300)
        }
    }

    //======== shop ログ関係 =======

    private val shopLogCache = HashMap<UUID, Double>()
    private fun cacheShopTotal(){

        val mysql = MySQLManager(plugin,"Man10BankShopHistory")
        val shopRecords = mysql.query(
            "SELECT `table`.`name`, \n" +
                    " `table`.`uuid`, \n" +
                    " SUM(`table`.`money`) AS `money` \n" +
                      "FROM \n" +
                    "(\n" +
                    "SELECT man10_shop_v2.man10shop_permissions.`name`,\n" +
                    "man10_shop_v2.man10shop_permissions.`uuid`,\n" +
                    "man10_shop_v2.man10shop_shops.`money`\n" +
                    "FROM man10_shop_v2.man10shop_permissions\n" +
                    "INNER JOIN man10_shop_v2.man10shop_shops ON man10_shop_v2.man10shop_permissions.shop_id = man10_shop_v2.man10shop_shops.shop_id\n" +
                    "WHERE man10_shop_v2.man10shop_shops.deleted = 0\n" +
                    "AND man10_shop_v2.man10shop_permissions.permission = \"OWNER\"\n" +
                    "AND man10_shop_v2.man10shop_shops.`admin` = \"false\"\n" +
                    "GROUP BY man10_shop_v2.man10shop_permissions.`shop_id`\n" +
                    ") \n" +
                    "AS `table` \n" +
                    "GROUP BY `table`.`uuid`\n" +
                    "ORDER BY money DESC"
        )?: return

        shopLogCache.clear()
        while(shopRecords.next()){
            shopLogCache[UUID.fromString(shopRecords.getString("uuid"))] = shopRecords.getDouble("money")
        }
    }

    fun getShopTotalBalance(p: Player): Double{
        return getShopTotalBalance(p.uniqueId)
    }

    fun getShopTotalBalance(uuid: UUID): Double {
        if (!shopLogCache.containsKey(uuid)) return 0.0
        return shopLogCache[uuid] ?: return 0.0
    }

    //=============================



    //ヒストリーに追加
    private fun addEstateHistory(p:Player,struct:EstateStruct){

        val uuid = p.uniqueId

        val mysql = MySQLManager(plugin,"Man10BankEstateHistory")
        val rs = mysql.query("SELECT * FROM estate_history_tbl WHERE uuid='${p.uniqueId}' ORDER BY date DESC LIMIT 1")

        val total = struct.total()

        if (rs==null || !rs.next()){
            mysqlQueue.add("INSERT INTO estate_history_tbl (uuid, date, player, vault, bank, cash, estate, loan, shop, total) " +
                    "VALUES ('${uuid}', now(), '${p.name}', ${struct.vault}, ${struct.bank},${struct.cash}, ${struct.estate},${struct.loan},${struct.shop}, ${total})")
            return
        }

        val lastVault = rs.getDouble("vault")
        val lastBank = rs.getDouble("bank")
        val lastCash = rs.getDouble("cash")
        val lastEstate = rs.getDouble("estate")
        val shopBalance = rs.getDouble("shop")

        mysql.close()
        rs.close()

        if (struct.vault != lastVault || struct.bank != lastBank || struct.estate != lastEstate ||struct.cash!=lastCash || struct.shop != shopBalance){
            mysqlQueue.add("INSERT INTO estate_history_tbl (uuid, date, player, vault, bank, cash, estate,loan, shop, total) " +
                    "VALUES ('${uuid}', now(), '${p.name}', ${struct.vault}, ${struct.bank},${struct.cash}, ${struct.estate},${struct.loan} , ${struct.shop}, ${total})")
        }


//        MySQLManager.mysqlQueue.add("INSERT INTO estate_history_tbl (uuid, date, player, vault, bank, estate, total) " +
//                "VALUES ('${uuid}', now(), '${p.name}', ${vault}, ${bank}, ${estate}, ${vault+bank+estate})")
//
    }

    fun createEstateData(p:Player){

        val mysql = MySQLManager(plugin,"Man10BankEstateHistory")

        val rs = mysql.query("SELECT player from estate_tbl where uuid='${p.uniqueId}'")

        if (rs== null || !rs.next()){
            mysql.execute("INSERT INTO estate_tbl (uuid, date, player, vault, bank, cash, estate, shop, total) " +
                    "VALUES ('${p.uniqueId}', now(), '${p.name}', 0, 0, 0, 0, 0, 0)")

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
        struct.cash = getCash(p)
        struct.estate = getEstate(p)
        struct.loan = ServerLoan.getBorrowingAmount(p)
        struct.shop = getShopTotalBalance(p);

        mysqlQueue.add("UPDATE estate_tbl SET " +
                "date=now(), player='${p.name}', vault=${struct.vault}, bank=${struct.bank}, cash=${struct.cash}," +
                " estate=${struct.estate},loan=${struct.loan}, shop=${struct.shop}, total=${struct.total()} WHERE uuid='${uuid}'")

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

        val shopSum = if (isInstalledShop){
            val shopData = mysql.query("SELECT SUM(money) AS `total` FROM man10_shop_v2.man10shop_shops WHERE deleted = 0 AND admin = \"false\"")?: return //shop全体合計

            shopData.next()

            shopData.getDouble(1)
        }else{
            0.0
        }

        val vaultSum = rs.getDouble(1)
        val bankSum = rs.getDouble(2)
        val cashSum = rs.getDouble(3)
        val estateSum = rs.getDouble(4)
        val loanSum = rs.getDouble(5)
        val total = vaultSum+bankSum+cashSum+estateSum+shopSum

        rs.close()
        mysql.close()

        mysqlQueue.add("INSERT INTO server_estate_history (vault, bank, cash, estate,loan, shop, total,year,month,day,hour, date) " +
                "VALUES ($vaultSum, $bankSum,$cashSum, $estateSum,${loanSum}, $shopSum, $total,$year,$month,$day,$hour, now())")

        Bukkit.getLogger().info("SavedServerEstateHistory")

    }

    fun getBalanceTotal():EstateStruct{

        val mysql = MySQLManager(plugin,"Man10BankEstateHistory")

        val struct = EstateStruct()

        val rs = mysql.query("SELECT vault,bank,cash,estate,loan,shop from server_estate_history ORDER BY date DESC LIMIT 1")?:return struct
        if (!rs.next())return struct
        struct.vault = rs.getDouble(1)
        struct.bank = rs.getDouble(2)
        struct.cash = rs.getDouble(3)
        struct.estate = rs.getDouble(4)
        struct.loan = rs.getDouble(5)
        struct.shop = rs.getDouble(6)

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
        sendMsg(show, " §b§lショップ口座:  §e§l${format(getShopTotalBalance(uuid))}円")
        sendMsg(show, " §b§lスコア:  §a§l${score}")
        sendMsg(show, " §c§lMan10リボ:  §e§l${format(serverLoan)}円")

        mysql.close()
        rs.close()

    }

    //その他の資産を返す
    fun getEstate(p:Player):Double{

        var estate = 0.0

        //インベントリ
        for (item in p.inventory.contents){
            if (item ==null ||item.type == Material.AIR)continue
            estate+=Cheque.getChequeAmount(item)

            for (i in getShulkerItem(item)){
                estate+=Cheque.getChequeAmount(i)
                for (j in getBundleItem(i)){
                    estate+=Cheque.getChequeAmount(j)
                }
            }

            for (i in getBundleItem(item)){
                estate+=Cheque.getChequeAmount(i)
            }
        }

        //エンダーチェスト
        for (item in p.enderChest.contents){
            if (item ==null ||item.type == Material.AIR)continue
            estate+=Cheque.getChequeAmount(item)

            for (i in getShulkerItem(item)){
                estate+=Cheque.getChequeAmount(i)
                for (j in getBundleItem(i)){
                    estate+=Cheque.getChequeAmount(j)
                }
            }

            for (i in getBundleItem(item)){
                estate+=Cheque.getChequeAmount(i)
            }

        }

        return estate

    }

    fun getCash(p:Player):Double{

        var cash = 0.0

        for (item in p.inventory.contents){
            if (item ==null ||item.type == Material.AIR)continue
            val money = ATMData.getMoneyAmount(item)
            cash+=money

            for (i in getShulkerItem(item)){
                cash+=ATMData.getMoneyAmount(i)

                for (j in getBundleItem(i)){
                    cash+=ATMData.getMoneyAmount(j)
                }
            }

            for (i in getBundleItem(item)){
                cash+=ATMData.getMoneyAmount(i)
            }
        }

        for (item in p.enderChest.contents){
            if (item ==null ||item.type == Material.AIR)continue
            val money = ATMData.getMoneyAmount(item)
            cash+=money

            for (i in getShulkerItem(item)){
                cash+=ATMData.getMoneyAmount(i)

                for (j in getBundleItem(i)){
                    cash+=ATMData.getMoneyAmount(j)
                }
            }

            for (i in getBundleItem(item)){
                cash+=ATMData.getMoneyAmount(i)
            }
        }

        return cash

    }

    fun getShulkerItem(item:ItemStack):List<ItemStack>{

        val meta = item.itemMeta?: emptyList<ItemStack>()

        if (meta is BlockStateMeta && meta.blockState is ShulkerBox && meta.hasBlockState()){

            val shulker = meta.blockState as ShulkerBox
            return shulker.inventory.toList()
        }
        return emptyList()
    }

    fun getBundleItem(item: ItemStack):List<ItemStack>{

        val meta = item.itemMeta?: emptyList<ItemStack>()

        if (meta is BundleMeta){
            return meta.items
        }

        return emptyList()
    }

    private fun historyThread(){

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
    var shop = 0.0

    fun total():Double{
        return vault+bank+cash+estate+crypto+shop//+loan
    }

}
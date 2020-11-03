package red.man10.man10offlinebank

import net.testusuke.open.man10mail.DataBase.MailConsole
import net.testusuke.open.man10mail.DataBase.MailSenderType
import net.testusuke.open.man10mail.MailUtil
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10offlinebank.Man10OfflineBank.Companion.format
import red.man10.man10offlinebank.Man10OfflineBank.Companion.plugin
import red.man10.man10offlinebank.Man10OfflineBank.Companion.rate
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import kotlin.collections.HashMap

object Bank {

    private val hasAccount = ConcurrentHashMap<UUID,Boolean>()
    private val mysqlQueue = LinkedBlockingQueue<String>()
    private val mysql : MySQLManager = MySQLManager(plugin,"Man10OfflineBank")

    //////////////////////////////////
    //口座を持っているかどうか
    //////////////////////////////////
    fun hasAccount(uuid:UUID):Boolean{

        val bool = hasAccount[uuid]

        if (bool == null){

            val mysql = MySQLManager(plugin,"Man10OfflineBank")

            val rs = mysql.query("SELECT balance FROM user_bank WHERE uuid='$uuid'")?:return false

            if (rs.next()) {
                hasAccount[uuid] = true

                mysql.close()
                rs.close()

                return true
            }
            hasAccount[uuid] = false

            mysql.close()
            rs.close()

            return false

        }
        return bool

    }

    /////////////////////////////////////
    //新規口座作成 既に持っていたら作らない
    /////////////////////////////////////
    private fun createAccount(uuid: UUID):Boolean{

        if (hasAccount(uuid))return false

        val p = Bukkit.getOfflinePlayer(uuid)

        mysqlQueue.add("INSERT INTO user_bank (player, uuid, balance) " +
                "VALUES ('${p.name}', '$uuid', 0);")

        addLog(uuid,plugin,"CreateAccount",0.0,true)

        hasAccount[uuid] = true

        return true
    }

    /**
     * ログを生成
     *
     * @param plugin 処理を行ったプラグインの名前
     * @param note ログの内容 (max64)
     * @param amount 動いた金額
     */
    private fun addLog(uuid: UUID,plugin:JavaPlugin,note:String,amount:Double,isDeposit:Boolean){

        val p = Bukkit.getOfflinePlayer(uuid)

        mysqlQueue.add("INSERT INTO money_log (player, uuid, plugin_name, amount, server, note, deposit) " +
                "VALUES " +
                "('${p.name}', " +
                "'$uuid', " +
                "'${plugin.name}', " +
                "$amount, " +
                "'${plugin.server.name}', " +
                "'$note', " +
                "${if (isDeposit) 1 else 0});")

    }

    /**
     * オフライン口座の残高を確認する
     *
     * @param uuid ユーザーのuuid*
     * @return 残高 存在しないユーザーだった場合、0.0が返される
     */
    @Synchronized
    fun getBalance(uuid:UUID):Double{

        var bal = 0.0

        if (!hasAccount(uuid))return 0.0

//        val mysql = MySQLManager(plugin,"Man10OfflineBank")

        val rs = mysql.query("SELECT balance FROM user_bank WHERE uuid='$uuid' for update;")?:return bal

        if (!rs.next()){
            return bal
        }

        bal = rs.getDouble("balance")

        return bal
    }

    @Synchronized
    fun setBalance(uuid:UUID,amount: Double){
        if (amount <0.1)return

        if (!hasAccount(uuid)){
            createAccount(uuid)
        }

        mysqlQueue.add("update user_bank set balance=$amount where uuid='$uuid';")

        addLog(uuid,plugin, "SetBalanceByCommand", amount,true)
    }

    /**
     * ユーザー名からuuidを取得する
     *
     *@return 口座が存在しなかったらnullを返す
     */
    fun getUUID(player:String):UUID?{

        val mysql = MySQLManager(plugin,"Man10OfflineBank")

        val rs = mysql.query("SELECT uuid FROM user_bank WHERE player='$player';")?:return null

        if (rs.next()){
            val uuid = UUID.fromString(rs.getString("uuid"))

            mysql.close()
            rs.close()

            return uuid
        }

        mysql.close()
        rs.close()

        return null

    }


    /**
     * オフライン口座に入金する
     *
     * @param plugin 入金したプラグイン
     * @param note 入金の内容(64文字以内)
     * @param amount 入金額(マイナスだった場合、入金処理は行われない)
     *
     */
    fun deposit(uuid: UUID, amount: Double, plugin: JavaPlugin, note:String){

        if (amount <0.1)return

        if (!hasAccount(uuid)){
            createAccount(uuid)
        }

        mysqlQueue.add("update user_bank set balance=balance+${amount/ rate} where uuid='$uuid';")

        addLog(uuid,plugin, note, amount,true)



    }

    /**
     * オフライン口座から出金する
     *
     * @param plugin 出金したプラグイン
     * @param note 出金の内容(64文字以内)
     * @param amount 出金額(マイナスだった場合、入金処理は行われない)
     *
     * @return　出金成功でtrue
     */
    @Synchronized
    fun withdraw(uuid: UUID, amount: Double, plugin: JavaPlugin, note:String):Boolean{

        if (amount <0.1)return false

        if (!hasAccount(uuid)){
            createAccount(uuid)
        }

        if (getBalance(uuid) < amount)return false

        mysqlQueue.add("update user_bank set balance=balance-${amount* rate} where uuid='$uuid';")

        addLog(uuid,plugin, note, amount,false)


        return true
    }

    fun balanceTop(): MutableList<Pair<OfflinePlayer, Double>>? {

        val list = mutableListOf<Pair<OfflinePlayer,Double>>()

        val mysql = MySQLManager(plugin,"Man10Bank baltop")

        val rs = mysql.query("select * from user_bank order by balance desc limit 10")?:return null

        while (rs.next()){
            list.add(Pair(Bukkit.getOfflinePlayer(UUID.fromString(rs.getString("uuid"))),rs.getDouble("balance")))
        }

        rs.close()
        mysql.close()

        return list

    }

    fun totalBalance():Double{

        val mysql = MySQLManager(plugin,"Man10Bank total")

        val rs = mysql.query("select sum(balance) from user_bank")?:return 0.0
        rs.next()

        val amount = rs.getDouble(1)

        rs.close()
        mysql.close()
        return amount

    }

    fun average():Double{
        val mysql = MySQLManager(plugin,"Man10Bank total")

        val rs = mysql.query("select avg(balance) from user_bank")?:return 0.0
        rs.next()

        val amount = rs.getDouble(1)

        rs.close()
        mysql.close()
        return amount
    }

    fun sendProfitAndLossMail(){

        val moneyLog = HashMap<OfflinePlayer,MoneyData>()

        val format = SimpleDateFormat("yyyy-MM-dd")
        val date = Date()

        val mysql = MySQLManager(plugin,"Man10Bank profit and loss")

        val rs = mysql.query("select * from money_log where date>${format.format(date)} and amount != 0;")?:return

        while (rs.next()){

            val p = Bukkit.getOfflinePlayer(UUID.fromString(rs.getString("uuid")))

            val isDeposit = rs.getInt("deposit") == 1

            val data = moneyLog[p] ?: MoneyData()

            if (isDeposit){
                data.deposit = data.deposit+ rs.getDouble("amount")
                data.depositCount ++
            }else{
                data.withdraw = data.withdraw+ rs.getDouble("amount")
                data.withdrawCount ++
            }

            moneyLog[p] = data

        }

        rs.close()
        mysql.close()

        for (log in moneyLog){

//            Bukkit.getScheduler().runTask(plugin) {
//                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
//                        "mmail send-tag &b&lMan10OfflineBank ${log.key.name} &c&l[入出金情報] Man10OfflineBank " +
//                                "&e&l${format.format(date)}の入出金情報です;" +
//                                "§e入金額:§a§l${format(log.value.deposit)};" +
//                                "§e出金額:§c§l${format(log.value.withdraw)};" +
//                                "§e取引回数:§a入金:${log.value.depositCount}回,§c出金:§c${log.value.withdrawCount}回")
//            }

            MailConsole.sendMail("&b&lMan10OfflineBank",log.key.uniqueId.toString()," §c§l[入出金情報] Man10OfflineBank","Man10OfflineBank",
                    "§e§l${format.format(date)}の入出金情報です;" +
                            "§e入金額:§a§l${format(log.value.deposit)};" +
                            "§e出金額:§c§l${format(log.value.withdraw)};" +
                            "§e取引回数:§a入金:${log.value.depositCount}回,§c出金:§c${log.value.withdrawCount}回",MailSenderType.CUSTOM)

        }

    }

    class MoneyData{
        var withdraw = 0.0
        var deposit = 0.0
        var withdrawCount = 0
        var depositCount = 0
    }


    /////////////////
    //query queue
    ////////////////
    fun mysqlQueue(){
        Thread {
            val sql = MySQLManager(plugin, "Man10OfflineBank Queue")
            try {
                while (true) {
                    val take = mysqlQueue.take()
                    sql.execute(take)
                }
            } catch (e: InterruptedException) {

            }
        }.start()

    }

    fun mailThread(){

        Thread{

            var sent = false

            while (true){

                val calender = Calendar.getInstance()

                if (calender.get(Calendar.MINUTE) == 59 && calender.get(Calendar.HOUR_OF_DAY) == 23 && !sent){
                    sendProfitAndLossMail()
                    sent = true
                }else if(sent){
                    sent = false
                }

                Thread.sleep(60000)
            }

        }.start()

    }
}
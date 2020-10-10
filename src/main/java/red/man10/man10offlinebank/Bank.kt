package red.man10.man10offlinebank

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10offlinebank.Man10OfflineBank.Companion.plugin
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

object Bank {

    private val hasAccount = ConcurrentHashMap<UUID,Boolean>()
    private val mysqlQueue = LinkedBlockingQueue<String>()

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
    fun getBalance(uuid:UUID):Double{

        var bal = 0.0

        if (!hasAccount(uuid))return 0.0

        val mysql = MySQLManager(plugin,"Man10OfflineBank")

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
    @Synchronized
    fun deposit(uuid: UUID, amount: Double, plugin: JavaPlugin, note:String){

        if (amount <0.1)return

        if (!hasAccount(uuid)){
            createAccount(uuid)
        }

        mysqlQueue.add("update user_bank set balance=balance+$amount where uuid='$uuid';")

        addLog(uuid,plugin, note, amount,true)

        Bukkit.getScheduler().runTask(plugin) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "mmail send-tag &b&lMan10OfflineBank ${Bukkit.getOfflinePlayer(uuid).name} &c&l[入金情報] Man10OfflineBank " +
                            "&a&lmbal口座に入金がありました;&e&l入金元:$note;&6&l金額:$amount;&e&l時刻:${Timestamp.from(Date().toInstant())}")
        }

    }

    /**
     * オフライン口座から出金する
     *
     * @param plugin 出金したプラグイン
     * @param note 出金の内容(64文字以内)
     * @param amount 出金額(マイナスだった場合、入金処理は行われない)
     *
     * @retur　出金成功でtrue
     */
    @Synchronized
    fun withdraw(uuid: UUID, amount: Double, plugin: JavaPlugin, note:String):Boolean{

        if (amount <0.1)return false

        if (!hasAccount(uuid)){
            createAccount(uuid)
        }

        if (getBalance(uuid) < amount)return false

        mysqlQueue.add("update user_bank set balance=balance-$amount where uuid='$uuid';")

        addLog(uuid,plugin, note, amount,false)

        Bukkit.getScheduler().runTask(plugin) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "mmail send-tag &b&lMan10OfflineBank ${Bukkit.getOfflinePlayer(uuid).name} &4&l[出金情報] Man10OfflineBank " +
                            "&a&lmbal口座から出金がありました;&e&l出金先:$note;&6&l金額:$amount;&e&l時刻:${Timestamp.from(Date().toInstant())}")
        }

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
//
//    fun sendProfitAndLossMail(){
//
//        val format = SimpleDateFormat("yyyy-MM-dd")
//
//        val mysql = MySQLManager(plugin,"Man10Bank profit and loss")
//
//        val rs = mysql.query("select * from money_log where date>${format.format(Date())} and amount != 0;")
//
//    }


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
}
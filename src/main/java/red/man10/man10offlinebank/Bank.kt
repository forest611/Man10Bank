package red.man10.man10offlinebank

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10offlinebank.Man10OfflineBank
import red.man10.realestate.MySQLManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.LinkedBlockingQueue

class Bank(private val plugin:Man10OfflineBank) {

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

        addLog(uuid,plugin,"CreateAccount",0.0)

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
    private fun addLog(uuid: UUID,plugin:JavaPlugin,note:String,amount:Double){

        val p = Bukkit.getOfflinePlayer(uuid)

        mysqlQueue.add("INSERT INTO money_log (player, uuid, plugin_name, amount, server, note) " +
                "VALUES " +
                "('${p.name}', " +
                "'$uuid', " +
                "'${plugin.name}', " +
                "$amount, " +
                "'${plugin.server.name}', " +
                "'$note');")

    }

    /**
     * オフライン口座の残高を確認する
     *
     * @param uuid ユーザーのuuid*
     * @return 残高 存在しないユーザーだった場合、-1.0が返される
     */
    fun getBalance(uuid:UUID):Double{

        var bal = 0.0

        if (!hasAccount(uuid))return 0.0

        val mysql = MySQLManager(plugin,"Man10OfflineBank")

        val rs = mysql.query("SELECT balance FROM user_bank WHERE uuid='$uuid' for update;")?:return bal
        rs.next()

        bal = rs.getDouble("balance")

        return bal
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

        if (amount <0)return

        if (!hasAccount(uuid)){
            createAccount(uuid)
        }

        mysqlQueue.add("update user_bank set balance=balance+$amount where uuid='$uuid';")

        addLog(uuid,plugin, note, amount)

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

        if (amount <0)return false

        if (!hasAccount(uuid)){
            createAccount(uuid)
        }

        if (getBalance(uuid) < amount)return false

        mysqlQueue.add("update user_bank set balance=balance-$amount where uuid='$uuid';")

        addLog(uuid,plugin, note, amount)

        return true
    }


    /////////////////
    //query queue
    ////////////////
    fun mysqlQueue(){
        Thread(Runnable {
            val sql = MySQLManager(plugin,"Man10OfflineBank Queue")
            try{
                while (true){
                    val take = mysqlQueue.take()
                    sql.execute(take)
                }
            }catch (e:InterruptedException){

            }
        }).start()



    }
}
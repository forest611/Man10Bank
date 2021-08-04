package red.man10.man10bank

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.Man10Bank.Companion.bankEnable
import red.man10.man10bank.Man10Bank.Companion.format
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.MySQLManager.Companion.mysqlQueue
import red.man10.man10bank.history.EstateData
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.floor

object Bank {

    private var mysql : MySQLManager = MySQLManager(plugin,"Man10OfflineBank")

    //////////////////////////////////
    //口座を持っているかどうか
    //////////////////////////////////
    @Synchronized
    fun hasAccount(uuid:UUID):Boolean{


        val rs = mysql.query("SELECT balance FROM user_bank WHERE uuid='$uuid'")?:return false

        if (rs.next()) {

            mysql.close()
            rs.close()

            return true
        }

        mysql.close()
        rs.close()

        return false

    }

    /////////////////////////////////////
    //新規口座作成 既に持っていたら作らない
    /////////////////////////////////////
    @Synchronized
    fun createAccount(uuid: UUID):Boolean{

        if (hasAccount(uuid))return false

        val p = Bukkit.getOfflinePlayer(uuid)

        val ret  = mysql.execute("INSERT INTO user_bank (player, uuid, balance) " +
                "VALUES ('${p.name}', '$uuid', 0);")

        if (!ret)return false

        addLog(uuid,plugin,"CreateAccount","口座を作成",0.0,true)

        vault.deposit(uuid,Man10Bank.firstMoney)

        return true
    }

    /**
     * ログを生成
     *
     * @param plugin 処理を行ったプラグインの名前
     * @param note ログの内容 (max64)
     * @param amount 動いた金額
     */
    private fun addLog(uuid: UUID,plugin:JavaPlugin,note:String,displayNote: String,amount:Double,isDeposit:Boolean){

        val p = Bukkit.getOfflinePlayer(uuid)

        mysqlQueue.add("INSERT INTO money_log (player, uuid, plugin_name, amount, server, note,display_note, deposit) " +
                "VALUES " +
                "('${p.name}', " +
                "'$uuid', " +
                "'${plugin.name}', " +
                "$amount, " +
                "'${plugin.server.name}', " +
                "'$note', " +
                "'${displayNote}'," +
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

        if (!hasAccount(uuid)){
            createAccount(uuid)
            return 0.0
        }

        val rs = mysql.query("SELECT balance FROM user_bank WHERE uuid='$uuid';")?:return bal

        if (!rs.next()){
            return bal
        }

        bal = rs.getDouble("balance")

        rs.close()
        mysql.close()

        return bal
    }

    @Synchronized
    fun setBalance(uuid:UUID,amount: Double){
        if (amount <0.0)return

        if (!hasAccount(uuid)){
            createAccount(uuid)
        }

        val ret = mysql.execute("update user_bank set balance=$amount where uuid='$uuid';")

        if (!ret)return

        addLog(uuid,plugin, "SetBalanceByCommand","所持金を${format(amount)}にセット", amount,true)
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
    fun deposit(uuid: UUID, amount: Double, plugin: JavaPlugin, note:String,displayNote:String?):Boolean{

        if (!bankEnable)return false

//        if (!hasAccount(uuid)){
//            createAccount(uuid)
//        }

        val finalAmount = floor(amount)

        val ret = mysql.execute("update user_bank set balance=balance+$finalAmount where uuid='$uuid';")

        if (!ret)return false

        addLog(uuid,plugin, note,displayNote?:note, finalAmount,true)

        val p = Bukkit.getOfflinePlayer(uuid)

        if (p.isOnline){
            sendMsg(p.player!!,"§e${format(amount)}円入金がありました。")
        }

        return true
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
    fun withdraw(uuid: UUID, amount: Double, plugin: JavaPlugin, note:String,displayNote:String?):Boolean{

        if (!bankEnable)return false

//        if (!hasAccount(uuid))return false

        val finalAmount = floor(amount)

        if (getBalance(uuid) < finalAmount)return false

        val ret = mysql.execute("update user_bank set balance=balance-${finalAmount} where uuid='$uuid';")

        if (!ret)return false

        addLog(uuid,plugin, note,displayNote?:note, finalAmount,false)

        val p = Bukkit.getOfflinePlayer(uuid)

        if (p.isOnline){
            sendMsg(p.player!!,"§e${format(amount)}円出金されました。")
        }

        return true
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

    fun changeName(player: Player){
        mysqlQueue.add("update user_bank set player='${player.name}' where uuid='${player.uniqueId}';")
    }


    private val simpleDateFormat = SimpleDateFormat("yyyy-MM/dd HH:mm")

    fun getBankLog(p:Player,page:Int): MutableList<BankLog> {

        val rs = mysql.query("select * from money_log where uuid='${p.uniqueId}' order by id desc Limit 10 offset ${(page)*10};")?:return Collections.emptyList()

        val list = mutableListOf<BankLog>()

        while (rs.next()){

            val data = BankLog()

            data.isDeposit = rs.getInt("deposit") == 1
            data.amount = rs.getDouble("amount")
            data.note = rs.getString("display_note")?:rs.getString("note")!!
            data.dateFormat = simpleDateFormat.format(rs.getTime("date"))

            list.add(data)
        }

        return list

    }


    fun reload(){
        Bukkit.getLogger().info("Start Reload Man10Bank")

        mysql = MySQLManager(plugin,"Man10OfflineBank")

        Bukkit.getLogger().info("Finish Reload Man10Bank")

    }

    class BankLog{

        var isDeposit = true
        var amount = 0.0
        var note = ""
        var dateFormat = ""
        var plugin = ""

    }

}
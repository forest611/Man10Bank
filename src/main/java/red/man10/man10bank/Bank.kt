package red.man10.man10bank

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.Man10Bank.Companion.bankEnable
import red.man10.man10bank.Man10Bank.Companion.format
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.MySQLManager.Companion.mysqlQueue
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.floor

object Bank {

    private lateinit var mysql : MySQLManager
    private var bankQueue = LinkedBlockingQueue<Pair<Any,ResultTransaction>>()

    init {
        Bukkit.getLogger().info("StartBankQueue")
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { bankQueue() })
    }

    //////////////////////////////////
    //口座を持っているかどうか
    //////////////////////////////////
    private fun hasAccount(uuid:UUID):Boolean{

        val sql = MySQLManager(plugin,"Man10Bank")

        val rs = sql.query("SELECT balance FROM user_bank WHERE uuid='$uuid'")?:return false

        if (rs.next()) {

            sql.close()
            rs.close()

            return true
        }

        sql.close()
        rs.close()

        return false

    }

    /////////////////////////////////////
    //新規口座作成 既に持っていたら作らない
    /////////////////////////////////////
    fun createAccount(uuid: UUID):Boolean{

        val sql = MySQLManager(plugin,"Man10Bank")

        if (hasAccount(uuid))return false

        val p = Bukkit.getOfflinePlayer(uuid)

        val ret  = sql.execute("INSERT INTO user_bank (player, uuid, balance) " +
                "VALUES ('${p.name}', '$uuid', 0);")

        if (!ret)return false

        addLog(uuid,plugin.name,"CreateAccount","口座を作成",0.0,true)

        return true
    }

    /**
     * ログを生成
     *
     * @param plugin 処理を行ったプラグインの名前
     * @param note ログの内容 (max64)
     * @param amount 動いた金額
     */
    private fun addLog(uuid: UUID,plugin:String,note:String,displayNote: String,amount:Double,isDeposit:Boolean){

        val p = Bukkit.getOfflinePlayer(uuid)

        mysqlQueue.add("INSERT INTO money_log (player, uuid, plugin_name, amount, server, note,display_note, deposit) " +
                "VALUES " +
                "('${p.name}', " +
                "'$uuid', " +
                "'${plugin}', " +
                "$amount, " +
                "'paper', " +
                "'$note', " +
                "'${displayNote}'," +
                "${if (isDeposit) 1 else 0});")

    }


    fun setBalance(uuid:UUID,amount: Double){

        val sql = MySQLManager(plugin,"Man10Bank")

        if (amount <0.0)return

        if (!hasAccount(uuid)){
            createAccount(uuid)
        }

        val ret = sql.execute("update user_bank set balance=$amount where uuid='$uuid';")

        if (!ret)return

        addLog(uuid,plugin.name, "SetBalanceByCommand","所持金を${format(amount)}にセット", amount,true)
    }

    /**
     * オフライン口座の残高を確認する
     *
     * @param uuid ユーザーのuuid*
     * @return 残高 存在しないユーザーだった場合、0.0が返される
     */
    private fun getBalanceQueue(uuid:UUID):Pair<Double,Int>{

        var bal = 0.0

        val rs = mysql.query("SELECT balance FROM user_bank WHERE uuid='$uuid';")?:return Pair(bal,2)

        if (!rs.next()){
            mysql.close()
            rs.close()
            return Pair(bal,3)
        }

        bal = rs.getDouble("balance")

        rs.close()
        mysql.close()

        return Pair(bal,0)
    }

    /**
     * オフライン口座に入金する
     *
     * @param plugin 入金したプラグイン
     * @param note 入金の内容(64文字以内)
     * @param amount 入金額(マイナスだった場合、入金処理は行われない)
     *
     */
    private fun depositQueue(uuid: UUID, amount: Double, plugin: String, note:String,displayNote:String?):Int{

        val p = Bukkit.getOfflinePlayer(uuid)
        if (!bankEnable){ return 1 }

        val finalAmount = floor(amount)

        val ret = mysql.execute("update user_bank set balance=balance+$finalAmount where uuid='$uuid';")

        if (!ret){ return 2 }

        addLog(uuid,plugin, note,displayNote?:note, finalAmount,true)

        if (p.isOnline){ sendMsg(p.player!!,"§e${format(amount)}円入金がありました。") }

        return 0
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
    private fun withdrawQueue(uuid: UUID, amount: Double, plugin: String, note:String,displayNote:String?):Int{

        val p = Bukkit.getOfflinePlayer(uuid)

        if (!bankEnable){
            Bukkit.getLogger().warning("[出金エラー]Man10Bankが閉じています ユーザー:${p.name}")
            return 1
        }

//        if (!hasAccount(uuid))return false

        val finalAmount = floor(amount)
        val balance = getBalanceQueue(uuid).first

        if (balance < finalAmount){ return 2 }

        val ret = mysql.execute("update user_bank set balance=balance-${finalAmount} where uuid='$uuid';")

        if (!ret){ return 3 }

        addLog(uuid,plugin, note,displayNote?:note, finalAmount,false)

        if (p.isOnline){ sendMsg(p.player!!,"§e${format(amount)}円出金されました。") }

        return 0
    }

    /**
     * ユーザー名からuuidを取得する
     *
     *@return 口座が存在しなかったらnullを返す
     */
    fun getUUID(player:String):UUID?{

        val sql = MySQLManager(plugin,"Man10Bank")

        val rs = sql.query("SELECT uuid FROM user_bank WHERE player='$player';")?:return null

        if (rs.next()){
            val uuid = UUID.fromString(rs.getString("uuid"))

            sql.close()
            rs.close()

            return uuid
        }

        sql.close()
        rs.close()

        return null

    }

    fun changeName(player: Player){
        mysqlQueue.add("update user_bank set player='${player.name}' where uuid='${player.uniqueId}';")
    }


    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")

    fun getBankLog(p:Player,page:Int): MutableList<BankLog> {

        val sql = MySQLManager(plugin,"Man10Bank")

        val rs = sql.query("select * from money_log where uuid='${p.uniqueId}' order by id desc Limit 10 offset ${(page)*10};")?:return Collections.emptyList()

        val list = mutableListOf<BankLog>()

        while (rs.next()){

            val data = BankLog()

            data.isDeposit = rs.getInt("deposit") == 1
            data.amount = rs.getDouble("amount")
            data.note = rs.getString("display_note")?:rs.getString("note")!!
            data.dateFormat = simpleDateFormat.format(rs.getTimestamp("date"))

            list.add(data)
        }

        sql.close()
        rs.close()

        return list

    }


    fun reload(){
        Bukkit.getLogger().info("Start Reload Man10Bank")

        mysql = MySQLManager(plugin,"Man10OfflineBank")

        Bukkit.getLogger().info("Finish Reload Man10Bank")

    }

    fun init(name:String){

        val uuid = getUUID(name)?:return

        mysql.execute("delete from user_bank where uuid='${uuid}';")
        mysql.execute("delete from loan_table where borrow_uuid='${uuid}';")

        vault.withdraw(uuid, vault.getBalance(uuid))

    }

    fun interface ResultTransaction{ fun onTransactionResult(errorCode:Int, amount:Double, errorMessage: String) }
    fun interface IntTransaction{ fun transaction():Int }
    fun interface PairTransaction{ fun transaction():Pair<Double,Int> }

    private fun bankQueue(){

        mysql  = MySQLManager(plugin,"Man10OfflineBank")

        while (true){
            val bankTransaction = bankQueue.take()

            val transaction = bankTransaction.first

            var errorCode = 0
            var amount = 1.0
            var errorMessage = ""

            try {
                if (transaction is IntTransaction){ errorCode = transaction.transaction() }

                if (transaction is PairTransaction){
                    val ret = transaction.transaction()
                    amount = ret.first
                    errorCode = ret.second
                }
            }catch (e:Exception){
                errorMessage = e.message.toString()
                Bukkit.getLogger().info("Man10BankQueueエラー:${errorMessage}")
            }finally {
                bankTransaction.second.onTransactionResult(errorCode,amount,errorMessage)
            }

        }
    }

    private fun addTransactionQueue(transaction: Any, transactionCallBack: ResultTransaction):ResultTransaction{
        bankQueue.add(Pair(transaction,transactionCallBack))
        return transactionCallBack
    }

    fun asyncDeposit(uuid: UUID, amount: Double, plugin: JavaPlugin, note:String,displayNote:String?,callback:ResultTransaction){

        val transaction = IntTransaction { return@IntTransaction depositQueue(uuid,amount,plugin.name,note,displayNote) }

        addTransactionQueue(transaction) { _code: Int, _amount: Double, _message: String ->
            callback.onTransactionResult(_code,_amount,_message)
        }

    }

    /**
     * 同期で入金する処理
     */
    fun deposit(uuid: UUID, amount: Double, plugin: JavaPlugin, note:String,displayNote:String?): Triple<Int, Double, String> {

        var ret = Triple(-1,0.0,"")

        val lock = Lock()

        asyncDeposit(uuid,amount,plugin,note,displayNote) { _code: Int, _amount: Double, _message: String ->
            ret = Triple(_code,_amount,_message)
            lock.unlock()
        }

        lock.lock()
        return ret
    }

    fun asyncWithdraw(uuid: UUID, amount: Double, plugin: JavaPlugin, note:String,displayNote:String?,callback: ResultTransaction){

        val transaction = IntTransaction { return@IntTransaction withdrawQueue(uuid,amount,plugin.name,note,displayNote) }

        addTransactionQueue(transaction) { _code: Int, _amount: Double, _message: String ->
            callback.onTransactionResult(_code,_amount,_message)
        }
    }

    /**
     * 同期で出金する処理
     */
    fun withdraw(uuid: UUID, amount: Double, plugin: JavaPlugin, note:String,displayNote:String?): Triple<Int, Double, String> {

        var ret = Triple(-1,0.0,"")

        val lock = Lock()

        asyncWithdraw(uuid,amount,plugin,note,displayNote) { _code: Int, _amount: Double, _message: String ->
            ret = Triple(_code,_amount,_message)
            lock.unlock()
        }

        lock.lock()

        return ret
    }

    /**
     * 金額を取得する処理
     */
    fun asyncGetBalance(uuid: UUID,callback: ResultTransaction){

        val transaction = PairTransaction { return@PairTransaction getBalanceQueue(uuid) }

        addTransactionQueue(transaction) { _code: Int, _amount: Double, _message: String ->
            callback.onTransactionResult(_code,_amount,_message)
        }

    }
    /**
     * 金額を取得する処理
     */
    fun getBalance(uuid: UUID):Double{
        var amount = -1.0

        val lock = Lock()

        asyncGetBalance(uuid){ _: Int, _amount: Double, _: String ->
            amount = _amount
            lock.unlock()
        }

        lock.lock()

        return amount

    }

    class BankLog{

        var isDeposit = true
        var amount = 0.0
        var note = ""
        var dateFormat = ""
        var plugin = ""

    }

    class Lock{

        @Volatile
        private  var isLock = false

        fun lock(){
            synchronized(this){ isLock = true }
            while (isLock){ Thread.sleep(1) }
        }

        fun unlock(){
            synchronized(this){ isLock = false }
        }

    }

}
package red.man10.man10bank

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.Bank.IntTransaction
import red.man10.man10bank.Bank.PairTransaction
import red.man10.man10bank.Man10Bank.Companion.bankEnable
import red.man10.man10bank.Man10Bank.Companion.format
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.MySQLManager.Companion.escapeStringForMySQL
import red.man10.man10bank.MySQLManager.Companion.mysqlQueue
import red.man10.man10bank.history.EstateData
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.CompletableFuture
import kotlin.math.ceil
import kotlin.math.floor

object Bank {

    private lateinit var mysql : MySQLManager
    private var bankQueue = LinkedBlockingQueue<Pair<Any,ResultTransaction>>()
    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")

    init {
        Bukkit.getLogger().info("StartBankQueue")
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { bankQueue() })
    }

    //////////////////////////////////
    //口座を持っているかどうか
    //////////////////////////////////
    private fun hasAccount(uuid:UUID):Boolean{

//        val sql = MySQLManager(plugin,"Man10Bank")
//
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
    private fun createAccount(uuid: UUID):Boolean{

        if (hasAccount(uuid))return false

        val p = Bukkit.getOfflinePlayer(uuid)

        val ret  = mysql.execute("INSERT INTO user_bank (player, uuid, balance) " +
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
     * オフライン口座に入出金する共通処理
     */
    private fun updateBalanceQueue(
        uuid: UUID,
        amount: Double,
        plugin: String,
        note: String,
        displayNote: String?,
        isDeposit: Boolean
    ): Int {

        val p = Bukkit.getOfflinePlayer(uuid)
        if (!bankEnable) {
            if (!isDeposit) {
                Bukkit.getLogger().warning("[出金エラー]Man10Bankが閉じています ユーザー:${p.name}")
            }
            return 1
        }

        val finalAmount = if (isDeposit) floor(amount) else ceil(amount)

        if (!isDeposit) {
            val balance = getBalanceQueue(uuid).first
            if (balance < finalAmount) {
                return 2
            }
        }

        val op = if (isDeposit) "+" else "-"
        val ret = mysql.execute("update user_bank set balance=balance${op}${finalAmount} where uuid='$uuid';")

        if (!ret) {
            return if (isDeposit) 2 else 3
        }

        addLog(uuid, plugin, note, displayNote ?: note, finalAmount, isDeposit)

        if (p.isOnline) {
            val msg = if (isDeposit) "§e${format(amount)}円入金がありました。" else "§e${format(amount)}円出金されました。"
            sendMsg(p.player!!, msg)
        }

        return 0
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
        return updateBalanceQueue(uuid, amount, plugin, note, displayNote, true)
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
        return updateBalanceQueue(uuid, amount, plugin, note, displayNote, false)
    }

    /**
     * ユーザー名からuuidを取得する
     *
     *@return 口座が存在しなかったらnullを返す
     */
    fun getUUID(player:String):UUID?{

        val sql = MySQLManager(plugin,"Man10Bank")

        val rs = sql.query("SELECT uuid FROM user_bank WHERE player='${escapeStringForMySQL(player)}';")?:return null

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

    private fun changeName(player: Player){
        mysqlQueue.add("update user_bank set player='${player.name}' where uuid='${player.uniqueId}';")
    }

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

        val future = CompletableFuture<Triple<Int, Double, String>>()

        asyncDeposit(uuid,amount,plugin,note,displayNote) { _code: Int, _amount: Double, _message: String ->
            future.complete(Triple(_code,_amount,_message))
        }

        return future.get()
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

        val future = CompletableFuture<Triple<Int, Double, String>>()

        asyncWithdraw(uuid,amount,plugin,note,displayNote) { _code: Int, _amount: Double, _message: String ->
            future.complete(Triple(_code,_amount,_message))
        }

        return future.get()
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
        val future = CompletableFuture<Double>()

        asyncGetBalance(uuid){ _, _amount, _ ->
            future.complete(_amount)
        }

        return future.get()

    }

    ////////////////////////////////
    //ログイン時にまとめてやる処理(アカウントの作成、名前を更新、資産レコード作成など)
    /////////////////////////////////
    fun loginProcess(p:Player){
        val t = IntTransaction {
            createAccount(p.uniqueId)
            changeName(p)
            EstateData.createEstateData(p)

            return@IntTransaction 0
        }

        addTransactionQueue(t) { _, _, _ -> }
    }

    class BankLog{

        var isDeposit = true
        var amount = 0.0
        var note = ""
        var dateFormat = ""
        var plugin = ""

    }

}
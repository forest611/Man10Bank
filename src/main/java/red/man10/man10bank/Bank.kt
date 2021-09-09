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
import java.util.concurrent.Semaphore
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.floor

object Bank {

    private var mysql : MySQLManager = MySQLManager(plugin,"Man10OfflineBank")
    private var bankQueue = LinkedBlockingQueue<Pair<String,BankTransaction>>()

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

    /**
     * オフライン口座の残高を確認する
     *
     * @param uuid ユーザーのuuid*
     * @return 残高 存在しないユーザーだった場合、0.0が返される
     */
    private fun getBalanceQueue(uuid:UUID):Pair<Double,Int>{

        var bal = 0.0

        if (!hasAccount(uuid)){
            createAccount(uuid)
            return Pair(bal,1)
        }

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

    @Synchronized
    fun setBalance(uuid:UUID,amount: Double){
        if (amount <0.0)return

        if (!hasAccount(uuid)){
            createAccount(uuid)
        }

        val ret = mysql.execute("update user_bank set balance=$amount where uuid='$uuid';")

        if (!ret)return

        addLog(uuid,plugin.name, "SetBalanceByCommand","所持金を${format(amount)}にセット", amount,true)
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

        val pName = Bukkit.getOfflinePlayer(uuid).name

        if (!bankEnable){
            Bukkit.getLogger().warning("[入金エラー]Man10Bankが閉じています ユーザー:$pName")
            return 1
        }

//        if (!hasAccount(uuid)){
//            createAccount(uuid)
//        }

        val finalAmount = floor(amount)

        val ret = mysql.execute("update user_bank set balance=balance+$finalAmount where uuid='$uuid';")

        if (!ret){
            Bukkit.getLogger().warning("[入金エラー]SQLの実行に失敗しました ユーザー:$pName")
            return 2
        }

        addLog(uuid,plugin, note,displayNote?:note, finalAmount,true)

        val p = Bukkit.getOfflinePlayer(uuid)

        if (p.isOnline){
            sendMsg(p.player!!,"§e${format(amount)}円入金がありました。")
        }

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

        val pName = Bukkit.getOfflinePlayer(uuid).name

        if (!bankEnable){
            Bukkit.getLogger().warning("[出金エラー]Man10Bankが閉じています ユーザー:$pName")
            return 1
        }

//        if (!hasAccount(uuid))return false

        val finalAmount = floor(amount)
        val balance = getBalance(uuid)

        if (balance < finalAmount){
            Bukkit.getLogger().warning("[出金エラー]口座のお金が足りませんでした 残高:${balance} 出金額:${finalAmount} ユーザー:$pName")
            return 2
        }

        val ret = mysql.execute("update user_bank set balance=balance-${finalAmount} where uuid='$uuid';")

        if (!ret){
            Bukkit.getLogger().warning("[出金エラー]SQLの実行に失敗しました ユーザー:$pName")
            return 3
        }

        addLog(uuid,plugin, note,displayNote?:note, finalAmount,false)

        val p = Bukkit.getOfflinePlayer(uuid)

        if (p.isOnline){
            sendMsg(p.player!!,"§e${format(amount)}円出金されました。")
        }

        return 0
    }

    /**
     * ユーザー名からuuidを取得する
     *
     *@return 口座が存在しなかったらnullを返す
     */
    fun getUUID(player:String):UUID?{

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
            data.dateFormat = simpleDateFormat.format(rs.getTimestamp("date"))

            list.add(data)
        }

        mysql.close()
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

    fun interface BankTransaction{
        fun onTransactionResult(errorCode:Int,amount:Double,errorMessage: String)
    }


    fun bankQueue(){

        while (true){
            val bankTransaction = bankQueue.take()

            val split = bankTransaction.first.split(";")

            val uuid = UUID.fromString(split[1])

            var _code = -1
            var _amount = 1.0
            val _message = ""

            when(split[0]){

                "deposit" ->{

                    val amount = split[2].toDouble()
                    val pluginName = split[3]
                    val note = split[4]
                    val displayNote = split[5]

                    _code = depositQueue(uuid,amount,pluginName,note,displayNote)
                }

                "withdraw" ->{
                    val amount = split[2].toDouble()
                    val pluginName = split[3]
                    val note = split[4]
                    val displayNote = split[5]

                    _code = withdrawQueue(uuid,amount,pluginName,note,displayNote)

                }

                "get" -> {
                    val ret  = getBalanceQueue(uuid)
                    _amount = ret.first
                    _code = ret.second
                }

            }

            bankTransaction.second.onTransactionResult(_code,_amount,_message)


        }
    }

    private fun addTransactionQueue(transaction: String, transactionCallBack: BankTransaction):BankTransaction{
        bankQueue.add(Pair(transaction,transactionCallBack))
        return transactionCallBack
    }

    private fun deposit(uuid: UUID, amount: Double, plugin: JavaPlugin, note:String,displayNote:String?,callBack:BankTransaction){

        val rNote = note.replace(";","")
        val rDisplayNote = displayNote?.replace(";","")?:rNote

        val transaction = "deposit;${uuid};${amount};${plugin.name};${rNote};${rDisplayNote}"

        addTransactionQueue(transaction) { _code: Int, _amount: Double, _message: String ->
            callBack.onTransactionResult(_code,_amount,_message)
        }

    }

    /**
     * 同期で入金する処理
     */
    @Synchronized
    fun deposit(uuid: UUID, amount: Double, plugin: JavaPlugin, note:String,displayNote:String?): Triple<Int, Double, String> {

        var ret = Triple(-1,0.0,"")

        //TODO:排他処理を作る

        deposit(uuid, amount, plugin, note, displayNote) {  _code: Int, _amount: Double, _message: String ->
            ret = Triple(_code,_amount,_message)
        }

        return ret
    }

    private fun withdraw(uuid: UUID, amount: Double, plugin: JavaPlugin, note:String,displayNote:String?,callBack:BankTransaction){

        val rNote = note.replace(";","")
        val rDisplayNote = displayNote?.replace(";","")?:rNote

        val transaction = "withdraw;${uuid};${amount};${plugin.name};${rNote};${rDisplayNote}"

        addTransactionQueue(transaction) { _code: Int, _amount: Double, _message: String ->
            callBack.onTransactionResult(_code,_amount,_message)
        }

    }

    /**
     * 同期で出金する処理
     */
    @Synchronized
    fun withdraw(uuid: UUID, amount: Double, plugin: JavaPlugin, note:String,displayNote:String?): Triple<Int, Double, String> {

        var ret = Triple(-1,0.0,"")

        //TODO:排他処理を作る

        withdraw(uuid,amount,plugin,note,displayNote) {  _code: Int, _amount: Double, _message: String ->
            ret = Triple(_code,_amount,_message)
        }

        return ret
    }

    /**
     * 金額を取得する処理
     */
    @Synchronized
    fun getBalance(uuid: UUID):Double{
        var amount = -1.0

        val transaction = "get;${uuid}"

        //TODO:排他処理を作る

        addTransactionQueue(transaction) { _code: Int, _amount: Double, _message: String ->
            amount = _amount
            Bukkit.getLogger().info("2")
        }

        Bukkit.getLogger().info("1")
        Bukkit.getLogger().info("3")
        return amount

    }

    class BankLog{

        var isDeposit = true
        var amount = 0.0
        var note = ""
        var dateFormat = ""
        var plugin = ""

    }

}
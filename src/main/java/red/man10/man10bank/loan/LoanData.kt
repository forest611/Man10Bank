package red.man10.man10bank.loan

import net.kyori.adventure.text.Component.*
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import red.man10.man10bank.Bank
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.MySQLManager
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

class LoanData {


    lateinit var paybackDate : Date
    private lateinit var borrow: UUID
    var debt : Double = 0.0
    private var id : Int = 0

    private val mysql = MySQLManager(plugin,"Man10Loan")

    @Synchronized
    fun create(lend:Player, borrow: Player, borrowedAmount : Double, rate:Double, paybackDay:Int):Boolean{

        //30日を基準に金利が設定される
        debt = calcRate(borrowedAmount,paybackDay,rate)
        this.borrow = borrow.uniqueId
        paybackDate = calcDate(paybackDay)

        if (!mysql.lock("loan_table")){
            sendMsg(lend,"§c§lただいま窓口が混雑しているようです。しばらくお待ちください。")
            return false
        }

        if (Bank.withdraw(lend.uniqueId, borrowedAmount+(borrowedAmount * Man10Bank.loanFee), plugin,"LoanCreate","借金の貸し出し").first!=0){
            sendMsg(lend,"§c§lお金が足りません！")
            return false
        }

        val result = mysql.execute("INSERT INTO loan_table " +
                "(lend_player, lend_uuid, borrow_player, borrow_uuid, borrow_date, payback_date, amount) " +
                "VALUES ('${lend.name}', " +
                "'${lend.uniqueId}', " +
                "'${borrow.name}', " +
                "'${borrow.uniqueId}', " +
                "now(), " +
                "'${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(paybackDate.time)}', " +
                "$debt);")

        if (!result){
            mysql.unlock()
            sendMsg(lend,"§c§lデータベースエラーが発生しました。運営に報告してください。- 01")
            return false
        }

        val rs = mysql.query("SELECT id from loan_table order by id desc limit 1;")

        if (rs == null || !rs.next()){
            mysql.unlock()
            sendMsg(lend,"§c§lデータベースエラーが発生しました。運営に報告してください。- 02")
            return false
        }
        id = rs.getInt("id")

        mysql.close()

        rs.close()
        mysql.close()

        Bank.deposit(borrow.uniqueId, borrowedAmount, plugin, "LoanCreate","借金の借り入れ")


        lendMap[id] = this

        return true
    }

    fun load(id:Int): LoanData? {

        val rs = mysql.query("select * from loan_table where id=$id;")?:return null

        if (!rs.next())return null

        borrow = UUID.fromString(rs.getString("borrow_uuid"))
        debt = rs.getDouble("amount")
        paybackDate = rs.getDate("payback_date")
        this.id = rs.getInt("id")

        rs.close()
        mysql.close()

        lendMap[id] = this

        return this

    }

    private fun save(amount:Double){

        val mysql = MySQLManager(plugin,"Man10Loan")

        mysql.execute("UPDATE loan_table set amount=$amount where id=$id;")

    }

    /**
     * @param p 手形の持ち主
     */
    fun payback(p:Player,item:ItemStack) {

        if (!enable){
            sendMsg(p, "§a現在借金の貸し出しなどはできません！")
            return
        }

        if (!Man10Bank.enableLocalLoan){
            sendMsg(p,"§c§lこのエリアでは個人間借金の取引を行うことはできません。")
            return
        }

        if (Date().before(paybackDate)){
            sendMsg(p,"§cこの手形はまだ有効ではありません！")
            return
        }

        Bukkit.getScheduler().runTask(plugin, Runnable { p.inventory.remove(item) })

        val borrowPlayer = Bukkit.getOfflinePlayer(borrow)
        val isOnline = borrowPlayer.isOnline

        if (debt <= 0.0)return

        val man10Bank = Bank.getBalance(borrow)

        val balance = Man10Bank.vault.getBalance(borrow)

        val takeMan10Bank = floor(if (man10Bank<debt)man10Bank else debt)

        if (takeMan10Bank != 0.0 && Bank.withdraw(borrow,takeMan10Bank, plugin,"paybackMoney","借金の返済").first == 0){

            debt -= takeMan10Bank

            if (takeMan10Bank>0){
                sendMsg(p,"§eMan10Bankから${Man10Bank.format(takeMan10Bank)}円回収成功しました！")
                Bank.deposit(p.uniqueId,takeMan10Bank, plugin,"paybackMoneyFromBank","借金の回収")
            }

        }

        val takeBalance = floor(if (balance<(debt))balance else debt)

        if (isOnline && takeBalance != 0.0 && Man10Bank.vault.withdraw(borrow,takeBalance)){

            debt -= floor(takeBalance)

            if (takeBalance>0){
                sendMsg(p,"§e所持金から${Man10Bank.format(takeBalance)}円回収成功しました！")
                Bank.deposit(p.uniqueId,takeBalance, plugin,"paybackMoneyFromBalance","借金の回収")
            }

        }

        if (isOnline){
            sendMsg(borrowPlayer.player!!,"§e${p.name}から借金の回収が行われました！")
        }

        if (debt>0){

            Bukkit.getScheduler().runTask(plugin, Runnable { p.inventory.addItem(getNote()) })

        }else{
            sendMsg(p,"§e全額回収し終わりました！")

            if (isOnline){
                sendMsg(borrowPlayer.player!!,"§e全額完済し終わりました！お疲れ様です！")
            }
        }

        save(debt)
    }

    fun getNote():ItemStack{

        val note = ItemStack(Material.PAPER)
        val meta = note.itemMeta

        meta.setCustomModelData(2)

        meta.displayName(text("§c§l約束手形 §7§l(Promissory Note)"))
        meta.lore = mutableListOf(
            "§4§l========[Man10Bank]========",
            "   §7§l債務者:  ${Bukkit.getOfflinePlayer(borrow).name}",
            "   §8§l有効日:  ${SimpleDateFormat("yyyy-MM-dd").format(paybackDate)}",
            "   §7§l支払額:  ${Man10Bank.format(debt)}",
            "§4§l==========================")

        meta.persistentDataContainer.set(NamespacedKey(plugin,"id"), PersistentDataType.INTEGER,id)

        note.itemMeta = meta

        return note
    }

    companion object{
        fun calcDate(day:Int):Date{
            val cal = Calendar.getInstance()
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR,day)

            return cal.time
        }

        fun calcRate(amount:Double,day:Int,rate:Double): Double {
            return floor(amount * (1.0+(day/30*rate)))
        }

        fun getTotalLoan(p:Player):Double{

            val mysql = MySQLManager(plugin,"Man10Bank")
            val rs = mysql.query("select SUM(amount) from loan_table where borrow_uuid='${p.uniqueId}';")?:return 0.0
            rs.next()
            val amount = rs.getDouble(1)

            rs.close()
            mysql.close()

            return amount
        }

        fun getLoanData(uuid: UUID):Set<Pair<Int,Double>>{

            val mysql = MySQLManager(plugin,"Man10Bank")
            val rs = mysql.query("select id,amount from loan_table where borrow_uuid='${uuid}';")?:return Collections.emptySet()

            val set = mutableSetOf<Pair<Int,Double>>()

            while (rs.next()){
                set.add(Pair(rs.getInt("id"),rs.getDouble("amount")))
            }

            rs.close()
            mysql.close()
            return set
        }

        val lendMap = ConcurrentHashMap<Int,LoanData>()

        var enable = true
    }

}
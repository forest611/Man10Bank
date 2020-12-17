package red.man10.man10bank.loan

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import red.man10.man10bank.Bank
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.rate
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.MySQLManager
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

class LoanData {


    lateinit var paybackDate : Date
    lateinit var borrow: UUID
    var nowAmount : Double = 0.0
    var id : Int = 0


    fun create(lend:Player, borrow: Player, borrowedAmount : Double, rate:Double, paybackDay:Int):Int{

        val finalAmount = borrowedAmount* Man10Bank.rate

        if (!Bank.withdraw(lend.uniqueId,(finalAmount* Man10Bank.loanFee), plugin,"LoanCreate"))return -1

        Bank.deposit(borrow.uniqueId,finalAmount, plugin,"LoanCreate")

        //30日を基準に金利が設定される
        nowAmount = calcRate(borrowedAmount,paybackDay,rate)

        this.borrow = borrow.uniqueId

        paybackDate = calcDate(paybackDay)

        val mysql = MySQLManager(plugin,"Man10Loan")

        mysql.execute("INSERT INTO loan_table " +
                "(lend_player, lend_uuid, borrow_player, borrow_uuid, borrow_date, payback_date, amount) " +
                "VALUES ('${lend.name}', " +
                "'${lend.uniqueId}', " +
                "'${borrow.name}', " +
                "'${borrow.uniqueId}', " +
                "now(), " +
                "'${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(paybackDate.time)}', " +
                "$nowAmount);")

        val rs = mysql.query("SELECT id from loan_table order by id desc limit 1;")?:return -2
        rs.next()

        id = rs.getInt("id")

        rs.close()
        mysql.close()

        lendMap[id] = this

        return id

    }

    fun load(id:Int): LoanData? {

        val mysql = MySQLManager(plugin,"Man10Loan")

        val rs = mysql.query("select * from loan_table where id=$id;")?:return null

        if (!rs.next())return null

        borrow = UUID.fromString(rs.getString("borrow_uuid"))
        nowAmount = rs.getDouble("amount")
        paybackDate = rs.getDate("payback_date")
        this.id = rs.getInt("id")

        rs.close()
        mysql.close()

        lendMap[id] = this

        return this

    }

    fun save(amount:Double){

        val mysql = MySQLManager(plugin,"Man10Loan")

        mysql.execute("UPDATE loan_table set amount=$amount where id=$id;")

    }

    /**
     * @param p 手形の持ち主
     */
    fun payback(p:Player) {

        val borrowPlayer = Bukkit.getOfflinePlayer(borrow)
        val isOnline = borrowPlayer.isOnline

        if (nowAmount <= 0.0)return

        val man10Bank = Bank.getBalance(borrow)

        val balance = Man10Bank.vault.getBalance(borrow)

//        var paybackBank = 0.0

        val takeMan10Bank = rate*(if (man10Bank<nowAmount)man10Bank else nowAmount)

        if (takeMan10Bank != 0.0 && Bank.withdraw(borrow,takeMan10Bank, plugin,"paybackMoney")){

            nowAmount -= takeMan10Bank/4

//            paybackBank +=takeMan10Bank

            if (takeMan10Bank>0){
                sendMsg(p,"§eMan10Bankから${Man10Bank.format(takeMan10Bank/4)}円回収成功しました！")
                Bank.deposit(p.uniqueId,takeMan10Bank, plugin,"paybackMoneyFromBank")
            }

        }


//        val takeBalance = floor(rate*(if (balance<nowAmount)balance else nowAmount))
        val takeBalance = if (balance<(nowAmount*rate))balance else nowAmount* rate

        if (isOnline && takeBalance != 0.0 && Man10Bank.vault.withdraw(borrow,takeBalance)){

            nowAmount -= floor(takeBalance / rate)

            if (takeBalance>0){
                sendMsg(p,"§e所持金から${Man10Bank.format(takeBalance)}円回収成功しました！${if (rate!=1.0) " (レート差あり)" else ""}")
                Bank.deposit(p.uniqueId,takeBalance, plugin,"paybackMoneyFromBalance")
            }

        }

        if (isOnline){
            sendMsg(borrowPlayer.player!!,"§e手形の持ち主から借金の回収が行われました！")
        }

        if (nowAmount>0){

            Bukkit.getScheduler().runTask(plugin, Runnable { p.inventory.addItem(getNote()) })

        }else{

            sendMsg(p,"§e全額回収し終わりました！")

            if (isOnline){
                sendMsg(borrowPlayer.player!!,"§e全額完済し終わりました！お疲れ様です！")
            }

        }

        save(nowAmount)

//        Bank.deposit(p.uniqueId,paybackBank, plugin,"paybackMoney")

//        return paybackBank
    }

    fun getNote():ItemStack{

        val note = ItemStack(Material.PINK_DYE)
        val meta = note.itemMeta

        meta.setCustomModelData(10)

        meta.setDisplayName("§c§l約束手形 §7§l(Promissory Note)")
        meta.lore = mutableListOf(
            "§4§l========[Man10Bank]========",
            "   §7§l債務者:  ${Bukkit.getOfflinePlayer(borrow).name}",
            "   §8§l有効日:  ${SimpleDateFormat("yyyy/MM/dd").format(paybackDate)}",
            "   §7§l返済金額:${Man10Bank.format(nowAmount)}",
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

        val lendMap = ConcurrentHashMap<Int,LoanData>()

        var enable = true
    }

}
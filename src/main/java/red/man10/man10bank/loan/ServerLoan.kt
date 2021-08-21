package red.man10.man10bank.loan

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import red.man10.man10bank.Bank
import red.man10.man10bank.Man10Bank.Companion.es
import red.man10.man10bank.Man10Bank.Companion.format
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.prefix
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.MySQLManager
import red.man10.man10score.ScoreDatabase
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
import kotlin.math.round

object ServerLoan {

    private val mysql = MySQLManager(plugin,"Man10ServerLoan")

    var scoreMultiplier = 1.0//スコアの乗数
    var recordMultiplier = 1.0//レコード数の乗数
    var medianMultiplier = 1.0//中央値の乗数

    var paymentThread = false

    val shareMap = ConcurrentHashMap<Player,Double>()
    val commandList = mutableListOf<Player>()

    var maxServerLoanAmount = 1_000_000.0

    var revolvingFee = 0.1 //日率の割合
    private var frequency = 3
    var lastPaymentCycle = 0

    private const val standardScore = 200


    fun checkServerLoan(p: Player){

        es.execute {
            val maxLoan = getLoanAmount(p)

            p.sendMessage("§f§l貸し出し可能上限額:§e§l${format(maxLoan)}円(最大:${format(maxServerLoanAmount)}円)")

            p.sendMessage(Component.text("§e§l§n[結果をシェアする]").clickEvent(ClickEvent.runCommand("/mrevo share")))

            shareMap[p] = maxLoan
        }
    }

    fun checkServerLoan(sender:Player,p:Player){

        es.execute {
            val maxLoan = getLoanAmount(p)

            sender.sendMessage("§f§l貸し出し可能上限額:§e§l${format(maxLoan)}円(最大:${format(maxServerLoanAmount)}円)")
        }

    }

    private fun getLoanAmount(p: Player): Double {
        val score = ScoreDatabase.getScore(p.uniqueId)

        val list = mutableListOf<Double>()

        val rs = mysql.query("select total from estate_history_tbl where uuid='${p.uniqueId}';") ?: return 0.0

        while (rs.next()) {
            list.add(rs.getDouble("total"))
        }

        rs.close()
        mysql.close()

        val rs2 = mysql.query("select count(*) from estate_history_tbl where uuid='${p.uniqueId}';") ?: return 0.0

        val records = if (rs2.next()) rs2.getInt(1) else 0

        rs2.close()
        mysql.close()

        list.sort()
        val m = list.size / 2

        val median: Double = if (list.size % 2 == 0) {
            (list[m] + list[m + 1]) / 2.0
        } else {
            list[m] + 1
        }

//        Bukkit.getLogger().info("スコア乗数${scoreMultiplier}現在のスコア${score}")
//        Bukkit.getLogger().info("レコード数の乗数${recordMultiplier}レコード数の合計${records}")
//        Bukkit.getLogger().info("中央値の乗数${medianMultiplier}中央値${median}")


        var calcAmount = median * medianMultiplier * score * scoreMultiplier * records * recordMultiplier

        if (calcAmount<0.0)calcAmount = 0.0

        return if (maxServerLoanAmount < calcAmount) maxServerLoanAmount else calcAmount
    }

    fun borrowingAmount(p:Player):Double{

        val rs = mysql.query("SELECT borrow_amount from server_loan_tbl where uuid='${p.uniqueId}'")?:return 0.0

        var ret = 0.0

        if (rs.next()){
            ret = rs.getDouble("borrow_amount")
        }

        rs.close()
        mysql.close()

        return ret
    }

    fun showBorrowMessage(p:Player,amount: Double){

        commandList.remove(p)

        if (amount <= 0.0){
            sendMsg(p,"1円以上を入力してください")
            return
        }

        val max = getLoanAmount(p)
        val borrowing = borrowingAmount(p)

        val borrowableAmount = max - borrowing

        if (borrowableAmount<amount){
            sendMsg(p,"§cあなたが借りることができる金額は${format(borrowableAmount)}円までです")
            p.sendMessage(Component.text("${prefix}§e§l§n[${format(borrowableAmount)}円借りる]").
            clickEvent(ClickEvent.runCommand("/mrevo borrow $borrowableAmount")))
            return
        }

        val allow = Component.text("${prefix}§c§l§n[借りる] ").clickEvent(ClickEvent.runCommand("/mrevo confirm ${floor(amount)}"))


        sendMsg(p,"§b§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")
        sendMsg(p,"§e§kXX§b§lMan10リボ§e§kXX")
        sendMsg(p,"§b貸し出される金額:${format(amount)}")
        sendMsg(p,"§b現在の利用額:${format(borrowing)}")
        sendMsg(p,"§b${frequency}日ごとに最低${format((borrowing+amount)*frequency*revolvingFee)}円支払う必要があります")
        p.sendMessage(allow)
        sendMsg(p,"§b§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")

        commandList.add(p)
    }

    fun borrow(p:Player, amount:Double){

        val max = getLoanAmount(p)
        val borrowing = borrowingAmount(p)


        if (amount <= 0.0){
            sendMsg(p,"1円以上を入力してください")
            return
        }


        val borrowableAmount = max - borrowing
        val minPaymentAmount = floor((borrowing+amount)*frequency*revolvingFee)

        if (borrowableAmount<amount){
            sendMsg(p,"§cあなたが借りることができる金額は${format(borrowableAmount)}円までです")
            p.sendMessage(Component.text("${prefix}§e§l§n[${borrowableAmount}円借りる]").
            clickEvent(ClickEvent.runCommand("/mrevo borrow $borrowableAmount")))
            return
        }

        val rs = mysql.query("SELECT payment_amount From server_loan_tbl where uuid='${p.uniqueId}'")?:return

        //初借金の場合
        if (!rs.next()){

            mysql.execute("INSERT INTO server_loan_tbl (player, uuid, borrow_date, last_pay_date, borrow_amount, payment_amount) " +
                    "VALUES ('${p.name}', '${p.uniqueId}', DEFAULT, DEFAULT, ${amount}, ${minPaymentAmount*2})")

            sendMsg(p,"""
                §e§l[返済について]
                §c§lMan10リボは、借りた日から${frequency}日ずつ銀行から引き落とされます
                §c§l支払いができなかった場合、スコアの減少などのペナルティがあるので、
                §c§l必ず銀行にお金を入れておくようにしましょう。
                §c§lまた、/mrevo payment <金額>で引き落とす額を設定できます。
            """.trimIndent())

        //2回目以降
        }else{
            mysql.execute(" UPDATE server_loan_tbl SET borrow_amount=borrow_amount+${amount},payment_amount=${minPaymentAmount*2} WHERE uuid = '${p.uniqueId}'")
        }

        rs.close()
        mysql.close()

        sendMsg(p,"§a§lお金を借りることができました！")

        vault.deposit(p.uniqueId,amount)

    }

    fun setPaymentAmount(p:Player,amount:Double){

        val now = borrowingAmount(p)
        val minPayment = now*frequency*revolvingFee

        if (amount <= 0.0){
            sendMsg(p,"1円以上を入力してください")
            return
        }

        if (now == 0.0){
            sendMsg(p,"§a§lあなたは現在Man10リボを使用していません")
            return
        }

        if (amount<minPayment){
            sendMsg(p,"支払額は最低${format(minPayment)}円にしてください")
            return
        }

        mysql.execute("UPDATE server_loan_tbl SET payment_amount=${amount} where uuid='${p.uniqueId}'")

        sendMsg(p,"支払額を変更しました！")

    }

    fun getPaymentAmount(p:Player):Double{

        val rs = mysql.query("select payment_amount from server_loan_tbl where uuid='${p.uniqueId}';")?:return 0.0
        if (!rs.next())return 0.0
        val amount = rs.getDouble("payment_amount")
        rs.close()
        mysql.close()
        return amount

    }

    fun paymentAll(p:Player){

        val rs = mysql.query("select last_pay_date,borrow_amount from server_loan_tbl where uuid='${p.uniqueId}'")?:return
        if (!rs.next()){
            sendMsg(p,"あなたはMan10リボを利用していません")
            return
        }

        val date = rs.getTimestamp("last_pay_date")
        val borrowing = rs.getDouble("borrow_amount")

        rs.close()
        mysql.close()

        val diffDay = round(((Date().time - date.time).toDouble() / (1000*60*60*24))).toInt()
        val payment = borrowing+(borrowing* revolvingFee*diffDay/ frequency)

        if (Bank.withdraw(p.uniqueId,payment, plugin,"Man10Revo","Man10リボの一括支払い")){
            mysql.execute("UPDATE server_loan_tbl set borrow_amount=0,last_pay_date=now()" +
                    " where uuid='${p.uniqueId}'")
            sendMsg(p,"§a§l支払い完了！")
            return
        }

        sendMsg(p,"所持金が足りません！銀行に${format(payment)}円以上入金してください！")

    }

    fun getNextPayTime(p:Player): Date? {

        val rs = mysql.query("select last_pay_date from server_loan_tbl where uuid='${p.uniqueId}';")?:return null

        if (!rs.next())return null

        val cal = Calendar.getInstance()
        cal.time = rs.getTimestamp("last_pay_date")

        rs.close()
        mysql.close()

        cal.add(Calendar.DAY_OF_MONTH,3)

        return cal.time
    }

    fun addLastPayTime(who:String,hour:Int):Int{

        if (who == "all"){
            mysql.execute("update server_loan_tbl set last_pay_date=DATE_ADD(last_pay_date,INTERVAL $hour HOUR)")
            return 0
        }

        val p = Bank.getUUID(who) ?: return 1

        mysql.execute("update server_loan_tbl set last_pay_date=DATE_ADD(last_pay_date,INTERVAL $hour HOUR) Where uuid='${p}'")

        return 0

    }

    //支払い処理
    fun paymentThread(){

        val now = Calendar.getInstance()

        while (true){

            now.time = Date()

            val nowValue = now.get(Calendar.DAY_OF_YEAR)

            if (nowValue != lastPaymentCycle){
                val rs = mysql.query("select * from server_loan_tbl where borrow_amount != 0")?:continue

                while (rs.next()){

                    val uuid = UUID.fromString(rs.getString("uuid"))
                    val borrowing = rs.getDouble("borrow_amount")
                    val payment = rs.getDouble("payment_amount")
                    val date = rs.getTimestamp("last_pay_date")

                    val diffDay = round((now.time.time - date.time).toDouble() / (1000*60*60*24)).toInt()

                    if (diffDay == 0 || diffDay%frequency!=0)continue

                    var finalAmount = borrowing-(payment - (borrowing* revolvingFee* diffDay))

                    if (finalAmount <0){ finalAmount = 0.0 }

                    if (Bank.withdraw(uuid,payment, plugin,"Man10Revolving","Man10リボの支払い")){

                        mysql.execute("UPDATE server_loan_tbl set borrow_amount=${floor(finalAmount)},last_pay_date=now()" +
                                " where uuid='${uuid}'")

                        continue
                    }

                    val score = ScoreDatabase.getScore(uuid)
                    val name = Bukkit.getOfflinePlayer(uuid).name!!

                    if (score> standardScore){
                        ScoreDatabase.setScore(name,(score/2),"まんじゅうリボの未払い",Bukkit.getConsoleSender())
                    }else{
                        ScoreDatabase.giveScore(name,-100,"まんじゅうリボの未払い",Bukkit.getConsoleSender())
                    }

                }

                rs.close()
                mysql.close()

                lastPaymentCycle = nowValue
                plugin.config.set("lastPaymentCycle",nowValue)
                plugin.saveConfig()

                Thread.sleep(60000)

            }

        }

    }

}
//TODO:全額返済追加
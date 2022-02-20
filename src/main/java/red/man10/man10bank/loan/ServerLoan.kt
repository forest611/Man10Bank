package red.man10.man10bank.loan

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import red.man10.man10bank.Bank
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.OP
import red.man10.man10bank.Man10Bank.Companion.format
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.prefix
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.MySQLManager
import red.man10.man10score.ScoreDatabase
import red.man10.man10score.ScoreDatabase.giveScore
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
import kotlin.math.round


object ServerLoan {


    //貸し出し金額を計算するための割合
    var scoreParam = 500.0
    var profitPercentage = 0.0
    var medianPercentage = 0.0

    var isEnable = true

    val shareMap = ConcurrentHashMap<Player,Double>()
    val commandList = mutableListOf<Player>()

    var maxServerLoanAmount = 1_000_000.0

    var revolvingFee = 0.1 //日率の割合
    private var frequency = 3
    var lastPaymentCycle = 0

    private const val standardScore = 200

    init {
        Bukkit.getLogger().info("StartPaymentThread")
        if (Man10Bank.paymentThread){
            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { paymentThread() })
        }
    }

    fun checkServerLoan(p: Player){

        val maxLoan = getLoanAmount(p)

        p.sendMessage("§f§l貸し出し可能上限額:§e§l${format(maxLoan)}円(最大:${format(maxServerLoanAmount)}円)")

        p.sendMessage(Component.text("§e§l§n[結果をシェアする]").clickEvent(ClickEvent.runCommand("/mrevo share")))

        shareMap[p] = maxLoan

    }

    fun checkServerLoan(sender:Player,p:Player){

        val maxLoan = getLoanAmount(p)

        sender.sendMessage("§f§l貸し出し可能上限額:§e§l${format(maxLoan)}円(最大:${format(maxServerLoanAmount)}円)")

    }

    private fun getLoanAmount(p:Player):Double{

        val score = ScoreDatabase.getScore(p.uniqueId)

        val mysql = MySQLManager(plugin,"Man10ServerLoan")

        val sdf = SimpleDateFormat("yyyy-MM-dd")
        val cal = Calendar.getInstance()
        cal.time = Date()
        cal.add(Calendar.DAY_OF_YEAR,-30)

        val rs = mysql.query("select avg(total) from estate_history_tbl where uuid='${p.uniqueId}' and date>'${sdf.format(cal.time)}' group by date_format(date,'%Y%m%d');")?:return 0.0

//        val first = if (rs.next()) {rs.getDouble(1)} else {0.0}
//        rs.afterLast()
//        val last = if (rs.previous()) { rs.getDouble(1) } else {0.0}


        val list = mutableListOf<Double>()

        while (rs.next()){ list.add(rs.getDouble(1)) }

        val first = if (list.isNotEmpty()) list[0] else 0.0
        val last = if (list.isNotEmpty()) list[list.size-1] else 0.0

        val profit = last-first
        val recordSize = list.size

        list.sort()
        val centerIndex = list.size / 2

        val median: Double = if (list.size % 2 == 0) {
            (list[centerIndex-1] + list[centerIndex]) / 2.0
        } else {
            list[centerIndex-1]
        }

        rs.close()
        mysql.close()

        //スコアの量によって最終的にかけられる金額が変わる(0なら借りれない) 1-1/(x/500+2)
        val scoreMulti = if (score<0) 0.0 else 1.0-1.0/((score.toDouble() / scoreParam)+2.0)

//        if (p.hasPermission(OP)){
//
//            sendMsg(p,"Perm S:${scoreParam},M:${medianPercentage},P:${profitPercentage}")
//            sendMsg(p,"Score:${score}")
//            sendMsg(p,"Median:${format(median)}")
//            sendMsg(p,"FirstAmount:${format(first)}")
//            sendMsg(p,"LastAmount:${format(last)}")
//            sendMsg(p,"MonthProfit:${format(profit)}")
//            sendMsg(p,"RecordSize:${recordSize}")
//            sendMsg(p,"Calculated:${format((profit*recordSize/30* profitPercentage)+(median* medianPercentage))}")
//
//        }

        var calcAmount = ((profit* profitPercentage)+(median* medianPercentage))*scoreMulti*recordSize/30

        if (calcAmount<0.0)calcAmount = 0.0

        return if (maxServerLoanAmount < calcAmount) maxServerLoanAmount else calcAmount
    }

//    private fun getLoanAmount(p: Player): Double {
//        val score = ScoreDatabase.getScore(p.uniqueId)
//
//        val list = mutableListOf<Double>()
//
//        val mysql = MySQLManager(plugin,"Man10ServerLoan")
//
//        val rs = mysql.query("select total from estate_history_tbl where uuid='${p.uniqueId}';") ?: return 0.0
//
//        while (rs.next()) {
//            list.add(rs.getDouble("total"))
//        }
//
//        rs.close()
//        mysql.close()
//
//        if (list.isEmpty()){ return 0.0 }
//
//        val rs2 = mysql.query("select count(*) from estate_history_tbl where uuid='${p.uniqueId}';") ?: return 0.0
//
//        val records = if (rs2.next()) rs2.getInt(1) else 0
//
//        rs2.close()
//        mysql.close()
//
//        list.sort()
//        val m = list.size / 2
//
//        val median: Double = if (list.size % 2 == 0) {
//            (list[m-1] + list[m]) / 2.0
//        } else {
//            list[m-1]
//        }
//
//        var calcAmount = median * medianMultiplier * score * scoreMultiplier * records * recordMultiplier
//
//        if (calcAmount<0.0)calcAmount = 0.0
//
//        return if (maxServerLoanAmount < calcAmount) maxServerLoanAmount else calcAmount
//    }

    fun getBorrowingAmount(p:Player):Double{

        val mysql = MySQLManager(plugin,"Man10ServerLoan")

        val rs = mysql.query("SELECT borrow_amount from server_loan_tbl where uuid='${p.uniqueId}'")?:return 0.0

        var ret = 0.0

        if (rs.next()){
            ret = rs.getDouble("borrow_amount")
        }

        rs.close()
        mysql.close()

        return ret
    }

//    fun getBorrowingAmount(uuid: UUID):Double{
//
//        val rs = mysql.query("SELECT borrow_amount from server_loan_tbl where uuid='${uuid}'")?:return 0.0
//
//        var ret = 0.0
//
//        if (rs.next()){
//            ret = rs.getDouble("borrow_amount")
//        }
//
//        rs.close()
//        mysql.close()
//
//        return ret
//    }

    fun showBorrowMessage(p:Player,amount: Double){

        commandList.remove(p)

        if (amount <= 0.0){
            sendMsg(p,"1円以上を入力してください")
            return
        }

        val max = getLoanAmount(p)
        val borrowing = getBorrowingAmount(p)

        val borrowableAmount = max - borrowing

        if (borrowableAmount<0.0){
            sendMsg(p,"§cあなたはもうお金を借りることができません！")
            return
        }

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
        sendMsg(p,"§c利息の計算方法:§l<利用額>x<金利>x<最後に支払ってからの日数>")
        sendMsg(p,"§c※支払額から利息を引いた額が返済に充てられます")
        sendMsg(p,"§b${frequency}日ごとに最低${format((borrowing+amount)*frequency*revolvingFee)}円支払う必要があります")
        p.sendMessage(allow)
        sendMsg(p,"§b§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")

        commandList.add(p)
    }

    fun borrow(p:Player, amount:Double){

        val max = getLoanAmount(p)
        val borrowing = getBorrowingAmount(p)


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

        val mysql = MySQLManager(plugin,"Man10ServerLoan")

        val rs = mysql.query("SELECT payment_amount From server_loan_tbl where uuid='${p.uniqueId}'")?:return

        //初借金の場合
        if (!rs.next()){

            mysql.execute("INSERT INTO server_loan_tbl (player, uuid, borrow_date, last_pay_date, borrow_amount, payment_amount) " +
                    "VALUES ('${p.name}', '${p.uniqueId}', DEFAULT, DEFAULT, ${amount}, ${minPaymentAmount*2})")

            p.sendMessage("""
                §e§l[返済について]
                §c§lMan10リボは、借りた日から${frequency}日ずつ銀行から引き落とされます
                §c§l支払いができなかった場合、スコアの減少などのペナルティがあるので、
                §c§l必ず銀行にお金を入れておくようにしましょう。
                §c§lまた、/mrevo payment <金額>で引き落とす額を設定できます。
            """.trimIndent())

        //2回目以降
        }else if (borrowing==0.0){
            mysql.execute(" UPDATE server_loan_tbl SET borrow_amount=${amount}, borrow_date=now(), " +
                    "last_pay_date=now(),payment_amount=${minPaymentAmount*2} WHERE uuid = '${p.uniqueId}'")
        }else{
            mysql.execute(" UPDATE server_loan_tbl SET borrow_amount=borrow_amount+${amount}" +
                    ",payment_amount=${minPaymentAmount*2} WHERE uuid = '${p.uniqueId}'")
        }

        rs.close()
        mysql.close()

        sendMsg(p,"§a§lお金を借りることができました！")

        vault.deposit(p.uniqueId,amount)

    }

    fun setPaymentAmount(p:Player,amount:Double){

        val nowBorrowing = getBorrowingAmount(p)
        val minPayment = nowBorrowing*frequency*revolvingFee

        if (amount <= 0.0){
            sendMsg(p,"1円以上を入力してください")
            return
        }

        if (nowBorrowing == 0.0){
            sendMsg(p,"§a§lあなたは現在Man10リボを使用していません")
            return
        }

        if (amount<minPayment){
            sendMsg(p,"支払額は最低${format(minPayment)}円にしてください")
            return
        }

        if (amount>nowBorrowing){
            sendMsg(p,"支払額が利用額を上回っています 一括返済する時は/mrevo payallコマンドを使ってください")
            return

        }

        val mysql = MySQLManager(plugin,"Man10ServerLoan")

        mysql.execute("UPDATE server_loan_tbl SET payment_amount=${amount} where uuid='${p.uniqueId}'")

        sendMsg(p,"支払額を変更しました！")

    }

    fun getPaymentAmount(p:Player):Double{

        val mysql = MySQLManager(plugin,"Man10ServerLoan")

        val rs = mysql.query("select payment_amount from server_loan_tbl where uuid='${p.uniqueId}';")?:return 0.0
        if (!rs.next()){
            mysql.close()
            rs.close()
            return 0.0
        }
        val amount = rs.getDouble("payment_amount")
        rs.close()
        mysql.close()
        return amount

    }

    fun paymentAll(p:Player){

        val mysql = MySQLManager(plugin,"Man10ServerLoan")

        val rs = mysql.query("select last_pay_date,borrow_amount from server_loan_tbl where uuid='${p.uniqueId}'")?:return
        if (!rs.next()){
            mysql.close()
            rs.close()
            sendMsg(p,"あなたはMan10リボを利用していません")
            return
        }

        val date = rs.getTimestamp("last_pay_date")
        val borrowing = rs.getDouble("borrow_amount")

        rs.close()
        mysql.close()

        val diffDay = round(((Date().time - date.time).toDouble() / (1000*60*60*24))).toInt()
        val payment = borrowing+(borrowing* revolvingFee*diffDay)

        if (Bank.withdraw(p.uniqueId,payment, plugin,"Man10Revo","Man10リボの一括支払い").first==0){
            mysql.execute("UPDATE server_loan_tbl set borrow_amount=0,last_pay_date=now()" +
                    " where uuid='${p.uniqueId}'")
            sendMsg(p,"§a§l支払い完了！")
            return
        }

        sendMsg(p,"所持金が足りません！銀行に${format(payment)}円以上入金してください！")

    }

    fun getNextPayTime(p:Player): Pair<Date,Int>? {

        val mysql = MySQLManager(plugin,"Man10ServerLoan")

        val rs = mysql.query("select last_pay_date,failed_payment from server_loan_tbl where uuid='${p.uniqueId}';")?:return null

        if (!rs.next()){
            mysql.close()
            rs.close()
            return null
        }

        val last = rs.getTimestamp("last_pay_date")
        val failedCount = rs.getInt("failed_payment")

        rs.close()
        mysql.close()

        val next = Calendar.getInstance()
        next.time = last

        next.add(Calendar.DAY_OF_MONTH,frequency)

        return Pair(next.time,failedCount)
    }

    fun addLastPayTime(who:String,hour:Int):Int{

        val mysql = MySQLManager(plugin,"Man10ServerLoan")

        if (who == "all"){
            mysql.execute("update server_loan_tbl set last_pay_date=DATE_ADD(last_pay_date,INTERVAL $hour HOUR)")
            return 0
        }

        val p = Bank.getUUID(who) ?: return 1

        mysql.execute("update server_loan_tbl set last_pay_date=DATE_ADD(last_pay_date,INTERVAL $hour HOUR) Where uuid='${p}'")

        return 0

    }

    fun getLoanTop(page:Int): MutableList<Pair<String, Double>> {

        val list = mutableListOf<Pair<String,Double>>()

        val mysql = MySQLManager(plugin,"Man10ServerLoan")

        val rs = mysql.query("SELECT player,borrow_amount FROM server_loan_tbl order by borrow_amount desc limit 10 offset ${(page*10)-10};")?:return list

        while (rs.next()){

            val p = rs.getString("player")
            val borrowAmount = rs.getDouble("borrow_amount")

            list.add(Pair(p,borrowAmount))
        }

        rs.close()
        mysql.close()

        return list
    }


    //支払い処理
    private fun paymentThread(){

        val now = Calendar.getInstance()

        while (true){

            now.time = Date()

            val nowValue = now.get(Calendar.DAY_OF_YEAR)

            if (nowValue != lastPaymentCycle){

                lastPaymentCycle = nowValue
                plugin.config.set("revolving.lastPaymentCycle",nowValue)
                plugin.saveConfig()

                batch()
            }

            Thread.sleep(60000)

        }

    }

    private fun batch(){

        val now = Date()

        Bukkit.getScheduler().runTask(plugin, Runnable { Bukkit.broadcast(Component.text("§e§lMan10リボの支払い処理開始")) })

        val sql = MySQLManager(plugin,"Man10ServerLoan")

        val rs = sql.query("select * from server_loan_tbl where borrow_amount != 0")

        if (rs == null){
            sql.close()
            return
        }

        while (rs.next()){

            val uuid = UUID.fromString(rs.getString("uuid"))
            val borrowing = rs.getDouble("borrow_amount")
            val payment = rs.getDouble("payment_amount")
            val date = rs.getTimestamp("last_pay_date")

            val p = Bukkit.getOfflinePlayer(uuid)

            val diffDay = dateDiff(date,now)

            if (diffDay<frequency)continue

            //利息
            val interest = borrowing* revolvingFee * diffDay
            //残った利用額
            var finalAmount = borrowing-(payment - interest)

            if (finalAmount <0){ finalAmount = 0.0 }

            if (Bank.withdraw(uuid,payment, plugin,"Man10Revolving","Man10リボの支払い").first==0){

                sql.execute("UPDATE server_loan_tbl set borrow_amount=${floor(finalAmount)},last_pay_date=now()" +
                        " where uuid='${uuid}'")

                if (p.isOnline){
                    sendMsg(p.player!!,"§a§lMan10リボの支払いができました")
                    if (finalAmount == 0.0){ sendMsg(p.player!!,"§a§lMan10リボの利用額が0円になりました！") }
                }
                continue
            }

            sql.execute("UPDATE server_loan_tbl set borrow_amount=${floor(borrowing+interest)},last_pay_date=now()," +
                    "failed_payment=failed_payment+1 where uuid='${uuid}'")

            if (p.isOnline){
                sendMsg(p.player!!,"§c§lMan10リボの支払いに失敗！スコアが減りました")
                sendMsg(p.player!!,"§c§lスコアが減り、支払えなかった利息が追加されました")
            }

            val score = ScoreDatabase.getScore(uuid)
            val name = Bukkit.getOfflinePlayer(uuid).name!!

            if (score> standardScore){
                giveScore(name,-(score/2),"まんじゅうリボの未払い",Bukkit.getConsoleSender())
            }else if ((score-100)>-300){
                giveScore(name,-100,"まんじゅうリボの未払い",Bukkit.getConsoleSender())
            }

        }

        rs.close()
        sql.close()


        Bukkit.getScheduler().runTask(plugin, Runnable { Bukkit.broadcast(Component.text("§e§lMan10リボの支払い処理終了")) })

    }

    private fun dateDiff(from: Date, to: Date): Int {
        // 差分の日数を計算する
        val dateTimeTo = to.time
        val dateTimeFrom = from.time
        return (dateTimeTo / (1000 * 60 * 60 * 24)).toInt() - (dateTimeFrom / (1000 * 60 * 60 * 24)).toInt()
    }
}
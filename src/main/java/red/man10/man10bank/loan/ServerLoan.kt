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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ServerLoan {

    private val mysql = MySQLManager(plugin,"Man10ServerLoan")

    var scoreMultiplier = 1.0//スコアの乗数
    var recordMultiplier = 1.0//レコード数の乗数
    var medianMultiplier = 1.0//中央値の乗数

    val shareMap = ConcurrentHashMap<Player,Double>()
    val commandList = mutableListOf<Player>()

    var maxServerLoanAmount = 1_000_000.0

    var revolvingFee = 0.1 //日率の割合
    var frequency = 3
    var lastPaymentCycle = Date()

    fun checkServerLoan(p: Player){

        es.execute {
            val maxLoan = getLoanAmount(p)

            p.sendMessage("§f§l貸し出し可能上限額:§e§l${format(maxLoan)}円(最大:${format(maxServerLoanAmount)}円)")

            p.sendMessage(Component.text("§e§l§n[結果をシェアする]").clickEvent(ClickEvent.runCommand("/slend share")))

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
//        val score = 10

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

        Bukkit.getLogger().info("スコア乗数${scoreMultiplier}現在のスコア${score}")
        Bukkit.getLogger().info("レコード数の乗数${recordMultiplier}レコード数の合計${records}")
        Bukkit.getLogger().info("中央値の乗数${medianMultiplier}中央値${median}")


        val calcAmount = median * medianMultiplier * score * scoreMultiplier * records * recordMultiplier

        return if (maxServerLoanAmount < calcAmount) maxServerLoanAmount else calcAmount
    }

    private fun borrowingAmount(p:Player):Double{

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

        val max = getLoanAmount(p)
        val borrowing = borrowingAmount(p)

        val borrowableAmount = max - borrowing

        if (borrowableAmount<amount){
            sendMsg(p,"§cあなたが借りることができる金額は${format(borrowableAmount)}円までです")
            p.sendMessage(Component.text("${prefix}§e§l§n[${borrowableAmount}円借りる]").
            clickEvent(ClickEvent.runCommand("/slend borrow $borrowableAmount")))
            return
        }

        val allow = Component.text("${prefix}§c§l§n[借りる] ").clickEvent(ClickEvent.runCommand("/slend confirm $amount"))


        sendMsg(p,"§b§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")
        sendMsg(p,"§e§kXX§b§lMan10リボ§e§kXX")
        sendMsg(p,"§b貸し出される金額:${format(amount)}")
        sendMsg(p,"§b現在の利用額:${format(borrowing)}")
        sendMsg(p,"§b${frequency}日ごとに最低${format(amount*frequency*revolvingFee)}円支払う必要があります")
        p.sendMessage(allow)
        sendMsg(p,"§b§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")

        commandList.add(p)
    }

    fun borrow(p:Player, amount:Double){

        val max = getLoanAmount(p)
        val borrowing = borrowingAmount(p)

        val borrowableAmount = max - borrowing

        if (borrowableAmount<amount){
            sendMsg(p,"§cあなたが借りることができる金額は${format(borrowableAmount)}円までです")
            p.sendMessage(Component.text("${prefix}§e§l§n[${borrowableAmount}円借りる]").
            clickEvent(ClickEvent.runCommand("/slend borrow $borrowableAmount")))
            return
        }

        val rs = mysql.query("SELECT payment_amount From server_loan_tbl where uuid='${p.uniqueId}'")?:return

        //初借金の場合
        if (!rs.next()){

            mysql.execute("INSERT INTO server_loan_tbl (player, uuid, borrow_date, last_pay_date, borrow_amount, payment_amount) " +
                    "VALUES ('${p.name}', '${p.uniqueId}', DEFAULT, DEFAULT, ${amount}, ${amount*frequency*revolvingFee*2})")

            sendMsg(p,"""
                §e§l[返済について]
                §c§lMan10リボは、借りた日から${frequency}日ずつ銀行から引き落とされます
                §c§l支払いができなかった場合、スコアの減少などのペナルティがあるので、
                §c§l必ず銀行にお金を入れておくようにしましょう。
                §c§lまた、/slend payment <金額>で引き落とす額を設定できます。
            """.trimIndent())

        //2回目以降
        }else{

            //支払額を引き上げる
            val payment = rs.getDouble("payment_amount")
            val q = if (payment<(borrowing+amount)*frequency*revolvingFee)"payment_amount=${amount*frequency*revolvingFee}" else ""

            mysql.execute(" UPDATE server_loan_tbl SET borrow_amount=borrow_amount+${amount} $q WHERE uuid = '${p.uniqueId}'")
        }

        rs.close()
        mysql.close()

        sendMsg(p,"§a§lお金を借りることができました！")

        vault.deposit(p.uniqueId,amount)

    }

    fun setPaymentAmount(p:Player,amount:Double){

        val now = borrowingAmount(p)

        if (now == 0.0){
            sendMsg(p,"§a§lあなたは現在Man10リボを使用していません")
            return
        }

        if (amount<amount*frequency*revolvingFee){
            sendMsg(p,"支払額は最低${format(amount*frequency*revolvingFee)}円にしてください")
            return
        }

        mysql.execute("UPDATE server_loan_tbl SET payment_amount=${amount} where uuid='${p.uniqueId}'")

        sendMsg(p,"支払額を変更しました！")

    }

    //支払い処理
    fun paymentThread(){

        val now = Calendar.getInstance()
        val last = Calendar.getInstance()

        while (true){

            now.time = Date()
            last.time = lastPaymentCycle

            if (now.get(Calendar.DAY_OF_MONTH) != last.get(Calendar.DAY_OF_MONTH)){

                val rs = mysql.query("select * from server_loan_tbl where borrow_amount != 0")?:continue

                while (rs.next()){

                    val uuid = UUID.fromString(rs.getString("uuid"))
                    val borrowing = rs.getDouble("borrow_amount")
                    val payment = rs.getDouble("payment_amount")
                    val date = rs.getTimestamp("last_pay_date")

                    val diffDay = (borrowing*(now.time.time - date.time) / (1000*60*60*24)).toInt()
                    val finalAmount = borrowing-(payment - (borrowing* revolvingFee* diffDay))

                    Bukkit.getLogger().info("$diffDay")

                    if (diffDay%frequency!=0)continue

                    if (Bank.withdraw(uuid,payment, plugin,"Man10Revolving","Man10リボの支払い")){

                        mysql.execute("UPDATE server_loan_tbl set borrow_amount=${finalAmount},last_pay_date=now()" +
                                " where uuid='${uuid}'")
                        Bukkit.getLogger().info("支払えた！")

                        continue
                    }

                    Bukkit.getLogger().info("支払えなかった！")
                    //TODO:支払わなかった時の処理

                }

                rs.close()
                mysql.close()

            }

            Thread.sleep(60000)
        }

    }

}
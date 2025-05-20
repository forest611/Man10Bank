package red.man10.man10bank.loan

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import red.man10.man10bank.Bank
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.format
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.prefix
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.MySQLManager
import red.man10.man10bank.loan.repository.ServerLoanRepository
import red.man10.man10score.ScoreDatabase
import red.man10.man10score.ScoreDatabase.giveScore
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round


object ServerLoan {


    //貸し出し金額を計算するための割合
    var lendParameter : Double = 3.55
    var borrowStandardScore : Int = 1
    private const val loserStandardScore : Int = 200

    var isEnable = true

    val shareMap = ConcurrentHashMap<Player,Double>()
    val commandList = mutableListOf<Player>()

    val maximumOfLoginTime = ConcurrentHashMap<Int,Double>()

    var maxServerLoanAmount : Double = 1_000_000.0
    var minServerLoanAmount : Double = 50000.0

    var revolvingFee = 0.1 //日率の割合
    private var frequency = 3
    var lastPaymentCycle = 0


    init {
        Bukkit.getLogger().info("StartPaymentThread")
        if (Man10Bank.paymentThread){
            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { paymentThread() })
        }
    }

    fun checkServerLoan(p: Player){

        val maxLoan = getMaximumLoanAmount(p)

        p.sendMessage("§f§l貸し出し可能上限額:§e§l${format(maxLoan)}円(最大:${format(maxServerLoanAmount)}円)")

        val shareButton = Component.text("§e§l§n[結果をシェアする]").clickEvent(ClickEvent.runCommand("/mrevo share"))
        val borrowButton = Component.text(" §e§l§n[${format(maxLoan)}円借りる]").clickEvent(ClickEvent.runCommand("/mrevo borrow $maxLoan"))

        p.sendMessage(shareButton.append(borrowButton))


        shareMap[p] = maxLoan

    }

    fun checkServerLoan(sender:Player,p:Player){

        val maxLoan = getMaximumLoanAmount(p)

        sender.sendMessage("§f§l貸し出し可能上限額:§e§l${format(maxLoan)}円(最大:${format(maxServerLoanAmount)}円)")

    }

    private fun getMaximumLoanAmount(p:Player):Double{

        val score = ScoreDatabase.getScore(p.uniqueId)

        val mysql = MySQLManager(plugin,"Man10ServerLoan")

        val sdf = SimpleDateFormat("yyyy-MM-dd")
        val cal = Calendar.getInstance()
        cal.time = Date()
        cal.add(Calendar.DAY_OF_YEAR,-30)

        val rs = mysql.query("select avg(total) from estate_history_tbl " +
                "where uuid='${p.uniqueId}' and date>'${sdf.format(cal.time)}' group by date_format(date,'%Y%m%d');")?:return 0.0

        val list = mutableListOf<Double>()

        while (rs.next()){
            list.add(rs.getDouble(1))
        }

        list.sort()
        val centerIndex = list.size / 2

        val median: Double = if(list.size == 0){
            0.0
        } else if (list.size % 2 == 0) {
            (list[centerIndex-1] + list[centerIndex]) / 2.0
        } else {
            list[max(centerIndex-1,0)]
        }

        rs.close()
        mysql.close()

        //（無条件で借りられる額）+〔(一カ月の残高中央値-スコアによる天引き)×3.55］＝貸出可能金額
        //債務者のスコア/基準スコア

        var scoreMulti = score.toDouble()/ max(borrowStandardScore,1)

        if (scoreMulti > 1.0) scoreMulti = 1.0

        var calcAmount = minServerLoanAmount+ (median*scoreMulti* lendParameter)

        if (calcAmount<0.0)calcAmount = 0.0

        calcAmount += minServerLoanAmount

        //ログイン時間による借りられる額の上限
        val totalLoginHour = ScoreDatabase.getConnectingSeconds(p.uniqueId)/3600.0

        val totalLoginMaximumPrice = maximumOfLoginTime
            .filter { it.key <= totalLoginHour }
            .maxByOrNull { it.value }
            ?.value ?: maxServerLoanAmount

        val maxAmount = min(maxServerLoanAmount,totalLoginMaximumPrice)

        //最大借りられる額と計算された借りられる額の小さい方を返す
        return min(calcAmount,maxAmount)
    }

    fun getBorrowingAmount(p:Player):Double{
        return getBorrowingAmount(p.uniqueId)
    }

    fun getBorrowingAmount(uuid: UUID):Double{
        return ServerLoanRepository.fetchLoan(uuid)?.borrowAmount ?: 0.0
    }

    fun showBorrowMessage(p:Player,amount: Double){

        commandList.remove(p)

        if (amount <= 0.0){
            sendMsg(p,"1円以上を入力してください")
            return
        }

        if (borrowedSubAccount(p.uniqueId)){
            sendMsg(p,"§c同一IPの他プレイヤーがすでに借入を行っています")
            return
        }

        val max = getMaximumLoanAmount(p)
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

    @Synchronized
    fun borrow(p:Player, amount:Double){

        if (borrowedSubAccount(p.uniqueId)){
            sendMsg(p,"§c同一IPの他プレイヤーがすでに借入を行っています")
            return
        }

        val record = ServerLoanRepository.fetchLoan(p.uniqueId)

        if (record == null) {
            val minimumPaymentAmount = floor(amount * frequency * revolvingFee)
            val created = ServerLoanRepository.insertLoan(p.name, p.uniqueId, amount, minimumPaymentAmount * 2)
            if (!created) {
                sendMsg(p, "§c§l銀行への問い合わせに失敗しました。運営に報告してください。- 01")
                return
            }

            p.sendMessage("""
                §e§l[返済について]
                §c§lMan10リボは、借りた日から${frequency}日ずつ銀行から引き落とされます
                §c§l支払いができなかった場合、スコアの減少などのペナルティがあるので、
                §c§l必ず銀行にお金を入れておくようにしましょう。
                §c§lまた、/mrevo payment <金額>で引き落とす額を設定できます。
            """.trimIndent())
        } else {
            val minimumPaymentAmount = floor((record.borrowAmount + amount) * frequency * revolvingFee)
            val updated = ServerLoanRepository.updateLoan(p.uniqueId, record.borrowAmount + amount, minimumPaymentAmount * 2)
            if (!updated) {
                sendMsg(p, "§c§l銀行への問い合わせに失敗しました。運営に報告してください。- 02")
                return
            }
        }

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

        ServerLoanRepository.setPaymentAmount(p.uniqueId, amount)

        sendMsg(p,"支払額を変更しました！")

    }

    fun getPaymentAmount(p:Player):Double{

        return ServerLoanRepository.fetchLoan(p.uniqueId)?.paymentAmount ?: 0.0

    }

    @Synchronized
    fun paymentAll(p:Player){

        val record = ServerLoanRepository.fetchLoan(p.uniqueId)
        if (record == null) {
            sendMsg(p,"あなたはMan10リボを利用していません")
            return
        }

        val diffDay = round(((Date().time - record.lastPayDate.time).toDouble() / (1000*60*60*24))).toInt()
        val payment = record.borrowAmount + (record.borrowAmount * revolvingFee * diffDay)

        if (Bank.withdraw(p.uniqueId,payment, plugin,"Man10Revo","Man10リボの一括支払い").first==0){
            ServerLoanRepository.setBorrowAmountZero(p.uniqueId)
            sendMsg(p,"§a§l支払い完了！")
            return
        }

        sendMsg(p,"所持金が足りません！銀行に${format(payment)}円以上入金してください！")

    }

    fun getNextPayTime(p:Player): Pair<Date,Int>? {
        val record = ServerLoanRepository.fetchLoan(p.uniqueId) ?: return null

        val next = Calendar.getInstance()
        next.time = record.lastPayDate
        next.add(Calendar.DAY_OF_MONTH,frequency)

        return Pair(next.time,record.failedPayment)
    }

    fun addLastPayTime(who:String,hour:Int):Int{

        return ServerLoanRepository.addLastPayTime(who,hour)
    }

        return ServerLoanRepository.fetchLoanTop(page)
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
        Bukkit.getScheduler().runTask(plugin) { Bukkit.broadcast(Component.text("§e§lMan10リボの支払い処理開始")) }

        val records = ServerLoanRepository.fetchActiveLoans()

        for (record in records) {
            val uuid = record.uuid
            val p = Bukkit.getOfflinePlayer(uuid)
            val diffDay = dateDiff(record.lastPayDate, now)
            if (diffDay < frequency) continue

            val interest = record.borrowAmount * revolvingFee * diffDay
            var finalAmount = record.borrowAmount - (record.paymentAmount - interest)
            if (finalAmount < 0) finalAmount = 0.0

            if (Bank.withdraw(uuid, record.paymentAmount, plugin,"Man10Revolving","Man10リボの支払い").first==0){
                ServerLoanRepository.updateAfterSuccess(uuid, finalAmount)
                if (p.isOnline){
                    sendMsg(p.player!!,"§a§lMan10リボの支払いができました")
                    if (finalAmount == 0.0){ sendMsg(p.player!!,"§a§lMan10リボの利用額が0円になりました！") }
                }
                continue
            }

            val score = ScoreDatabase.getScore(uuid)
            val name = p.name?:continue
            if (score<-300){
                Bukkit.getLogger().info("スコア-300以下なので支払い処理通過 mcid:$name score:$score")
                continue
            }
            if (score> loserStandardScore){
                giveScore(name,-(score/2),"まんじゅうリボの未払い",Bukkit.getConsoleSender())
            }else if ((score-100)>-300){
                giveScore(name,-100,"まんじゅうリボの未払い",Bukkit.getConsoleSender())
            }
            ServerLoanRepository.updateAfterFailure(uuid, record.borrowAmount + interest)
            if (p.isOnline){
                sendMsg(p.player!!,"§c§lMan10リボの支払いに失敗！スコアが減りました")
                sendMsg(p.player!!,"§c§lスコアが減り、支払えなかった利息が追加されました")
            }
        }

        Bukkit.getScheduler().runTask(plugin) { Bukkit.broadcast(Component.text("§e§lMan10リボの支払い処理終了")) }

    }

    fun isLoser(p:Player):Boolean{

        val score = ScoreDatabase.getScore(p.uniqueId)

        if (score>=0)return false

        val nextDate = getNextPayTime(p) ?:return false

        if (nextDate.second>0 && getBorrowingAmount(p) >0){
            return true
        }


        return false

    }

    fun borrowedSubAccount(uuid: UUID):Boolean{

        val account = ScoreDatabase.getSubAccount(uuid)

        for (id in account){
            if (id==uuid)continue
            if (getBorrowingAmount(id)>0){
                return true
            }
        }
        return false
    }

    private fun dateDiff(from: Date, to: Date): Int {
        // 差分の日数を計算する
        val dateTimeTo = to.time
        val dateTimeFrom = from.time
        return (dateTimeTo / (1000 * 60 * 60 * 24)).toInt() - (dateTimeFrom / (1000 * 60 * 60 * 24)).toInt()
    }
}

package red.man10.man10bank.loan

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank.Companion.format
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.MySQLManager
import red.man10.man10score.ScoreDatabase
import java.util.concurrent.ConcurrentHashMap

object ServerLoan {

    private val mysql = MySQLManager(plugin,"Man10ServerLoan")

    var scoreMultiplier = 1.0//スコアの乗数
    var recordMultiplier = 1.0//レコード数の乗数
    var medianMultiplier = 1.0//中央値の乗数

    val shareMap = ConcurrentHashMap<Player,Double>()

    var maxServerLoanAmount = 1_000_000.0

    fun checkServerLoan(p: Player){

        val score = ScoreDatabase.getScore(p.uniqueId)
//        val score = 10

        val list = mutableListOf<Double>()


        val rs = mysql.query("select total from estate_history_tbl where uuid='${p.uniqueId}';")?:return

        while (rs.next()){
            list.add(rs.getDouble("total"))
        }

        rs.close()
        mysql.close()

        val rs2 = mysql.query("select count(*) from estate_history_tbl where uuid='${p.uniqueId}';")?:return

        val records = if (rs2.next())rs2.getInt(1) else 0

        mysql.close()
        rs2.close()

        list.sort()
        val m = list.size/2

        val median : Double = if (list.size%2 == 0){
            (list[m]+list[m+1])/2.0
        }else{
            list[m]+1
        }

        broadcastOnMainThread("§e§l[まんじゅう銀行AI]§7現在過去の資産データを取得中・・・§kX")

        Thread.sleep(3000)

        broadcastOnMainThread("§e§l[まんじゅう銀行AI]§7${p.name}の資産データの取得完了§8(こっ、、こいつ金持ってねぇなぁ、、、w)")


        Thread.sleep(3000)

        broadcastOnMainThread("§e§l[まんじゅう銀行AI]§7過去の犯罪データを調査中・・・§kX")

        Thread.sleep(3000)

        broadcastOnMainThread("§e§l[まんじゅう銀行AI]§7${p.name}の犯罪データの取得完了§8(なんだこの犯罪履歴は、、、！)")

        Thread.sleep(3000)

        broadcastOnMainThread("§e§l[まんじゅう銀行AI]§7公的ローンの貸し出し可能金額を計算中・・・§kX")

        Thread.sleep(5000)

        broadcastOnMainThread("§e§l[まんじゅう銀行AI]§7計算完了！")

        Thread.sleep(1000)

        Bukkit.getLogger().info("スコア乗数${scoreMultiplier}現在のスコア${score}")
        Bukkit.getLogger().info("レコード数の乗数${recordMultiplier}レコード数の合計${records}")
        Bukkit.getLogger().info("中央値の乗数${medianMultiplier}中央値${median}")

        val calcAmount = median * medianMultiplier * score* scoreMultiplier * records * recordMultiplier
        val maxLoan = if (maxServerLoanAmount<calcAmount) maxServerLoanAmount else calcAmount

        Bukkit.getLogger().info("MaxServerLoan:${format(maxLoan)}")

        p.sendMessage("§f§l貸し出し可能上限額:§e§l${format(maxLoan)}円(最大:${format(maxServerLoanAmount)}円)")

        p.sendMessage(Component.text("§e§l§n[結果をシェアする]").clickEvent(ClickEvent.runCommand("/slend share")))

        shareMap[p] = maxLoan
    }

    fun checkServerLoan(sender:Player,p:Player){

        val score = ScoreDatabase.getScore(p.uniqueId)
//        val score = 10

        val list = mutableListOf<Double>()

        val rs = mysql.query("select total from estate_history_tbl where uuid='${p.uniqueId}';")?:return

        while (rs.next()){
            list.add(rs.getDouble("total"))
        }

        rs.close()
        mysql.close()

        val rs2 = mysql.query("select count(*) from estate_history_tbl where uuid='${p.uniqueId}';")?:return

        val records = if (rs2.next())rs2.getInt(1) else 0

        rs2.close()
        mysql.close()

        list.sort()
        val m = list.size/2

        val median : Double = if (list.size%2 == 0){
            (list[m]+list[m+1])/2.0
        }else{
            list[m]+1
        }


        sender.sendMessage("スコア乗数${scoreMultiplier}現在のスコア${score}")
        sender.sendMessage("レコード数の乗数${recordMultiplier}レコード数の合計${records}")
        sender.sendMessage("中央値の乗数${medianMultiplier}中央値${median}")

        val calcAmount = median * medianMultiplier * score* scoreMultiplier * records * recordMultiplier
        val maxLoan = if (maxServerLoanAmount<calcAmount) maxServerLoanAmount else calcAmount

        Bukkit.getLogger().info("MaxServerLoan:${format(maxLoan)}")

        sender.sendMessage("§f§l貸し出し可能上限額:§e§l${format(maxLoan)}円(最大:${format(maxServerLoanAmount)}円)")

//        p.sendMessage(Component.text("§e§l§n[結果をシェアする]").clickEvent(ClickEvent.runCommand("/slend share")))

//        shareMap[p] = maxLoan


    }

    private fun broadcastOnMainThread(msg:String){
        Bukkit.getScheduler().runTask(plugin, Runnable { Bukkit.broadcast(Component.text(msg)) })
    }

}
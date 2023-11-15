package red.man10.man10bank.bank

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.threadPool
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.Permissions
import red.man10.man10bank.api.APIBank
import red.man10.man10bank.api.APIHistory
import red.man10.man10bank.api.APILocalLoan
import red.man10.man10bank.api.APIServerLoan
import red.man10.man10bank.history.EstateHistory
import red.man10.man10bank.util.Utility
import red.man10.man10bank.util.Utility.format
import red.man10.man10bank.util.Utility.msg
import red.man10.man10bank.util.Utility.prefix
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.abs

/**
 *  銀行の残高を閲覧したりするコマンド
 */
object BankCommand : CommandExecutor{

    val labels = arrayOf("bal","balance","bank","money")

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (sender !is Player)return true

        if (!labels.contains(label)){ return true }

        //所持金確認コマンド
        if (args.isEmpty()){
            asyncShowBalance(sender,sender.uniqueId)
            return true
        }

        when(args[0]){

            "help" ->{
                showCommand(sender)
                return true
            }

            "log" ->{
                val page  = if (args.size == 1) 0 else args[1].toIntOrNull()?:0
                asyncShowLog(sender.uniqueId,sender,page)
            }

            "bs" -> {
                asyncShowBalanceSheet(sender.uniqueId,sender)
            }

            /////////運営用コマンド/////////

            //人の所持金を見る
            "user" ->{
                if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return true

                threadPool.execute {
                    val uuid = APIBank.getUUID(args[1])
                    if (uuid == null){
                        msg(sender,"ログイン履歴がない可能性があります")
                        return@execute
                    }
                    asyncShowBalance(sender,uuid)
                }
            }

            "logop" ->{//bal logop forest611 0
                if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return true

                val page  = if (args.size == 2) 0 else args[2].toIntOrNull()?:0
                threadPool.execute {
                    val uuid = APIBank.getUUID(args[1])
                    if (uuid == null){
                        msg(sender,"プレイヤーが見つかりません")
                        return@execute
                    }
                    asyncShowLog(uuid,sender,page)
                }
            }

            "give" ->{
                if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return true

                if (args.size< 3){
                    msg(sender,"§a§l/${label} give <player> <金額>")
                    return true
                }

                val uuid = APIBank.getUUID(args[1])
                val amount = Utility.fixedPerse(args[2])

                if (uuid == null){
                    msg(sender,"ユーザーが見つかりませんでした")
                    return true
                }

                if (amount==null){
                    msg(sender,"§c§l数字で入力してください！")
                    return true
                }

                if (amount < 1){
                    msg(sender,"§c§l1円以上を入力してください！")
                    return true
                }

                threadPool.execute {
                    val result = APIBank.addBank(APIBank.TransactionData(
                        uuid.toString(),
                        amount,
                        Man10Bank.instance.name,
                        "GivenFromServer",
                        "サーバーから発行"
                    ))

                    if (result != APIBank.BankResult.SUCCESSFUL){
                        msg(sender,"§c入金エラーが発生しました")
                        return@execute
                    }
                    msg(sender,"§e入金できました！")
                }
            }

            "take" ->{
                if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return true

                if (args.size< 3){
                    msg(sender,"§a§l/${label} take <player> <金額>")
                    return true
                }

                val amount = Utility.fixedPerse(args[2])
                val uuid = APIBank.getUUID(args[1])

                if (uuid == null){
                    msg(sender,"ユーザーが見つかりませんでした")
                    return true
                }

                if (amount==null){
                    msg(sender,"§c§l数字で入力してください！")
                    return true
                }

                if (amount < 1){
                    msg(sender,"§c§l1円以上を入力してください！")
                    return true
                }

                threadPool.execute {
                    val result = APIBank.takeBank(APIBank.TransactionData(
                        uuid.toString(),
                        amount,
                        Man10Bank.instance.name,
                        "TakenByCommand",
                        "サーバーから徴収"
                    ))

                    if (result != APIBank.BankResult.SUCCESSFUL){
                        msg(sender,"§c出金エラーが発生しました")
                        return@execute
                    }
                    msg(sender,"§e出金できました！")
                }
            }

            "set" ->{
                if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return true

                if (args.size< 3){
                    msg(sender,"§a§l/${label} set <player> <金額>")
                    return true
                }

                val amount = Utility.fixedPerse(args[2])
                val uuid = APIBank.getUUID(args[1])

                if (uuid == null){
                    msg(sender,"ユーザーが見つかりませんでした")
                    return true
                }

                if (amount==null){
                    msg(sender,"§c§l数字で入力してください！")
                    return true
                }

                if (amount < 1){
                    msg(sender,"§c§l1円以上を入力してください！")
                    return true
                }

                threadPool.execute {
                    APIBank.setBank(APIBank.TransactionData(
                        uuid.toString(),
                        amount,
                        Man10Bank.instance.name,
                        "SetByCommand",
                        "サーバーによる設定"
                    ))

                    msg(sender,"§e設定できました！")
                }
            }

//            "on" ->{
//                if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return true
//
//                msg(sender,"銀行をオープンしました")
//            }
//
//            "off" ->{
//                if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return true
//                Man10Bank.close()
//                msg(sender,"銀行をクローズしました")
//            }


        }


        return true
    }

    fun asyncShowBalance(sender: CommandSender, uuid:UUID){

        threadPool.execute {
            val p = Bukkit.getPlayer(uuid)

            if (p == null){
                EstateHistory.asyncShowEstate(sender,uuid)
                return@execute
            }

            val revoInfo = APIServerLoan.getInfo(p.uniqueId)

            val bankAmount = APIBank.getBalance(p.uniqueId)

            if (bankAmount == -1.0){
                APIBank.createBank(uuid)
                msg(sender,"§e§l銀行口座を開設しました！")
                return@execute
            }

            val loan = revoInfo?.borrow_amount?:0.0
            val payment =revoInfo?.payment_amount?:0.0
            val failed = revoInfo?.failed_payment?:0
            val nextDate = APIServerLoan.nextPayDate(p.uniqueId)
            val score = APIBank.getScore(p.uniqueId)
            val balance = vault.getBalance(p.uniqueId)
            val cash = ATM.getCash(p)
            val estate = APIHistory.getUserEstate(p.uniqueId)?.estete?:0.0

            msg(sender,"§e§l==========${p.name}のお金==========")
            msg(sender," §b§l電子マネー:  §e§l${format(balance)}円")
            msg(sender," §b§l銀行:  §e§l${format(bankAmount)}円")
            if (bankAmount == -2.0){
                msg(sender," §c§l取引失敗を検知しました。運営に報告してください")
            }
            if (cash>0.0){ msg(sender," §b§l現金:  §e§l${format(cash)}円") }
            if (estate>0.0){ msg(sender," §b§lその他の資産:  §e§l${format(estate)}円") }

            msg(sender," §b§lスコア: §a§l${format(score.toDouble())}")

            if (loan!=0.0 && nextDate!=null){
                msg(sender," §b§lまんじゅうリボ:  §c§l${format(loan)}円")
                msg(sender," §b§l支払額:  §c§l${format(payment)}円")
                msg(sender," §b§l次の支払日: §c§l${nextDate.format(DateTimeFormatter.ISO_LOCAL_DATE)}")
                if (failed>0){
                    msg(sender," §c§lMan10リボの支払いに失敗しました(失敗回数:${failed}回)。支払いに失敗するとスコアの減少や労働所送りにされることがあります")
                }
            }
            sender.sendMessage(text("$prefix §a§l§n[ここをクリックでコマンドをみる]").clickEvent(ClickEvent.runCommand("/bank help")))

        }

    }

    private fun showCommand(sender:Player){
        val pay = text("$prefix §e[電子マネーを友達に送る]  §n/pay").clickEvent(ClickEvent.suggestCommand("/pay "))
        val atm = text("$prefix §a[電子マネーのチャージ・現金化]  §n/atm").clickEvent(ClickEvent.runCommand("/atm"))
        val deposit = text("$prefix §b[電子マネーを銀行に入れる]  §n/deposit").clickEvent(ClickEvent.suggestCommand("/deposit "))
        val withdraw = text("$prefix §c[電子マネーを銀行から出す]  §n/withdraw").clickEvent(ClickEvent.suggestCommand("/withdraw "))
        val revolving = text("$prefix §e[Man10リボを使う]  §n/mrevo borrow").clickEvent(ClickEvent.suggestCommand("/mrevo borrow "))
        val ranking = text("$prefix §6[お金持ちランキング]  §n/mbaltop").clickEvent(ClickEvent.runCommand("/mbaltop"))
        val log = text("$prefix §7[銀行の履歴]  §n/bal log").clickEvent(ClickEvent.runCommand("/bal log"))

        sender.sendMessage(pay)
        sender.sendMessage(atm)
        sender.sendMessage(deposit)
        sender.sendMessage(withdraw)
        sender.sendMessage(revolving)
        sender.sendMessage(ranking)
        sender.sendMessage(log)
    }

    private fun asyncShowLog(uuid:UUID, sender:CommandSender, page:Int){

        threadPool.execute {
            val skip = page*10
            val log = APIBank.getBankLog(uuid,10,skip)
            val mcid = Bukkit.getOfflinePlayer(uuid).name

            msg(sender,"§d§l===========${mcid}の銀行の履歴==========")

            log.forEach { data->
                val tag = if (data.deposit) "§a[入金]" else "§c[出金]"
                msg(sender,"$tag §e${data.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))} " +
                        "§e§l${format(data.amount)} §e${data.display_note}")
            }

            val arg = if (mcid == sender.name) "/bal log " else "/bal logop $mcid "

            val previous = if (page!=0) {
                text("${prefix}§b§l§n<<==前のページ ").clickEvent(ClickEvent.runCommand(arg+(page-1)))
            }else text(prefix)

            val next = if (log.size == 10){
                text("§b§l§n次のページ==>>").clickEvent(ClickEvent.runCommand(arg+(page+1)))
            }else text("")

            sender.sendMessage(previous.append(next))
        }
    }

    private fun asyncShowBalanceSheet(uuid: UUID,sender: CommandSender){
        threadPool.execute {
            val estate = APIHistory.getUserEstate(uuid)
            val p = Bukkit.getOfflinePlayer(uuid)
            val mcid = p.name

            val vault = vault.getBalance(uuid)
            val bank = APIBank.getBalance(uuid)
            val items = estate?.estete?:0.0
            val cash = if (p.isOnline) ATM.getCash(p.player!!) else estate?.cash?:0.0
            val serverLoan = APIServerLoan.getInfo(uuid)?.borrow_amount?:0.0
            val localLoan = APILocalLoan.totalLoan(uuid)

            val assets = vault+bank+items+cash
            val liability = localLoan + serverLoan
            val equity = assets - liability
            val symbol = if (equity<0) { "△" } else {""}

            msg(sender,"§d§l===========${mcid}のバランスシート==========")
            msg(sender,String.format(" §b現金:        %14s §f| §c個人間借金:            §c%14s", format(cash), format(localLoan)))
            msg(sender,String.format(" §b電子マネー:   %14s §f| §cMan10リボ:            §c%14s", format(vault), format(serverLoan)))
            msg(sender,String.format(" §b銀行:        %14s §f| §c合計負債:              §c%14s", format(bank), format(liability)))
            msg(sender,String.format(" §bその他:      %14s §f| §a純資産:                §a%14s", format(items), "${format(abs(equity))}${symbol}"))
            msg(sender,String.format(" §b合計資産:     %14s §f| §c負債と純資産:          §b%14s", format(assets), format(liability+equity)))

        }
    }

}
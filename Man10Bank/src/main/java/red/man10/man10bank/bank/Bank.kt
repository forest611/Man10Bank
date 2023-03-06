package red.man10.man10bank.bank

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.Permissions
import red.man10.man10bank.api.APIBank
import red.man10.man10bank.api.APIHistory
import red.man10.man10bank.api.APIServerLoan
import red.man10.man10bank.util.BlockingQueue
import red.man10.man10bank.util.Utility.format
import red.man10.man10bank.util.Utility.msg
import red.man10.man10bank.util.Utility.prefix
import java.text.SimpleDateFormat
import java.util.*

object Bank : CommandExecutor, Listener{

    val labels = arrayOf("bal","balance","bank","money")

    fun addBank(p:Player,amount:Double){

    }

    fun takeBank(p:Player,amount:Double){

    }


    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (sender !is Player)return true

        if (!labels.contains(label)){ return true }

        if (args.isEmpty()){
            BlockingQueue.addTask { showBalance(sender,sender) }
            return true
        }

        when(args[0]){

            "help" ->{
                showCommand(sender)
                return true
            }

            "log" ->{

            }

            "add" ->{
                if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return true


            }

            "take" ->{
                if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return true


            }

            "set" ->{
                if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return true


            }

            "on" ->{
                if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return true

            }

            "off" ->{
                if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return true

            }

            "reload" ->{
                if (!sender.hasPermission(Permissions.BANK_OP_COMMAND))return true


            }






        }


        return true
    }

    private fun showBalance(p:Player, sender: CommandSender){

        val serverLoanData = APIServerLoan.getInfo(p.uniqueId)

        val bankAmount = APIBank.getBalance(p.uniqueId)
        val loan = serverLoanData?.borrow_amount?:0.0
        val payment =serverLoanData?.payment_amount?:0.0
        val failed = serverLoanData?.failed_payment?:0
        val nextDate : Date? = APIServerLoan.nextPayDate(p.uniqueId)
        val score = 0
        val cash = vault.getBalance(p.uniqueId)
        val estate = APIHistory.getUserEstate(p.uniqueId)?.estete?:0.0


        msg(sender,"§e§l==========${p.name}のお金==========")
        msg(sender," §b§l電子マネー:  §e§l${format(vault.getBalance(p.uniqueId))}円")
        msg(sender," §b§l銀行:  §e§l${format(bankAmount)}円")
        if (cash>0.0){ msg(sender," §b§l現金:  §e§l${format(cash)}円") }
        if (estate>0.0){ msg(sender," §b§lその他の資産:  §e§l${format(estate)}円") }
//        if(EstateData.getShopTotalBalance(p) > 0.0) msg(sender, " §b§lショップ口座:  §e§l${format(EstateData.getShopTotalBalance(p))}円")

        msg(sender," §b§lスコア: §a§l${format(score.toDouble())}")

        if (loan!=0.0 && nextDate!=null){
            msg(sender," §b§lまんじゅうリボ:  §c§l${format(loan)}円")
            msg(sender," §b§l支払額:  §c§l${format(payment)}円")
            msg(sender," §b§l次の支払日: §c§l${SimpleDateFormat("yyyy-MM-dd").format(nextDate)}")
            if (failed>0){
                msg(sender," §c§lMan10リボの支払いに失敗しました(失敗回数:${failed}回)。支払いに失敗するとスコアの減少や労働所送りにされることがあります")
            }
        }

        sender.sendMessage(text("$prefix §a§l§n[ここをクリックでコマンドをみる]").clickEvent(ClickEvent.runCommand("/bank help")))
    }

    private fun showCommand(sender:Player){
        val pay = text("$prefix §e[電子マネーを友達に送る]  §n/pay").clickEvent(ClickEvent.suggestCommand("/pay "))
        val atm = text("$prefix §a[電子マネーのチャージ・現金化]  §n/atm").clickEvent(ClickEvent.runCommand("/atm"))
        val deposit = text("$prefix §b[電子マネーを銀行に入れる]  §n/deposit").clickEvent(ClickEvent.suggestCommand("/deposit "))
        val withdraw = text("$prefix §c[電子マネーを銀行から出す]  §n/withdraw").clickEvent(ClickEvent.suggestCommand("/withdraw "))
        val revolving = text("$prefix §e[Man10リボを使う]  §n/mrevo borrow").clickEvent(ClickEvent.suggestCommand("/mrevo borrow "))
        val ranking = text("$prefix §6[お金持ちランキング]  §n/mbaltop").clickEvent(ClickEvent.runCommand("/mbaltop"))
        val log = text("$prefix §7[銀行の履歴]  §n/ballog").clickEvent(ClickEvent.runCommand("/ballog"))

        sender.sendMessage(pay)
        sender.sendMessage(atm)
        sender.sendMessage(deposit)
        sender.sendMessage(withdraw)
        sender.sendMessage(revolving)
        sender.sendMessage(ranking)
        sender.sendMessage(log)
    }

    @EventHandler
    fun login(e: PlayerJoinEvent){
        val p = e.player

//        Bank.loginProcess(p)

        Thread{
            Thread.sleep(3000)
            showBalance(p,p)
        }.start()
    }

    @EventHandler (priority = EventPriority.LOWEST)
    fun logout(e: PlayerQuitEvent){
//        Thread{EstateData.saveCurrentEstate(e.player)}.start()
    }

    @EventHandler
    fun closeEnderChest(e: InventoryCloseEvent){
        if (e.inventory.type != InventoryType.ENDER_CHEST)return
        val p = e.player as Player
//        Thread{}.start()
    }
}
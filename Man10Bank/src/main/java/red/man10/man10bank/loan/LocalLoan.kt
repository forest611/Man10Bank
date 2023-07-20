package red.man10.man10bank.loan

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent.runCommand
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import red.man10.man10bank.Man10Bank.Companion.async
import red.man10.man10bank.Man10Bank.Companion.instance
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.api.APIBank
import red.man10.man10bank.api.APILocalLoan
import red.man10.man10bank.util.Utility.format
import red.man10.man10bank.util.Utility.msg
import red.man10.man10bank.util.Utility.prefix
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.floor

object LocalLoan: Listener,CommandExecutor{

    /**
     * 個人間借金を作る
     */
    private fun create(lender:Player, borrower:Player, amount:Double, interest:Double, due:Int){

        if (!vault.withdraw(lender.uniqueId,amount)){
            msg(borrower,"提案者の所持金が足りませんでした。")
            msg(lender,"所持金が足りないためお金を貸すことができません！")
            return
        }

        val paybackDate = calcDue(due)
        //返済額=貸出額+(貸出額x利子x日数)
        val payment = amount + (amount * interest * due)

        val ret = APILocalLoan.create(
            APILocalLoan.LocalLoanTable(
            0,
            lender.name,
            lender.uniqueId.toString(),
            borrower.name,
            borrower.uniqueId.toString(),
            LocalDateTime.now(),
            paybackDate,
            payment
        ))

        //サーバーへの問い合わせ失敗や発行失敗
        if (ret<=0){
            msg(lender,"手形の発行に失敗。銀行への問い合わせができませんでした")
            msg(borrower,"手形の発行に失敗。銀行への問い合わせができませんでした")
            return
        }

        val note = getNote(ret)

        if (note == null){
            msg(lender,"手形の発行に失敗。銀行への問い合わせができませんでした")
            msg(borrower,"手形の発行に失敗。銀行への問い合わせができませんでした")
            return
        }

        lender.inventory.addItem(note)

        vault.deposit(borrower.uniqueId,amount)

        msg(lender,"手形の発行に成功")
        msg(borrower,"手形の発行に成功")
    }

    /**
     * 手形を発行する
     */
    private fun getNote(id:Int): ItemStack? {

        val data = APILocalLoan.getInfo(id)?:return null

        val note = ItemStack(Material.PAPER)
        val meta = note.itemMeta

        meta.setCustomModelData(2)

        meta.displayName(text("§c§l約束手形 §7§l(Promissory Note)"))
        meta.lore = mutableListOf(
            "§4§l========[Man10Bank]========",
            "   §7§l債務者:  ${Bukkit.getOfflinePlayer(data.borrow_uuid).name}",
            "   §8§l有効日:  ${data.payback_date.format(DateTimeFormatter.ISO_DATE_TIME)}",
            "   §7§l支払額:  ${format(data.amount)}",
            "§4§l==========================")

        meta.persistentDataContainer.set(NamespacedKey(instance,"id"), PersistentDataType.INTEGER,id)

        note.itemMeta = meta

        return note
    }

    private fun calcDue(day:Int,borrow:LocalDateTime = LocalDateTime.now()):LocalDateTime{
        borrow.plusDays(day.toLong())
//        val calender = Calendar.getInstance()
//        calender.time = borrow
//        calender.add(Calendar.DAY_OF_YEAR,day)
        return borrow
    }

    @EventHandler
    fun asyncUseNote(e:PlayerInteractEvent){

        if (!e.hasItem() || !e.action.isRightClick)return

        val item = e.item?:return
        val meta = item.itemMeta?:return

        val id = meta.persistentDataContainer[NamespacedKey(instance,"id"), PersistentDataType.INTEGER]?:return

        val p = e.player

        item.amount = 0

        async.execute {

            val data = APILocalLoan.getInfo(id)?:return@execute
            val uuid = UUID.fromString(data.borrow_uuid)

            val vaultMoney = vault.getBalance(uuid)
            val bankMoney = APIBank.getBalance(uuid)

            var paidMoney = 0.0

            //銀行
            if (APIBank.takeBank(APIBank.TransactionData(uuid.toString(),
                    bankMoney,
                    instance.name,
                    "paybackmoney",
                    "借金の返済")) == APIBank.BankResult.SUCCESSFUL){
                paidMoney += bankMoney
            }

            //電子マネー
            if (vault.withdraw(uuid,vaultMoney)){
                paidMoney += vaultMoney
            }

            when(APILocalLoan.pay(id,paidMoney)){
                "Paid"->{
                    msg(p,"${data.borrow_player}から${format(paidMoney)}円の回収を行いました")
                    vault.deposit(p.uniqueId,paidMoney)

                    //債務者への通知
                    val b = Bukkit.getPlayer(uuid)
                    if (b!=null){
                        msg(b,"${format(paidMoney)}円の個人間借金の回収を行いました")
                    }
                }

                "AllPaid"->{
                    val diff = paidMoney - data.amount

                    msg(p,"${data.borrow_player}から${format(paidMoney-diff)}円の回収を行いました")
                    msg(p,"全額回収完了")
                    vault.deposit(p.uniqueId,paidMoney-diff)

                    APIBank.addBank(
                        APIBank.TransactionData(
                            uuid.toString(),
                            diff,
                            instance.name,
                            "PaybackDifference",
                            "差額の返金"
                        ))

                    //債務者への通知
                    val b = Bukkit.getPlayer(uuid)
                    if (b!=null){
                        msg(b,"${format(paidMoney)}円の個人間借金の回収を行いました")
                        msg(b,"完済し終わりました！お疲れ様です！")
                    }
                }

                else ->{
                    APIBank.addBank(
                        APIBank.TransactionData(
                            uuid.toString(),
                            paidMoney,
                            instance.name,
                            "Payback",
                            "不具合による返金"
                        )
                    )

                    msg(p,"借金の回収に失敗しました")
                }
            }

            p.inventory.addItem(getNote(id)!!)
        }
    }

    private val map = HashMap<UUID,CommandParam>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {

        if (label != "mlend")return true

        if (sender !is Player)return true

        if (args.isNullOrEmpty()){
            msg(sender,"§a/mlend <貸す相手> <金額> <返済日(日)> <金利(日)0.0〜1.0>")
            return true
        }

        if (args[0] == "allow"){
            val data = map[sender.uniqueId]?:return true

            val lendP = Bukkit.getPlayer(data.lender)

            if (lendP == null){
                msg(sender,"提案者がログアウトしたようです")
                return true
            }

            map.remove(sender.uniqueId)
            async.execute {
                create(lendP,sender,data.amount,data.interest,data.due)
            }

            return true
        }

        if (args[0] == "deny"){
            val data = map[sender.uniqueId]?:return true

            val p = Bukkit.getPlayer(data.lender)
            if (p!=null){
                msg(p,"提案が断られました")
            }

            msg(sender,"提案を断りました")

            map.remove(sender.uniqueId)
            return true
        }

        if (args.size != 4){
            msg(sender,"§a/mlend <貸す相手> <金額> <返済日(日)> <金利(日)0.0〜1.0>")
            return true
        }

        val borrower = Bukkit.getPlayer(args[0])
        val amount = args[1].toDoubleOrNull()
        val due = args[2].toIntOrNull()
        val interest = args[3].toDoubleOrNull()

        if (borrower == null){
            msg(sender,"オンラインでないプレイヤーです")
            return true
        }

        if (amount == null || amount<0){
            msg(sender,"金額は数字で１円以上を入力してください")
            return true
        }

        val fixedAmount = floor(amount)

        if (due == null){
            msg(sender,"期日は数字で入力してください")
            return true
        }

        if (interest == null){
            msg(sender,"金利は数字で入力してください")
            return true
        }

        val data = CommandParam(sender.uniqueId,fixedAmount, due, interest)
        map[borrower.uniqueId] = data

        val allowOrDeny = text("${prefix}§b§l§n[借りる] ").clickEvent(runCommand("/mlend allow"))
            .append(text("§c§l§n[借りない]").clickEvent(runCommand("/mlend deny")))

        msg(sender,"§a§l借金の提案を相手に提示しました")

        msg(borrower,"§e§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")
        msg(borrower,"§e§kXX§b§l借金の提案§e§kXX")
        msg(borrower,"§e貸し出す人:${sender.name}")
        msg(borrower,"§e貸し出される金額:${format(fixedAmount)}")
        msg(borrower,"§e返す金額:${format(fixedAmount + (fixedAmount * interest * due))}")
        msg(borrower,"§e返す日:$${calcDue(due).format(DateTimeFormatter.ISO_DATE_TIME)}")
        borrower.sendMessage(allowOrDeny)
        msg(borrower,"§e§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")
        return true
    }

    data class CommandParam(
        val lender: UUID,
        val amount : Double,
        val due : Int,
        val interest : Double
    )

}
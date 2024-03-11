package red.man10.man10bank.loan

import kotlinx.coroutines.launch
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
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import red.man10.man10bank.Man10Bank.Companion.coroutineScope
import red.man10.man10bank.Man10Bank.Companion.instance
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.Permissions
import red.man10.man10bank.api.APIBank
import red.man10.man10bank.api.APILocalLoan
import red.man10.man10bank.status.StatusManager
import red.man10.man10bank.util.Utility.format
import red.man10.man10bank.util.Utility.loggerInfo
import red.man10.man10bank.util.Utility.msg
import red.man10.man10bank.util.Utility.prefix
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.floor

object LocalLoan: Listener,CommandExecutor{

    private lateinit var property: APILocalLoan.LocalLoanProperty

    suspend fun setup(){
        property = APILocalLoan.property()
        loggerInfo("個人間借金の設定を読み込みました")
        loggerInfo("最小金利:${property.minimumInterest}")
        loggerInfo("最大金利:${property.maximumInterest}")
        loggerInfo("手数料:${property.fee}")
    }

    /**
     * 個人間借金を作る
     */
    private suspend fun create(lender:Player, borrower:Player, amount:Double, interest:Double, due:Int){

        val withdrawAmount = amount * (1 + property.fee)

        if (!vault.withdraw(lender.uniqueId,withdrawAmount)){
            msg(borrower,"提案者の所持金が足りませんでした。")
            msg(lender,"所持金が足りないためお金を貸すことができません！")
            return
        }

        //返済日
        val paybackDate = LocalDateTime.now().plusDays(due.toLong())
        //返済額=貸出額+(貸出額x利子x日数)
        val payment = amount + (amount * interest * due)

        val result = APILocalLoan.create(
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
        if (result<=0){
            msg(lender,"手形の発行に失敗。銀行への問い合わせができませんでした")
            msg(borrower,"手形の発行に失敗。銀行への問い合わせができませんでした")
            vault.deposit(lender.uniqueId, withdrawAmount)
            return
        }

        val note = getNote(result)

        //手形の発行失敗
        if (note == null){
            msg(lender,"手形の発行に失敗。銀行への問い合わせができませんでした")
            msg(borrower,"手形の発行に失敗。銀行への問い合わせができませんでした")
            vault.deposit(lender.uniqueId, withdrawAmount)
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
    private suspend fun getNote(id:Int): ItemStack? {

        val data = APILocalLoan.getInfo(id)?:return null

        val note = ItemStack(Material.PAPER)
        val meta = note.itemMeta

        meta.setCustomModelData(2)

        Bukkit.getLogger().info("Player:${Bukkit.getOfflinePlayer(data.borrow_uuid).name} UUID${data.borrow_uuid}")

        meta.displayName(text("§c§l約束手形 §7§l(Promissory Note)"))
        meta.lore = mutableListOf(
            "§4§l========[Man10Bank]========",
            "   §7§l債務者:  ${Bukkit.getOfflinePlayer(data.borrow_uuid).name}",
            "   §8§l有効日:  ${data.payback_date.format(DateTimeFormatter.ISO_LOCAL_DATE)}",
            "   §7§l支払額:  ${format(data.amount)}円",
            "§4§l==========================")

        meta.persistentDataContainer.set(NamespacedKey(instance,"id"), PersistentDataType.INTEGER,id)

        note.itemMeta = meta

        return note
    }


    @EventHandler
    fun asyncUseNote(e:PlayerInteractEvent){

        if (!e.hasItem() || !e.action.isRightClick)return
        if (e.hand != EquipmentSlot.HAND)return

        val item = e.item?:return
        val meta = item.itemMeta?:return

        val id = meta.persistentDataContainer[NamespacedKey(instance,"id"), PersistentDataType.INTEGER]?:return
        val user = e.player

        coroutineScope.launch {

            //ここでnullが帰ってきたら手形じゃないと判定
            val data = APILocalLoan.getInfo(id)?:return@launch

            if (!StatusManager.status.enableLocalLoan){
                msg(user,"現在メンテナンスにより個人間借金は行えません")
                return@launch
            }

            if (data.payback_date.isAfter(LocalDateTime.now())){
                msg(user,"この手形はまだ有効になっていません")
                return@launch
            }

            val borrowUUID = UUID.fromString(data.borrow_uuid)
            val vaultMoney = vault.getBalance(borrowUUID)
            val bankMoney = APIBank.getBalance(borrowUUID)

            var paidMoney = 0.0

            val takeFromVault = if (vaultMoney>data.amount) data.amount else vaultMoney

            //電子マネー
            if (vault.withdraw(borrowUUID,takeFromVault)){
                paidMoney += takeFromVault
            }

            val takeFromBank = if (bankMoney>data.amount-paidMoney) data.amount-paidMoney else bankMoney

            //銀行
            if (APIBank.takeBalance(APIBank.TransactionData(borrowUUID.toString(),
                    takeFromBank,
                    instance.name,
                    "paybackmoney",
                    "借金の返済")) == APIBank.BankResult.Successful){
                paidMoney += takeFromBank
            }

            val borrow = Bukkit.getPlayer(borrowUUID)

            when(APILocalLoan.pay(id,paidMoney)){
                APILocalLoan.PaymentResult.SuccessPay->{
                    msg(user,"${data.borrow_player}から${format(paidMoney)}円の回収を行いました")
                    vault.deposit(user.uniqueId,paidMoney)

                    //債務者への通知
                    if (borrow!=null){
                        msg(borrow,"${format(paidMoney)}円の個人間借金の回収を行いました")
                    }
                }

                APILocalLoan.PaymentResult.SuccessAllPay->{
                    val diff = paidMoney - data.amount

                    if (borrow != null) {
                        msg(borrow,"${data.borrow_player}から${format(paidMoney)}円の回収を行いました")
                    }
                    msg(user,"全額回収完了")
                    vault.deposit(user.uniqueId,paidMoney-diff)

                    borrow?.let { msg(it,"完済し終わりました！お疲れ様でした！") }
                    msg(user,"${format(paidMoney)}円の個人間借金の回収を行いました")

                }

                APILocalLoan.PaymentResult.AlreadyPaid ->{
                    msg(user,"この借金は回収済みです")
                }

                else ->{
                    APIBank.addBalance(
                        APIBank.TransactionData(
                            borrowUUID.toString(),
                            paidMoney,
                            instance.name,
                            "Payback",
                            "不具合による返金"
                        )
                    )

                    msg(user,"借金の回収に失敗しました")
                }
            }

            val newNote = getNote(id)?:return@launch

            Bukkit.getScheduler().runTask(instance, Runnable {
                item.amount = 0
                user.inventory.addItem(newNote)
            })

        }
    }

    private val map = HashMap<UUID,CommandParam>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {

        if (label != "mlend")return true

        if (sender !is Player)return true

        if (!StatusManager.status.enableLocalLoan){
            msg(sender,"現在メンテナンスにより個人間借金は行えません")
            return false
        }

        //ヘルプメッセージ
        if (args.isNullOrEmpty()){
            msg(sender,"§a/mlend <貸す相手> <金額> <返済日(日)> " +
                    "<金利(1日ごと)${format(property.minimumInterest,1)}〜${format(property.maximumInterest,1)}>")
            if (property.fee>0.0){
                msg(sender,"§a貸出額の${format(property.fee,2)}%を貸出側から手数料としていただきます")
            }
            return true
        }

        if (args[0] == "property"){

            if (!sender.hasPermission(Permissions.BANK_OP_COMMAND)){
                msg(sender,"§c§lあなたには権限がありません")
                return true
            }

            msg(sender,"最小金利:${property.minimumInterest}")
            msg(sender,"最大金利:${property.maximumInterest}")
            msg(sender,"手数料:${property.fee}")
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
            coroutineScope.launch {
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
            msg(sender,"§a/mlend <貸す相手> <金額> <返済日(日)> " +
                    "<金利(1日ごと)${format(property.minimumInterest,1)}〜${format(property.maximumInterest,1)}>")
            if (property.fee>0.0){
                msg(sender,"§a貸出額の${format(property.fee,2)}%を貸出側から手数料としていただきます")
            }
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

        if (due == null || due < 0){
            msg(sender,"期日は数字で1日以上を入力してください")
            return true
        }

        if (interest == null || interest !in property.minimumInterest..property.maximumInterest){
            msg(sender,"金利は数字で指定金利内で入力してください")
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
        msg(borrower,"§e返す日:$${LocalDateTime.now().plusDays(due.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)}")
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
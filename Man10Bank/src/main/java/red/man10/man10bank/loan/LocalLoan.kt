package red.man10.man10bank.loan

import net.kyori.adventure.text.Component.text
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
import red.man10.man10bank.Man10Bank.Companion.instance
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.api.APIBank
import red.man10.man10bank.api.APILocalLoan
import red.man10.man10bank.bank.Bank
import red.man10.man10bank.util.BlockingQueue
import red.man10.man10bank.util.Utility
import red.man10.man10bank.util.Utility.format
import red.man10.man10bank.util.Utility.msg
import java.text.SimpleDateFormat
import java.util.*

object LocalLoan: Listener,CommandExecutor{

    /**
     * 個人間借金を作る
     */
    fun create(lender:Player,borrower:Player,amount:Double,interest:Double,paybackDay:Int){

        val paybackDate = Calendar.getInstance()
        paybackDate.time = Date()
        paybackDate.add(Calendar.DAY_OF_YEAR,paybackDay)

        val payment = amount*(interest+1)

        val ret = APILocalLoan.create(
            APILocalLoan.LocalLoanTable(
            0,
            lender.name,
            lender.uniqueId.toString(),
            borrower.name,
            borrower.uniqueId.toString(),
            Date(),
            paybackDate.time,
            payment
        ))

        if (ret<=0){
            msg(lender,"手形の発行に失敗。銀行への問い合わせができませんでした")
            return
        }


    }

    /**
     * 手形を発行する
     */
    fun getNote(id:Int): ItemStack? {

        val data = APILocalLoan.getInfo(id)?:return null

        val note = ItemStack(Material.PAPER)
        val meta = note.itemMeta

        meta.setCustomModelData(2)

        meta.displayName(text("§c§l約束手形 §7§l(Promissory Note)"))
        meta.lore = mutableListOf(
            "§4§l========[Man10Bank]========",
            "   §7§l債務者:  ${Bukkit.getOfflinePlayer(data.borrow_uuid).name}",
            "   §8§l有効日:  ${SimpleDateFormat("yyyy-MM-dd").format(data.payback_date)}",
            "   §7§l支払額:  ${Utility.format(data.amount)}",
            "§4§l==========================")

        meta.persistentDataContainer.set(NamespacedKey(instance,"id"), PersistentDataType.INTEGER,id)

        note.itemMeta = meta

        return note
    }


    @EventHandler
    fun asyncUseNote(e:PlayerInteractEvent){

        if (!e.hasItem() || !e.action.isRightClick)return

        val item = e.item?:return
        val meta = item.itemMeta?:return

        val id = meta.persistentDataContainer[NamespacedKey(instance,"id"), PersistentDataType.INTEGER]?:return

        val p = e.player

        item.amount = 0

        BlockingQueue.addTask {

            val data = APILocalLoan.getInfo(id)?:return@addTask
            val uuid = UUID.fromString(data.borrow_uuid)

            val vaultMoney = vault.getBalance(uuid)
            val bankMoney = APIBank.getBalance(uuid)

            var paidMoney = 0.0

            //銀行
            if (APIBank.takeBank(APIBank.TransactionData(uuid.toString(),
                    bankMoney,
                    instance.name,
                    "paybackmoney",
                    "借金の返済")) == "Successful"){
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

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        return true
    }


}
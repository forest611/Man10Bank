package red.man10.man10bank.loan

import net.kyori.adventure.text.Component.*
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import red.man10.man10bank.Bank
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.loan.repository.LocalLoanRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

class LoanData {


    lateinit var paybackDate : Date
    private lateinit var borrow: UUID
    var debt : Double = 0.0
    private var id : Int = 0
    private var collateralItem: String? = null  // 担保アイテム(Base64)
    private var collateralItems: List<ItemStack>? = null  // 担保アイテムのリスト


    @Synchronized
    fun load(id:Int): LoanData? {
        val record = LocalLoanRepository.fetchLoan(id) ?: return null

        this.borrow = record.borrowUUID
        this.debt = record.amount
        this.paybackDate = record.paybackDate
        this.id = record.id
        this.collateralItem = record.collateralItem
        this.collateralItems = record.collateralItem?.let { base64ToItems(it) }

        return this
    }

    @Synchronized
    fun create(lend:Player, borrow: Player, borrowedAmount : Double, paybackAmount: Double, paybackDay:Int, collateralItems: List<ItemStack>? = null):Boolean{
        //返済金額を直接設定
        this.debt = paybackAmount
        this.borrow = borrow.uniqueId
        this.paybackDate = calcDate(paybackDay)
        this.collateralItems = collateralItems
        this.collateralItem = collateralItems?.let { itemsToBase64(it) }

        if (Bank.withdraw(lend.uniqueId, borrowedAmount+(borrowedAmount * Man10Bank.loanFee), plugin,"LoanCreate","借金の貸し出し").first!=0){
            sendMsg(lend,"§c§lお金が足りません！")
            return false
        }

        val insertedId = LocalLoanRepository.insertLoan(
            lend.name,
            lend.uniqueId,
            borrow.name,
            borrow.uniqueId,
            paybackDate,
            debt,
            collateralItem
        ) ?: run {
            sendMsg(lend,"§c§lデータベースエラーが発生しました。運営に報告してください。- 01")
            return false
        }

        id = insertedId

        Bank.deposit(borrow.uniqueId, borrowedAmount, plugin, "LoanCreate","借金の借り入れ")

        return true
    }

    /**
     * @param p 手形の持ち主
     * 担保を回収して借金を完済扱いにする
     */
    @Synchronized
    fun collectCollateral(p:Player, item:ItemStack) {
        if (!Man10Bank.enableLocalLoan || Man10Bank.localLoanDisableWorlds.contains(p.world.name)) {
            sendMsg(p, "§c§lこのエリアでは個人間借金の取引を行うことはできません。")
            return
        }
        if (Date().before(paybackDate)) {
            sendMsg(p, "§cこの手形はまだ有効ではありません！")
            return
        }
        if (debt <= 0.0) {
            sendMsg(p, "§cこの借金は既に完済されています。")
            return
        }
        if (collateralItems.isNullOrEmpty()) {
            sendMsg(p, "§cこの借金には担保が設定されていません。")
            return
        }
        val borrowPlayer = Bukkit.getOfflinePlayer(borrow)
        val isOnline = Man10Bank.loadedPlayerUUIDs.contains(borrowPlayer.uniqueId) && borrowPlayer.isOnline

        try {
            // インベントリの空きスロット数を確認
            val emptySlots = p.inventory.contents.count { it == null }
            val requiredSlots = collateralItems!!.size
            
            if (emptySlots < requiredSlots) {
                sendMsg(p, "§c§lインベントリに空きが足りません！（必要: ${requiredSlots}スロット、空き: ${emptySlots}スロット）")
                return
            }
            
            // 担保アイテムをプレイヤーに付与
            collateralItems!!.forEach { collateralItem ->
                p.inventory.addItem(collateralItem)
            }

            // 借金を完済扱いにする
            debt = 0.0
            val result = LocalLoanRepository.updateAmount(id, debt)

            if (!result) {
                sendMsg(p, "§c§lデータベースエラーが発生しました。運営に報告してください。")
                return
            }

            sendMsg(p, "§a§l担保を回収しました！借金は完済扱いになりました。")

            if (isOnline) {
                sendMsg(borrowPlayer.player!!, "§c§l${p.name}によって担保が回収されました！")
            }
        } catch (e: Exception) {
            sendMsg(p, "§c§l担保回収中にエラーが発生しました。")
        } finally {
            // 手形の更新
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val meta = item.itemMeta
                meta.lore = mutableListOf(
                    "§4§l========[Man10Bank]========",
                    "   §7§l債務者:  ${Bukkit.getOfflinePlayer(borrow).name}",
                    "   §8§l有効日:  ${SimpleDateFormat("yyyy-MM-dd").format(paybackDate)}",
                    "   §7§l支払額:  ${Man10Bank.format(debt)}",
                    "§4§l==========================")

                item.itemMeta = meta
            })
        }
    }

    /**
     * @param p 手形の持ち主
     */
    @Synchronized
    fun collect(p:Player, item:ItemStack) {
        if (!Man10Bank.enableLocalLoan||Man10Bank.localLoanDisableWorlds.contains(p.world.name)){
            sendMsg(p,"§c§lこのエリアでは個人間借金の取引を行うことはできません。")
            return
        }
        if (Date().before(paybackDate)){
            sendMsg(p,"§cこの手形はまだ有効ではありません！")
            return
        }
        if (debt <= 0.0){
            sendMsg(p,"§cこの借金は既に完済されています。")
            return
        }
        val borrowPlayer = Bukkit.getOfflinePlayer(borrow)
        val isOnline = Man10Bank.loadedPlayerUUIDs.contains(borrowPlayer.uniqueId)&&borrowPlayer.isOnline
        val bankBalance = Bank.getBalance(borrow)
        val vaultBalance = Man10Bank.vault.getBalance(borrow)

        try {
            val takeBankBalance = floor(if (bankBalance<debt)bankBalance else debt)

            if (takeBankBalance != 0.0 && Bank.withdraw(borrow,takeBankBalance, plugin,"paybackMoney","借金の返済").first == 0){
                debt -= takeBankBalance

                val result = LocalLoanRepository.updateAmount(id, debt)
                //データベースエラーの時は例外を投げる
                if (!result){
                    Bank.deposit(borrow,takeBankBalance, plugin,"paybackMoney","借金の返金")
                    sendMsg(p,"§c§lデータベースエラーが発生しました。運営に報告してください。- mysql")
                    throw Exception("[Man10Loan]データベースエラーが発生しました。運営に報告してください。- mysql")
                }
                if (takeBankBalance>0){
                    sendMsg(p,"§eMan10Bankから${Man10Bank.format(takeBankBalance)}円回収成功しました！")
                    Bank.deposit(p.uniqueId,takeBankBalance, plugin,"paybackMoneyFromBank","借金の回収")
                }
            }
            val takeVaultBalance = floor(if (vaultBalance<(debt))vaultBalance else debt)

            if (isOnline && takeVaultBalance != 0.0 && Man10Bank.vault.withdraw(borrow,takeVaultBalance)){
                debt -= floor(takeVaultBalance)

                val result = LocalLoanRepository.updateAmount(id, debt)

                //データベースエラーの時は例外を投げる
                if (!result) {
                    Man10Bank.vault.deposit(borrow, takeVaultBalance)
                    sendMsg(p, "§c§lデータベースエラーが発生しました。運営に報告してください。- mysql")
                    throw Exception("[Man10Loan]データベースエラーが発生しました。運営に報告してください。- mysql")
                }
                if (takeVaultBalance>0){
                    sendMsg(p,"§e所持金から${Man10Bank.format(takeVaultBalance)}円回収成功しました！")
                    Bank.deposit(p.uniqueId,takeVaultBalance, plugin,"paybackMoneyFromBalance","借金の回収")
                }
            }
            if (isOnline){
                sendMsg(borrowPlayer.player!!,"§e${p.name}から借金の回収が行われました！")
            }
        }catch (_:Exception){
        }finally {
            // 手形の更新
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val meta = item.itemMeta
                meta.lore = mutableListOf(
                    "§4§l========[Man10Bank]========",
                    "   §7§l債務者:  ${Bukkit.getOfflinePlayer(borrow).name}",
                    "   §8§l有効日:  ${SimpleDateFormat("yyyy-MM-dd").format(paybackDate)}",
                    "   §7§l支払額:  ${Man10Bank.format(debt)}",
                    "§4§l==========================")

                item.itemMeta = meta
            })
        }
    }

    fun getNote():ItemStack{
        val note = ItemStack(Material.PAPER)
        val meta = note.itemMeta

        meta.setCustomModelData(2)

        meta.displayName(text("§c§l約束手形 §7§l(Promissory Note)"))
        meta.lore = mutableListOf(
            "§4§l========[Man10Bank]========",
            "   §7§l債務者:  ${Bukkit.getOfflinePlayer(borrow).name}",
            "   §8§l有効日:  ${SimpleDateFormat("yyyy-MM-dd").format(paybackDate)}",
            "   §7§l支払額:  ${Man10Bank.format(debt)}",
            "§4§l==========================")

        meta.persistentDataContainer.set(NamespacedKey(plugin,"id"), PersistentDataType.INTEGER,id)

        note.itemMeta = meta

        return note
    }

    companion object{
        fun calcDate(day:Int):Date{
            val cal = Calendar.getInstance()
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR,day)

            return cal.time
        }

        // ItemStackのリストをBase64文字列に変換
        fun itemsToBase64(items: List<ItemStack>): String {
            val outputStream = ByteArrayOutputStream()
            BukkitObjectOutputStream(outputStream).use { objectOutputStream ->
                objectOutputStream.writeInt(items.size)
                for (item in items) {
                    objectOutputStream.writeObject(item)
                }
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray())
        }

        // Base64文字列をItemStackのリストに変換
        fun base64ToItems(base64: String): List<ItemStack> {
            val inputStream = ByteArrayInputStream(Base64.getDecoder().decode(base64))
            return BukkitObjectInputStream(inputStream).use { objectInputStream ->
                val size = objectInputStream.readInt()
                val items = mutableListOf<ItemStack>()
                for (i in 0 until size) {
                    items.add(objectInputStream.readObject() as ItemStack)
                }
                items
            }
        }

        fun getTotalLoan(p:Player):Double{
            return LocalLoanRepository.fetchTotalLoan(p.uniqueId)
        }

        fun getLoanData(uuid: UUID):Set<Pair<Int,Double>>{
            return LocalLoanRepository.fetchLoanData(uuid)
        }
    }
}
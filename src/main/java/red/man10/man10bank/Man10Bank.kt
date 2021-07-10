package red.man10.man10bank

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent
import org.apache.commons.lang.math.NumberUtils
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.MySQLManager.Companion.mysqlQueue
import red.man10.man10bank.atm.ATMData
import red.man10.man10bank.atm.ATMInventory
import red.man10.man10bank.atm.ATMListener
import red.man10.man10bank.history.EstateData
import red.man10.man10bank.loan.Event
import red.man10.man10bank.loan.LoanCommand
import java.text.Normalizer
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.HashMap

class Man10Bank : JavaPlugin(),Listener {

    companion object{
        const val prefix = "§l[§e§lMan10Bank§f§l]"

        lateinit var vault : VaultManager

        lateinit var plugin : Man10Bank

        fun sendMsg(p:Player,msg:String){
            p.sendMessage(prefix+msg)
        }

        fun sendMsg(p: CommandSender, msg: String) {
            p.sendMessage(prefix+msg)
        }

        fun format(double: Double):String{
            return String.format("%,.0f",double)
        }

        const val OP = "man10bank.op"
        const val USER = "man10bank.user"

//        var fee : Double = 0.00
//        var rate : Double = 1.0

        var bankEnable = true

        var loanFee : Double = 1.0
        var loanRate : Double = 1.0
        var loanMax : Double = 10000000.0

        var firstMoney : Double = 10000.0 // お初に渡すお金の金額

    }

    private val checking = HashMap<Player,Command>()

    lateinit var es : ExecutorService

    override fun onEnable() {
        // Plugin startup logic

        plugin = this

        saveDefaultConfig()

        es = Executors.newCachedThreadPool()

        mysqlQueue()

        vault = VaultManager(this)

//        fee = config.getDouble("fee",1.0)
//        rate = config.getDouble("rate",1.0)

        loanFee = config.getDouble("loanfee",1.1)
        loanMax = config.getDouble("loanmax",10000000.0)
        loanRate = config.getDouble("loanrate",1.0)
        firstMoney = config.getDouble("firstmoney",10000.0)

        ATMData.loadItem()

        server.pluginManager.registerEvents(this,this)
        server.pluginManager.registerEvents(Event(),this)
        server.pluginManager.registerEvents(ATMListener,this)

        getCommand("mlend")!!.setExecutor(LoanCommand())

        es.execute {
            EstateData.historyThread()
        }

    }

    override fun onDisable() {
        // Plugin shutdown logic
        es.shutdownNow()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {


        when(label){

            "atm" ->{
                if (sender !is Player)return false

                if (args.isEmpty()){
                    if (!bankEnable)return false
                    ATMInventory.openMainMenu(sender)
                    return true
                }

                when(args[0]){

                    "setmoney"->{

                        if (!sender.hasPermission(OP))return false

                        val amount = args[1].toDoubleOrNull() ?: return true

                        val ret = ATMData.setItem(sender.inventory.itemInMainHand,amount)

                        if (ret){
                            sendMsg(sender,"設定完了:${amount}")
                        }
                    }
                }

            }

            "mbaltop" ->{
                if (sender !is Player)return false

                es.execute{

                    val balTopMap = EstateData.getBalanceTop()?:return@execute

                    for (data in balTopMap){
                        sendMsg(sender,"§b§l${data.key.name} : §e§l$ ${format(data.value)}")
                    }

                    val totalMap = EstateData.getBalanceTotal()?:return@execute

                    sendMsg(sender,"§e§l電子マネーの合計:${format(totalMap["vault"]?:0.0)}")
                    sendMsg(sender,"§e§l現金の合計:${format(totalMap["estate"]?:0.0)}")
                    sendMsg(sender,"§e§l銀行口座の合計:${format(totalMap["bank"]?:0.0)}")
                    sendMsg(sender,"§e§l全ての合計:${format(totalMap["total"]?:0.0)}")


                }
            }

            "bal","balance","money","bank" ->{
                if (args.isEmpty()){

                    if (sender !is Player)return false

                    es.execute{
                        sendMsg(sender,"§e§l==========${sender.name}のお金==========")

                        //時差による表示ずれ対策で、一旦所持金を呼び出す
                        val bankAmount = format(Bank.getBalance(sender.uniqueId))

                        val cash = ATMData.getInventoryMoney(sender) + ATMData.getEnderChestMoney(sender)

                        sendMsg(sender," §b§l電子マネー:  §e§l${format(vault.getBalance(sender.uniqueId))}円")
                        sendMsg(sender," §b§l現金:  §e§l${format(cash)}円")
                        sendMsg(sender," §b§l銀行:  §e§l${bankAmount}円")

                        val deposit = text("$prefix §b[電子マネーを銀行に入れる]  §n/deposit").clickEvent(ClickEvent.suggestCommand("/deposit "))
                        val withdraw = text("$prefix §c[電子マネーを銀行から出す]  §n/withdraw").clickEvent(ClickEvent.suggestCommand("/withdraw "))
                        val atm = text("$prefix §a[電子マネーのチャージ・現金化]  §n/atm").clickEvent(ClickEvent.runCommand("/atm"))
                        val pay = text("$prefix §e[電子マネーを友達に送る]  §n/pay").clickEvent(ClickEvent.suggestCommand("/pay "))

                        sender.sendMessage(pay)
                        sender.sendMessage(atm)
                        sender.sendMessage(deposit)
                        sender.sendMessage(withdraw)

                    }

                    return true
                }

                when(args[0]){

                    "log" ->{
                        if (sender !is Player)return false

                        es.execute{

                            val deposit = format(Bank.calcLog(true,sender))
                            val withdraw = format(Bank.calcLog(false,sender))

                            sendMsg(sender,"§e§l今月の入出金額の合計")

                            sendMsg(sender,"§a§l入金額:${deposit}")
                            sendMsg(sender,"§c§l出金額:${withdraw}")
                        }
                        return true
                    }

                    "user" ->{
                        if (sender !is Player)return false
                        if (!sender.hasPermission(OP))return true

                        sendMsg(sender,"現在取得中....")

                        es.execute{
                            val uuid = Bank.getUUID(args[1])

                            if (uuid == null){
                                sendMsg(sender,"まだ口座が開設されていない可能性があります")
                                return@execute
                            }

                            sendMsg(sender,"§e§l==========現在の銀行口座残高==========")
                            sendMsg(sender,"§b§kXX§e§l${format(Bank.getBalance(uuid))}§b§kXX")

                        }

                    }

                    "take" ->{
                        if (!sender.hasPermission(OP))return true

                        val a = args[2].replace(",","")

                        if (!NumberUtils.isDigits(a)){
                            sendMsg(sender,"§c§l回収する額を半角数字で入力してください！")
                            return true
                        }

                        val amount = a.toDouble()

                        if (amount < 0){
                            sendMsg(sender,"§c§l0未満の値は入金出来ません！")
                            return true
                        }

                        es.execute {

                            val uuid = Bank.getUUID(args[1])?: return@execute

                            if (!Bank.withdraw(uuid,amount,this,"TakenByCommand")){
                                Bank.setBalance(uuid,0.0)
                                sendMsg(sender,"§a回収額が残高を上回っていたので、残高が0になりました")
                                return@execute
                            }
                            sendMsg(sender,"§a${format(amount)}円回収しました")
                            sendMsg(sender,"§a現在の残高：${format(Bank.getBalance(uuid))}")

                        }
                        return true

                    }

                    "give"->{
                        if (!sender.hasPermission(OP))return true

                        val a = args[2].replace(",","")

                        if (!NumberUtils.isDigits(a)){
                            sendMsg(sender,"§c§l入金する額を半角数字で入力してください！")
                            return true
                        }

                        val amount = a.toDouble()

                        if (amount < 0){
                            sendMsg(sender,"§c§l0未満の値は入金出来ません！")
                            return true
                        }

                        es.execute {

                            val uuid =  Bank.getUUID(args[1])?: return@execute

                            Bank.deposit(uuid,amount,this,"GivenFromServer")

                            sendMsg(sender,"§a${format(amount)}円入金しました")
                            sendMsg(sender,"§a現在の残高：${format(Bank.getBalance(uuid))}")

                        }

                    }

                    "set"->{
                        if (!sender.hasPermission(OP))return true

                        val a = args[2].replace(",","")

                        if (!NumberUtils.isDigits(a)){
                            sendMsg(sender,"§c§l設定する額を半角数字で入力してください！")
                            return true
                        }

                        val amount = a.toDouble()

                        if (amount < 0){
                            sendMsg(sender,"§c§l0未満の値は入金出来ません！")
                            return true
                        }

                        es.execute {

                            val uuid =  Bank.getUUID(args[1])?: return@execute

                            Bank.setBalance(uuid,amount)

                            sendMsg(sender,"§a${format(amount)}円に設定しました")

                        }

                    }

                    "on" ->{
                        if (!sender.hasPermission(OP))return false

                        bankEnable = true

                        Bukkit.broadcastMessage("§e§lMan10Bankが開きました！",)
                        return true

                    }

                    "off" ->{
                        if (!sender.hasPermission(OP))return false

                        bankEnable = false
                        Bukkit.broadcastMessage("§e§lMan10Bankが閉じました！")
                        return true

                    }

                    "reload" ->{
                        if (!sender.hasPermission(OP))return false

                        es.execute{
                            reloadConfig()

//                            fee = config.getDouble("fee")
//                            rate = config.getDouble("rate")
                            loanMax = config.getDouble("loanmax")
                            loanFee = config.getDouble("loanfee")

                            Bank.reload()
                            ATMData.loadItem()
                        }

                    }
                }

            }

            "deposit" ->{

                if (sender !is Player)return false

                if (!bankEnable)return false

                if (args.isEmpty()){
                    sendMsg(sender,"§c§l/deposit <金額> : 銀行に電子マネーを入れる")
                    return true
                }

                //入金額
                val amount : Double = if (args[0] == "all"){
                    vault.getBalance(sender.uniqueId)
                }else{

                    val a = args[0].replace(",","")

                    val b = ZenkakuToHankaku(a)

                    if (b == -1.0){
                        sendMsg(sender,"§c§l数字で入力してください！")
                        return true
                    }
                    b
                }

                if (amount < 1){
                    sendMsg(sender,"§c§l1円以上を入力してください！")
                    return true
                }

                if (!vault.withdraw(sender.uniqueId,amount)){
                    sendMsg(sender,"§c§l電子マネーが足りません！")
                    return true

                }

                es.execute {
                    Bank.deposit(sender.uniqueId,amount,this,"PlayerDepositOnCommand")

                    sendMsg(sender,"§a§l入金できました！")
                }

                return true


            }

            "withdraw" ->{

                if (sender !is Player)return false

                if (!bankEnable)return false

                if (args.isEmpty()){
                    sendMsg(sender,"§c§l/withdraw <金額> : 銀行から電子マネーを出す")
                    return true
                }

                es.execute {

                    val amount = if (args[0] == "all"){
                        Bank.getBalance(sender.uniqueId)
                    }else{

                        val a = args[0].replace(",","")

                        val b = ZenkakuToHankaku(a)

                        if (b == -1.0){
                            sendMsg(sender,"§c§l数字で入力してください！")
                            return@execute
                        }
                        b
                    }

                    if (amount < 1){
                        sendMsg(sender,"§c§l1円以上を入力してください！")
                        return@execute
                    }

                    if (!Bank.withdraw(sender.uniqueId,amount,this,"PlayerWithdrawOnCommand")){
                        sendMsg(sender,"§c§l銀行のお金が足りません！")
                        return@execute
                    }

                    vault.deposit(sender.uniqueId,amount)

                    sendMsg(sender,"§a§l出金できました！")

                }

                return true
            }

            "pay" ->{
                if (!sender.hasPermission(USER))return true

                if (sender !is Player)return true

                if (!isEnabled)return true

                if (args.size != 2){
                    sendMsg(sender,"§c§l/pay <送る相手> <金額> : 電子マネーを友達に振り込む")
                    return true
                }

                val a = args[1].replace(",","")

                val amount = ZenkakuToHankaku(a)

                if (amount == -1.0){
                    sendMsg(sender,"§c§l/pay <送る相手> <金額> : 電子マネーを友達に振り込む")
                    return true
                }

                if (amount <1){
                    sendMsg(sender,"§c§l1円以上を入力してください！")
                    return true
                }

                if (checking[sender] == null||checking[sender]!! != command){

                    sendMsg(sender,"§7§l送る電子マネー:${format(amount)}円")
                    sendMsg(sender,"§7§l送る相手:${args[0]}")
                    sendMsg(sender,"§7§l確認のため、同じコマンドをもう一度入力してください")

                    checking[sender] = command

                    return true
                }

                checking.remove(sender)

                val p = Bukkit.getPlayer(args[0])

                if (p==null){
                    sendMsg(sender,"§c§l送る相手がオフラインかもしれません")
                    return true
                }

                if (!vault.withdraw(sender.uniqueId,amount)){
                    sendMsg(sender,"§c§l送る電子マネーが足りません！")
                    return true
                }

                vault.deposit(p.uniqueId,amount)

                sendMsg(sender,"§a§l送金できました！")
                sendMsg(p,"§a${sender.name}さんから${format(amount)}円送られました！")

                return true

            }

            "mpay" ->{
                if (!sender.hasPermission(USER))return true

                if (sender !is Player)return true

                if (!isEnabled)return true

                if (args.size != 2){
                    sendMsg(sender,"§c§l/mpay <送る相手> <金額> : 銀行のお金を友達に振り込む")
                    return false
                }

                val a = args[1].replace(",","")

                val amount = ZenkakuToHankaku(a)

                if (amount == -1.0){
                    sendMsg(sender,"§c§l/mpay <送る相手> <金額> : 銀行のお金を友達に振り込む")
                    return true
                }

                if (amount <1){
                    sendMsg(sender,"§c§l1円以上を入力してください！")
                    return true
                }

                if (checking[sender] == null||checking[sender]!! != command){

                    sendMsg(sender,"§7§l送る銀行のお金:${format(amount)}円")
                    sendMsg(sender,"§7§l送る相手:${args[0]}")
                    sendMsg(sender,"§7§l確認のため、同じコマンドをもう一度入力してください")

                    checking[sender] = command

                    return true
                }

                checking.remove(sender)

                es.execute {
                    val uuid = Bank.getUUID(args[0])

                    if (uuid == null){
                        sendMsg(sender,"§c§l送金失敗！まだman10サーバーにきたことがない人かもしれません！")
                        return@execute
                    }


                    if (!Bank.withdraw(sender.uniqueId,amount,this,"RemittanceTo${args[0]}")){
                        sendMsg(sender,"§c§l送金する銀行のお金が足りません！")
                        return@execute

                    }

                    Bank.deposit(uuid,amount,this,"RemittanceFrom${sender.name}")

                    sendMsg(sender,"§a§l送金成功！")

                    val p = Bukkit.getPlayer(uuid)?:return@execute
                    sendMsg(p,"§a${sender.name}さんから${format(amount)}円送られました！")
                }

                return true

            }
        }

        return true
    }

    private val aliasList = mutableListOf("bal","balance","money","bank","deposit","withdraw")

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {

        if (aliasList.contains(alias))return Collections.emptyList()

        if (alias == "pay" || alias == "mpay"){

            if (args.size > 1)return Collections.emptyList()

            val pList = mutableListOf<String>()

            for (p in Bukkit.getOnlinePlayers()){

                if (p == sender)continue
                pList.add("${p.name} ")
            }

            return pList
        }

        return Collections.emptyList()
    }


    private fun ZenkakuToHankaku(number:String):Double{

        val normalize = Normalizer.normalize(number,Normalizer.Form.NFKC)

        val double = normalize.toDoubleOrNull() ?: return -1.0

        return double
    }

    @EventHandler
    fun login(e:PlayerJoinEvent){
        es.execute {
            Bank.createAccount(e.player.uniqueId)
            Bank.changeName(e.player)
            EstateData.createEstateData(e.player)
        }
    }

    @EventHandler
    fun logout(e:PlayerQuitEvent){
        es.execute {
            EstateData.saveCurrentEstate(e.player)
        }
    }
}
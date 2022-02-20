package red.man10.man10bank

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent
import org.apache.commons.lang.math.NumberUtils
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.MySQLManager.Companion.mysqlQueue
import red.man10.man10bank.atm.ATMData
import red.man10.man10bank.atm.ATMInventory
import red.man10.man10bank.atm.ATMListener
import red.man10.man10bank.cheque.Cheque
import red.man10.man10bank.history.EstateData
import red.man10.man10bank.loan.*
import red.man10.man10score.ScoreDatabase
import java.text.Normalizer
import java.text.SimpleDateFormat
import kotlin.math.abs
import kotlin.math.floor


class Man10Bank : JavaPlugin(),Listener {

    companion object{
        const val prefix = "§l[§e§lMan10Bank§f§l]"

        lateinit var vault : VaultManager

        lateinit var plugin : Man10Bank

        lateinit var dunceHat : ItemStack

        var kickDunce = false

        var isInstalledShop = false

        fun sendMsg(p:Player,msg:String){
            p.sendMessage(prefix+msg)
        }

        fun format(double: Double):String{
            return String.format("%,.0f",double)
        }

        const val OP = "man10bank.op"
        const val USE_CHEQUE = "man10bank.use_cheque"
        const val ISSUE_CHEQUE = "man10bank.issue_cheque"

        var bankEnable = true

        var loanFee : Double = 1.0
        var loanRate : Double = 1.0
        var loanMax : Double = 10000000.0

        var paymentThread = false
        var loggingServerHistory = false
    }

    private val checking = HashMap<Player,Command>()

    override fun onEnable() {
        // Plugin startup logic

        plugin = this

        saveDefaultConfig()

        mysqlQueue()

        vault = VaultManager(this)

        loadConfig()

        server.pluginManager.registerEvents(this,this)
        server.pluginManager.registerEvents(Event(),this)
        server.pluginManager.registerEvents(ATMListener,this)
        server.pluginManager.registerEvents(Cheque,this)

        getCommand("mlend")!!.setExecutor(LoanCommand())
        getCommand("mrevo")!!.setExecutor(ServerLoanCommand())

    }

    override fun onDisable() {
        // Plugin shutdown logic
        mysqlQueue.add("quit")
        Bukkit.getScheduler().cancelTasks(this)
    }

    private fun loadConfig(){

        reloadConfig()

        loanFee = config.getDouble("mlendFee",0.1)
        loanMax = config.getDouble("mlendMax",10000000.0)
        loanRate = config.getDouble("mlendRate",1.0)

        loggingServerHistory = config.getBoolean("loggingServerHistory",false)
        paymentThread = config.getBoolean("paymentThread",false)

        dunceHat = config.getItemStack("dunceHat")?: ItemStack(Material.STONE)
        kickDunce = config.getBoolean("kickDunce",false)

        isInstalledShop = config.getBoolean("isInstalledShop", true)

        val hasShop = plugin.server.pluginManager.getPlugin("Man10ShopV2")!=null

        if (!hasShop && isInstalledShop){
            Bukkit.getLogger().warning("このサーバーにはMan10ShopV2が導入されていません！")
        }

        ServerLoan.medianPercentage = config.getDouble("revolving.medianPercentage")
        ServerLoan.profitPercentage = config.getDouble("revolving.profitPercentage")
        ServerLoan.scorePercentage = config.getDouble("revolving.scorePercentage")
        ServerLoan.maxServerLoanAmount = config.getDouble("revolving.maxServerLoan")
        ServerLoan.revolvingFee = config.getDouble("revolving.revolvingFee")
        ServerLoan.lastPaymentCycle = config.getInt("revolving.lastPaymentCycle")
        ServerLoan.isEnable = config.getBoolean("revolving.enable",false)

        ATMData.loadItem()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {


        when(label){

            "mchequeop" ->{//mchequeop amount <memo>
                if (sender !is Player)return false
                if (!sender.hasPermission(OP))return false

                val amount = args[0].toDoubleOrNull()?:return false
                val note = if (args.size>1)args[1] else null

                Bukkit.getScheduler().runTaskAsynchronously(this, Runnable { Cheque.createCheque(sender,amount,note,true) })

                return true
            }

            "mcheque" ->{//mcheque amount <memo>
                if (sender !is Player)return false
                if (!sender.hasPermission(ISSUE_CHEQUE)){
                    sendMsg(sender,"§cあなたは小切手を発行する権限がありません")
                    return false
                }

                if (args.isEmpty()){
                    sendMsg(sender,"§e§l/mcheque <金額> <メモ>")
                    return true
                }

                val amount = floor(ZenkakuToHankaku(args[0]))

                if (amount<=0.0){
                    sendMsg(sender,"金額を1以上にしてください")
                    return true
                }

                val note = if (args.size>1)args[1] else null

                Bukkit.getScheduler().runTaskAsynchronously(this, Runnable { Cheque.createCheque(sender,amount,note,false) })

                return true
            }

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

                val page = if (args.isEmpty()) 1 else args[0].toIntOrNull()?:1

                Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                    val balTopMap = EstateData.getBalanceTop(page)
                    val total = EstateData.getBalanceTotal()

                    var i = (page*10)-9

                    if (sender is Player){

                        sendMsg(sender,"§6§k§lXX§e§l富豪トップ${page*10}§6§k§lXX")

                        for (data in balTopMap){
                            sendMsg(sender,"§7§l${i}.§b§l${data.first} : §e§l${format(data.second)}円")
                            i++
                        }

                        sendMsg(sender,"§e§l電子マネーの合計:${format(total.vault)}円")
                        sendMsg(sender,"§e§l現金の合計:${format(total.cash)}円")
                        sendMsg(sender,"§e§l銀行口座の合計:${format(total.bank)}円")
                        sendMsg(sender,"§e§lショップ残高の合計:${format(total.shop)}円")
                        sendMsg(sender,"§e§lその他資産の合計:${format(total.estate)}円")
                        sendMsg(sender,"§c§l公的ローンの合計:${format(total.loan)}円")
                        sendMsg(sender,"§e§l全ての合計:${format(total.total())}円")

                        return@Runnable
                    }

                    sender.sendMessage("§6§k§lXX§e§l富豪トップ${page*10}§6§k§lXX")

                    for (data in balTopMap){
                        sender.sendMessage("§7§l${i}.§b§l${data.first} : §e§l${format(data.second)}円")
                        i++
                    }
                    sender.sendMessage("§e§l電子マネーの合計:${format(total.vault)}円")
                    sender.sendMessage("§e§l現金の合計:${format(total.estate)}円")
                    sender.sendMessage("§e§l銀行口座の合計:${format(total.bank)}円")
                    sender.sendMessage("§e§lショップ残高の合計:${format(total.shop)}円")
                    sender.sendMessage("§e§lその他資産の合計:${format(total.estate)}円")
                    sender.sendMessage("§c§l公的ローンの合計:${format(total.loan)}円")
                    sender.sendMessage("§e§l全ての合計:${format(total.total())}円")

                })

            }

            "mloantop" ->{

                val page = if (args.isEmpty()) 1 else args[0].toIntOrNull()?:1

                Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                    val loanTop = ServerLoan.getLoanTop(page)
                    val total = EstateData.getBalanceTotal()

                    var i = (page*10)-9

                    if (sender is Player){

                        sendMsg(sender,"§4§k§lXX§c§l借金トップ${page*10}§4§k§lXX")

                        for (data in loanTop){
                            sendMsg(sender,"§c§l${i}.§l${data.first} : §4§l${format(data.second)}円")
                            i++
                        }

                        sendMsg(sender,"§c§l公的ローンの合計:${format(total.loan)}円")

                        return@Runnable
                    }

                    sender.sendMessage("§4§k§lXX§c§l借金トップ${page*10}§4§k§lXX")

                    for (data in loanTop){
                        sender.sendMessage("§c§l${i}.§l${data.first} : §4§l${format(data.second)}円")
                        i++
                    }

                    sender.sendMessage("§c§l公的ローンの合計:${format(total.loan)}円")

                })

            }

            "bal","balance","money","bank" ->{

                if (sender !is Player)return false

                if (args.isEmpty()){
                    Bukkit.getScheduler().runTaskAsynchronously(this, Runnable { showBalance(sender,sender) })
                    return true
                }

                when(args[0]){

                    "help" ->{
                        showCommand(sender)
                    }

                    "bs" ->{
                        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                            showBalanceSheet(sender,sender)
                        })
                    }

                    "log" ->{

                        val page = if (args.size>=2) args[1].toIntOrNull()?:0 else 0

                        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                            val list = Bank.getBankLog(sender,page)

                            sendMsg(sender,"§d§l===========銀行の履歴==========")
                            for (data in list){

                                val tag = if (data.isDeposit) "§a[入金]" else "§c[出金]"
                                sendMsg(sender,"$tag §e${data.dateFormat} §e§l${format(data.amount)} §e${data.note}")

                            }

                            val previous = if (page!=0) {
                                text("${prefix}§b§l<<==前のページ ").clickEvent(ClickEvent.runCommand("/bal log ${page-1}"))
                            }else text(prefix)

                            val next = if (list.size == 10){
                                text("§b§l次のページ==>>").clickEvent(ClickEvent.runCommand("/bal log ${page+1}"))
                            }else text("")

                            sender.sendMessage(previous.append(next))

                        })

                        return true
                    }

                    "logop" ->{

                        if (!sender.hasPermission(OP))return false

                        val p = if (args.size >= 2)Bukkit.getPlayer(args[1]) else sender
                        val page = if (args.size>=2) args[2].toIntOrNull()?:0 else 0

                        if (p == null){
                            sender.sendMessage("プレイヤーがオフラインです")
                            return true
                        }

                        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                            val list = Bank.getBankLog(p,page)

                            sendMsg(sender,"§d§l===========銀行の履歴==========")
                            for (data in list){

                                val tag = if (data.isDeposit) "§a[入金]" else "§c[出金]"
                                sendMsg(sender,"$tag §e${data.dateFormat} ${data.note} ${format(data.amount)}")

                            }

                            val previous = if (page!=0) {
                                text("${prefix}§b§l<<==前のページ ").clickEvent(ClickEvent.runCommand("/bal logop ${page-1}"))
                            }else text(prefix)

                            val next = if (list.size == 10){
                                text("§b§l次のページ==>>").clickEvent(ClickEvent.runCommand("/bal logop ${page+1}"))
                            }else text("")

                            sender.sendMessage(previous.append(next))

                        })

                    }

                    "init" ->{
                        if (!sender.hasPermission(OP))return false

                        if (args.size!=2)return false

                        if (checking[sender]== null || checking[sender]!=command){
                            sendMsg(sender,"データは復元できません。確認のため、もう一度入力してください")
                            checking[sender] = command
                            return true
                        }

                        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                            Bank.init(args[1])
                            sendMsg(sender,"${args[1]}のデータを初期化しました")

                        })

                        checking.remove(sender)

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

                        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                            val uuid = Bank.getUUID(args[1])?: return@Runnable

                            if (Bank.withdraw(uuid,amount,this,"TakenByCommand","サーバーから徴収").first!=0){
                                Bank.setBalance(uuid,0.0)
                                sendMsg(sender,"§a回収額が残高を上回っていたので、残高が0になりました")
                                return@Runnable
                            }
                            sendMsg(sender,"§a${format(amount)}円回収しました")
                            sendMsg(sender,"§a現在の残高：${format(Bank.getBalance(uuid))}")

                        })

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

                        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                            val uuid =  Bank.getUUID(args[1])?: return@Runnable

                            Bank.deposit(uuid,amount,this,"GivenFromServer","サーバーから発行")

                            sendMsg(sender,"§a${format(amount)}円入金しました")
                            sendMsg(sender,"§a現在の残高：${format(Bank.getBalance(uuid))}")

                        })

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

                        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                            val uuid =  Bank.getUUID(args[1])?: return@Runnable

                            Bank.setBalance(uuid,amount)

                            sendMsg(sender,"§a${format(amount)}円に設定しました")

                        })
                    }

                    "on" ->{
                        if (!sender.hasPermission(OP))return false

                        bankEnable = true

                        Bukkit.broadcast(text("§e§lMan10Bankが開きました！"))
                        return true

                    }

                    "off" ->{
                        if (!sender.hasPermission(OP))return false

                        bankEnable = false
                        Bukkit.broadcast(text("§e§lMan10Bankが閉じました！"))
                        return true

                    }

                    "reload" ->{
                        if (!sender.hasPermission(OP))return false

                        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                            reloadConfig()
                            loadConfig()
                            Bank.reload()
                        })

                    }

                    "hat" ->{
                        if (!sender.hasPermission(OP))return false

                        val item = sender.inventory.itemInMainHand

                        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                            config.set("dunceHat",item)
                            saveConfig()
                        })

                    }

                    else ->{
                        val p = Bukkit.getOfflinePlayer(args[0]).player

                        if (!sender.hasPermission(OP))return true

                        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                            if (p==null){
                                EstateData.showOfflineUserEstate(sender,args[0])
                                return@Runnable
                            }

                            showBalance(sender,p)
                        })

                        return true

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

                Bank.asyncDeposit(sender.uniqueId,amount,this,"PlayerDepositOnCommand","/depositによる入金"){ code:Int,_:Double,_:String ->

                    if (code != 0){
                        sendMsg(sender,"入金エラーが発生しました")
                        vault.deposit(sender.uniqueId,amount)
                        return@asyncDeposit
                    }

                    if (code == 0){ sendMsg(sender,"§a§l入金できました！") }
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

                Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                    val amount = if (args[0] == "all"){
                        Bank.getBalance(sender.uniqueId)
                    }else{

                        val a = args[0].replace(",","")

                        val b = ZenkakuToHankaku(a)

                        if (b == -1.0){
                            sendMsg(sender,"§c§l数字で入力してください！")
                            return@Runnable
                        }
                        b
                    }

                    if (amount < 1){
                        sendMsg(sender,"§c§l1円以上を入力してください！")
                        return@Runnable
                    }


                    Bank.asyncWithdraw(sender.uniqueId,amount,this,"PlayerWithdrawOnCommand","/withdrawによる出金") { code: Int, _: Double, _: String ->
                        if (code == 2){
                            sendMsg(sender,"§c§l銀行のお金が足りません！")
                            return@asyncWithdraw
                        }
                        vault.deposit(sender.uniqueId,amount)
                        sendMsg(sender,"§a§l出金できました！")
                    }

                })


                return true
            }

            "pay" ->{
//                if (!sender.hasPermission(USER))return true

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
//                if (!sender.hasPermission(USER))return true

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

                Bukkit.getScheduler().runTaskAsynchronously(this, Runnable  {
                    val uuid = Bank.getUUID(args[0])

                    if (uuid == null){
                        sendMsg(sender,"§c§l送金失敗！まだman10サーバーにきたことがない人かもしれません！")
                        return@Runnable
                    }


                    if (Bank.withdraw(sender.uniqueId,amount,this,"RemittanceTo${args[0]}","${args[0]}へ送金").first != 0){
                        sendMsg(sender,"§c§l送金する銀行のお金が足りません！")
                        return@Runnable

                    }

                    Bank.deposit(uuid,amount,this,"RemittanceFrom${sender.name}","${sender.name}からの送金")

                    sendMsg(sender,"§a§l送金成功！")

                    val p = Bukkit.getPlayer(uuid)?:return@Runnable
                    sendMsg(p,"§a${sender.name}さんから${format(amount)}円送られました！")
                })

                return true

            }

            "ballog" -> {

                if (sender !is Player)return false

                val page = if (args.isNotEmpty()) args[0].toIntOrNull()?:0 else 0

                Bukkit.getScheduler().runTaskAsynchronously(this, Runnable {
                    val list = Bank.getBankLog(sender,page)

                    sendMsg(sender,"§d§l===========銀行の履歴==========")
                    for (data in list){

                        val tag = if (data.isDeposit) "§a[入金]" else "§c[出金]"
                        sendMsg(sender,"$tag §e${data.dateFormat} §e§l${format(data.amount)} §e${data.note}")

                    }

                    val previous = if (page!=0) {
                        text("${prefix}§b§l<<==前のページ ").clickEvent(ClickEvent.runCommand("/ballog ${page-1}"))
                    }else text(prefix)

                    val next = if (list.size == 10){
                        text("§b§l次のページ==>>").clickEvent(ClickEvent.runCommand("/ballog ${page+1}"))
                    }else text("")

                    sender.sendMessage(previous.append(next))

                })

                return true


            }
        }

        return true
    }

    private fun ZenkakuToHankaku(number: String): Double {

        val normalize = Normalizer.normalize(number, Normalizer.Form.NFKC)

        return normalize.toDoubleOrNull() ?: return -1.0
    }

    private fun showBalance(sender:Player,p:Player){

        //時差による表示ずれ対策で、一旦所持金を呼び出す

        val loan = ServerLoan.getBorrowingAmount(p)
        val payment = ServerLoan.getPaymentAmount(p)
        val nextDate = ServerLoan.getNextPayTime(p)
        val score = ScoreDatabase.getScore(p.uniqueId)

        val bankAmount = Bank.getBalance(p.uniqueId)

        val cash: Double = EstateData.getCash(p)
        val estate: Double = EstateData.getEstate(p)

        sendMsg(sender,"§e§l==========${p.name}のお金==========")
        sendMsg(sender," §b§l電子マネー:  §e§l${format(vault.getBalance(p.uniqueId))}円")
        sendMsg(sender," §b§l銀行:  §e§l${format(bankAmount)}円")
        if (cash>0.0){ sendMsg(sender," §b§l現金:  §e§l${format(cash)}円") }
        if (estate>0.0){ sendMsg(sender," §b§lその他の資産:  §e§l${format(estate)}円") }
        if(EstateData.getShopTotalBalance(p) > 0.0) sendMsg(sender, " §b§lショップ口座:  §e§l${format(EstateData.getShopTotalBalance(p))}円")

        sendMsg(sender," §b§lスコア: §a§l${format(score.toDouble())}")

        if (loan!=0.0 && nextDate!=null){
            sendMsg(sender," §b§lまんじゅうリボ:  §c§l${format(loan)}円")
            sendMsg(sender," §b§l支払額:  §c§l${format(payment)}円")
            sendMsg(sender," §b§l次の支払日: §c§l${SimpleDateFormat("yyyy-MM-dd").format(nextDate.first)}")
            if (nextDate.second>0){
                sendMsg(sender," §c§lMan10リボの支払いに失敗しました(失敗回数:${nextDate.second})。支払いに失敗するとスコアの減少やJailがあります")
            }
        }

        sender.sendMessage(text("$prefix §a§l§n[ここをクリックでコマンドをみる]").clickEvent(ClickEvent.runCommand("/bank help")))

    }

    private fun showBalanceSheet(sender:Player,p:Player){

        val serverLoan = ServerLoan.getBorrowingAmount(p)
        val userLoan = LoanData.getTotalLoan(p)
        val bankAmount = Bank.getBalance(p.uniqueId)
        val cash: Double = EstateData.getCash(p)
        val estate: Double = EstateData.getEstate(p)
        val bal = vault.getBalance(p.uniqueId)

        val assets = bankAmount+cash+estate+bal
        val liability = serverLoan + userLoan
        val equity = assets-liability
        val symbol = if (equity<0) { "△" } else {""}

        sender.sendMessage("§e§l===============${p.name}のバランスシート=================")
        sender.sendMessage(String.format(" §b現金:        %14s §f| §c個人間借金:            §c%14s", format(cash), format(userLoan)))
        sender.sendMessage(String.format(" §b電子マネー:   %14s §f| §cMan10リボ:            §c%14s", format(bal), format(serverLoan)))
        sender.sendMessage(String.format(" §b銀行:        %14s §f| §c合計負債:              §c%14s", format(bankAmount), format(liability)))
        sender.sendMessage(String.format(" §bその他:      %14s §f| §a純資産:                §a%14s", format(estate), "${format(abs(equity))}${symbol}"))
        sender.sendMessage(String.format(" §b合計資産:     %14s §f| §c負債と純資産:          §b%14s", format(assets), format(liability+equity)))

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
    fun login(e:PlayerJoinEvent){

        val p = e.player

        Bank.loginProcess(p)

        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable  {
            Thread.sleep(3000)
            showBalance(p,p)

            val score = ScoreDatabase.getScore(p.uniqueId)
            if (score<=-300 && ServerLoan.getBorrowingAmount(p)>0){

                if (kickDunce){
                    Bukkit.getScheduler().runTask(this,Runnable{
                        p.kick(text("§c§lあなたは借金の支払いをせずにスコアが-300を下回っているので、このワールドに入れません！"))
                    })
                    return@Runnable
                }

                sendMsg(p,"§c§lあなたは借金の支払いをせずにスコアが-300を下回っているので、§e[§8§lLoser§e]§c§lになっています！ ")
                Bukkit.getScheduler().runTask(this,Runnable{

                    if (!p.hasPermission("man10bank.loser")){
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"lp user ${p.name} parent add loser")
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"man10kit pop ${p.name} ")
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"man10kit set ${p.name} loser")
                    }
                })
            }
        })
    }

    @EventHandler (priority = EventPriority.LOWEST)
    fun logout(e:PlayerQuitEvent){
        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable { EstateData.saveCurrentEstate(e.player) })
    }

    @EventHandler
    fun closeEnderChest(e:InventoryCloseEvent){

        if (e.inventory.type != InventoryType.ENDER_CHEST)return

        val p = e.player as Player

        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable { EstateData.saveCurrentEstate(p) })
    }
}
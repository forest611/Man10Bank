package red.man10.man10bank.command

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent.runCommand
import net.kyori.adventure.text.event.HoverEvent.showText
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey
import red.man10.man10bank.Bank
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.format
import red.man10.man10bank.Man10Bank.Companion.loanFee
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.prefix
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.loan.CollateralGUI
import red.man10.man10bank.loan.LoanData
import red.man10.man10bank.loan.LoanData.Companion.getLoanDataList
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
import org.bukkit.command.TabCompleter

class LocalLoanCommand : CommandExecutor, TabCompleter {

    private val USER = "man10lend.user"
    private val OP = "man10lend.op"

    private val sdf = SimpleDateFormat("yyyy-MM-dd")
    
    private val cacheMap = ConcurrentHashMap<Player, Cache>()
    // 手形IDごとの処理中フラグを管理（排他制御用）
    private val processingNotes = ConcurrentHashMap<Int, Boolean>()

    data class Cache(
        var day: Int = 0,
        var amount: Double = 0.0,
        var paybackAmount: Double = 0.0,
        var collateralItems: MutableList<ItemStack> = mutableListOf(),  // 担保アイテムリスト
        var lend: Player,
        var borrow: Player,
        var allowed: Boolean = false  // 借り手が借りることを許可したかどうか
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (label != "mlend") return false
        if (sender !is Player) return true

        if (!sender.hasPermission(OP) &&
            (!Man10Bank.enableLocalLoan || Man10Bank.localLoanDisableWorlds.contains(sender.world.name))) {
            sendMsg(sender, "§c§lこのエリアでは個人間借金の取引を行うことはできません。")
            return true
        }

        if (args.isEmpty()) {
            showUsage(sender)
            return true
        }

        return when (args[0]) {
            "off" ->            {
                if (sender.hasPermission(OP)) {
                    Man10Bank.enableLocalLoan = false
                    plugin.config.set("enableLocalLoan",true)
                    plugin.saveConfig()
                    sendMsg(sender, "§c§l個人間借金の取引を無効化しました")
                }
                true
            }
            "on" ->             {
                if (sender.hasPermission(OP)) {
                    Man10Bank.enableLocalLoan = true
                    plugin.config.set("enableLocalLoan",true)
                    plugin.saveConfig()
                    sendMsg(sender, "§a§l個人間借金の取引を有効化しました")
                }
                true
            }
            "allow" ->          { onAllowed(sender); true }
            "deny" ->           { onDenied(sender); true }
            "collateral" ->     { showCollateral(sender); true }
            "setcollateral" ->  { setCollateral(sender); true }
            "confirm" ->        { onConfirmed(sender); true }
            "userdata" ->       {
                if (args.size >= 2){
                    userData(sender, args[1])
                }
                true
            }
            "reissue" ->        {
                if (args.size >= 2) {
                    args[1].toIntOrNull()?.let { reissue(sender, it) }
                        ?: sendMsg(sender, "数字を入力してください")
                }
                true
            }
            "collect" ->        { collectDebt(sender); true }
            "collectcollateral" -> { collectCollateral(sender); true }
            "receivecollateral" -> { receiveCollateral(sender,args); true }
            else ->                {onPropose(sender, args)}
        }
    }

    private fun showUsage(p: Player) {
        sendMsg(p, "§a/mlend <プレイヤー> <貸出金額> <返済金額> <期間(日)>")
        sendMsg(p, "§a貸出金額の${loanFee * 100}%を手数料としていただきます")
        sendMsg(p,"§a/mlend receivecollateral 担保を取り戻す")
    }

    private fun onPropose(sender: Player, args: Array<out String>): Boolean {
        if (!sender.hasPermission(USER)) {
            sendMsg(sender, "§4お金を貸す権限がありません！")
            return false
        }

        val borrow = Bukkit.getPlayer(args[0]) ?: run {
            sendMsg(sender, "§c相手はオフラインです")
            return false
        }

        if (sender.name == borrow.name && !sender.hasPermission(OP)) {
            sendMsg(sender, "§c自分に借金はできません")
            return false
        }

        if (!borrow.hasPermission(USER)) {
            sendMsg(sender, "§4貸し出す相手に権限がありません")
            return false
        }

        if (getLendPlayerCache(sender) != null){
            sendMsg(sender, "§c§lあなたはすでに借金の提案を行っています！")
            return false
        }

        if (cacheMap.containsKey(borrow)) {
            sendMsg(sender, "§c§l相手はすでに借金の提案を受けています！")
            return false
        }

        val amount: Double
        val paybackAmount: Double
        val day: Int
        try {
            amount = floor(args[1].toDouble())
            paybackAmount = floor(args[2].toDouble())
            day = args[3].toInt()
            if (day > 365 || day < 0) {
                sendMsg(sender, "§c返済期限は0日以上、一年以内にしてください！")
                return false
            }
            if (amount > Man10Bank.loanMax || amount < 1) {
                sendMsg(sender, "§c貸出金額は1円以上、${format(Man10Bank.loanMax)}円以下に設定してください！")
                return false
            }
            if (paybackAmount < amount) {
                sendMsg(sender, "§c返済金額は貸出金額以上に設定してください！")
                return false
            }
            if (paybackAmount > amount * 2) {
                sendMsg(sender, "§c返済金額は貸出金額の2倍以下に設定してください！")
                return false
            }
        } catch (e: Exception) {
            sendMsg(sender, "§c入力に問題があります！")
            return false
        }

        sendMsg(sender, "§a§l借金の提案を相手に提示しました")
        sendMsg(borrow, "§e§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")
        sendMsg(borrow, "§e§kXX§b§l借金の提案(3分以内に設定してください)§e§kXX")
        sendMsg(borrow, "§e貸し出す人:§l${sender.name}")
        sendMsg(borrow, "§e貸し出される金額: §l${format(amount)}円")
        sendMsg(borrow, "§e返す金額: §l${format(paybackAmount)}円")
        sendMsg(borrow, "§e返す日: §l${sdf.format(LoanData.calcDate(day))}")

        val allowOrDeny = text(" ${prefix}§6§l§n[担保を設定する] ").clickEvent(runCommand("/mlend setcollateral"))
            .append(text("§b§l§n[借りる] ").clickEvent(runCommand("/mlend allow")))
            .append(text("§c§l§n[借りない]").clickEvent(runCommand("/mlend deny")))
        borrow.sendMessage(allowOrDeny)

        sendMsg(borrow, "§e§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")

        val cache = Cache(
            amount = amount,
            paybackAmount = paybackAmount,
            day = day,
            borrow = borrow,
            lend = sender
        )
        cacheMap[borrow] = cache

        // 3分後に借り手がまだ許可していなければ拒否する
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val cacheLater = cacheMap[borrow] ?: return@Runnable
            if (!cacheLater.allowed) {
                sendMsg(borrow, "§c§l借金の提案を拒否しました。3分以内に許可しなかったため")
                onDenied(borrow)
            }
        }, 3600L) // 3分後に自動キャンセル
        return true
    }

    private fun onAllowed(borrow: Player) {
        if (!borrow.hasPermission(USER)) return
        val cache = cacheMap[borrow] ?: run {
            sendMsg(borrow, "§cあなたに借金の提案は来ていません！")
            return
        }

        // 承認フラグ
        if (cache.allowed) {
            sendMsg(borrow, "§c§lあなたはすでに借りることを許可しています！")
            return
        }
        cache.allowed = true
        cacheMap[borrow] = cache // 更新

        // 提案者がログアウトしたら拒否する
        if (!cache.lend.isOnline) {
            sendMsg(borrow, "§c§l提案者がログアウトしました")
            onDenied(borrow)
            return
        }
        // 借り手が「借りる」をクリックした時点で、貸し手に担保確認を促す
        sendMsg(borrow, "§a§l借金の申請を受け付けました。貸し手の承認を待っています...")
        if (!cache.lend.isOnline) {
            sendMsg(borrow, "§c§l貸し手がオフラインになりました")
            return
        }

        sendMsg(cache.lend, "§e§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")
        sendMsg(cache.lend, "§b§l${borrow.name}が借金を受け入れました！1分以内に承認か拒否を行ってください")

        if (cache.collateralItems.isNotEmpty()) {
            sendMsg(cache.lend, "§e担保: ${cache.collateralItems.size}個のアイテム")
            val viewCollateralButton = text("${prefix}§6§l§n[担保を確認する] ").clickEvent(runCommand("/mlend collateral"))
            cache.lend.sendMessage(viewCollateralButton)
        } else {
            sendMsg(cache.lend, "§c担保: なし")
        }

        val confirmDenyButtons = text("${prefix}§a§l§n[最終承認] ").clickEvent(runCommand("/mlend confirm"))
            .append(text("§c§l§n[拒否]").clickEvent(runCommand("/mlend deny")))
        cache.lend.sendMessage(confirmDenyButtons)
        sendMsg(cache.lend, "§e§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")

        // 1分後に自動キャンセル
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val cacheLater = cacheMap[borrow] ?: return@Runnable
            cacheLater.lend.performCommand("mlend deny")
        }, 1200L)
    }

    private fun onDenied(sender: Player) {
        val cache = cacheMap[sender] ?: getLendPlayerCache(sender) ?: run {
            sendMsg(sender, "§cあなたに借金の提案は来ていません！")
            return
        }
        cacheMap.remove(cache.borrow)
        sendMsg(sender, "§c借金の提案を断りました！")
        cache.lend.sendMessage("§c相手が借金の提案を拒否しました！")

        // 担保が設定されていた場合、借り手に返却
        if (cache.collateralItems.isNotEmpty()) {
            sendInventoryAndDrop(cache.borrow, cache.collateralItems)
        }
    }

    private fun onConfirmed(sender: Player) {
        // 貸し手として最終承認を行う
        val cache = cacheMap.values.find { it.lend == sender } ?: run {
            sendMsg(sender, "§c承認する借金の提案がありません")
            return
        }
        //　増殖対策で先にキャッシュを削除しておく
        cacheMap.remove(cache.borrow)

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            sendMsg(sender, "Man10Bankシステムに問い合わせ中・・・§l§kXX")
            val data = LoanData()
            // 貸出処理。失敗したら担保を返す
            if (!data.create(cache.lend, cache.borrow, cache.amount, cache.paybackAmount, cache.day, cache.collateralItems.takeIf { it.isNotEmpty() })) {
                if (cache.collateralItems.isNotEmpty()) {
                    sendInventoryAndDrop(cache.borrow, cache.collateralItems)
                    sendMsg(cache.lend, "§c§l借金の契約に失敗しました。担保を返却しました。")
                }
                return@Runnable
            }
            cache.lend.inventory.addItem(data.getNote())
            sendMsg(cache.borrow, "§a§l借金の契約が成立しました！")
            sendMsg(cache.lend, "§a§l借金の契約が成立しました！")
        })
    }

    private fun userData(sender: Player, name: String) {
        if (!sender.hasPermission(OP)) return
        Thread {
            val uuid = Bank.getUUID(name)
            if (uuid == null) {
                sendMsg(sender, "存在しないユーザーです")
                return@Thread
            }
            sendMsg(sender, name)
            sendMsg(sender, "手形の再発行用ID/金額")
            val data = LoanData.getLoanDataList(uuid)
            data.forEach { sendMsg(sender, "§c§l${it.first}/${format(it.second)}") }
        }.start()
    }

    private fun reissue(sender: Player, id: Int) {
        if (!sender.hasPermission(OP)) return
        Thread {
            val data = LoanData().load(id) ?: run {
                sendMsg(sender, "借金情報が見つかりません")
                return@Thread
            }
            Bukkit.getScheduler().runTask(plugin, Runnable { sender.inventory.addItem(data.getNote()) })
        }.start()
    }

    private fun showCollateral(sender: Player) {
        // 借り手または貸し手として担保を確認
        val cache = cacheMap[sender] ?: getLendPlayerCache(sender) ?: run {
            sendMsg(sender, "§c借金の提案がありません")
            return
        }
        if (cache.collateralItems.isEmpty()) {
            sendMsg(sender, "§c担保が設定されていません")
            return
        }
        CollateralGUI.openCollateralViewGUI(sender, cache)
    }

    private fun setCollateral(sender: Player) {
        val cache = cacheMap[sender] ?: run {
            sendMsg(sender, "§c借金の提案がありません")
            return
        }
        if (cache.borrow != sender) {
            sendMsg(sender, "§c借り手のみが担保を設定できます")
            return
        }
        if (cache.allowed) {
            sendMsg(sender, "§c§lすでに借りることを許可しています。担保を設定することはできません。")
            return
        }
        CollateralGUI.openCollateralGUI(sender, cache)
    }

    private fun collectDebt(sender: Player) {
        if (!sender.hasPermission(USER)) return
        
        val item = sender.inventory.itemInMainHand
        if (item.type.isAir) {
            sendMsg(sender, "§c手に手形を持ってください")
            return
        }
        
        val meta = item.itemMeta ?: return
        val id = meta.persistentDataContainer.get(NamespacedKey(plugin, "id"), PersistentDataType.INTEGER) ?: run {
            sendMsg(sender, "§cこれは有効な手形ではありません")
            return
        }

        // 排他制御：同じ手形で複数の処理が同時に実行されないようにする
        if (processingNotes.putIfAbsent(id, true) != null) {
            sendMsg(sender, "§c§lこの手形は現在処理中です。しばらくお待ちください。")
            return
        }
        Thread {
            try {
                val data = LoanData().load(id) ?: run {
                    sendMsg(sender, "§c手形情報が見つかりません")
                    return@Thread
                }
                data.collectMoney(sender, item)
            } finally {
                processingNotes.remove(id)
            }
        }.start()
    }

    private fun collectCollateral(sender: Player) {
        if (!sender.hasPermission(USER)) return
        
        val item = sender.inventory.itemInMainHand
        if (item.type.isAir) {
            sendMsg(sender, "§c手に手形を持ってください")
            return
        }
        
        val meta = item.itemMeta ?: return
        val id = meta.persistentDataContainer.get(NamespacedKey(plugin, "id"), PersistentDataType.INTEGER) ?: run {
            sendMsg(sender, "§cこれは有効な手形ではありません")
            return
        }

        // 排他制御：同じ手形で複数の処理が同時に実行されないようにする
        if (processingNotes.putIfAbsent(id, true) != null) {
            sendMsg(sender, "§c§lこの手形は現在処理中です。しばらくお待ちください。")
            return
        }
        Thread {
            try {
                val data = LoanData().load(id) ?: run {
                    sendMsg(sender, "§c手形情報が見つかりません")
                    return@Thread
                }
                data.collectCollateral(sender, item)
            } finally {
                processingNotes.remove(id)
            }
        }.start()
    }

    private fun receiveCollateral(sender: Player, args: Array<out String>) {
        if (!sender.hasPermission(USER)) return

        if (args.size < 2) {
            showCollateralLoanList(sender)
            return
        }

        val id = args[1].toIntOrNull() ?: run {
            sendMsg(sender, "§c有効な手形IDを入力してください")
            return
        }

        // 排他制御：同じ手形で複数の処理が同時に実行されないようにする
        if (processingNotes.putIfAbsent(id, true) != null) {
            sendMsg(sender, "§c§lこの手形は現在処理中です。しばらくお待ちください。")
            return
        }
        Thread {
            try {
                val data = LoanData().load(id) ?: run {
                    sendMsg(sender, "§c手形情報が見つかりません")
                    return@Thread
                }
                data.receiveCollateral(sender)
            } finally {
                processingNotes.remove(id)
            }
        }.start()
    }

    private fun showCollateralLoanList(p: Player){
        val dataList = getLoanDataList(p.uniqueId)

        sendMsg(p,"§e§l取り戻せる担保の一覧[クリックで担保を受け取る]")
        for (data in dataList) {
            val loanData = LoanData().load(data.first) ?: continue
            if (loanData.collateralItems.isNullOrEmpty() || loanData.debt > 0.0) continue

            val paybackStr = SimpleDateFormat("yyyy-MM-dd").format(loanData.paybackDate)

            val text = text("${prefix}§b§l[${paybackStr}]")
                .clickEvent(runCommand("/mlend receivecollateral ${data.first}"))
                .hoverEvent(showText(text("§e§l担保を受け取る")))
            p.sendMessage(text)
        }
    }


    private fun getLendPlayerCache(player: Player): Cache? {
        return cacheMap[player] ?: cacheMap.values.find { it.lend.uniqueId == player.uniqueId }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        if (sender !is Player) return mutableListOf()
        if (command.name != "mlend") return mutableListOf()
        
        return when (args.size) {
            1 -> {
                val completions = mutableListOf<String>()
                // receivecollateralコマンドを補完
                // オンラインプレイヤー名を補完
                Bukkit.getOnlinePlayers().forEach { player ->
                    if (player.name.lowercase().startsWith(args[0].lowercase()) && player != sender) {
                        completions.add(player.name)
                    }
                }
                if ("receivecollateral".startsWith(args[0].lowercase())) {
                    completions.add("receivecollateral")
                }
                completions
            }
            2 -> {
                // プレイヤー名が入力されている場合、貸出金額のヒントを表示
                if (args[0] != "receivecollateral" && Bukkit.getPlayer(args[0]) != null) {
                    mutableListOf("<貸出金額>")
                } else {
                    mutableListOf()
                }
            }
            3 -> {
                // 貸出金額が入力されている場合、返済金額のヒントを表示
                if (args[0] != "receivecollateral" && Bukkit.getPlayer(args[0]) != null) {
                    mutableListOf("<返済金額>")
                } else {
                    mutableListOf()
                }
            }
            4 -> {
                // 返済金額が入力されている場合、期間のヒントを表示
                if (args[0] != "receivecollateral" && Bukkit.getPlayer(args[0]) != null) {
                    mutableListOf("<期間(日)>")
                } else {
                    mutableListOf()
                }
            }
            else -> mutableListOf()
        }
    }

    companion object {
        fun sendInventoryAndDrop(p:Player,list:List<ItemStack>){
            list.forEach { item ->
                if (p.inventory.firstEmpty() == -1) {
                    p.world.dropItem(p.location, item)
                } else {
                    p.inventory.addItem(item)
                }
            }
            sendMsg(p, "§e担保アイテムが返却されました。")
        }
    }
}

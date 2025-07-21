package red.man10.man10bank.loan

import net.kyori.adventure.text.Component.text
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.command.LocalLoanCommand

class CollateralGUI : Listener {
    
    // InventoryHolderを実装したクラス
    class CollateralHolder(
        val cache: LocalLoanCommand.Cache,
        val isEditable: Boolean = true
    ) : InventoryHolder {
        private lateinit var inventory: Inventory
        
        override fun getInventory(): Inventory = inventory
        
        fun setInventory(inv: Inventory) {
            this.inventory = inv
        }
    }

    companion object {
        private const val TITLE = "§6§l担保設定"
        private const val VIEW_TITLE = "§6§l担保確認"
        private const val CONFIRM_SLOT = 8  // 確定ボタンの位置（右端）
        
        // 担保設定GUIを開く
        fun openCollateralGUI(player: Player, cache: LocalLoanCommand.Cache) {
            val holder = CollateralHolder(cache, true)
            val inv = Bukkit.createInventory(holder, 9, TITLE)
            holder.inventory = inv
            
            // 既存の担保アイテムを表示（最大7個まで）
            cache.collateralItems.forEachIndexed { index, item ->
                if (index < 7) {  // 左側7スロット分
                    inv.setItem(index, item)
                }
            }
            
            // 確定ボタン
            val confirmButton = ItemStack(Material.LIME_STAINED_GLASS_PANE)
            val meta = confirmButton.itemMeta
            meta.displayName(text("§a§l担保を確定"))
            meta.lore = listOf(
                "§7クリックして担保を確定します",
                "§7担保に設定したアイテムは借金完済まで",
                "§7返却されません"
            )
            confirmButton.itemMeta = meta
            inv.setItem(CONFIRM_SLOT, confirmButton)
            
            player.openInventory(inv)
        }
        
        // 担保確認GUIを開く（読み取り専用）
        fun openCollateralViewGUI(player: Player, cache: LocalLoanCommand.Cache) {
            val holder = CollateralHolder(cache, false)
            val inv = Bukkit.createInventory(holder, 9, VIEW_TITLE)
            holder.inventory = inv
            
            // 担保アイテムを表示（クリック不可）
            cache.collateralItems.forEachIndexed { index, item ->
                if (index < 9) {
                    inv.setItem(index, item)
                }
            }
            
            player.openInventory(inv)
        }
    }
    
    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val player = e.whoClicked as? Player ?: return
        val holder = e.inventory.holder as? CollateralHolder ?: return
        val cache = holder.cache
        val clickedSlot = e.rawSlot
        
        // 読み取り専用モードの場合は全てキャンセル
        if (!holder.isEditable) {
            e.isCancelled = true
            return
        }

        // 確定ボタンをクリック
        if (clickedSlot == CONFIRM_SLOT) {
            e.isCancelled = true
            
            // 担保アイテムを収集（確定ボタン以外の7スロット）
            cache.collateralItems.clear()
            for (i in 0 until 7) {
                val item = e.inventory.getItem(i)
                if (item != null && item.type != Material.AIR) {
                    cache.collateralItems.add(item.clone())
                }
            }
            
            if (cache.collateralItems.isEmpty()) {
                sendMsg(player, "§c担保アイテムが設定されていません")
                return
            }
            
            player.closeInventory()
            sendMsg(player, "§a担保を設定しました（${cache.collateralItems.size}個のアイテム）")
            return
        }
    }
    
    @EventHandler
    fun onInventoryClose(e: InventoryCloseEvent) {
        val player = e.player as? Player ?: return
        val holder = e.inventory.holder as? CollateralHolder ?: return
        
        // 読み取り専用モードの場合は何もしない
        if (!holder.isEditable) return
        
        // 編集可能モードでタイトルが一致する場合のみ処理
        if (e.view.title() != text(TITLE)) return
        
        val cache = holder.cache
        
        // 閉じた時に担保アイテムを保存（確定ボタン以外の7スロット）
        cache.collateralItems.clear()
        for (i in 0 until 7) {
            val item = e.inventory.getItem(i)
            if (item != null && item.type != Material.AIR) {
                cache.collateralItems.add(item.clone())
            }
        }
        
        // アイテムをプレイヤーに返却
        for (i in 0 until 7) {
            val item = e.inventory.getItem(i)
            if (item != null && item.type != Material.AIR) {
                val leftover = player.inventory.addItem(item)
                leftover.values.forEach { player.world.dropItem(player.location, it) }
            }
        }
    }
    
    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }
}
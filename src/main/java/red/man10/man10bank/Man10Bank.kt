package red.man10.man10bank

import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.bank.service.BankService
import red.man10.man10bank.db.DatabaseProvider

class Man10Bank : JavaPlugin(), Listener {

    lateinit var bankService: BankService

    override fun onEnable() {
        saveDefaultConfig()
        DatabaseProvider.init(this)
        logger.info("Man10Bank を有効化しました。DB初期化: ${DatabaseProvider.isInitialized()}")

        bankService = BankService(DatabaseProvider.database())

    }

    override fun onDisable() {
        logger.info("Man10Bank を無効化しました。")
    }
}

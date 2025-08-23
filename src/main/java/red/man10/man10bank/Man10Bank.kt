package red.man10.man10bank

import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.db.DatabaseProvider

class Man10Bank : JavaPlugin(), Listener {
    override fun onEnable() {
        // 設定を保存し、DB接続を初期化する
        saveDefaultConfig()
        DatabaseProvider.init(this)

        // ここで必要に応じてコマンド/イベントを登録する
        logger.info("Man10Bank を有効化しました。DB初期化: ${'$'}{DatabaseProvider.isInitialized()}")
    }

    override fun onDisable() {
        // プラグイン停止時に必要なクリーンアップがあればここで実施
        logger.info("Man10Bank を無効化しました。")
    }
}

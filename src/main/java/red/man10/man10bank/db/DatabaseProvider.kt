package red.man10.man10bank.db

import org.bukkit.plugin.java.JavaPlugin
import org.ktorm.database.Database

object DatabaseProvider {
    @Volatile
    private var db: Database? = null

    data class DbConfig(
        val host: String,
        val port: Int,
        val db: String,
        val user: String,
        val pass: String,
    ) {
        val jdbcUrl: String =
            "jdbc:mysql://$host:$port/$db?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=UTC"
    }

    fun init(plugin: JavaPlugin) {
        if (db != null) return
        plugin.reloadConfig()
        val cfg = plugin.config
        val mysql = cfg.getConfigurationSection("mysql")
            ?: throw IllegalStateException("config.yml の mysql セクションが見つかりません")

        val conf = DbConfig(
            host = mysql.getString("host") ?: "localhost",
            port = mysql.getInt("port"),
            db = mysql.getString("db") ?: "man10_bank",
            user = mysql.getString("user") ?: "root",
            pass = mysql.getString("pass") ?: "",
        )

        db = Database.connect(
            url = conf.jdbcUrl,
            driver = "com.mysql.cj.jdbc.Driver",
            user = conf.user,
            password = conf.pass
        )
    }

    fun database(): Database = db
        ?: throw IllegalStateException("Database が初期化されていません。先に DatabaseProvider.init(plugin) を呼び出してください。")

    fun isInitialized(): Boolean = db != null
}

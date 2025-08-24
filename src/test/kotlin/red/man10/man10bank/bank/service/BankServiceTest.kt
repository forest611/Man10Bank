package red.man10.man10bank.bank.service

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.ktorm.database.Database
import red.man10.man10bank.shared.ResultCode
import java.sql.Connection
import java.util.UUID

class BankServiceTest {

    private lateinit var db: Database
    private lateinit var service: BankService

    @BeforeEach
    fun setUp() {
        db = Database.connect(
            url = "jdbc:h2:mem:bank;MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        // 最小限のテーブルを作成
        db.useConnection { c ->
            val st = c.createStatement()
            st.addBatch(
                """
                CREATE TABLE user_bank (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  player VARCHAR(16),
                  uuid VARCHAR(36),
                  balance DECIMAL
                );
                """.trimIndent()
            )
            st.addBatch(
                """
                CREATE TABLE money_log (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  player VARCHAR(16),
                  uuid VARCHAR(36),
                  plugin_name VARCHAR(16),
                  amount DECIMAL,
                  note VARCHAR(64),
                  display_note VARCHAR(64),
                  server VARCHAR(16),
                  deposit BOOLEAN,
                  date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                """.trimIndent()
            )
            st.executeBatch()
            st.close()
        }
        // Bukkit依存を避けるため、serverNameのみ渡す
        service = BankService(db, serverName = "test")
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
        db.useConnection { it.createStatement().use { st -> st.execute("DROP ALL OBJECTS") } }
    }

    @Test
    @DisplayName("getBalance: 未登録UUIDはnullを返す")
    fun getBalance_returnsNull_whenUnknown() = runBlocking {
        val uuid = UUID.randomUUID()
        val bal = service.getBalance(uuid)
        assertNull(bal)
    }

    @Test
    @DisplayName("deposit: 0以下の金額はINVALID_AMOUNT")
    fun deposit_invalidAmount_returnsInvalid() = runBlocking {
        val uuid = UUID.randomUUID()
        val res0 = service.deposit(uuid, 0.toBigDecimal(), "Test", "Deposit", null)
        val resNeg = service.deposit(uuid, (-10).toBigDecimal(), "Test", "Deposit", null)
        assertEquals(ResultCode.INVALID_AMOUNT, res0.code)
        assertEquals(ResultCode.INVALID_AMOUNT, resNeg.code)
    }

    @Test
    @DisplayName("withdraw: 0以下の金額はINVALID_AMOUNT")
    fun withdraw_invalidAmount_returnsInvalid() = runBlocking {
        val uuid = UUID.randomUUID()
        val res0 = service.withdraw(uuid, 0.toBigDecimal(), "Test", "Withdraw", null)
        val resNeg = service.withdraw(uuid, (-10).toBigDecimal(), "Test", "Withdraw", null)
        assertEquals(ResultCode.INVALID_AMOUNT, res0.code)
        assertEquals(ResultCode.INVALID_AMOUNT, resNeg.code)
    }

    // 残高不足の分岐は Bukkit の OfflinePlayer 参照前に取得できないため、
    // このテストではカバーしません（MockBukkit 等の導入で対応可能）。

    @Test
    @DisplayName("setBalance: 負の値はINVALID_AMOUNT")
    fun setBalance_negative_returnsInvalid() = runBlocking {
        val uuid = UUID.randomUUID()
        val res = service.setBalance(uuid, (-1).toBigDecimal(), "Test", "Set", null)
        assertEquals(ResultCode.INVALID_AMOUNT, res.code)
    }

    @Test
    @DisplayName("transfer: 0以下の金額はINVALID_AMOUNT")
    fun transfer_invalidAmount_returnsInvalid() = runBlocking {
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        val res0 = service.transfer(from, to, 0.toBigDecimal())
        val resNeg = service.transfer(from, to, (-5).toBigDecimal())
        assertEquals(ResultCode.INVALID_AMOUNT, res0.code)
        assertEquals(ResultCode.INVALID_AMOUNT, resNeg.code)
    }

    // ============ MockBukkit を用いた追加テスト（依存が無い環境ではスキップ） ============
    private fun mockBukkitAvailable(): Boolean = try {
        Class.forName("be.seeseemelk.mockbukkit.MockBukkit"); true
    } catch (_: Throwable) { false }

    private suspend fun <T> withMockBukkit(block: suspend (server: Any) -> T): T {
        val clazz = Class.forName("be.seeseemelk.mockbukkit.MockBukkit")
        val mock = clazz.getMethod("mock").invoke(null)
        return try { block(mock) } finally { clazz.getMethod("unmock").invoke(null) }
    }

    @Test
    @DisplayName("withdraw: 残高不足はINSUFFICIENT_FUNDS（MockBukkit）")
    fun withdraw_insufficientFunds_withMock_returnsInsufficient() = runBlocking {
        assumeTrue(mockBukkitAvailable(), "MockBukkit がクラスパスにありません。build.gradle に依存を追加してください。")
        withMockBukkit { server ->
            val serverClazz = Class.forName("be.seeseemelk.mockbukkit.ServerMock")
            val addPlayer = serverClazz.getMethod("addPlayer", String::class.java)
            val player = addPlayer.invoke(server, "Alice")
            val uuid = player.javaClass.getMethod("getUniqueId").invoke(player) as UUID
            val res = service.withdraw(uuid, 100.toBigDecimal(), "Test", "Withdraw", null)
            assertEquals(ResultCode.INSUFFICIENT_FUNDS, res.code)
        }
    }

    @Test
    @DisplayName("transfer: 正常系（送金成功でfrom残高が減る）（MockBukkit）")
    fun transfer_success_withMock_updatesBalances() = runBlocking {
        assumeTrue(mockBukkitAvailable(), "MockBukkit がクラスパスにありません。build.gradle に依存を追加してください。")
        withMockBukkit { server ->
            val serverClazz = Class.forName("be.seeseemelk.mockbukkit.ServerMock")
            val addPlayer = serverClazz.getMethod("addPlayer", String::class.java)
            val from = addPlayer.invoke(server, "FromUser")
            val to = addPlayer.invoke(server, "ToUser")
            val fromId = from.javaClass.getMethod("getUniqueId").invoke(from) as UUID
            val toId = to.javaClass.getMethod("getUniqueId").invoke(to) as UUID

            val dep = service.deposit(fromId, 1000.toBigDecimal(), "Test", "Deposit", null)
            assertEquals(ResultCode.SUCCESS, dep.code)

            val res = service.transfer(fromId, toId, 300.toBigDecimal())
            assertEquals(ResultCode.SUCCESS, res.code)

            val fromBal = service.getBalance(fromId)
            val toBal = service.getBalance(toId)
            assertEquals(700.toBigDecimal(), fromBal)
            assertEquals(300.toBigDecimal(), toBal)
        }
    }
}

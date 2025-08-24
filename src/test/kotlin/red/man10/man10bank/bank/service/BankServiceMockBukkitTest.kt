package red.man10.man10bank.bank.service

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.ktorm.database.Database
import red.man10.man10bank.shared.ResultCode
import java.util.UUID
import java.util.UUID.*

class BankServiceMockBukkitTest {

    private var server: Any? = null
    private lateinit var db: Database
    private lateinit var service: BankService

    private fun mockBukkitAvailable(): Boolean = try {
        Class.forName("be.seeseemelk.mockbukkit.MockBukkit"); true
    } catch (_: Throwable) { false }

    @BeforeEach
    fun setup() {
        assumeTrue(mockBukkitAvailable(), "MockBukkit がクラスパスにありません。build.gradle に依存を追加してください。")

        val mockBukkit = Class.forName("be.seeseemelk.mockbukkit.MockBukkit")
        val mockMethod = mockBukkit.getMethod("mock")
        server = mockMethod.invoke(null)

        val dbName = "bank-mock-" + randomUUID().toString().replace("-", "")
        db = Database.connect(
            url = "jdbc:h2:mem:${dbName};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        db.useConnection { c ->
            c.createStatement().use { st ->
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
            }
        }

        service = BankService(db, serverName = "test")
    }

    @AfterEach
    fun tearDown() {
        if (this::service.isInitialized) service.shutdown()
        server?.let {
            try {
                val mockBukkit = Class.forName("be.seeseemelk.mockbukkit.MockBukkit")
                val unmock = mockBukkit.getMethod("unmock")
                unmock.invoke(null)
            } catch (_: Throwable) { /* ignore */ }
        }
    }

    @Test
    @DisplayName("withdraw: 残高不足はINSUFFICIENT_FUNDS（MockBukkit）")
    fun withdraw_insufficientFunds_withMock_returnsInsufficient() = runBlocking {
        assumeTrue(mockBukkitAvailable())
        val playerMockClazz = Class.forName("be.seeseemelk.mockbukkit.entity.PlayerMock")
        val serverMockClazz = Class.forName("be.seeseemelk.mockbukkit.ServerMock")
        val addPlayer = serverMockClazz.getMethod("addPlayer", String::class.java)
        val player = addPlayer.invoke(server, "Alice")
        val getUniqueId = playerMockClazz.getMethod("getUniqueId")
        val uuid = getUniqueId.invoke(player) as UUID

        val res = service.withdraw(uuid, 100.toBigDecimal(), "Test", "Withdraw", null)
        Assertions.assertEquals(ResultCode.INSUFFICIENT_FUNDS, res.code)
    }

    @Test
    @DisplayName("transfer: 正常系（送金成功でfrom残高が減る）")
    fun transfer_success_updatesBalances() = runBlocking {
        assumeTrue(mockBukkitAvailable())
        val serverMockClazz = Class.forName("be.seeseemelk.mockbukkit.ServerMock")
        val addPlayer = serverMockClazz.getMethod("addPlayer", String::class.java)
        val from = addPlayer.invoke(server, "FromUser")
        val to = addPlayer.invoke(server, "ToUser")
        val playerMockClazz = Class.forName("be.seeseemelk.mockbukkit.entity.PlayerMock")
        val getUniqueId = playerMockClazz.getMethod("getUniqueId")
        val fromId = getUniqueId.invoke(from) as UUID
        val toId = getUniqueId.invoke(to) as UUID

        val dep = service.deposit(fromId, 1000.toBigDecimal(), "Test", "Deposit", null)
        Assertions.assertEquals(ResultCode.SUCCESS, dep.code)

        val res = service.transfer(fromId, toId, 300.toBigDecimal())
        Assertions.assertEquals(ResultCode.SUCCESS, res.code)

        val fromBal = service.getBalance(fromId)
        val toBal = service.getBalance(toId)
        Assertions.assertEquals(700.toBigDecimal(), fromBal)
        Assertions.assertEquals(300.toBigDecimal(), toBal)
    }
}

package red.man10.man10bank.service

import be.seeseemelk.mockbukkit.MockBukkit
import be.seeseemelk.mockbukkit.ServerMock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import red.man10.man10bank.api.AtmApiClient
import red.man10.man10bank.api.VaultApiClient
import red.man10.man10bank.config.ConfigManager.ApiConfig
import red.man10.man10bank.config.ConfigManager.ApiTimeouts
import red.man10.man10bank.net.HttpClientFactory
import red.man10.man10bank.service.vault.VaultCache
import red.man10.man10bank.service.vault.VaultService
import red.man10.man10bank.service.vault.VaultWriteQueue
import java.io.File
import java.net.ConnectException
import java.util.Collections
import java.util.logging.Logger

/**
 * AtmService の整合性テスト（VaultProvider 11.4）。
 * - fail-closed ゲート: 未接続中は現金も電子マネーも動かさない。
 * - 現金返却は「DB 未コミットが確定できた失敗（4xx）」だけ。結果不明（トランスポート失敗）では
 *   自動返却せず、増殖（timeout-but-success + 返却）を防ぐ。
 */
@DisplayName("AtmService の整合性テスト（MockBukkit）")
class AtmServiceTest {

    private lateinit var server: ServerMock
    private lateinit var plugin: JavaPlugin
    private lateinit var cashItemManager: CashItemManager
    private val clients = mutableListOf<HttpClient>()

    @TempDir
    lateinit var tempDir: File

    private val requestPaths = Collections.synchronizedList(mutableListOf<String>())

    private fun config() = ApiConfig(
        baseUrl = "http://localhost",
        apiKey = null,
        timeouts = ApiTimeouts(requestMs = 2_000, connectMs = 1_000, socketMs = 2_000),
        retries = 0,
    )

    private fun claimJson() = """{"sessionId":"sess-1","balance":1000,"version":1,"maxBalance":1000000000000}"""

    /** claim は成功し、/Vault/deposit だけ挙動を差し替えられる ATM 一式を作る。 */
    private fun buildAtm(depositThrows: Throwable? = null, depositStatus: HttpStatusCode = HttpStatusCode.OK, depositBody: String = """{"balance":1500,"version":2}"""): Pair<AtmService, VaultService> {
        val engine = MockEngine { req ->
            val path = req.url.encodedPath
            requestPaths.add(path)
            when {
                path.endsWith("/session/claim") -> respond(
                    content = claimJson(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                path.endsWith("/Vault/deposit") && depositThrows != null -> throw depositThrows
                path.endsWith("/Vault/deposit") -> respond(
                    content = depositBody,
                    status = depositStatus,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                else -> respond(
                    content = """{"balance":1000,"version":1}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        }
        val client = HttpClientFactory.create(config(), engine)
        clients.add(client)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val queue = VaultWriteQueue(tempDir, Logger.getLogger("AtmServiceTest"))
        val vaultService = VaultService(plugin, "test", scope, VaultApiClient(client), VaultCache(), queue)
        val atmService = AtmService(plugin, scope, AtmApiClient(client), vaultService, cashItemManager)
        return atmService to vaultService
    }

    /** PDC 付きの現金アイテム（1枚 100 円）を作る。 */
    private fun cashBill(amount: Int): ItemStack {
        val bill = ItemStack(Material.PAPER)
        cashItemManager.save(bill, "100")
        return bill.clone().apply { this.amount = amount }
    }

    private fun awaitWithTicks(message: String, timeoutMs: Long = 3_000, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return
            server.scheduler.performOneTick()
            Thread.sleep(10)
        }
        assertTrue(cond(), message)
    }

    @BeforeEach
    fun setup() {
        server = MockBukkit.mock()
        plugin = MockBukkit.createMockPlugin()
        cashItemManager = CashItemManager(plugin)
        requestPaths.clear()
    }

    @AfterEach
    fun teardown() {
        clients.forEach { it.close() }
        clients.clear()
        MockBukkit.unmock()
    }

    @Test
    @DisplayName("入金: 未接続なら現金アイテムを消費せず、サービスへリクエストしない")
    fun depositGateBlocksWhenDisconnected() {
        val (atmService, vaultService) = buildAtm()
        vaultService.setConnected(false)
        val p = server.addPlayer()
        val cash = cashBill(5)

        atmService.depositCashToVault(p, arrayOf(cash))

        assertEquals(5, cash.amount, "未接続では現金を確保(消費)しない")
        assertTrue(requestPaths.isEmpty(), "未接続では入金リクエストを打たない")
    }

    @Test
    @DisplayName("出金: 未接続ならサービスへリクエストしない（現金を付与しない）")
    fun withdrawGateBlocksWhenDisconnected() {
        val (atmService, vaultService) = buildAtm()
        vaultService.setConnected(false)
        val p = server.addPlayer()

        atmService.withdrawVaultToCash(p, 1000.0)

        assertTrue(requestPaths.isEmpty(), "未接続では出金リクエストを打たない")
    }

    @Test
    @DisplayName("入金 4xx 拒否: DB 未コミットが確定しているため現金を返却する")
    fun depositRejectedReturnsCash() {
        val (atmService, vaultService) = buildAtm(
            depositStatus = HttpStatusCode.Conflict,
            depositBody = """{"title":"上限超過","status":409,"code":"MaxBalanceExceeded"}""",
        )
        vaultService.setConnected(true)
        val p = server.addPlayer()
        runBlocking { vaultService.claim(p.uniqueId, p.name) }
        val cash = cashBill(5)

        atmService.depositCashToVault(p, arrayOf(cash))

        assertEquals(0, cash.amount, "先に現金を確保する")
        awaitWithTicks("拒否が確定したため現金がインベントリへ返却される") {
            p.inventory.contents.filterNotNull().sumOf { it.amount } == 5
        }
    }

    @Test
    @DisplayName("入金 結果不明（トランスポート失敗）: 現金を自動返却しない（増殖防止）")
    fun depositUnknownResultDoesNotReturnCash() {
        val (atmService, vaultService) = buildAtm(
            depositThrows = ConnectException("connection refused"),
        )
        vaultService.setConnected(true)
        val p = server.addPlayer()
        runBlocking { vaultService.claim(p.uniqueId, p.name) }
        val cash = cashBill(5)

        atmService.depositCashToVault(p, arrayOf(cash))

        assertEquals(0, cash.amount, "現金は確保されたまま")
        // 少し待っても現金は返却されない（結果不明のため手動復旧に委ねる）
        repeat(20) {
            server.scheduler.performOneTick()
            Thread.sleep(5)
        }
        assertEquals(0, p.inventory.contents.filterNotNull().sumOf { it.amount }, "結果不明では自動返却しない")
    }
}

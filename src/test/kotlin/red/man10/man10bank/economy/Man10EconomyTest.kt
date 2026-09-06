package red.man10.man10bank.economy

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
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import red.man10.man10bank.api.VaultApiClient
import red.man10.man10bank.config.ConfigManager.ApiConfig
import red.man10.man10bank.config.ConfigManager.ApiTimeouts
import red.man10.man10bank.net.HttpClientFactory
import red.man10.man10bank.service.vault.VaultCache
import red.man10.man10bank.service.vault.VaultService
import red.man10.man10bank.service.vault.VaultWriteQueue
import java.io.File
import java.util.logging.Logger

/**
 * Man10Economy の確定設計テスト（MockBukkit）。
 * - isEnabled は Provider 登録状態のみを表し、サービス一時障害では true を維持する（VaultProvider 5.6）。
 * - hasAccount は「同一サーバーでオンライン + 台帳エントリあり」のときだけ true（6.2）。
 * - 文字列指定はオンラインプレイヤーだけを解決し、オフライン UUID を生成しない（6.3）。
 */
@DisplayName("Man10Economy のテスト（MockBukkit）")
class Man10EconomyTest {

    private lateinit var server: ServerMock
    private lateinit var plugin: JavaPlugin
    private lateinit var cache: VaultCache
    private lateinit var service: VaultService
    private lateinit var economy: Man10Economy
    private lateinit var client: HttpClient

    @TempDir
    lateinit var tempDir: File

    private fun config() = ApiConfig(
        baseUrl = "http://localhost",
        apiKey = null,
        timeouts = ApiTimeouts(requestMs = 2_000, connectMs = 1_000, socketMs = 2_000),
        retries = 0,
    )

    // claim は sessionId+残高上限、それ以外は残高 JSON を返すエンジン。
    private fun engine() = MockEngine { req ->
        val body = if (req.url.encodedPath.endsWith("/session/claim")) {
            """{"sessionId":"sess-1","balance":1000,"version":1,"maxBalance":1000000000000}"""
        } else {
            """{"balance":1000,"version":1}"""
        }
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }

    @BeforeEach
    fun setup() {
        server = MockBukkit.mock()
        plugin = MockBukkit.createMockPlugin()
        cache = VaultCache()
        client = HttpClientFactory.create(config(), engine())
        val queue = VaultWriteQueue(tempDir, Logger.getLogger("Man10EconomyTest"))
        service = VaultService(plugin, "test", CoroutineScope(Dispatchers.Unconfined), VaultApiClient(client), cache, queue)
        service.setConnected(true)
        service.setProviderActive(true)
        economy = Man10Economy(plugin, service, "円", "円")
    }

    @AfterEach
    fun teardown() {
        client.close()
        MockBukkit.unmock()
    }

    /** claim 済み（READY・残高上限配布済み）のオンラインプレイヤーを作る。 */
    private fun readyPlayer(balance: Long = 1000L): be.seeseemelk.mockbukkit.entity.PlayerMock {
        val p = server.addPlayer()
        runBlocking { service.claim(p.uniqueId, p.name) }
        // claim 応答は残高 1000 固定のため、必要ならテスト側で上書きする。
        if (balance != 1000L) cache.beginSession(p.uniqueId, "sess-1", balance, 1L)
        return p
    }

    @Test
    @DisplayName("基本メタ情報: name/単一通貨/小数桁0/書式")
    fun meta() {
        assertEquals("Man10Bank", economy.name)
        assertFalse(economy.hasBankSupport())
        assertEquals(0, economy.fractionalDigits())
        assertEquals("1,234円", economy.format(1234.0))
        assertEquals("円", economy.currencyNameSingular())
        assertEquals("円", economy.currencyNamePlural())
    }

    @Test
    @DisplayName("isEnabled: Provider 登録状態だけを表し、切断中も true を維持する")
    fun isEnabledIgnoresConnectionState() {
        assertTrue(economy.isEnabled)
        service.setConnected(false)
        assertTrue(economy.isEnabled, "一時障害では true のまま（外部プラグインの接続破棄を防ぐ）")
        service.setProviderActive(false)
        assertFalse(economy.isEnabled, "登録解除で false")
    }

    @Test
    @DisplayName("切断中: isEnabled=true でも入出金は FAILURE、has は false（取引メソッド側で拒否）")
    fun writesRejectedWhileDisconnected() {
        val p = readyPlayer()
        service.setConnected(false)

        assertTrue(economy.isEnabled)
        assertFalse(economy.withdrawPlayer(p, 100.0).transactionSuccess())
        assertFalse(economy.depositPlayer(p, 100.0).transactionSuccess())
        assertFalse(economy.has(p, 100.0))
        assertEquals(1000.0, economy.getBalance(p), "getBalance は台帳の値を返してよい")
    }

    @Test
    @DisplayName("getBalance: claim 前は 0、claim 後は確定残高を返す")
    fun getBalanceLifecycle() {
        val p = server.addPlayer()
        assertEquals(0.0, economy.getBalance(p), "claim 前は 0")
        runBlocking { service.claim(p.uniqueId, p.name) }
        assertEquals(1000.0, economy.getBalance(p))
    }

    @Test
    @DisplayName("hasAccount: オンライン + 台帳エントリありのときだけ true")
    fun hasAccountSemantics() {
        val p = server.addPlayer()
        assertFalse(economy.hasAccount(p), "エントリが無ければ false")
        runBlocking { service.claim(p.uniqueId, p.name) }
        assertTrue(economy.hasAccount(p))

        cache.evict(p.uniqueId)
        assertFalse(economy.hasAccount(p))
    }

    @Test
    @DisplayName("入出金: 出金は予約で残高が即減り、入金は確定まで残高に現れない")
    fun depositWithdrawSemantics() {
        val p = readyPlayer()

        val wd = economy.withdrawPlayer(p, 300.0)
        assertTrue(wd.transactionSuccess())
        assertEquals(700.0, economy.getBalance(p), "出金は予約で即減る")

        val dep = economy.depositPlayer(p, 500.0)
        assertTrue(dep.transactionSuccess())
        assertEquals(700.0, economy.getBalance(p), "未確定入金は残高へ加えない（確定応答後に反映）")
    }

    @Test
    @DisplayName("文字列指定: オンラインの完全一致だけ解決し、未知の名前は失敗する")
    fun stringOverloadsResolveOnlineOnly() {
        val p = readyPlayer()

        @Suppress("DEPRECATION")
        assertEquals(1000.0, economy.getBalance(p.name))
        @Suppress("DEPRECATION")
        assertTrue(economy.has(p.name, 500.0))
        @Suppress("DEPRECATION")
        assertEquals(0.0, economy.getBalance("unknown_player"), "未知の名前からオフライン UUID を生成しない")
        @Suppress("DEPRECATION")
        assertFalse(economy.withdrawPlayer("unknown_player", 100.0).transactionSuccess())
    }

    @Test
    @DisplayName("bank 系 API はすべて NOT_IMPLEMENTED")
    fun bankApisNotImplemented() {
        assertEquals(
            net.milkbowl.vault.economy.EconomyResponse.ResponseType.NOT_IMPLEMENTED,
            economy.bankBalance("x").type,
        )
        assertTrue(economy.banks.isEmpty())
    }
}

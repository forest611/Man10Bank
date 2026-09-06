package red.man10.man10bank.service.vault

import be.seeseemelk.mockbukkit.MockBukkit
import be.seeseemelk.mockbukkit.ServerMock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import red.man10.man10bank.api.model.request.VaultMoveDirection
import red.man10.man10bank.config.ConfigManager.ApiConfig
import red.man10.man10bank.config.ConfigManager.ApiTimeouts
import red.man10.man10bank.net.HttpClientFactory
import java.io.File
import java.net.ConnectException
import java.util.Collections
import java.util.UUID
import java.util.logging.Logger

/**
 * VaultService の確定設計テスト（MockBukkit + Ktor MockEngine）。
 * - 単一書き込み者: session claim が無い対象への書き込みは増額を含め拒否する（VaultProvider 5.4）。
 * - 予約台帳: Provider/内製の減算は予約してから送り、未確定入金は残高に現れない（5.3 / 不変条件12）。
 * - 送信キュー: 冪等キー付きで後送し、確定応答で台帳を収束させる（6.4）。
 */
@DisplayName("VaultService の確定設計テスト（MockBukkit + Ktor MockEngine）")
class VaultServiceTest {

    private lateinit var server: ServerMock
    private lateinit var plugin: JavaPlugin
    private lateinit var cache: VaultCache
    private lateinit var scope: CoroutineScope

    @TempDir
    lateinit var tempDir: File

    private val clients = mutableListOf<HttpClient>()

    /** すべてのリクエストの path を記録する（fail-closed 時に POST を一切打たないことの検証用）。 */
    private val requestPaths = Collections.synchronizedList(mutableListOf<String>())

    private fun config() = ApiConfig(
        baseUrl = "http://localhost",
        apiKey = null,
        timeouts = ApiTimeouts(requestMs = 2_000, connectMs = 1_000, socketMs = 2_000),
        retries = 0,
    )

    private fun claimJson(sessionId: String = "sess-1", balance: Long = 1000L, version: Long = 1L) =
        """{"sessionId":"$sessionId","balance":$balance,"version":$version,"maxBalance":1000000000000}"""

    private fun balanceJson(balance: Long, version: Long) = """{"balance":$balance,"version":$version}"""

    /** path ごとの応答を差し替えられるサービスを作る。 */
    private fun newService(
        connected: Boolean = true,
        respondFor: (path: String) -> Pair<HttpStatusCode, String> = { HttpStatusCode.OK to balanceJson(0L, 0L) },
    ): VaultService {
        val engine = MockEngine { req ->
            val path = req.url.encodedPath
            requestPaths.add(path)
            val (status, body) = respondFor(path)
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return newService(connected, engine)
    }

    private fun newService(connected: Boolean, engine: MockEngine): VaultService {
        val client = HttpClientFactory.create(config(), engine)
        clients.add(client)
        val queue = VaultWriteQueue(tempDir, Logger.getLogger("VaultServiceTest"))
        val service = VaultService(plugin, "test", scope, VaultApiClient(client), cache, queue)
        service.setConnected(connected)
        return service
    }

    /** claim を実行して READY にする（残高上限もこのとき配布される）。 */
    private fun claimReady(service: VaultService, uuid: UUID, balance: Long = 1000L) {
        runBlocking { service.claim(uuid, "player") }
        assertEquals(VaultCache.Status.READY, cache.statusOf(uuid), "前提: claim 成功で READY")
        assertEquals(balance, cache.snapshot(uuid)?.confirmedBalance)
    }

    private fun awaitTrue(message: String, timeoutMs: Long = 3_000, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return
            Thread.sleep(20)
        }
        assertTrue(cond(), message)
    }

    @BeforeEach
    fun setup() {
        server = MockBukkit.mock()
        plugin = MockBukkit.createMockPlugin()
        cache = VaultCache()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        requestPaths.clear()
    }

    @AfterEach
    fun teardown() {
        scope.cancel()
        clients.forEach { it.close() }
        clients.clear()
        MockBukkit.unmock()
    }

    @Test
    @DisplayName("fail-closed: 未接続なら Provider 入出金は即 FAILURE・台帳不変・POST を打たない")
    fun providerFailClosedWhenDisconnected() {
        val p = server.addPlayer()
        cache.beginSession(p.uniqueId, "sess-1", 1000L, 1L)
        val service = newService(connected = false)

        assertFalse(service.providerDeposit(p.uniqueId, 100.0).transactionSuccess())
        assertFalse(service.providerWithdraw(p.uniqueId, 100.0).transactionSuccess())
        assertEquals(1000L, cache.snapshot(p.uniqueId)?.availableBalance)
        assertTrue(requestPaths.isEmpty(), "未接続では一切リクエストしない")
    }

    @Test
    @DisplayName("fail-closed: 接続済みでも残高上限（claim）未取得なら Provider 書き込みは拒否する")
    fun providerFailClosedWithoutMaxBalance() {
        val p = server.addPlayer()
        cache.beginSession(p.uniqueId, "sess-1", 1000L, 1L)
        val service = newService(connected = true) // claim していないため maxBalance 未取得

        assertEquals(VaultService.WriteHealth.DOWN, service.writeHealth())
        assertFalse(service.providerWithdraw(p.uniqueId, 100.0).transactionSuccess())
    }

    @Test
    @DisplayName("claim: READY 化し確定残高と sessionId を保持する。isReady が true になる")
    fun claimMakesReady() {
        val p = server.addPlayer()
        val service = newService { path ->
            if (path.endsWith("/session/claim")) HttpStatusCode.OK to claimJson(balance = 2500L, version = 4L)
            else HttpStatusCode.OK to balanceJson(0L, 0L)
        }
        runBlocking { service.claim(p.uniqueId, p.name) }

        assertTrue(service.isReady())
        val snap = cache.snapshot(p.uniqueId)!!
        assertEquals(VaultCache.Status.READY, snap.status)
        assertEquals("sess-1", snap.sessionId)
        assertEquals(2500L, snap.confirmedBalance)
        assertEquals(4L, snap.confirmedVersion)
    }

    @Test
    @DisplayName("単一書き込み者: claim の無い対象への内製操作は増額（deposit）も拒否し、POST を打たない")
    fun residentOnlyEvenForDeposit() {
        val p = server.addPlayer()
        val service = newService { path ->
            if (path.endsWith("/session/claim")) HttpStatusCode.OK to claimJson()
            else HttpStatusCode.OK to balanceJson(0L, 0L)
        }
        claimReady(service, p.uniqueId)
        requestPaths.clear()

        val offlineUuid = UUID.fromString("00000000-0000-0000-0000-00000000dead")
        val results = runBlocking {
            listOf(
                service.depositConfirmed(offlineUuid, 100.0, "t", "t"),
                service.withdrawConfirmed(offlineUuid, 100.0, "t", "t"),
                service.move(offlineUuid, 100.0, VaultMoveDirection.BankToVault, "t", "t"),
                service.adminOperate(offlineUuid, VaultAdminOp.SET, 100.0, "t", "t"),
            )
        }
        results.forEach { assertTrue(it.isFailure, "非在席対象への操作は増額を含め全て失敗する") }
        assertTrue(requestPaths.isEmpty(), "非在席対象にはリクエスト自体を打たない")
    }

    @Test
    @DisplayName("Provider 入金: SUCCESS でも未確定入金は残高へ現れず、確定応答後にだけ反映される")
    fun providerDepositNotVisibleUntilConfirmed() {
        val p = server.addPlayer()
        val service = newService { path ->
            when {
                path.endsWith("/session/claim") -> HttpStatusCode.OK to claimJson(balance = 1000L, version = 1L)
                path.endsWith("/deposit") -> HttpStatusCode.OK to balanceJson(1100L, 2L)
                else -> HttpStatusCode.OK to balanceJson(1000L, 1L)
            }
        }
        claimReady(service, p.uniqueId)

        val res = service.providerDeposit(p.uniqueId, 100.0)
        assertTrue(res.transactionSuccess())
        assertEquals(1000L, cache.snapshot(p.uniqueId)?.availableBalance, "未確定入金は available に含めない")

        service.start(resyncIntervalSeconds = 3600L)
        awaitTrue("確定応答後に残高が反映される") {
            cache.snapshot(p.uniqueId)?.confirmedBalance == 1100L
        }
    }

    @Test
    @DisplayName("Provider 出金: 予約で available が即減り、二重引き落としをローカルで防ぐ")
    fun providerWithdrawReserves() {
        val p = server.addPlayer()
        val service = newService { path ->
            if (path.endsWith("/session/claim")) HttpStatusCode.OK to claimJson(balance = 1000L)
            else HttpStatusCode.OK to balanceJson(0L, 0L)
        }
        claimReady(service, p.uniqueId)

        assertTrue(service.providerWithdraw(p.uniqueId, 700.0).transactionSuccess())
        assertEquals(300L, cache.snapshot(p.uniqueId)?.availableBalance)
        assertFalse(service.providerWithdraw(p.uniqueId, 400.0).transactionSuccess(), "予約後の available を超える出金は拒否")
        assertFalse(service.hasSync(p.uniqueId, 400.0))
        assertTrue(service.hasSync(p.uniqueId, 300.0))
    }

    @Test
    @DisplayName("金額正規化: NaN / Infinity / 1円未満は Provider 経路で拒否する")
    fun normalizeRejectsInvalidAmounts() {
        val p = server.addPlayer()
        val service = newService { path ->
            if (path.endsWith("/session/claim")) HttpStatusCode.OK to claimJson()
            else HttpStatusCode.OK to balanceJson(0L, 0L)
        }
        claimReady(service, p.uniqueId)
        requestPaths.clear()

        assertFalse(service.providerDeposit(p.uniqueId, Double.NaN).transactionSuccess())
        assertFalse(service.providerDeposit(p.uniqueId, Double.POSITIVE_INFINITY).transactionSuccess())
        assertFalse(service.providerDeposit(p.uniqueId, 0.5).transactionSuccess())
        assertFalse(service.providerWithdraw(p.uniqueId, -100.0).transactionSuccess())
        assertEquals(1000L, cache.snapshot(p.uniqueId)?.availableBalance, "台帳は不変")
        assertTrue(requestPaths.isEmpty())
    }

    @Test
    @DisplayName("送信キュー: 冪等キー+sessionId 付きで後送され、withdraw の確定で予約が消し込まれる")
    fun queueSendsWithOperationIdAndConfirms() {
        val p = server.addPlayer()
        val sentBodies = Collections.synchronizedList(mutableListOf<String>())
        val engine = MockEngine { req ->
            val path = req.url.encodedPath
            requestPaths.add(path)
            val body = when {
                path.endsWith("/session/claim") -> claimJson(balance = 1000L, version = 1L)
                path.endsWith("/withdraw") -> {
                    sentBodies.add(String(req.body.toByteArray()))
                    balanceJson(300L, 2L)
                }
                else -> balanceJson(1000L, 1L)
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val service = newService(connected = true, engine = engine)
        claimReady(service, p.uniqueId)

        assertTrue(service.providerWithdraw(p.uniqueId, 700.0).transactionSuccess())
        service.start(resyncIntervalSeconds = 3600L)

        awaitTrue("確定応答で予約が消え confirmed が反映される") {
            val snap = cache.snapshot(p.uniqueId)
            snap?.confirmedBalance == 300L && snap.pendingTotal == 0L
        }
        val sent = sentBodies.single()
        assertTrue(sent.contains("\"operationId\""), "冪等キーを送る: $sent")
        assertTrue(sent.contains("\"sessionId\":\"sess-1\""), "claim の sessionId を送る: $sent")
        assertTrue(sent.contains("\"source\":\"PROVIDER\""), "source=PROVIDER を送る: $sent")
    }

    @Test
    @DisplayName("送信キュー: 4xx 業務失敗は予約を取り消し、対象を CONFLICT にして隔離する")
    fun queueBusinessFailureQuarantines() {
        val p = server.addPlayer()
        val service = newService { path ->
            when {
                path.endsWith("/session/claim") -> HttpStatusCode.OK to claimJson(balance = 1000L)
                path.endsWith("/withdraw") -> HttpStatusCode.Conflict to """{"title":"残高不足","status":409}"""
                else -> HttpStatusCode.OK to balanceJson(1000L, 1L)
            }
        }
        claimReady(service, p.uniqueId)

        assertTrue(service.providerWithdraw(p.uniqueId, 700.0).transactionSuccess())
        service.start(resyncIntervalSeconds = 3600L)

        awaitTrue("CONFLICT になり予約が取り消される") {
            cache.statusOf(p.uniqueId) == VaultCache.Status.CONFLICT &&
                cache.snapshot(p.uniqueId)?.pendingTotal == 0L
        }
        awaitTrue("failed/ へ隔離される") {
            File(tempDir, "vault-queue/failed").listFiles()?.isNotEmpty() == true
        }
        assertFalse(service.providerWithdraw(p.uniqueId, 10.0).transactionSuccess(), "CONFLICT 中は新規書き込み拒否")
    }

    @Test
    @DisplayName("切断: setConnected(false) で未送信キューがディスクへ退避される")
    fun disconnectPersistsUnsentOps() {
        val p = server.addPlayer()
        val service = newService { path ->
            if (path.endsWith("/session/claim")) HttpStatusCode.OK to claimJson(balance = 1000L)
            else HttpStatusCode.OK to balanceJson(0L, 0L)
        }
        claimReady(service, p.uniqueId)

        assertTrue(service.providerWithdraw(p.uniqueId, 700.0).transactionSuccess())
        service.setConnected(false)

        val pending = File(tempDir, "vault-queue/pending").listFiles()
        assertTrue(pending != null && pending.isNotEmpty(), "未送信操作が退避される")
    }

    @Test
    @DisplayName("withdrawConfirmed: 成功で予約消し込み、409 拒否で予約が戻る")
    fun withdrawConfirmedReserveLifecycle() {
        val p = server.addPlayer()
        val okService = newService { path ->
            when {
                path.endsWith("/session/claim") -> HttpStatusCode.OK to claimJson(balance = 1000L, version = 1L)
                path.endsWith("/withdraw") -> HttpStatusCode.OK to balanceJson(400L, 2L)
                else -> HttpStatusCode.OK to balanceJson(1000L, 1L)
            }
        }
        claimReady(okService, p.uniqueId)
        val ok = runBlocking { okService.withdrawConfirmed(p.uniqueId, 600.0, "t", "t") }
        assertTrue(ok.isSuccess)
        val snap = cache.snapshot(p.uniqueId)!!
        assertEquals(400L, snap.confirmedBalance)
        assertEquals(0L, snap.pendingTotal)

        // 409 拒否: 予約が取り消され available が戻る
        val ngService = newService { path ->
            when {
                path.endsWith("/withdraw") -> HttpStatusCode.Conflict to """{"title":"残高不足","status":409}"""
                path.endsWith("/session/claim") -> HttpStatusCode.OK to claimJson()
                else -> HttpStatusCode.OK to balanceJson(400L, 2L)
            }
        }
        ngService.setConnected(true)
        runBlocking { ngService.claim(p.uniqueId, p.name) } // maxBalance を配布(キャッシュは claim 応答で上書きされる)
        val before = cache.snapshot(p.uniqueId)!!.availableBalance
        val ng = runBlocking { ngService.withdrawConfirmed(p.uniqueId, before.toDouble(), "t", "t") }
        assertTrue(ng.isFailure)
        assertEquals(before, cache.snapshot(p.uniqueId)!!.availableBalance, "拒否で予約が戻る")
    }

    @Test
    @DisplayName("transfer: 送金先が非在席なら失敗し POST を打たない。成功時は両台帳を収束させる")
    fun transferRequiresBothResidents() {
        val from = server.addPlayer()
        val to = server.addPlayer()
        val service = newService { path ->
            when {
                path.endsWith("/session/claim") -> HttpStatusCode.OK to claimJson(balance = 1000L, version = 1L)
                path.endsWith("/transfer") -> HttpStatusCode.OK to
                    """{"fromBalance":700,"fromVersion":2,"toBalance":1300,"toVersion":2}"""
                else -> HttpStatusCode.OK to balanceJson(1000L, 1L)
            }
        }
        claimReady(service, from.uniqueId)
        requestPaths.clear()

        // 送金先が未 claim → 拒否
        val ng = runBlocking { service.transfer(from.uniqueId, to.uniqueId, 300.0, "t", "t") }
        assertTrue(ng.isFailure)
        assertTrue(requestPaths.isEmpty())

        claimReady(service, to.uniqueId)
        val ok = runBlocking { service.transfer(from.uniqueId, to.uniqueId, 300.0, "t", "t") }
        assertTrue(ok.isSuccess)
        assertEquals(700L, cache.snapshot(from.uniqueId)?.confirmedBalance)
        assertEquals(1300L, cache.snapshot(to.uniqueId)?.confirmedBalance, "受取側は応答で収束（push 不要）")
    }

    @Test
    @DisplayName("トランスポート障害: 確定経路の失敗で即 fail-closed になり再接続を要求する")
    fun transportFailureTriggersFailClosed() {
        val p = server.addPlayer()
        var reconnectRequested = false
        // claim だけ成功し、以後の書き込みは接続レベルで失敗するエンジン。
        val engine = MockEngine { req ->
            if (req.url.encodedPath.endsWith("/session/claim")) {
                respond(
                    content = claimJson(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                throw ConnectException("connection refused")
            }
        }
        val service = newService(connected = true, engine = engine)
        service.setReconnectRequester { reconnectRequested = true }
        claimReady(service, p.uniqueId)
        assertTrue(service.isReady())

        val res = runBlocking { service.depositConfirmed(p.uniqueId, 100.0, "t", "t") }
        assertTrue(res.isFailure)
        assertTrue(reconnectRequested, "接続レベル障害で WS 再接続を要求する")
        assertFalse(service.isReady(), "即 fail-closed になる")
    }
}

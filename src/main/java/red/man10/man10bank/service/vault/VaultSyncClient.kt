package red.man10.man10bank.service.vault

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import red.man10.man10bank.Man10Bank
import java.net.URLEncoder
import java.util.UUID

/**
 * サービスへの Vault session/presence WebSocket チャネル（VaultProvider 7.4）。
 *
 * - 起動時に 1 本張る。残高 push は流れず、受信するのは ping と session 失効通知のみ。
 * - サービス側は session の寿命をこの接続に紐づける。接続が確立している間だけ claim が有効で、
 *   切断するとサービス側で当サーバーの全 session が失効する（fail-closed）。
 * - 接続成立時に在席プレイヤー全員を claim し直す（再接続時の全件再同期を兼ねる）。
 * - 切断時は指数バックオフで再接続し、[VaultService.setConnected] を経由して書き込みを止める。
 *
 * 認証は共有 [HttpClient] の DefaultRequest が付与する Bearer を upgrade リクエストに流用する。
 */
class VaultSyncClient(
    private val plugin: Man10Bank,
    private val scope: CoroutineScope,
    private val client: HttpClient,
    private val service: VaultService,
    private val baseUrl: String,
    private val serverName: String,
) {
    @Volatile
    private var session: WebSocketSession? = null
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            var backoffMs = 1_000L
            while (isActive) {
                try {
                    connectAndRun()
                    backoffMs = 1_000L // 正常にセッションを終えたらバックオフをリセット
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    plugin.logger.warning("Vault同期WebSocketが切断されました: ${e.message}")
                }
                service.setConnected(false)
                session = null
                if (!isActive) break
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    suspend fun stop() {
        job?.cancelAndJoin()
        job = null
    }

    /**
     * 接続レベル障害を検知した側（[VaultService] の REST 失敗等）からの要求で、
     * 現在のセッションを閉じて再接続ループを促す。セッションを閉じると受信ループが終了し、
     * `setConnected(false)` → バックオフ後に再接続を試みる。サービスが生きていれば
     * 再接続と再 claim で即復帰するため固着しない。未接続中（session==null）は何もしない。
     */
    fun requestReconnect() {
        val active = session ?: return
        scope.launch { runCatching { active.close() } }
    }

    private suspend fun connectAndRun() {
        client.webSocket(request = { url(wsUrl()) }) {
            session = this
            service.setConnected(true)
            plugin.logger.info("Vault同期WebSocketに接続しました")

            // 在席プレイヤー全員を claim し直す（初回接続・再接続とも全件再同期を兼ねる。VaultProvider 7.4）。
            claimOnlinePlayers()

            for (frame in incoming) {
                if (frame is Frame.Text) handleFrame(frame.readText())
            }
        }
    }

    private suspend fun claimOnlinePlayers() {
        val online = onlinePlayersOnMain()
        for ((uuid, name) in online) {
            service.claim(uuid, name)
        }
        if (online.isNotEmpty()) {
            plugin.logger.info("Vault: 在席 ${online.size} 名の session を claim し、残高を再同期しました")
        }
    }

    private fun handleFrame(text: String) {
        val event = VaultSyncProtocol.decode(text) ?: return
        when (event.type?.lowercase()) {
            "ping" -> session?.let { s -> scope.launch { runCatching { s.send(Frame.Text(VaultSyncProtocol.pong())) } } }
            "session_revoked" -> handleSessionRevoked(event)
        }
    }

    private fun handleSessionRevoked(event: VaultSyncProtocol.ServerEvent) {
        val uuid = runCatching { UUID.fromString(event.uuid ?: return) }.getOrNull() ?: return
        service.onSessionRevoked(uuid, event.sessionId)
    }

    private suspend fun onlinePlayersOnMain(): List<Pair<UUID, String>> {
        if (plugin.server.isPrimaryThread) {
            return plugin.server.onlinePlayers.map { it.uniqueId to it.name }
        }
        val deferred = CompletableDeferred<List<Pair<UUID, String>>>()
        plugin.server.scheduler.runTask(plugin, Runnable {
            deferred.complete(plugin.server.onlinePlayers.map { it.uniqueId to it.name })
        })
        return deferred.await()
    }

    private fun wsUrl(): String {
        val base = baseUrl.trim().trimEnd('/')
        val wsBase = when {
            base.startsWith("https://", ignoreCase = true) -> "wss://" + base.substring("https://".length)
            base.startsWith("http://", ignoreCase = true) -> "ws://" + base.substring("http://".length)
            else -> base
        }
        val encoded = URLEncoder.encode(serverName, Charsets.UTF_8.name())
        return "$wsBase/api/Vault/ws?server=$encoded"
    }
}

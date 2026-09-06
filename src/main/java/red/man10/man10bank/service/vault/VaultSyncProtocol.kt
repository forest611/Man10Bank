package red.man10.man10bank.service.vault

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Vault 同期 WebSocket のメッセージ表現とエンコード/デコード（VaultProvider 7.4）。
 * 残高 push は流れない。サーバー → クライアントは ping と session 失効通知のみ。
 * ソケット I/O から切り離して単体テスト可能にする。
 */
object VaultSyncProtocol {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /** サーバー → クライアントの受信イベント。type により session_revoked / ping 等を区別する。 */
    @Serializable
    data class ServerEvent(
        val type: String? = null,
        val uuid: String? = null,
        val sessionId: String? = null,
        val newServer: String? = null,
    )

    /** 受信テキストを [ServerEvent] へデコードする（失敗時は null）。 */
    fun decode(text: String): ServerEvent? =
        runCatching { json.decodeFromString(ServerEvent.serializer(), text) }.getOrNull()

    /** ハートビート応答。 */
    fun pong(): String = "{\"type\":\"pong\"}"
}

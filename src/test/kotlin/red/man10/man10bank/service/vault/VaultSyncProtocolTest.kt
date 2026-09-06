package red.man10.man10bank.service.vault

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Vault 同期 WebSocket プロトコルのテスト（VaultProvider 7.4）。
 * 残高 push は存在しない。受信は ping / session_revoked のみ。
 */
@DisplayName("VaultSyncProtocol のテスト")
class VaultSyncProtocolTest {

    @Test
    @DisplayName("session_revoked イベントをデコードできる")
    fun decodeSessionRevoked() {
        val text = """{"type":"session_revoked","uuid":"00000000-0000-0000-0000-000000000001","sessionId":"sess-1","newServer":"lobby2"}"""
        val event = VaultSyncProtocol.decode(text)
        assertEquals("session_revoked", event?.type)
        assertEquals("00000000-0000-0000-0000-000000000001", event?.uuid)
        assertEquals("sess-1", event?.sessionId)
        assertEquals("lobby2", event?.newServer)
    }

    @Test
    @DisplayName("ping をデコードでき、pong の送出 JSON が正しい")
    fun pingPong() {
        assertEquals("ping", VaultSyncProtocol.decode("""{"type":"ping"}""")?.type)
        assertEquals("""{"type":"pong"}""", VaultSyncProtocol.pong())
    }

    @Test
    @DisplayName("未知フィールドは無視し、壊れた JSON は null を返す")
    fun decodeLenient() {
        val event = VaultSyncProtocol.decode("""{"type":"session_revoked","unknown":123}""")
        assertEquals("session_revoked", event?.type)
        assertNull(VaultSyncProtocol.decode("{not-json"))
    }
}

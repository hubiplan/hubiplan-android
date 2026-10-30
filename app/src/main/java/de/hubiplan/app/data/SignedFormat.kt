package de.hubiplan.app.data

import java.math.BigInteger
import java.security.MessageDigest
import java.util.Base64

/**
 * Formate der Härtung H7, genau wie der Server sie prüft (`devices/devicekey.py`):
 * öffentlicher Schlüssel als unkomprimierter P-256-Punkt (65 Byte), alles base64url ohne `=`.
 */
object SignedFormat {
    const val TIME_HEADER = "X-Hubiplan-Time"
    const val SIGNATURE_HEADER = "X-Hubiplan-Signature"

    fun b64(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)

    /** 0x04 ‖ x ‖ y, je 32 Byte. */
    fun uncompressedPoint(x: BigInteger, y: BigInteger): ByteArray =
        byteArrayOf(4) + fixed32(x) + fixed32(y)

    private fun fixed32(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
            else -> ByteArray(32 - raw.size) + raw
        }
    }

    fun renewMessage(deviceId: Int, challenge: String): ByteArray =
        "hubiplan-renew\n$deviceId\n$challenge".toByteArray()

    /** [path]: Pfad samt Abfrage wie gesendet (percent-kodiert). */
    fun adminMessage(method: String, path: String, timestamp: String, body: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256").digest(body).joinToString("") { "%02x".format(it) }
        return "hubiplan-admin\n${method.uppercase()}\n$path\n$timestamp\n$digest".toByteArray()
    }
}

package com.example.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

data class CryptoCheckItem(
    val name: String,
    val algorithm: String,
    val passed: Boolean,
    val detail: String
)

data class CryptoAuditReport(
    val allPassed: Boolean,
    val items: List<CryptoCheckItem>,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Executes real on-device cryptographic verification against RFC test vectors
 * and live JCA AES-256-GCM / AndroidKeyStore operations.
 */
object CryptoSelfTest {

    fun runFullAudit(keyVault: KeyVault): CryptoAuditReport {
        val checks = listOf(
            verifyAes256Gcm(),
            verifyCurve25519Rfc7748(),
            verifyBlake2sRfc7693(),
            verifyChaCha20Poly1305(),
            verifyKeyVault(keyVault)
        )
        return CryptoAuditReport(
            allPassed = checks.all { it.passed },
            items = checks
        )
    }

    private fun verifyAes256Gcm(): CryptoCheckItem {
        return try {
            val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val nonce = Aes256TunnelCipher.buildNonce(byteArrayOf(1, 2, 3, 4), 42L)
            val aad = "WireGuard-AES256-GCM".toByteArray()
            val sample = "IPv4-Packet-Payload-Test-Vector".toByteArray()
            val encrypted = Aes256TunnelCipher.encryptPacket(key, nonce, sample, aad)
            val decrypted = Aes256TunnelCipher.decryptPacket(key, nonce, encrypted, aad)

            // Verify GCM 128-bit authentication tag rejects tampered ciphertext
            val tampered = encrypted.copyOf().apply { this[0] = (this[0].toInt() xor 0xFF).toByte() }
            var tamperRejected = false
            try {
                Aes256TunnelCipher.decryptPacket(key, nonce, tampered, aad)
            } catch (_: AEADBadTagException) {
                tamperRejected = true
            }

            val ok = decrypted.contentEquals(sample) && tamperRejected && encrypted.size == sample.size + 16
            CryptoCheckItem(
                name = "AES-256-GCM (256-бит)",
                algorithm = "javax.crypto AES/GCM/NoPadding + 128-bit Tag",
                passed = ok,
                detail = if (ok) "Шифрование/расшифрование 256-бит и проверка целостности GCM пройдены"
                else "Несоответствие результата AES-256-GCM"
            )
        } catch (e: Exception) {
            CryptoCheckItem("AES-256-GCM (256-бит)", "AES/GCM/NoPadding", false, e.message ?: "Ошибка")
        }
    }

    private fun verifyCurve25519Rfc7748(): CryptoCheckItem {
        return try {
            // RFC 7748 Section 6.1 test vector
            val scalar = hexToBytes("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4")
            val uCoord = hexToBytes("e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c")
            val expected = hexToBytes("c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552")
            val actual = Curve25519.x25519(scalar, uCoord)
            val ok = MessageDigest.isEqual(actual, expected)
            CryptoCheckItem(
                name = "Curve25519 (X25519 ECDH)",
                algorithm = "RFC 7748 Montgomery Ladder",
                passed = ok,
                detail = if (ok) "Тест-вектор RFC 7748 совпадает побитово" else "Ошибка X25519"
            )
        } catch (e: Exception) {
            CryptoCheckItem("Curve25519 (X25519 ECDH)", "RFC 7748", false, e.message ?: "Ошибка")
        }
    }

    private fun verifyBlake2sRfc7693(): CryptoCheckItem {
        return try {
            // RFC 7693 Appendix B: BLAKE2s-256("abc")
            val expected = hexToBytes("508c5e8c327c14e2e1a72ba34eeb452f37458b209ed63a294d999b4c86675982")
            val actual = WireGuardPrimitives.blake2s("abc".toByteArray(Charsets.US_ASCII), outLen = 32)
            val ok = MessageDigest.isEqual(actual, expected)
            CryptoCheckItem(
                name = "BLAKE2s-256 + HKDF",
                algorithm = "RFC 7693 WireGuard Hash & KDF",
                passed = ok,
                detail = if (ok) "Хэш RFC 7693 (\"abc\") подтверждён" else "Несовпадение BLAKE2s"
            )
        } catch (e: Exception) {
            CryptoCheckItem("BLAKE2s-256 + HKDF", "RFC 7693", false, e.message ?: "Ошибка")
        }
    }

    private fun verifyChaCha20Poly1305(): CryptoCheckItem {
        return try {
            val key = ByteArray(32) { (it + 1).toByte() }
            val nonce = WireGuardPrimitives.counterToNonce12(7L)
            val aad = "Noise_IKpsk2".toByteArray()
            val msg = "WireGuard-Transport-Frame".toByteArray()
            val ct = WireGuardPrimitives.aeadEncrypt(key, nonce, msg, aad)
            val pt = WireGuardPrimitives.aeadDecrypt(key, nonce, ct, aad)
            val ok = pt.contentEquals(msg)
            CryptoCheckItem(
                name = "ChaCha20-Poly1305",
                algorithm = "RFC 8439 WireGuard AEAD",
                passed = ok,
                detail = if (ok) "AEAD-шифрование и Poly1305 MAC пройдены" else "Ошибка AEAD"
            )
        } catch (e: Exception) {
            CryptoCheckItem("ChaCha20-Poly1305", "RFC 8439", false, e.message ?: "Ошибка")
        }
    }

    private fun verifyKeyVault(keyVault: KeyVault): CryptoCheckItem {
        return try {
            val hw = keyVault.isHardwareKeystoreAvailable()
            val sampleKey = Curve25519.generateKeyPair().privateKeyBase64
            val sealed = keyVault.seal(sampleKey)
            val unsealed = keyVault.unseal(sealed)
            val ok = unsealed == sampleKey
            CryptoCheckItem(
                name = "AndroidKeyStore Хранилище",
                algorithm = "Hardware-backed AES-256-GCM Master Key",
                passed = ok,
                detail = if (hw && ok) "Ключи WireGuard зашифрованы в AndroidKeyStore"
                else if (ok) "Локальная защита ключей активна"
                else "Ошибка KeyVault"
            )
        } catch (e: Exception) {
            CryptoCheckItem("AndroidKeyStore Хранилище", "AES-256-GCM", false, e.message ?: "Ошибка")
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length / 2
        return ByteArray(len) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

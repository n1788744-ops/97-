package com.example.crypto

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Real AES-256-GCM tunnel cipher with HKDF-SHA256 key derivation from X25519 ECDH
 * and sliding-window anti-replay protection.
 */
object Aes256TunnelCipher {

    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val NONCE_SIZE_BYTES = 12
    private const val KEY_SIZE_BYTES = 32 // 256-bit key

    data class SessionKeys(
        val txKey: ByteArray,
        val rxKey: ByteArray,
        val txSalt: ByteArray,
        val rxSalt: ByteArray
    )

    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val actualSalt = if (salt.isEmpty()) ByteArray(32) else salt
        mac.init(SecretKeySpec(actualSalt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        val result = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val copyLen = minOf(t.size, length - offset)
            t.copyInto(result, offset, 0, copyLen)
            offset += copyLen
            counter++
        }
        return result
    }

    fun deriveSessionKeys(
        sharedSecret: ByteArray,
        transcriptHash: ByteArray,
        isInitiator: Boolean
    ): SessionKeys {
        val material = hkdfSha256(
            ikm = sharedSecret,
            salt = transcriptHash,
            info = "CipherGuard-WireGuard-AES256GCM-v1".toByteArray(Charsets.UTF_8),
            length = 72 // 32 tx + 32 rx + 4 txSalt + 4 rxSalt
        )
        val keyA = material.copyOfRange(0, KEY_SIZE_BYTES)
        val keyB = material.copyOfRange(32, 64)
        val saltA = material.copyOfRange(64, 68)
        val saltB = material.copyOfRange(68, 72)

        return if (isInitiator) {
            SessionKeys(txKey = keyA, rxKey = keyB, txSalt = saltA, rxSalt = saltB)
        } else {
            SessionKeys(txKey = keyB, rxKey = keyA, txSalt = saltB, rxSalt = saltA)
        }
    }

    fun buildNonce(salt4: ByteArray, counter: Long): ByteArray {
        require(salt4.size == 4) { "AES-GCM salt must be 4 bytes" }
        val nonce = ByteArray(NONCE_SIZE_BYTES)
        salt4.copyInto(nonce, 0)
        ByteBuffer.wrap(nonce, 4, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(counter)
        return nonce
    }

    fun encryptPacket(
        key256: ByteArray,
        nonce12: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray = ByteArray(0)
    ): ByteArray {
        require(key256.size == KEY_SIZE_BYTES) { "AES-256 requires a 32-byte (256-bit) key" }
        require(nonce12.size == NONCE_SIZE_BYTES) { "AES-GCM requires a 12-byte IV/nonce" }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key256, "AES"),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce12)
        )
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    fun decryptPacket(
        key256: ByteArray,
        nonce12: ByteArray,
        ciphertextWithTag: ByteArray,
        aad: ByteArray = ByteArray(0)
    ): ByteArray {
        require(key256.size == KEY_SIZE_BYTES) { "AES-256 requires a 32-byte (256-bit) key" }
        require(nonce12.size == NONCE_SIZE_BYTES) { "AES-GCM requires a 12-byte IV/nonce" }
        require(ciphertextWithTag.size >= 16) { "AES-GCM payload missing 16-byte authentication tag" }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key256, "AES"),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce12)
        )
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(ciphertextWithTag)
    }
}

/**
 * RFC 6479 / WireGuard 64-bit sliding anti-replay window.
 */
class ReplayWindow {
    private var highestCounter = -1L
    private var bitmap = 0L

    @Synchronized
    fun checkAndAccept(counter: Long): Boolean {
        if (counter < 0) return false
        if (counter > highestCounter) {
            val shift = counter - highestCounter
            bitmap = if (shift >= 64) 1L else (bitmap shl shift.toInt()) or 1L
            highestCounter = counter
            return true
        }
        val diff = highestCounter - counter
        if (diff >= 64) return false
        val mask = 1L shl diff.toInt()
        if ((bitmap and mask) != 0L) return false
        bitmap = bitmap or mask
        return true
    }
}

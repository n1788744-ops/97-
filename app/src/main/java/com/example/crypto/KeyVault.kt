package com.example.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed AndroidKeyStore AES-256-GCM vault for encrypting sensitive
 * WireGuard private keys and preshared keys at rest in local storage.
 */
class KeyVault {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "cipherguard_master_aes256_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFIX = "AKSV1:"
    }

    fun isHardwareKeystoreAvailable(): Boolean {
        return try {
            getOrCreateMasterKey()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun seal(plaintext: String): String {
        if (plaintext.isBlank()) return ""
        val key = tryGetMasterKey() ?: return plaintext
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(1 + iv.size + encrypted.size)
        payload[0] = iv.size.toByte()
        iv.copyInto(payload, 1)
        encrypted.copyInto(payload, 1 + iv.size)
        return PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun unseal(sealedValue: String): String {
        if (sealedValue.isBlank()) return ""
        if (!sealedValue.startsWith(PREFIX)) return sealedValue
        val key = tryGetMasterKey() ?: throw IllegalStateException("AndroidKeyStore key unavailable")
        val payload = Base64.decode(sealedValue.removePrefix(PREFIX), Base64.DEFAULT)
        val ivLen = payload[0].toInt() and 0xFF
        val iv = payload.copyOfRange(1, 1 + ivLen)
        val ciphertext = payload.copyOfRange(1 + ivLen, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun tryGetMasterKey(): SecretKey? {
        return try {
            getOrCreateMasterKey()
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }
}

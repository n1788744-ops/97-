package com.example.crypto

import android.util.Base64
import java.math.BigInteger
import java.security.SecureRandom

/**
 * RFC 7748 Curve25519 (X25519) key generation and Diffie-Hellman key agreement
 * used by WireGuard Noise_IKpsk2 and AES-256-GCM ECDH session establishment.
 */
object Curve25519 {

    private val P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val P_MINUS_2 = P.subtract(BigInteger.valueOf(2))
    private val A24 = BigInteger.valueOf(121665)
    private val BASE_POINT_U = ByteArray(32).apply { this[0] = 9 }

    data class KeyPair(
        val privateKey: ByteArray,
        val publicKey: ByteArray
    ) {
        val privateKeyBase64: String get() = encodeBase64(privateKey)
        val publicKeyBase64: String get() = encodeBase64(publicKey)
    }

    fun generateKeyPair(): KeyPair {
        val priv = generatePrivateKey()
        val pub = publicKeyFromPrivate(priv)
        return KeyPair(priv, pub)
    }

    fun generatePrivateKey(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        clampScalar(key)
        return key
    }

    fun generatePresharedKey(): ByteArray {
        val psk = ByteArray(32)
        SecureRandom().nextBytes(psk)
        return psk
    }

    fun publicKeyFromPrivate(privateKey: ByteArray): ByteArray {
        require(privateKey.size == 32) { "X25519 private key must be 32 bytes" }
        return x25519(privateKey, BASE_POINT_U)
    }

    fun sharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        require(privateKey.size == 32 && peerPublicKey.size == 32) {
            "X25519 keys must be 32 bytes"
        }
        val secret = x25519(privateKey, peerPublicKey)
        // Reject all-zero low-order shared secret per RFC 7748 Section 6.1
        var orAll = 0
        for (b in secret) orAll = orAll or (b.toInt() and 0xFF)
        check(orAll != 0) { "Invalid Curve25519 peer public key (all-zero shared secret)" }
        return secret
    }

    /**
     * Exact RFC 7748 Section 5 X25519 Montgomery Ladder.
     */
    fun x25519(k: ByteArray, u: ByteArray): ByteArray {
        require(k.size == 32 && u.size == 32) { "Inputs must be 32 bytes" }
        val scalar = k.copyOf()
        clampScalar(scalar)

        val uClamped = u.copyOf()
        uClamped[31] = (uClamped[31].toInt() and 0x7F).toByte()

        val x1 = decodeLittleEndian(uClamped)
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = x1
        var z3 = BigInteger.ONE
        var swap = 0

        for (t in 254 downTo 0) {
            val byteIdx = t ushr 3
            val bitIdx = t and 7
            val kt = (scalar[byteIdx].toInt() ushr bitIdx) and 1
            swap = swap xor kt
            if (swap != 0) {
                val tx = x2; x2 = x3; x3 = tx
                val tz = z2; z2 = z3; z3 = tz
            }
            swap = kt

            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)

            val sum = da.add(cb).mod(P)
            x3 = sum.multiply(sum).mod(P)
            val diff = da.subtract(cb).mod(P)
            z3 = x1.multiply(diff.multiply(diff).mod(P)).mod(P)
            x2 = aa.multiply(bb).mod(P)
            val a24e = A24.multiply(e).mod(P)
            z2 = e.multiply(aa.add(a24e).mod(P)).mod(P)
        }

        if (swap != 0) {
            val tx = x2; x2 = x3; x3 = tx
            val tz = z2; z2 = z3; z3 = tz
        }

        val z2Inv = z2.modPow(P_MINUS_2, P)
        val res = x2.multiply(z2Inv).mod(P)
        return encodeLittleEndian(res)
    }

    fun isValidBase64Key(keyBase64: String): Boolean {
        return try {
            val decoded = decodeBase64(keyBase64.trim())
            decoded.size == 32
        } catch (_: Exception) {
            false
        }
    }

    fun encodeBase64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun decodeBase64(value: String): ByteArray =
        Base64.decode(value.trim(), Base64.DEFAULT)

    private fun clampScalar(k: ByteArray) {
        k[0] = (k[0].toInt() and 248).toByte()
        k[31] = (k[31].toInt() and 127).toByte()
        k[31] = (k[31].toInt() or 64).toByte()
    }

    private fun decodeLittleEndian(bytes: ByteArray): BigInteger {
        val reversed = ByteArray(bytes.size)
        for (i in bytes.indices) {
            reversed[i] = bytes[bytes.size - 1 - i]
        }
        return BigInteger(1, reversed)
    }

    private fun encodeLittleEndian(value: BigInteger): ByteArray {
        val bigEndian = value.toByteArray()
        val out = ByteArray(32)
        val len = minOf(bigEndian.size, 32)
        for (i in 0 until len) {
            out[i] = bigEndian[bigEndian.size - 1 - i]
        }
        return out
    }
}

package com.example.crypto

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 7693 BLAKE2s, RFC 8439 ChaCha20-Poly1305, XChaCha20-Poly1305,
 * WireGuard HKDF (KDF1/KDF2/KDF3), and TAI64N timestamp primitives.
 */
object WireGuardPrimitives {

    // -------------------------------------------------------------------------
    // BLAKE2s (RFC 7693)
    // -------------------------------------------------------------------------

    private val BLAKE2S_IV = intArrayOf(
        0x6A09E667.toInt(), 0xBB67AE85.toInt(), 0x3C6EF372.toInt(), 0xA54FF53A.toInt(),
        0x510E527F.toInt(), 0x9B05688C.toInt(), 0x1F83D9AB.toInt(), 0x5BE0CD19.toInt()
    )

    private val BLAKE2S_SIGMA = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
        intArrayOf(11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4),
        intArrayOf(7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8),
        intArrayOf(9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13),
        intArrayOf(2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9),
        intArrayOf(12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11),
        intArrayOf(13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10),
        intArrayOf(6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5),
        intArrayOf(10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0)
    )

    fun blake2s(data: ByteArray, outLen: Int = 32, key: ByteArray = ByteArray(0)): ByteArray {
        require(outLen in 1..32) { "BLAKE2s outLen must be 1..32" }
        require(key.size <= 32) { "BLAKE2s key must be <= 32 bytes" }

        val h = BLAKE2S_IV.copyOf()
        h[0] = h[0] xor (0x01010000 or (key.size shl 8) or outLen)

        val buf = ByteArray(64)
        var bufLen = 0
        var totalBytes = 0L

        fun compress(block: ByteArray, last: Boolean) {
            val m = IntArray(16) { i ->
                (block[i * 4].toInt() and 0xFF) or
                    ((block[i * 4 + 1].toInt() and 0xFF) shl 8) or
                    ((block[i * 4 + 2].toInt() and 0xFF) shl 16) or
                    ((block[i * 4 + 3].toInt() and 0xFF) shl 24)
            }
            val v = IntArray(16)
            for (i in 0 until 8) {
                v[i] = h[i]
                v[i + 8] = BLAKE2S_IV[i]
            }
            v[12] = v[12] xor totalBytes.toInt()
            v[13] = v[13] xor (totalBytes ushr 32).toInt()
            if (last) v[14] = v[14] xor -1

            for (round in 0 until 10) {
                val s = BLAKE2S_SIGMA[round]
                g(v, 0, 4, 8, 12, m[s[0]], m[s[1]])
                g(v, 1, 5, 9, 13, m[s[2]], m[s[3]])
                g(v, 2, 6, 10, 14, m[s[4]], m[s[5]])
                g(v, 3, 7, 11, 15, m[s[6]], m[s[7]])
                g(v, 0, 5, 10, 15, m[s[8]], m[s[9]])
                g(v, 1, 6, 11, 12, m[s[10]], m[s[11]])
                g(v, 2, 7, 8, 13, m[s[12]], m[s[13]])
                g(v, 3, 4, 9, 14, m[s[14]], m[s[15]])
            }

            for (i in 0 until 8) {
                h[i] = h[i] xor v[i] xor v[i + 8]
            }
        }

        if (key.isNotEmpty()) {
            key.copyInto(buf)
            bufLen = 64
        }

        var offset = 0
        while (offset < data.size) {
            if (bufLen == 64) {
                totalBytes += 64
                compress(buf, last = false)
                buf.fill(0)
                bufLen = 0
            }
            val copyLen = minOf(64 - bufLen, data.size - offset)
            data.copyInto(buf, destinationOffset = bufLen, startIndex = offset, endIndex = offset + copyLen)
            bufLen += copyLen
            offset += copyLen
        }

        totalBytes += bufLen.toLong()
        if (bufLen < 64) buf.fill(0, bufLen, 64)
        compress(buf, last = true)

        val out = ByteArray(outLen)
        for (i in 0 until outLen) {
            out[i] = ((h[i ushr 2] ushr ((i and 3) * 8)) and 0xFF).toByte()
        }
        return out
    }

    private fun g(v: IntArray, a: Int, b: Int, c: Int, d: Int, x: Int, y: Int) {
        v[a] = v[a] + v[b] + x
        v[d] = Integer.rotateRight(v[d] xor v[a], 16)
        v[c] = v[c] + v[d]
        v[b] = Integer.rotateRight(v[b] xor v[c], 12)
        v[a] = v[a] + v[b] + y
        v[d] = Integer.rotateRight(v[d] xor v[a], 8)
        v[c] = v[c] + v[d]
        v[b] = Integer.rotateRight(v[b] xor v[c], 7)
    }

    fun hash(vararg chunks: ByteArray): ByteArray {
        val total = chunks.sumOf { it.size }
        val combined = ByteArray(total)
        var pos = 0
        for (c in chunks) {
            c.copyInto(combined, pos)
            pos += c.size
        }
        return blake2s(combined, outLen = 32)
    }

    fun mac16(key: ByteArray, data: ByteArray): ByteArray =
        blake2s(data = data, outLen = 16, key = key)

    fun hmacBlake2s(key: ByteArray, vararg inputs: ByteArray): ByteArray {
        val normalizedKey = if (key.size > 64) blake2s(key, 32) else key
        val iPad = ByteArray(64) { 0x36 }
        val oPad = ByteArray(64) { 0x5C }
        for (i in normalizedKey.indices) {
            iPad[i] = (iPad[i].toInt() xor normalizedKey[i].toInt()).toByte()
            oPad[i] = (oPad[i].toInt() xor normalizedKey[i].toInt()).toByte()
        }
        val innerInputLen = 64 + inputs.sumOf { it.size }
        val innerInput = ByteArray(innerInputLen)
        iPad.copyInto(innerInput, 0)
        var pos = 64
        for (inp in inputs) {
            inp.copyInto(innerInput, pos)
            pos += inp.size
        }
        val innerHash = blake2s(innerInput, 32)
        val outerInput = ByteArray(64 + 32)
        oPad.copyInto(outerInput, 0)
        innerHash.copyInto(outerInput, 64)
        return blake2s(outerInput, 32)
    }

    fun kdf1(key: ByteArray, input: ByteArray): ByteArray {
        val prk = hmacBlake2s(key, input)
        return hmacBlake2s(prk, byteArrayOf(0x01))
    }

    fun kdf2(key: ByteArray, input: ByteArray): Pair<ByteArray, ByteArray> {
        val prk = hmacBlake2s(key, input)
        val t1 = hmacBlake2s(prk, byteArrayOf(0x01))
        val t2 = hmacBlake2s(prk, t1, byteArrayOf(0x02))
        return t1 to t2
    }

    fun kdf3(key: ByteArray, input: ByteArray): Triple<ByteArray, ByteArray, ByteArray> {
        val prk = hmacBlake2s(key, input)
        val t1 = hmacBlake2s(prk, byteArrayOf(0x01))
        val t2 = hmacBlake2s(prk, t1, byteArrayOf(0x02))
        val t3 = hmacBlake2s(prk, t2, byteArrayOf(0x03))
        return Triple(t1, t2, t3)
    }

    // -------------------------------------------------------------------------
    // ChaCha20-Poly1305 (RFC 8439) & XChaCha20-Poly1305
    // -------------------------------------------------------------------------

    private val POLY1305_P = BigInteger.ONE.shiftLeft(130).subtract(BigInteger.valueOf(5))

    fun aeadEncrypt(
        key: ByteArray,
        nonce12: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray
    ): ByteArray {
        require(key.size == 32 && nonce12.size == 12)
        // Use hardware/platform JCA ChaCha20-Poly1305 when available (Android 9+/API 28+),
        // fallback cleanly to RFC 8439 implementation on API 24..27 & JVM tests.
        return try {
            val cipher = Cipher.getInstance("ChaCha20-Poly1305")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce12))
            if (aad.isNotEmpty()) cipher.updateAAD(aad)
            cipher.doFinal(plaintext)
        } catch (_: GeneralSecurityException) {
            pureChaCha20Poly1305Encrypt(key, nonce12, plaintext, aad)
        }
    }

    fun aeadDecrypt(
        key: ByteArray,
        nonce12: ByteArray,
        ciphertextWithTag: ByteArray,
        aad: ByteArray
    ): ByteArray {
        require(key.size == 32 && nonce12.size == 12)
        require(ciphertextWithTag.size >= 16) { "Ciphertext too short for Poly1305 tag" }
        return try {
            val cipher = Cipher.getInstance("ChaCha20-Poly1305")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce12))
            if (aad.isNotEmpty()) cipher.updateAAD(aad)
            cipher.doFinal(ciphertextWithTag)
        } catch (e: GeneralSecurityException) {
            // If provider lacks ChaCha20-Poly1305 algorithm, use pure RFC 8439;
            // if AEADBadTagException was thrown because tag is invalid, do NOT swallow it.
            if (e is javax.crypto.AEADBadTagException) throw e
            pureChaCha20Poly1305Decrypt(key, nonce12, ciphertextWithTag, aad)
        }
    }

    fun pureChaCha20Poly1305Encrypt(
        key: ByteArray,
        nonce12: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray
    ): ByteArray {
        val otk = chacha20Block(key, 0, nonce12).copyOfRange(0, 32)
        val ciphertext = chacha20Xor(key, 1, nonce12, plaintext)
        val tag = poly1305Tag(otk, aad, ciphertext)
        return ciphertext + tag
    }

    fun pureChaCha20Poly1305Decrypt(
        key: ByteArray,
        nonce12: ByteArray,
        ciphertextWithTag: ByteArray,
        aad: ByteArray
    ): ByteArray {
        require(ciphertextWithTag.size >= 16) { "Invalid ChaCha20-Poly1305 payload" }
        val ciphertextLen = ciphertextWithTag.size - 16
        val ciphertext = ciphertextWithTag.copyOfRange(0, ciphertextLen)
        val receivedTag = ciphertextWithTag.copyOfRange(ciphertextLen, ciphertextWithTag.size)
        val otk = chacha20Block(key, 0, nonce12).copyOfRange(0, 32)
        val expectedTag = poly1305Tag(otk, aad, ciphertext)
        if (!MessageDigest.isEqual(expectedTag, receivedTag)) {
            throw javax.crypto.AEADBadTagException("Poly1305 MAC verification failed")
        }
        return chacha20Xor(key, 1, nonce12, ciphertext)
    }

    fun xChaCha20Poly1305Encrypt(
        key: ByteArray,
        nonce24: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray
    ): ByteArray {
        require(key.size == 32 && nonce24.size == 24)
        val subKey = hChaCha20(key, nonce24.copyOfRange(0, 16))
        val subNonce = ByteArray(12)
        nonce24.copyInto(subNonce, destinationOffset = 4, startIndex = 16, endIndex = 24)
        return aeadEncrypt(subKey, subNonce, plaintext, aad)
    }

    fun xChaCha20Poly1305Decrypt(
        key: ByteArray,
        nonce24: ByteArray,
        ciphertextWithTag: ByteArray,
        aad: ByteArray
    ): ByteArray {
        require(key.size == 32 && nonce24.size == 24)
        val subKey = hChaCha20(key, nonce24.copyOfRange(0, 16))
        val subNonce = ByteArray(12)
        nonce24.copyInto(subNonce, destinationOffset = 4, startIndex = 16, endIndex = 24)
        return aeadDecrypt(subKey, subNonce, ciphertextWithTag, aad)
    }

    fun hChaCha20(key: ByteArray, nonce16: ByteArray): ByteArray {
        val state = initChaChaState(key, nonce16)
        repeat(10) {
            quarterRound(state, 0, 4, 8, 12)
            quarterRound(state, 1, 5, 9, 13)
            quarterRound(state, 2, 6, 10, 14)
            quarterRound(state, 3, 7, 11, 15)
            quarterRound(state, 0, 5, 10, 15)
            quarterRound(state, 1, 6, 11, 12)
            quarterRound(state, 2, 7, 8, 13)
            quarterRound(state, 3, 4, 9, 14)
        }
        val out = ByteArray(32)
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        for (idx in intArrayOf(0, 1, 2, 3, 12, 13, 14, 15)) {
            buf.putInt(state[idx])
        }
        return out
    }

    private fun chacha20Xor(key: ByteArray, initialCounter: Int, nonce12: ByteArray, input: ByteArray): ByteArray {
        val out = ByteArray(input.size)
        var counter = initialCounter
        var offset = 0
        while (offset < input.size) {
            val block = chacha20Block(key, counter++, nonce12)
            val len = minOf(64, input.size - offset)
            for (i in 0 until len) {
                out[offset + i] = (input[offset + i].toInt() xor block[i].toInt()).toByte()
            }
            offset += len
        }
        return out
    }

    private fun chacha20Block(key: ByteArray, counter: Int, nonce12: ByteArray): ByteArray {
        val initial = IntArray(16)
        initial[0] = 0x61707865
        initial[1] = 0x3320646E
        initial[2] = 0x79622D32
        initial[3] = 0x6B206574
        val kBuf = ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 8) initial[4 + i] = kBuf.getInt()
        initial[12] = counter
        val nBuf = ByteBuffer.wrap(nonce12).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 3) initial[13 + i] = nBuf.getInt()

        val working = initial.copyOf()
        repeat(10) {
            quarterRound(working, 0, 4, 8, 12)
            quarterRound(working, 1, 5, 9, 13)
            quarterRound(working, 2, 6, 10, 14)
            quarterRound(working, 3, 7, 11, 15)
            quarterRound(working, 0, 5, 10, 15)
            quarterRound(working, 1, 6, 11, 12)
            quarterRound(working, 2, 7, 8, 13)
            quarterRound(working, 3, 4, 9, 14)
        }
        val out = ByteArray(64)
        val outBuf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 16) {
            outBuf.putInt(working[i] + initial[i])
        }
        return out
    }

    private fun initChaChaState(key: ByteArray, nonce16: ByteArray): IntArray {
        val state = IntArray(16)
        state[0] = 0x61707865
        state[1] = 0x3320646E
        state[2] = 0x79622D32
        state[3] = 0x6B206574
        val kBuf = ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 8) state[4 + i] = kBuf.getInt()
        val nBuf = ByteBuffer.wrap(nonce16).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 4) state[12 + i] = nBuf.getInt()
        return state
    }

    private fun quarterRound(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
        x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] xor x[a], 16)
        x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] xor x[c], 12)
        x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] xor x[a], 8)
        x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] xor x[c], 7)
    }

    private fun poly1305Tag(otk32: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        val rBytes = otk32.copyOfRange(0, 16)
        rBytes[3] = (rBytes[3].toInt() and 15).toByte()
        rBytes[7] = (rBytes[7].toInt() and 15).toByte()
        rBytes[11] = (rBytes[11].toInt() and 15).toByte()
        rBytes[15] = (rBytes[15].toInt() and 15).toByte()
        rBytes[4] = (rBytes[4].toInt() and 252).toByte()
        rBytes[8] = (rBytes[8].toInt() and 252).toByte()
        rBytes[12] = (rBytes[12].toInt() and 252).toByte()
        val r = decodeLittleEndianUnsigned(rBytes)
        val s = decodeLittleEndianUnsigned(otk32.copyOfRange(16, 32))

        val aadPad = (16 - (aad.size % 16)) % 16
        val ctPad = (16 - (ciphertext.size % 16)) % 16
        val macData = ByteArray(aad.size + aadPad + ciphertext.size + ctPad + 16)
        aad.copyInto(macData, 0)
        ciphertext.copyInto(macData, aad.size + aadPad)
        ByteBuffer.wrap(macData, macData.size - 16, 16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(aad.size.toLong())
            .putLong(ciphertext.size.toLong())

        var acc = BigInteger.ZERO
        var pos = 0
        while (pos < macData.size) {
            val end = minOf(pos + 16, macData.size)
            val block = ByteArray(end - pos + 1)
            macData.copyInto(block, 0, pos, end)
            block[block.size - 1] = 1
            val n = decodeLittleEndianUnsigned(block)
            acc = acc.add(n).multiply(r).mod(POLY1305_P)
            pos = end
        }
        acc = acc.add(s)
        val tag = ByteArray(16)
        val be = acc.toByteArray()
        for (i in 0 until minOf(16, be.size)) {
            tag[i] = be[be.size - 1 - i]
        }
        return tag
    }

    private fun decodeLittleEndianUnsigned(bytes: ByteArray): BigInteger {
        val rev = ByteArray(bytes.size)
        for (i in bytes.indices) rev[i] = bytes[bytes.size - 1 - i]
        return BigInteger(1, rev)
    }

    fun counterToNonce12(counter: Long): ByteArray {
        val nonce = ByteArray(12)
        ByteBuffer.wrap(nonce, 4, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(counter)
        return nonce
    }

    /**
     * WireGuard TAI64N 12-byte timestamp (big-endian 8-byte seconds + 4-byte nanoseconds).
     */
    fun tai64nNow(): ByteArray {
        val nowMs = System.currentTimeMillis()
        val seconds = (nowMs / 1000L) + 0x400000000000000aL
        val nanos = ((nowMs % 1000L) * 1_000_000L).toInt()
        return ByteBuffer.allocate(12)
            .order(ByteOrder.BIG_ENDIAN)
            .putLong(seconds)
            .putInt(nanos)
            .array()
    }
}

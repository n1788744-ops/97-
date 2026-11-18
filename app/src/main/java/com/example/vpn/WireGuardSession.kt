package com.example.vpn

import com.example.crypto.Aes256TunnelCipher
import com.example.crypto.Curve25519
import com.example.crypto.ReplayWindow
import com.example.crypto.WireGuardPrimitives
import com.example.data.model.VpnCipherMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

/**
 * Real WireGuard Noise_IKpsk2 handshake state machine and transport cipher
 * supporting both standard WireGuard (ChaCha20-Poly1305) and WireGuard + AES-256-GCM.
 */
class WireGuardSession(
    private val localPrivateKey: ByteArray,
    private val localPublicKey: ByteArray,
    private val peerPublicKey: ByteArray,
    private val presharedKey: ByteArray?,
    val cipherMode: VpnCipherMode
) {
    companion object {
        private const val MSG_HANDSHAKE_INITIATION: Byte = 1
        private const val MSG_HANDSHAKE_RESPONSE: Byte = 2
        private const val MSG_COOKIE_REPLY: Byte = 3
        private const val MSG_TRANSPORT_DATA: Byte = 4

        private val CONSTRUCTION = "Noise_IKpsk2_25519_ChaChaPoly_BLAKE2s".toByteArray(Charsets.US_ASCII)
        private val IDENTIFIER = "WireGuard v1 zx2c4 Jason@zx2c4.com".toByteArray(Charsets.US_ASCII)
        private val LABEL_MAC1 = "mac1----".toByteArray(Charsets.US_ASCII)
        private val LABEL_COOKIE = "cookie--".toByteArray(Charsets.US_ASCII)

        private val INITIAL_CHAIN_KEY: ByteArray = WireGuardPrimitives.hash(CONSTRUCTION)
        private val INITIAL_HASH: ByteArray = WireGuardPrimitives.hash(INITIAL_CHAIN_KEY, IDENTIFIER)
    }

    private val localSenderIndex: Int = SecureRandom().nextInt()
    private var remoteSenderIndex: Int = 0

    private var ephemeralPrivateKey: ByteArray = ByteArray(0)
    private var ephemeralPublicKey: ByteArray = ByteArray(0)
    private var chainingKey: ByteArray = ByteArray(0)
    private var handshakeHash: ByteArray = ByteArray(0)

    private var lastMac1Sent: ByteArray = ByteArray(16)
    private var lastCookie: ByteArray? = null

    private var wgSendKey: ByteArray? = null
    private var wgRecvKey: ByteArray? = null
    private var aesKeys: Aes256TunnelCipher.SessionKeys? = null

    private val sendCounter = AtomicLong(0L)
    private val replayWindow = ReplayWindow()

    @Volatile
    var isEstablished: Boolean = false
        private set

    fun createHandshakeInitiation(): ByteArray {
        val eph = Curve25519.generateKeyPair()
        ephemeralPrivateKey = eph.privateKey
        ephemeralPublicKey = eph.publicKey

        var ck = INITIAL_CHAIN_KEY.copyOf()
        var h = WireGuardPrimitives.hash(INITIAL_HASH, peerPublicKey)

        ck = WireGuardPrimitives.kdf1(ck, ephemeralPublicKey)
        h = WireGuardPrimitives.hash(h, ephemeralPublicKey)

        val es = Curve25519.sharedSecret(ephemeralPrivateKey, peerPublicKey)
        val (ck1, key1) = WireGuardPrimitives.kdf2(ck, es)
        ck = ck1

        val zeroNonce = ByteArray(12)
        val encryptedStatic = WireGuardPrimitives.aeadEncrypt(key1, zeroNonce, localPublicKey, h)
        h = WireGuardPrimitives.hash(h, encryptedStatic)

        val ss = Curve25519.sharedSecret(localPrivateKey, peerPublicKey)
        val (ck2, key2) = WireGuardPrimitives.kdf2(ck, ss)
        ck = ck2

        val timestamp = WireGuardPrimitives.tai64nNow()
        val encryptedTimestamp = WireGuardPrimitives.aeadEncrypt(key2, zeroNonce, timestamp, h)
        h = WireGuardPrimitives.hash(h, encryptedTimestamp)

        chainingKey = ck
        handshakeHash = h

        val packet = ByteArray(148)
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(MSG_HANDSHAKE_INITIATION)
        buf.put(0); buf.put(0); buf.put(0)
        buf.putInt(localSenderIndex)
        buf.put(ephemeralPublicKey)
        buf.put(encryptedStatic)
        buf.put(encryptedTimestamp)

        val mac1Key = WireGuardPrimitives.hash(LABEL_MAC1, peerPublicKey)
        val mac1 = WireGuardPrimitives.mac16(mac1Key, packet.copyOfRange(0, 116))
        lastMac1Sent = mac1
        mac1.copyInto(packet, 116)

        val mac2 = lastCookie?.let { cookie ->
            WireGuardPrimitives.mac16(cookie, packet.copyOfRange(0, 132))
        } ?: ByteArray(16)
        mac2.copyInto(packet, 132)

        return packet
    }

    fun handleIncomingControlPacket(packet: ByteArray): Boolean {
        if (packet.isEmpty()) return false
        return when (packet[0]) {
            MSG_HANDSHAKE_RESPONSE -> consumeHandshakeResponse(packet)
            MSG_COOKIE_REPLY -> consumeCookieReply(packet)
            else -> false
        }
    }

    fun consumeHandshakeResponse(packet: ByteArray): Boolean {
        if (packet.size != 92 || packet[0] != MSG_HANDSHAKE_RESPONSE) return false

        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(4)
        val respSender = buf.int
        val respReceiver = buf.int
        if (respReceiver != localSenderIndex) return false

        val respEphemeral = ByteArray(32).also { buf.get(it) }
        val encryptedEmpty = ByteArray(16).also { buf.get(it) }
        val receivedMac1 = ByteArray(16).also { buf.get(it) }

        val mac1Key = WireGuardPrimitives.hash(LABEL_MAC1, localPublicKey)
        val expectedMac1 = WireGuardPrimitives.mac16(mac1Key, packet.copyOfRange(0, 60))
        if (!MessageDigest.isEqual(expectedMac1, receivedMac1)) return false

        var ck = WireGuardPrimitives.kdf1(chainingKey, respEphemeral)
        var h = WireGuardPrimitives.hash(handshakeHash, respEphemeral)

        val ee = Curve25519.sharedSecret(ephemeralPrivateKey, respEphemeral)
        ck = WireGuardPrimitives.kdf1(ck, ee)

        val se = Curve25519.sharedSecret(localPrivateKey, respEphemeral)
        ck = WireGuardPrimitives.kdf1(ck, se)

        val psk = if (presharedKey != null && presharedKey.size == 32) presharedKey else ByteArray(32)
        val (ckAfterPsk, tau, key3) = WireGuardPrimitives.kdf3(ck, psk)
        ck = ckAfterPsk
        h = WireGuardPrimitives.hash(h, tau)

        // Verify Noise_IKpsk2 empty payload authentication tag
        WireGuardPrimitives.aeadDecrypt(key3, ByteArray(12), encryptedEmpty, h)
        h = WireGuardPrimitives.hash(h, encryptedEmpty)

        val (sendKey, recvKey) = WireGuardPrimitives.kdf2(ck, ByteArray(0))
        remoteSenderIndex = respSender
        wgSendKey = sendKey
        wgRecvKey = recvKey

        if (cipherMode == VpnCipherMode.WIREGUARD_AES_256_GCM) {
            aesKeys = Aes256TunnelCipher.deriveSessionKeys(
                sharedSecret = sendKey + recvKey,
                transcriptHash = h,
                isInitiator = true
            )
        }

        sendCounter.set(0L)
        isEstablished = true
        return true
    }

    private fun consumeCookieReply(packet: ByteArray): Boolean {
        if (packet.size != 64 || packet[0] != MSG_COOKIE_REPLY) return false
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(4)
        val receiver = buf.int
        if (receiver != localSenderIndex) return false
        val nonce24 = ByteArray(24).also { buf.get(it) }
        val encryptedCookie = ByteArray(32).also { buf.get(it) }
        val cookieKey = WireGuardPrimitives.hash(LABEL_COOKIE, peerPublicKey)
        lastCookie = WireGuardPrimitives.xChaCha20Poly1305Decrypt(
            key = cookieKey,
            nonce24 = nonce24,
            ciphertextWithTag = encryptedCookie,
            aad = lastMac1Sent
        )
        return true
    }

    fun encryptTransportPacket(ipPacket: ByteArray): ByteArray {
        check(isEstablished) { "WireGuard session not established" }
        val counter = sendCounter.getAndIncrement()
        // WireGuard pads transport payloads to a multiple of 16 bytes (0-byte keepalive stays empty)
        val padded = if (ipPacket.isEmpty()) {
            ipPacket
        } else {
            val rem = ipPacket.size % 16
            if (rem == 0) ipPacket else ipPacket.copyOf(ipPacket.size + (16 - rem))
        }

        val header = ByteArray(16)
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(MSG_TRANSPORT_DATA)
            put(0); put(0); put(0)
            putInt(remoteSenderIndex)
            putLong(counter)
        }

        val encryptedPayload = when (cipherMode) {
            VpnCipherMode.WIREGUARD_CHACHA20 -> {
                val key = wgSendKey ?: error("Missing WireGuard send key")
                val nonce = WireGuardPrimitives.counterToNonce12(counter)
                WireGuardPrimitives.aeadEncrypt(key, nonce, padded, ByteArray(0))
            }
            VpnCipherMode.WIREGUARD_AES_256_GCM -> {
                val keys = aesKeys ?: error("Missing AES-256-GCM session keys")
                val nonce = Aes256TunnelCipher.buildNonce(keys.txSalt, counter)
                Aes256TunnelCipher.encryptPacket(keys.txKey, nonce, padded, header)
            }
        }

        return header + encryptedPayload
    }

    fun decryptTransportPacket(packet: ByteArray): ByteArray? {
        if (!isEstablished || packet.size < 32 || packet[0] != MSG_TRANSPORT_DATA) return null

        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(4)
        val receiverIdx = buf.int
        if (receiverIdx != localSenderIndex) return null
        val counter = buf.long

        val header = packet.copyOfRange(0, 16)
        val ciphertext = packet.copyOfRange(16, packet.size)

        val plaintext = when (cipherMode) {
            VpnCipherMode.WIREGUARD_CHACHA20 -> {
                val key = wgRecvKey ?: return null
                val nonce = WireGuardPrimitives.counterToNonce12(counter)
                WireGuardPrimitives.aeadDecrypt(key, nonce, ciphertext, ByteArray(0))
            }
            VpnCipherMode.WIREGUARD_AES_256_GCM -> {
                val keys = aesKeys ?: return null
                val nonce = Aes256TunnelCipher.buildNonce(keys.rxSalt, counter)
                Aes256TunnelCipher.decryptPacket(keys.rxKey, nonce, ciphertext, header)
            }
        }

        if (!replayWindow.checkAndAccept(counter)) return null
        return extractIpPacketLength(plaintext)
    }

    private fun extractIpPacketLength(plaintext: ByteArray): ByteArray {
        if (plaintext.isEmpty()) return plaintext // Keepalive packet
        if (plaintext.size < 20) return ByteArray(0)
        val version = (plaintext[0].toInt() ushr 4) and 0xF
        val totalLen = when (version) {
            4 -> ((plaintext[2].toInt() and 0xFF) shl 8) or (plaintext[3].toInt() and 0xFF)
            6 -> if (plaintext.size >= 40) {
                40 + (((plaintext[4].toInt() and 0xFF) shl 8) or (plaintext[5].toInt() and 0xFF))
            } else {
                plaintext.size
            }
            else -> plaintext.size
        }
        return if (totalLen in 20..plaintext.size) plaintext.copyOfRange(0, totalLen) else plaintext
    }
}

package com.example.data.model

enum class VpnCipherMode(
    val id: String,
    val displayName: String,
    val protocolBadge: String,
    val detail: String
) {
    WIREGUARD_CHACHA20(
        id = "wg_chacha20",
        displayName = "WireGuard (ChaCha20-Poly1305)",
        protocolBadge = "WireGuard • Noise_IKpsk2",
        detail = "Стандартный протокол WireGuard: Curve25519 + ChaCha20-Poly1305 + BLAKE2s"
    ),
    WIREGUARD_AES_256_GCM(
        id = "wg_aes256_gcm",
        displayName = "WireGuard + AES-256-GCM",
        protocolBadge = "WireGuard • AES-256-GCM",
        detail = "X25519 ECDH + HKDF-SHA256 + 256-битное шифрование AES/GCM/NoPadding"
    );

    companion object {
        fun fromId(id: String): VpnCipherMode =
            entries.firstOrNull { it.id == id } ?: WIREGUARD_AES_256_GCM
    }
}

data class ServerProfile(
    val id: Long = 0,
    val name: String,
    val countryCode: String,
    val city: String,
    val endpointHost: String,
    val endpointPort: Int,
    val privateKeyBase64: String,
    val publicKeyBase64: String,
    val peerPublicKeyBase64: String,
    val presharedKeyBase64: String = "",
    val interfaceAddresses: String = "10.66.66.2/32",
    val dnsServers: String = "1.1.1.1, 9.9.9.9",
    val allowedIps: String = "0.0.0.0/0, ::/0",
    val mtu: Int = 1420,
    val persistentKeepaliveSec: Int = 25,
    val cipherMode: VpnCipherMode = VpnCipherMode.WIREGUARD_AES_256_GCM,
    val lastLatencyMs: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)

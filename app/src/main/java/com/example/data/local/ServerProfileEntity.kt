package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "server_profiles")
data class ServerProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val countryCode: String,
    val city: String,
    val endpointHost: String,
    val endpointPort: Int,
    val sealedPrivateKey: String,
    val publicKeyBase64: String,
    val peerPublicKeyBase64: String,
    val sealedPresharedKey: String,
    val interfaceAddresses: String,
    val dnsServers: String,
    val allowedIps: String,
    val mtu: Int,
    val persistentKeepaliveSec: Int,
    val cipherModeId: String,
    val lastLatencyMs: Int?,
    val createdAt: Long
)

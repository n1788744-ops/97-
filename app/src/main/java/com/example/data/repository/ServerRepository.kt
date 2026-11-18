package com.example.data.repository

import com.example.crypto.Curve25519
import com.example.crypto.KeyVault
import com.example.data.local.ServerProfileDao
import com.example.data.local.ServerProfileEntity
import com.example.data.local.SettingsDataStore
import com.example.data.local.VpnSettings
import com.example.data.model.ServerProfile
import com.example.data.model.VpnCipherMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ServerRepository(
    private val dao: ServerProfileDao,
    private val keyVault: KeyVault,
    private val settingsDataStore: SettingsDataStore
) {
    val serversFlow: Flow<List<ServerProfile>> = dao.observeAll().map { entities ->
        entities.mapNotNull { runCatching { it.toDomain() }.getOrNull() }
    }

    val settingsFlow: Flow<VpnSettings> = settingsDataStore.settingsFlow

    suspend fun getById(id: Long): ServerProfile? =
        dao.getById(id)?.let { runCatching { it.toDomain() }.getOrNull() }

    suspend fun getSelectedOrFirst(): ServerProfile? {
        val selectedId = settingsDataStore.getSnapshot().selectedServerId
        if (selectedId != null) {
            getById(selectedId)?.let { return it }
        }
        return dao.getFirst()?.let { runCatching { it.toDomain() }.getOrNull() }
    }

    suspend fun saveProfile(profile: ServerProfile): Long {
        val derivedPub = runCatching {
            Curve25519.encodeBase64(
                Curve25519.publicKeyFromPrivate(Curve25519.decodeBase64(profile.privateKeyBase64))
            )
        }.getOrDefault(profile.publicKeyBase64)

        val entity = ServerProfileEntity(
            id = profile.id,
            name = profile.name.trim(),
            countryCode = profile.countryCode.trim().uppercase().ifBlank { "WG" },
            city = profile.city.trim().ifBlank { profile.endpointHost },
            endpointHost = profile.endpointHost.trim(),
            endpointPort = profile.endpointPort,
            sealedPrivateKey = keyVault.seal(profile.privateKeyBase64.trim()),
            publicKeyBase64 = derivedPub,
            peerPublicKeyBase64 = profile.peerPublicKeyBase64.trim(),
            sealedPresharedKey = keyVault.seal(profile.presharedKeyBase64.trim()),
            interfaceAddresses = profile.interfaceAddresses.trim(),
            dnsServers = profile.dnsServers.trim(),
            allowedIps = profile.allowedIps.trim(),
            mtu = profile.mtu,
            persistentKeepaliveSec = profile.persistentKeepaliveSec,
            cipherModeId = profile.cipherMode.id,
            lastLatencyMs = profile.lastLatencyMs,
            createdAt = profile.createdAt
        )
        val savedId = dao.insert(entity)
        val resolvedId = if (profile.id > 0) profile.id else savedId
        settingsDataStore.setSelectedServerId(resolvedId)
        return resolvedId
    }

    suspend fun selectServer(id: Long) {
        settingsDataStore.setSelectedServerId(id)
    }

    suspend fun deleteServer(id: Long) {
        dao.deleteById(id)
        val snapshot = settingsDataStore.getSnapshot()
        if (snapshot.selectedServerId == id) {
            val next = dao.getFirst()?.id
            settingsDataStore.setSelectedServerId(next)
        }
    }

    suspend fun updateLatency(id: Long, latencyMs: Int?) {
        dao.updateLatency(id, latencyMs)
    }

    suspend fun setCustomDns(enabled: Boolean, dns: String) =
        settingsDataStore.setCustomDns(enabled, dns)

    suspend fun setAutoReconnect(enabled: Boolean) =
        settingsDataStore.setAutoReconnect(enabled)

    suspend fun toggleDisallowedPackage(packageName: String) =
        settingsDataStore.toggleDisallowedPackage(packageName)

    private fun ServerProfileEntity.toDomain(): ServerProfile {
        return ServerProfile(
            id = id,
            name = name,
            countryCode = countryCode,
            city = city,
            endpointHost = endpointHost,
            endpointPort = endpointPort,
            privateKeyBase64 = keyVault.unseal(sealedPrivateKey),
            publicKeyBase64 = publicKeyBase64,
            peerPublicKeyBase64 = peerPublicKeyBase64,
            presharedKeyBase64 = keyVault.unseal(sealedPresharedKey),
            interfaceAddresses = interfaceAddresses,
            dnsServers = dnsServers,
            allowedIps = allowedIps,
            mtu = mtu,
            persistentKeepaliveSec = persistentKeepaliveSec,
            cipherMode = VpnCipherMode.fromId(cipherModeId),
            lastLatencyMs = lastLatencyMs,
            createdAt = createdAt
        )
    }
}

package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.vpnSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cipherguard_settings"
)

data class VpnSettings(
    val selectedServerId: Long? = null,
    val useCustomDns: Boolean = false,
    val customDns: String = "1.1.1.1, 9.9.9.9",
    val autoReconnectOnBoot: Boolean = true,
    val disallowedPackages: Set<String> = emptySet()
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val SELECTED_SERVER_ID = longPreferencesKey("selected_server_id")
        val USE_CUSTOM_DNS = booleanPreferencesKey("use_custom_dns")
        val CUSTOM_DNS = stringPreferencesKey("custom_dns")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val DISALLOWED_PACKAGES = stringSetPreferencesKey("disallowed_packages")
    }

    val settingsFlow: Flow<VpnSettings> = context.vpnSettingsDataStore.data.map { prefs ->
        VpnSettings(
            selectedServerId = prefs[Keys.SELECTED_SERVER_ID],
            useCustomDns = prefs[Keys.USE_CUSTOM_DNS] ?: false,
            customDns = prefs[Keys.CUSTOM_DNS] ?: "1.1.1.1, 9.9.9.9",
            autoReconnectOnBoot = prefs[Keys.AUTO_RECONNECT] ?: true,
            disallowedPackages = prefs[Keys.DISALLOWED_PACKAGES] ?: emptySet()
        )
    }

    suspend fun getSnapshot(): VpnSettings = settingsFlow.first()

    suspend fun setSelectedServerId(id: Long?) {
        context.vpnSettingsDataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.SELECTED_SERVER_ID)
            else prefs[Keys.SELECTED_SERVER_ID] = id
        }
    }

    suspend fun setCustomDns(enabled: Boolean, dns: String) {
        context.vpnSettingsDataStore.edit { prefs ->
            prefs[Keys.USE_CUSTOM_DNS] = enabled
            prefs[Keys.CUSTOM_DNS] = dns
        }
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.vpnSettingsDataStore.edit { prefs ->
            prefs[Keys.AUTO_RECONNECT] = enabled
        }
    }

    suspend fun toggleDisallowedPackage(packageName: String) {
        context.vpnSettingsDataStore.edit { prefs ->
            val current = prefs[Keys.DISALLOWED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(packageName)) current.remove(packageName)
            prefs[Keys.DISALLOWED_PACKAGES] = current
        }
    }
}

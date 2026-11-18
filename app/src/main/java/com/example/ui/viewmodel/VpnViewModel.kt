package com.example.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoAuditReport
import com.example.crypto.CryptoSelfTest
import com.example.crypto.Curve25519
import com.example.crypto.KeyVault
import com.example.data.local.SettingsDataStore
import com.example.data.local.VpnDatabase
import com.example.data.local.VpnSettings
import com.example.data.model.ServerProfile
import com.example.data.model.VpnCipherMode
import com.example.data.parser.WireGuardConfigParser
import com.example.data.repository.InstalledAppItem
import com.example.data.repository.NetworkDiagnosticsRepository
import com.example.data.repository.PublicIpInfo
import com.example.data.repository.ServerRepository
import com.example.vpn.CipherGuardVpnService
import com.example.vpn.TunnelLogEntry
import com.example.vpn.TunnelMetrics
import com.example.vpn.TunnelStateHolder
import com.example.vpn.TunnelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val keyVault = KeyVault()
    private val settingsDataStore = SettingsDataStore(context)
    private val database = VpnDatabase.getInstance(context)
    private val serverRepository = ServerRepository(
        dao = database.serverProfileDao(),
        keyVault = keyVault,
        settingsDataStore = settingsDataStore
    )
    private val networkRepo = NetworkDiagnosticsRepository(context)

    val servers: StateFlow<List<ServerProfile>> = serverRepository.serversFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<VpnSettings> = serverRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VpnSettings())

    val selectedServer: StateFlow<ServerProfile?> = combine(servers, settings) { list, cfg ->
        list.firstOrNull { it.id == cfg.selectedServerId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tunnelStatus: StateFlow<TunnelStatus> = TunnelStateHolder.status
    val tunnelMetrics: StateFlow<TunnelMetrics> = TunnelStateHolder.metrics
    val tunnelLogs: StateFlow<List<TunnelLogEntry>> = TunnelStateHolder.logs

    private val _preVpnIpInfo = MutableStateFlow<PublicIpInfo?>(null)
    val preVpnIpInfo: StateFlow<PublicIpInfo?> = _preVpnIpInfo.asStateFlow()

    private val _currentIpInfo = MutableStateFlow<PublicIpInfo?>(null)
    val currentIpInfo: StateFlow<PublicIpInfo?> = _currentIpInfo.asStateFlow()

    private val _isCheckingIp = MutableStateFlow(false)
    val isCheckingIp: StateFlow<Boolean> = _isCheckingIp.asStateFlow()

    private val _ipError = MutableStateFlow<String?>(null)
    val ipError: StateFlow<String?> = _ipError.asStateFlow()

    private val _hasVpnPermission = MutableStateFlow(checkVpnPermission())
    val hasVpnPermission: StateFlow<Boolean> = _hasVpnPermission.asStateFlow()

    private val _hasNotificationPermission = MutableStateFlow(checkNotificationPermission())
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    private val _pendingVpnConsentIntent = MutableStateFlow<Intent?>(null)
    val pendingVpnConsentIntent: StateFlow<Intent?> = _pendingVpnConsentIntent.asStateFlow()

    private val _cryptoAudit = MutableStateFlow<CryptoAuditReport?>(null)
    val cryptoAudit: StateFlow<CryptoAuditReport?> = _cryptoAudit.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private var pendingConnectAfterConsent = false

    init {
        refreshPermissionsState()
        runCryptoAudit()
        refreshPublicIp(isInitial = true)

        viewModelScope.launch {
            var wasConnected = false
            tunnelStatus.collect { status ->
                when (status) {
                    is TunnelStatus.Connected -> {
                        wasConnected = true
                        refreshPublicIp(isInitial = false)
                    }
                    is TunnelStatus.Disconnected -> {
                        if (wasConnected) {
                            wasConnected = false
                            refreshPublicIp(isInitial = false)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun refreshPermissionsState() {
        _hasVpnPermission.value = checkVpnPermission()
        _hasNotificationPermission.value = checkNotificationPermission()
    }

    fun requestSystemVpnPermission() {
        val intent = VpnService.prepare(context)
        if (intent == null) {
            _hasVpnPermission.value = true
            _uiMessage.value = "Системное разрешение VPN уже активно"
        } else {
            pendingConnectAfterConsent = false
            _pendingVpnConsentIntent.value = intent
        }
    }

    fun onVpnConsentResult(granted: Boolean) {
        _pendingVpnConsentIntent.value = null
        refreshPermissionsState()
        if (granted) {
            TunnelStateHolder.log("SYSTEM", "Системное разрешение BIND_VPN_SERVICE выдано пользователем")
            if (pendingConnectAfterConsent) {
                pendingConnectAfterConsent = false
                startVpnForSelectedServer()
            }
        } else {
            pendingConnectAfterConsent = false
            TunnelStateHolder.log("SYSTEM", "Пользователь отклонил системное разрешение VPN", isError = true)
            TunnelStateHolder.updateStatus(
                TunnelStatus.Error("Системное разрешение VPN не выдано")
            )
        }
    }

    fun onNotificationPermissionResult() {
        refreshPermissionsState()
    }

    fun toggleTunnelConnection() {
        when (tunnelStatus.value) {
            is TunnelStatus.Connected, is TunnelStatus.Connecting, is TunnelStatus.Preparing -> {
                CipherGuardVpnService.stopTunnel(context)
            }
            is TunnelStatus.Disconnected, is TunnelStatus.Error -> {
                val target = selectedServer.value
                if (target == null) {
                    _uiMessage.value = "Добавьте или импортируйте профиль сервера WireGuard"
                    return
                }
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent != null) {
                    pendingConnectAfterConsent = true
                    TunnelStateHolder.updateStatus(TunnelStatus.Preparing)
                    _pendingVpnConsentIntent.value = prepareIntent
                } else {
                    _hasVpnPermission.value = true
                    startVpnForSelectedServer()
                }
            }
        }
    }

    fun connectToServer(serverId: Long) {
        viewModelScope.launch {
            serverRepository.selectServer(serverId)
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                pendingConnectAfterConsent = true
                TunnelStateHolder.updateStatus(TunnelStatus.Preparing)
                _pendingVpnConsentIntent.value = prepareIntent
            } else {
                _hasVpnPermission.value = true
                CipherGuardVpnService.startConnect(context, serverId)
            }
        }
    }

    private fun startVpnForSelectedServer() {
        val target = selectedServer.value ?: return
        CipherGuardVpnService.startConnect(context, target.id)
    }

    fun refreshPublicIp(isInitial: Boolean = false) {
        viewModelScope.launch {
            _isCheckingIp.value = true
            _ipError.value = null
            networkRepo.fetchPublicIpInfo()
                .onSuccess { info ->
                    _currentIpInfo.value = info
                    val connected = tunnelStatus.value is TunnelStatus.Connected
                    if (!connected && (_preVpnIpInfo.value == null || isInitial || !networkRepo.isSystemVpnTransportActive())) {
                        _preVpnIpInfo.value = info
                    }
                }
                .onFailure { err ->
                    _ipError.value = err.message ?: "Не удалось определить публичный IP"
                }
            _isCheckingIp.value = false
        }
    }

    fun selectServer(serverId: Long) {
        viewModelScope.launch {
            serverRepository.selectServer(serverId)
        }
    }

    fun saveServerProfile(profile: ServerProfile, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                serverRepository.saveProfile(profile)
            }.onSuccess {
                _uiMessage.value = "Профиль «${profile.name}» сохранён"
                onSaved()
            }.onFailure { err ->
                _uiMessage.value = err.message ?: "Ошибка сохранения профиля"
            }
        }
    }

    fun importConfigText(
        rawText: String,
        fallbackName: String,
        cipherMode: VpnCipherMode,
        onSuccess: () -> Unit = {}
    ) {
        val parsed = WireGuardConfigParser.parse(
            rawConfig = rawText,
            fallbackName = fallbackName,
            defaultCipherMode = cipherMode
        )
        parsed.onSuccess { profile ->
            saveServerProfile(profile, onSuccess)
        }.onFailure { err ->
            _uiMessage.value = err.message ?: "Некорректная конфигурация .conf"
        }
    }

    fun importConfigUri(uri: Uri, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }
            if (content.isNullOrBlank()) {
                _uiMessage.value = "Файл конфигурации пуст или недоступен"
                return@launch
            }
            val fileName = uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?: "Импорт WireGuard"

            importConfigText(
                rawText = content,
                fallbackName = fileName,
                cipherMode = VpnCipherMode.WIREGUARD_AES_256_GCM,
                onSuccess = onSuccess
            )
        }
    }

    fun deleteServer(serverId: Long) {
        viewModelScope.launch {
            if ((tunnelStatus.value as? TunnelStatus.Connected)?.serverId == serverId) {
                CipherGuardVpnService.stopTunnel(context)
            }
            serverRepository.deleteServer(serverId)
            _uiMessage.value = "Профиль сервера удалён"
        }
    }

    fun pingServer(profile: ServerProfile) {
        viewModelScope.launch {
            val ms = networkRepo.measureEndpointLatency(profile.endpointHost, profile.endpointPort)
            serverRepository.updateLatency(profile.id, ms)
            _uiMessage.value = if (ms != null) {
                "Отклик ${profile.endpointHost}: $ms мс"
            } else {
                "Хост ${profile.endpointHost} недоступен по ICMP/TCP"
            }
        }
    }

    fun generateKeyPair(): Curve25519.KeyPair = Curve25519.generateKeyPair()

    fun runCryptoAudit() {
        viewModelScope.launch(Dispatchers.Default) {
            _cryptoAudit.value = CryptoSelfTest.runFullAudit(keyVault)
        }
    }

    fun updateCustomDns(enabled: Boolean, dns: String) {
        viewModelScope.launch {
            serverRepository.setCustomDns(enabled, dns)
        }
    }

    fun updateAutoReconnect(enabled: Boolean) {
        viewModelScope.launch {
            serverRepository.setAutoReconnect(enabled)
        }
    }

    fun toggleDisallowedPackage(packageName: String) {
        viewModelScope.launch {
            serverRepository.toggleDisallowedPackage(packageName)
        }
    }

    fun loadInstalledApps() {
        if (_installedApps.value.isNotEmpty()) return
        viewModelScope.launch {
            _installedApps.value = networkRepo.loadInstalledLauncherApps()
        }
    }

    fun clearLogs() {
        TunnelStateHolder.clearLogs()
    }

    fun clearMessage() {
        _uiMessage.value = null
    }

    private fun checkVpnPermission(): Boolean = VpnService.prepare(context) == null

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}

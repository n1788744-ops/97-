package com.example.vpn

import com.example.data.model.VpnCipherMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface TunnelStatus {
    data object Disconnected : TunnelStatus
    data object Preparing : TunnelStatus
    data class Connecting(
        val serverId: Long,
        val serverName: String,
        val endpoint: String,
        val cipherMode: VpnCipherMode
    ) : TunnelStatus

    data class Connected(
        val serverId: Long,
        val serverName: String,
        val countryCode: String,
        val city: String,
        val endpoint: String,
        val cipherMode: VpnCipherMode,
        val connectedSinceMs: Long,
        val handshakeLatencyMs: Int
    ) : TunnelStatus

    data class Error(
        val message: String,
        val serverName: String? = null
    ) : TunnelStatus
}

data class TunnelMetrics(
    val txBytes: Long = 0L,
    val rxBytes: Long = 0L,
    val txPackets: Long = 0L,
    val rxPackets: Long = 0L,
    val txBytesPerSec: Long = 0L,
    val rxBytesPerSec: Long = 0L,
    val lastHandshakeMs: Long = 0L
)

data class TunnelLogEntry(
    val timestamp: String,
    val tag: String,
    val message: String,
    val isError: Boolean = false
)

object TunnelStateHolder {

    private const val MAX_LOGS = 80

    private val _status = MutableStateFlow<TunnelStatus>(TunnelStatus.Disconnected)
    val status: StateFlow<TunnelStatus> = _status.asStateFlow()

    private val _metrics = MutableStateFlow(TunnelMetrics())
    val metrics: StateFlow<TunnelMetrics> = _metrics.asStateFlow()

    private val _logs = MutableStateFlow<List<TunnelLogEntry>>(emptyList())
    val logs: StateFlow<List<TunnelLogEntry>> = _logs.asStateFlow()

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun updateStatus(newStatus: TunnelStatus) {
        _status.value = newStatus
    }

    fun updateMetrics(metrics: TunnelMetrics) {
        _metrics.value = metrics
    }

    fun resetMetrics() {
        _metrics.value = TunnelMetrics()
    }

    fun log(tag: String, message: String, isError: Boolean = false) {
        val entry = TunnelLogEntry(
            timestamp = synchronized(timeFormatter) { timeFormatter.format(Date()) },
            tag = tag,
            message = message,
            isError = isError
        )
        _logs.update { current ->
            (listOf(entry) + current).take(MAX_LOGS)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}

package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.crypto.Curve25519
import com.example.crypto.KeyVault
import com.example.data.local.SettingsDataStore
import com.example.data.local.VpnDatabase
import com.example.data.model.ServerProfile
import com.example.data.repository.ServerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.atomic.AtomicLong

class CipherGuardVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.example.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.ACTION_DISCONNECT"
        const val EXTRA_SERVER_ID = "extra_server_id"
        private const val NOTIFICATION_ID = 7001

        fun startConnect(context: Context, serverId: Long) {
            val intent = Intent(context, CipherGuardVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_SERVER_ID, serverId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopTunnel(context: Context) {
            val intent = Intent(context, CipherGuardVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnelJob: Job? = null
    private var tunInterface: ParcelFileDescriptor? = null
    private var udpChannel: DatagramChannel? = null

    private lateinit var repository: ServerRepository
    private lateinit var settingsDataStore: SettingsDataStore

    private val txBytesCounter = AtomicLong(0L)
    private val rxBytesCounter = AtomicLong(0L)
    private val txPacketsCounter = AtomicLong(0L)
    private val rxPacketsCounter = AtomicLong(0L)

    override fun onCreate() {
        super.onCreate()
        val db = VpnDatabase.getInstance(this)
        settingsDataStore = SettingsDataStore(this)
        repository = ServerRepository(db.serverProfileDao(), KeyVault(), settingsDataStore)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            TunnelStateHolder.log("VPN", "Остановка туннеля по запросу пользователя")
            teardownTunnel(TunnelStatus.Disconnected)
            stopSelf()
            return START_NOT_STICKY
        }

        val serverId = intent?.getLongExtra(EXTRA_SERVER_ID, -1L) ?: -1L
        val startedBySystemAlwaysOn = intent == null || intent.action == null

        promoteToForeground(
            title = getString(R.string.vpn_notification_title_connecting),
            text = "Инициализация защищённого туннеля…"
        )

        tunnelJob?.cancel()
        tunnelJob = serviceScope.launch {
            val profile = if (serverId > 0) {
                repository.getById(serverId)
            } else {
                repository.getSelectedOrFirst()
            }

            if (profile == null) {
                val msg = getString(R.string.error_no_server_selected)
                TunnelStateHolder.log("VPN", msg, isError = true)
                teardownTunnel(
                    if (startedBySystemAlwaysOn) TunnelStatus.Disconnected
                    else TunnelStatus.Error(msg)
                )
                stopSelf()
                return@launch
            }

            runVpnTunnel(profile)
        }

        return START_STICKY
    }

    private suspend fun runVpnTunnel(profile: ServerProfile) {
        val endpointLabel = "${profile.endpointHost}:${profile.endpointPort}"
        TunnelStateHolder.resetMetrics()
        txBytesCounter.set(0L)
        rxBytesCounter.set(0L)
        txPacketsCounter.set(0L)
        rxPacketsCounter.set(0L)

        TunnelStateHolder.updateStatus(
            TunnelStatus.Connecting(
                serverId = profile.id,
                serverName = profile.name,
                endpoint = endpointLabel,
                cipherMode = profile.cipherMode
            )
        )
        TunnelStateHolder.log(
            "HANDSHAKE",
            "Запуск ${profile.cipherMode.protocolBadge} -> $endpointLabel"
        )
        promoteToForeground(
            title = getString(R.string.vpn_notification_title_connecting),
            text = "${profile.name} ($endpointLabel)"
        )

        try {
            val localPriv = Curve25519.decodeBase64(profile.privateKeyBase64)
            val localPub = Curve25519.publicKeyFromPrivate(localPriv)
            val peerPub = Curve25519.decodeBase64(profile.peerPublicKeyBase64)
            val psk = profile.presharedKeyBase64.takeIf { it.isNotBlank() }?.let {
                Curve25519.decodeBase64(it)
            }

            val remoteAddr = InetAddress.getByName(profile.endpointHost)
            val remoteSocketAddr = InetSocketAddress(remoteAddr, profile.endpointPort)

            val channel = DatagramChannel.open().also { ch ->
                ch.configureBlocking(true)
                val protected = protect(ch.socket())
                if (!protected) {
                    throw IllegalStateException("Не удалось защитить UDP-сокет через VpnService.protect()")
                }
                ch.connect(remoteSocketAddr)
                ch.socket().soTimeout = 2500
            }
            udpChannel = channel

            val session = WireGuardSession(
                localPrivateKey = localPriv,
                localPublicKey = localPub,
                peerPublicKey = peerPub,
                presharedKey = psk,
                cipherMode = profile.cipherMode
            )

            // Perform real WireGuard Noise_IKpsk2 handshake with server
            val handshakeStartMs = System.currentTimeMillis()
            var established = false
            val recvBuf = ByteArray(2048)

            for (attempt in 1..4) {
                if (!serviceScope.isActive) break
                val initiation = session.createHandshakeInitiation()
                channel.write(ByteBuffer.wrap(initiation))
                TunnelStateHolder.log(
                    "Noise_IKpsk2",
                    "Отправлен Handshake Initiation (148 байт), попытка $attempt/4"
                )

                val waitDeadline = System.currentTimeMillis() + 2400L
                while (System.currentTimeMillis() < waitDeadline && serviceScope.isActive) {
                    val readCount = runCatching {
                        val bb = ByteBuffer.wrap(recvBuf)
                        channel.read(bb)
                    }.getOrDefault(-1)

                    if (readCount > 0) {
                        val pkt = recvBuf.copyOfRange(0, readCount)
                        if (session.handleIncomingControlPacket(pkt)) {
                            if (session.isEstablished) {
                                established = true
                                break
                            } else {
                                // Cookie reply received; retransmit immediately with MAC2
                                break
                            }
                        }
                    } else {
                        break
                    }
                }
                if (established) break
            }

            if (!established) {
                val errMsg = "Сервер $endpointLabel не ответил на рукопожатие WireGuard. " +
                    "Убедитесь, что UDP-порт ${profile.endpointPort} открыт и ключи совпадают."
                TunnelStateHolder.log("HANDSHAKE", errMsg, isError = true)
                teardownTunnel(TunnelStatus.Error(errMsg, profile.name))
                stopSelf()
                return
            }

            val rttMs = (System.currentTimeMillis() - handshakeStartMs).toInt().coerceAtLeast(1)
            repository.updateLatency(profile.id, rttMs)
            TunnelStateHolder.log(
                "CRYPTO",
                "Рукопожатие завершено за ${rttMs} мс. Активен шифр: ${profile.cipherMode.displayName}"
            )

            // Establish Android OS TUN interface only after cryptographic handshake succeeds
            val pfd = buildTunInterface(profile)
                ?: throw IllegalStateException("VpnService.Builder.establish() вернул null. Требуется системное разрешение VPN.")
            tunInterface = pfd

            val connectedAt = System.currentTimeMillis()
            TunnelStateHolder.updateStatus(
                TunnelStatus.Connected(
                    serverId = profile.id,
                    serverName = profile.name,
                    countryCode = profile.countryCode,
                    city = profile.city,
                    endpoint = endpointLabel,
                    cipherMode = profile.cipherMode,
                    connectedSinceMs = connectedAt,
                    handshakeLatencyMs = rttMs
                )
            )
            promoteToForeground(
                title = getString(R.string.vpn_notification_title_connected),
                text = "${profile.name} • ${profile.cipherMode.protocolBadge}"
            )

            val tunIn = FileInputStream(pfd.fileDescriptor)
            val tunOut = FileOutputStream(pfd.fileDescriptor)
            channel.socket().soTimeout = 0

            // Outbound loop: TUN -> Encrypt (ChaCha20-Poly1305 or AES-256-GCM) -> UDP
            val outboundJob = serviceScope.launch {
                val packetBuf = ByteArray(32768)
                while (isActive && session.isEstablished) {
                    val len = tunIn.read(packetBuf)
                    if (len > 0) {
                        val ipPacket = packetBuf.copyOfRange(0, len)
                        val encrypted = session.encryptTransportPacket(ipPacket)
                        channel.write(ByteBuffer.wrap(encrypted))
                        txBytesCounter.addAndGet(len.toLong())
                        txPacketsCounter.incrementAndGet()
                    } else if (len < 0) {
                        break
                    }
                }
            }

            // Inbound loop: UDP -> Decrypt -> TUN
            val inboundJob = serviceScope.launch {
                val udpBuf = ByteBuffer.allocate(32768)
                while (isActive && session.isEstablished) {
                    udpBuf.clear()
                    val len = channel.read(udpBuf)
                    if (len > 0) {
                        val raw = ByteArray(len)
                        udpBuf.flip()
                        udpBuf.get(raw)
                        if (raw[0] == 4.toByte()) {
                            val plainIp = session.decryptTransportPacket(raw)
                            if (plainIp != null && plainIp.isNotEmpty()) {
                                tunOut.write(plainIp)
                                rxBytesCounter.addAndGet(plainIp.size.toLong())
                                rxPacketsCounter.incrementAndGet()
                            }
                        } else {
                            session.handleIncomingControlPacket(raw)
                        }
                    }
                }
            }

            // Keepalive & 1-second telemetry loop
            val keepaliveIntervalMs = (profile.persistentKeepaliveSec.coerceAtLeast(15)) * 1000L
            var lastKeepaliveAt = System.currentTimeMillis()
            var prevTx = 0L
            var prevRx = 0L

            while (serviceScope.isActive && session.isEstablished && outboundJob.isActive && inboundJob.isActive) {
                delay(1000L)
                val now = System.currentTimeMillis()
                val curTx = txBytesCounter.get()
                val curRx = rxBytesCounter.get()
                val txRate = (curTx - prevTx).coerceAtLeast(0L)
                val rxRate = (curRx - prevRx).coerceAtLeast(0L)
                prevTx = curTx
                prevRx = curRx

                TunnelStateHolder.updateMetrics(
                    TunnelMetrics(
                        txBytes = curTx,
                        rxBytes = curRx,
                        txPackets = txPacketsCounter.get(),
                        rxPackets = rxPacketsCounter.get(),
                        txBytesPerSec = txRate,
                        rxBytesPerSec = rxRate,
                        lastHandshakeMs = connectedAt
                    )
                )

                if (now - lastKeepaliveAt >= keepaliveIntervalMs) {
                    runCatching {
                        val keepalive = session.encryptTransportPacket(ByteArray(0))
                        channel.write(ByteBuffer.wrap(keepalive))
                        lastKeepaliveAt = now
                    }
                }
            }

            outboundJob.cancel()
            inboundJob.cancel()
        } catch (e: Exception) {
            if (serviceScope.isActive) {
                val msg = e.message ?: "Ошибка VPN-туннеля"
                TunnelStateHolder.log("ERROR", msg, isError = true)
                teardownTunnel(TunnelStatus.Error(msg, profile.name))
                stopSelf()
            }
        }
    }

    private suspend fun buildTunInterface(profile: ServerProfile): ParcelFileDescriptor? {
        val settings = settingsDataStore.getSnapshot()
        val builder = Builder()
            .setSession("CipherGuard • ${profile.name}")
            .setMtu(profile.mtu)

        // Parse local interface addresses (CIDR)
        profile.interfaceAddresses.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { cidr ->
                parseCidr(cidr)?.let { (ip, prefix) ->
                    builder.addAddress(ip, prefix)
                }
            }

        // Parse routed networks (e.g., 0.0.0.0/0, ::/0 for full IP masking)
        profile.allowedIps.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { cidr ->
                parseCidr(cidr)?.let { (ip, prefix) ->
                    builder.addRoute(ip, prefix)
                }
            }

        // Configure encrypted tunnel DNS to prevent DNS leaks
        val dnsList = if (settings.useCustomDns && settings.customDns.isNotBlank()) {
            settings.customDns
        } else {
            profile.dnsServers
        }
        dnsList.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { dns ->
                runCatching { builder.addDnsServer(dns) }
            }

        // Apply per-app split tunneling exclusions
        settings.disallowedPackages.forEach { pkg ->
            try {
                packageManager.getPackageInfo(pkg, 0)
                builder.addDisallowedApplication(pkg)
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        TunnelStateHolder.log(
            "TUN",
            "TUN-интерфейс создан: MTU=${profile.mtu}, IP=${profile.interfaceAddresses}, Маршрут=${profile.allowedIps}"
        )
        return builder.establish()
    }

    private fun parseCidr(cidr: String): Pair<String, Int>? {
        val parts = cidr.split("/")
        if (parts.isEmpty()) return null
        val ip = parts[0].trim()
        val defaultPrefix = if (ip.contains(":")) 128 else 32
        val prefix = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: defaultPrefix
        return ip to prefix
    }

    private fun teardownTunnel(finalStatus: TunnelStatus) {
        tunnelJob?.cancel()
        tunnelJob = null
        runCatching { udpChannel?.close() }
        udpChannel = null
        runCatching { tunInterface?.close() }
        tunInterface = null
        TunnelStateHolder.updateStatus(finalStatus)
        TunnelStateHolder.resetMetrics()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun promoteToForeground(title: String, text: String) {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CipherGuardVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(
            this,
            getString(R.string.vpn_notification_channel_id)
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(
                0,
                getString(R.string.vpn_notification_action_disconnect),
                disconnectIntent
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                getString(R.string.vpn_notification_channel_id),
                getString(R.string.vpn_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.vpn_notification_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    override fun onRevoke() {
        TunnelStateHolder.log("SYSTEM", "Системное разрешение VPN отозвано системой", isError = true)
        teardownTunnel(TunnelStatus.Disconnected)
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        teardownTunnel(TunnelStatus.Disconnected)
        serviceScope.cancel()
        super.onDestroy()
    }
}

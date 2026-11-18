package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ServerProfile
import com.example.data.repository.PublicIpInfo
import com.example.ui.theme.AmberHandshake
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.vpn.TunnelMetrics
import com.example.vpn.TunnelStatus
import java.util.Locale

@Composable
fun TunnelPowerOrb(
    status: TunnelStatus,
    hasServer: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = status is TunnelStatus.Connected
    val isBusy = status is TunnelStatus.Connecting || status is TunnelStatus.Preparing
    val isError = status is TunnelStatus.Error

    val targetColor by animateColorAsState(
        targetValue = when {
            isConnected -> EmeraldShield
            isBusy -> CyanPulse
            isError -> CrimsonAlert
            hasServer -> CyanPulse.copy(alpha = 0.78f)
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(400),
        label = "orb_color"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    Box(
        modifier = modifier.size(188.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseRadius = size.minDimension / 2f
            val outerRingRadius = if (isConnected || isBusy) baseRadius * 0.94f * pulseScale else baseRadius * 0.9f

            // Ambient radial halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        targetColor.copy(alpha = if (isConnected) 0.24f else 0.12f),
                        Color.Transparent
                    )
                ),
                radius = outerRingRadius
            )

            // Static track ring
            drawCircle(
                color = targetColor.copy(alpha = 0.22f),
                radius = baseRadius * 0.84f,
                style = Stroke(width = 3.dp.toPx())
            )

            if (isBusy) {
                drawArc(
                    color = targetColor,
                    startAngle = sweepAngle,
                    sweepAngle = 110f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            } else if (isConnected) {
                drawCircle(
                    color = targetColor,
                    radius = baseRadius * 0.84f,
                    style = Stroke(width = 3.5.dp.toPx())
                )
            }
        }

        Surface(
            modifier = Modifier
                .size(134.dp)
                .clip(CircleShape)
                .minimumInteractiveComponentSize()
                .testTag("vpn_power_button")
                .clickable(onClick = onClick),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.5.dp, targetColor.copy(alpha = 0.65f)),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                targetColor.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = when {
                        isConnected -> Icons.Rounded.VerifiedUser
                        isBusy -> Icons.Rounded.Shield
                        else -> Icons.Rounded.PowerSettingsNew
                    },
                    contentDescription = if (isConnected) "Отключить VPN" else "Подключить VPN",
                    tint = targetColor,
                    modifier = Modifier.size(42.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when {
                        isConnected -> "ЗАЩИЩЕНО"
                        isBusy -> "СОЕДИНЕНИЕ"
                        else -> "ПОДКЛЮЧИТЬ"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = targetColor
                )
            }
        }
    }
}

@Composable
fun IpMaskingCard(
    tunnelStatus: TunnelStatus,
    preVpnIp: PublicIpInfo?,
    currentIp: PublicIpInfo?,
    isChecking: Boolean,
    ipError: String?,
    onRefreshIp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = tunnelStatus is TunnelStatus.Connected
    val accent = if (isConnected) EmeraldShield else AmberHandshake
    val ipChanged = isConnected &&
        preVpnIp != null &&
        currentIp != null &&
        preVpnIp.ip != currentIp.ip

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(
            1.dp,
            if (isConnected) EmeraldShield.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.15f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isConnected) Icons.Rounded.Lock else Icons.Outlined.Public,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = if (isConnected) "Внешний IP через VPN-туннель" else "Текущий внешний IP (без VPN)",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when {
                                ipChanged -> "IP-адрес и геолокация успешно изменены"
                                isConnected -> "Маршрутизация трафика через зашифрованный канал"
                                else -> "Реальный IP виден внешним сервисам"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onRefreshIp,
                    enabled = !isChecking,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_ip_button")
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = CyanPulse
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Обновить внешний IP",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (currentIp != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentIp.ip,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${currentIp.country} (${currentIp.countryCode}) • ${currentIp.city}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${currentIp.isp} ${if (currentIp.asn != "--") "• ${currentIp.asn}" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accent.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = currentIp.countryCode,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = accent,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                if (ipChanged) {
                    preVpnIp?.let { originalIp ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = EmeraldShield.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${originalIp.ip} (${originalIp.countryCode})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = EmeraldShield,
                                modifier = Modifier.size(14.dp)
                            )
                                Text(
                                    text = "${currentIp.ip} (${currentIp.countryCode})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldShield,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = ipError ?: if (isChecking) "Определение IP-адреса и геолокации…" else "Нажмите обновить для проверки IP",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ipError != null) CrimsonAlert else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TelemetryGrid(
    status: TunnelStatus,
    metrics: TunnelMetrics,
    elapsedSeconds: Long,
    modifier: Modifier = Modifier
) {
    val connected = status as? TunnelStatus.Connected
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TelemetryTile(
            label = "ВХОД (RX)",
            value = formatBytesRate(metrics.rxBytesPerSec),
            subValue = "Всего: ${formatBytes(metrics.rxBytes)}",
            accent = CyanPulse,
            modifier = Modifier.weight(1f)
        )
        TelemetryTile(
            label = "ИСХОД (TX)",
            value = formatBytesRate(metrics.txBytesPerSec),
            subValue = "Всего: ${formatBytes(metrics.txBytes)}",
            accent = EmeraldShield,
            modifier = Modifier.weight(1f)
        )
        TelemetryTile(
            label = "СЕССИЯ",
            value = if (connected != null) formatDuration(elapsedSeconds) else "00:00",
            subValue = connected?.let { "${it.handshakeLatencyMs} мс" } ?: "Ожидание",
            accent = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TelemetryTile(
    label: String,
    value: String,
    subValue: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SelectedServerBanner(
    server: ServerProfile?,
    onOpenServers: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .minimumInteractiveComponentSize()
            .testTag("selected_server_card")
            .clickable(onClick = onOpenServers),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CyanPulse.copy(alpha = 0.14f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = server?.countryCode ?: "WG",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = CyanPulse
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server?.name ?: "Нет выбранного сервера",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = server?.let {
                            "${it.endpointHost}:${it.endpointPort} • ${it.cipherMode.protocolBadge}"
                        } ?: "Импортируйте .conf или добавьте сервер WireGuard",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "Выбрать сервер",
                tint = CyanPulse,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes Б"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f КБ", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.2f МБ", mb)
    return String.format(Locale.US, "%.2f ГБ", mb / 1024.0)
}

fun formatBytesRate(bytesPerSec: Long): String {
    if (bytesPerSec < 1024) return "$bytesPerSec Б/с"
    val kb = bytesPerSec / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f КБ/с", kb)
    return String.format(Locale.US, "%.2f МБ/с", kb / 1024.0)
}

fun formatDuration(totalSeconds: Long): String {
    val hrs = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

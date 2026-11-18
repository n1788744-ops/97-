package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ServerProfile
import com.example.data.repository.PublicIpInfo
import com.example.ui.components.IpMaskingCard
import com.example.ui.components.SelectedServerBanner
import com.example.ui.components.TelemetryGrid
import com.example.ui.components.TunnelPowerOrb
import com.example.ui.theme.AmberHandshake
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.vpn.TunnelMetrics
import com.example.vpn.TunnelStatus
import kotlinx.coroutines.delay

@Composable
fun TunnelScreen(
    tunnelStatus: TunnelStatus,
    tunnelMetrics: TunnelMetrics,
    selectedServer: ServerProfile?,
    preVpnIp: PublicIpInfo?,
    currentIp: PublicIpInfo?,
    isCheckingIp: Boolean,
    ipError: String?,
    hasVpnPermission: Boolean,
    onToggleConnect: () -> Unit,
    onRequestVpnPermission: () -> Unit,
    onRefreshIp: () -> Unit,
    onNavigateToServers: () -> Unit,
    onQuickImportConf: () -> Unit,
    modifier: Modifier = Modifier
) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(tunnelStatus) {
        if (tunnelStatus is TunnelStatus.Connected) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - tunnelStatus.connectedSinceMs) / 1000L)
                    .coerceAtLeast(0L)
                delay(1000L)
            }
        } else {
            elapsedSeconds = 0L
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Atmospheric Hero Header with Generated Globe Illustration
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_vpn_hero_globe_1794989391751),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.32f)
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.45f)
                                )
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CIPHERGUARD VPN",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanPulse
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (tunnelStatus) {
                                is TunnelStatus.Connected -> "Туннель зашифрован"
                                is TunnelStatus.Connecting -> "Рукопожатие…"
                                is TunnelStatus.Preparing -> "Запрос доступа VPN…"
                                is TunnelStatus.Error -> "Ошибка подключения"
                                TunnelStatus.Disconnected -> "Готов к защите"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = selectedServer?.cipherMode?.protocolBadge
                                ?: "WireGuard • AES-256-GCM",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFamily
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // System VPN Permission Indicator Chip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (hasVpnPermission) {
                            EmeraldShield.copy(alpha = 0.14f)
                        } else {
                            AmberHandshake.copy(alpha = 0.16f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (hasVpnPermission) EmeraldShield.copy(alpha = 0.45f)
                            else AmberHandshake.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (hasVpnPermission) Icons.Outlined.CheckCircle else Icons.Outlined.AdminPanelSettings,
                                contentDescription = null,
                                tint = if (hasVpnPermission) EmeraldShield else AmberHandshake,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (hasVpnPermission) "VPN OK" else "Нужен доступ",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasVpnPermission) EmeraldShield else AmberHandshake
                            )
                        }
                    }
                }
            }

            if (!hasVpnPermission) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = AmberHandshake.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AmberHandshake.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Системное разрешение VPN",
                                style = MaterialTheme.typography.titleSmall,
                                color = AmberHandshake
                            )
                            Text(
                                text = "Разрешите Android маршрутизировать сетевой трафик через защищённый TUN-интерфейс.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = onRequestVpnPermission,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("grant_vpn_permission_banner_btn")
                        ) {
                            Text("Разрешить", color = AmberHandshake, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Central Animated VPN Shield Power Control
            TunnelPowerOrb(
                status = tunnelStatus,
                hasServer = selectedServer != null,
                onClick = onToggleConnect
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (tunnelStatus is TunnelStatus.Error) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = CrimsonAlert.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = CrimsonAlert
                        )
                        Text(
                            text = tunnelStatus.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                Text(
                    text = when (tunnelStatus) {
                        is TunnelStatus.Connected ->
                            "Шифр: ${tunnelStatus.cipherMode.displayName} • ${tunnelStatus.endpoint}"
                        is TunnelStatus.Connecting ->
                            "Обмен ключами X25519 с ${tunnelStatus.endpoint}…"
                        is TunnelStatus.Preparing ->
                            "Подтвердите системный запрос Android VPN"
                        TunnelStatus.Disconnected ->
                            if (selectedServer != null) "Нажмите, чтобы зашифровать весь интернет-трафик"
                            else "Добавьте конфигурацию WireGuard для запуска туннеля"
                        is TunnelStatus.Error -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Public IP & Geolocation Masking Card
            IpMaskingCard(
                tunnelStatus = tunnelStatus,
                preVpnIp = preVpnIp,
                currentIp = currentIp,
                isChecking = isCheckingIp,
                ipError = ipError,
                onRefreshIp = onRefreshIp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Live Cipher & Traffic Telemetry
            TelemetryGrid(
                status = tunnelStatus,
                metrics = tunnelMetrics,
                elapsedSeconds = elapsedSeconds
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedServer != null) {
                SelectedServerBanner(
                    server = selectedServer,
                    onOpenServers = onNavigateToServers
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Подключите свой WireGuard-сервер",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Импортируйте реальный .conf файл WireGuard или создайте профиль с ключами Curve25519 для реальной смены IP и шифрования AES-256-GCM.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onQuickImportConf,
                                colors = ButtonDefaults.buttonColors(containerColor = CyanPulse),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("home_import_conf_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FileOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Импорт .conf", color = Color(0xFF001F26))
                            }
                            OutlinedButton(
                                onClick = onNavigateToServers,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("home_add_server_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.VpnKey,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Настроить")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.crypto.CryptoAuditReport
import com.example.data.local.VpnSettings
import com.example.data.repository.InstalledAppItem
import com.example.ui.theme.AmberHandshake
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.vpn.TunnelLogEntry

@Composable
fun SecurityScreen(
    hasVpnPermission: Boolean,
    hasNotificationPermission: Boolean,
    settings: VpnSettings,
    cryptoAudit: CryptoAuditReport?,
    installedApps: List<InstalledAppItem>,
    logs: List<TunnelLogEntry>,
    onRequestVpnPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onUpdateCustomDns: (Boolean, String) -> Unit,
    onLoadInstalledApps: () -> Unit,
    onToggleDisallowedPackage: (String) -> Unit,
    onRunCryptoAudit: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var dnsInput by remember(settings.customDns) { mutableStateOf(settings.customDns) }
    var showSplitTunnelDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Системная защита и криптография",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 1. System VPN & Network Permissions Card
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null, tint = CyanPulse)
                        Text(
                            text = "Системные разрешения VPN",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    PermissionStatusRow(
                        title = "Android VpnService (BIND_VPN_SERVICE)",
                        subtitle = if (hasVpnPermission) "Доступ к системному TUN-интерфейсу разрешён"
                        else "Требуется подтверждение для перехвата и шифрования трафика",
                        granted = hasVpnPermission,
                        actionLabel = "Разрешить",
                        testTag = "security_grant_vpn_btn",
                        onAction = onRequestVpnPermission
                    )

                    PermissionStatusRow(
                        title = "Уведомления туннеля (POST_NOTIFICATIONS)",
                        subtitle = if (hasNotificationPermission) "Постоянный индикатор шифрования активен"
                        else "Необходимо для отображения статуса Foreground VPN",
                        granted = hasNotificationPermission,
                        actionLabel = "Включить",
                        testTag = "security_grant_notif_btn",
                        onAction = onRequestNotificationPermission
                    )

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("open_android_vpn_settings_btn")
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Always-On VPN и Kill Switch в настройках Android")
                    }
                }
            }

            // 2. DNS Leak Protection & Split Tunneling
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Dns, contentDescription = null, tint = EmeraldShield)
                            Column {
                                Text("Защита от утечек DNS", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Принудительный DNS внутри VPN-туннеля",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = settings.useCustomDns,
                            onCheckedChange = { checked -> onUpdateCustomDns(checked, dnsInput) },
                            modifier = Modifier.testTag("custom_dns_switch")
                        )
                    }

                    if (settings.useCustomDns) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = dnsInput,
                                onValueChange = { dnsInput = it },
                                label = { Text("DNS-серверы") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { onUpdateCustomDns(true, dnsInput) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanPulse),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("OK", color = Color(0xFF001F26))
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onLoadInstalledApps()
                            showSplitTunnelDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("split_tunneling_btn")
                    ) {
                        Icon(Icons.Outlined.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Раздельное туннелирование (исключено: ${settings.disallowedPackages.size})")
                    }
                }
            }

            // 3. Real Cryptographic Audit Card
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Security, contentDescription = null, tint = EmeraldShield)
                            Text("Проверка криптографии", style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(
                            onClick = onRunCryptoAudit,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("run_crypto_audit_btn")
                        ) {
                            Text("Проверить", color = CyanPulse)
                        }
                    }

                    cryptoAudit?.items?.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.passed) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (item.passed) EmeraldShield else CrimsonAlert,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = item.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Real-Time Protocol & Handshake Log Console
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Terminal, contentDescription = null, tint = CyanPulse)
                            Text("Журнал WireGuard / AES-256", style = MaterialTheme.typography.titleMedium)
                        }
                        if (logs.isNotEmpty()) {
                            TextButton(onClick = onClearLogs) {
                                Text("Очистить")
                            }
                        }
                    }

                    if (logs.isEmpty()) {
                        Text(
                            text = "События рукопожатия Noise_IKpsk2 и TUN-интерфейса появятся здесь после запуска туннеля.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        logs.take(15).forEach { entry ->
                            Text(
                                text = "[${entry.timestamp}] [${entry.tag}] ${entry.message}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.isError) CrimsonAlert else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showSplitTunnelDialog) {
        AlertDialog(
            onDismissRequest = { showSplitTunnelDialog = false },
            title = { Text("Исключить приложения из VPN") },
            text = {
                if (installedApps.isEmpty()) {
                    Text("Загрузка списка приложений…")
                } else {
                    LazyColumn(modifier = Modifier.height(340.dp)) {
                        items(installedApps, key = { it.packageName }) { app ->
                            val excluded = app.packageName in settings.disallowedPackages
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleDisallowedPackage(app.packageName) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = excluded,
                                    onCheckedChange = { onToggleDisallowedPackage(app.packageName) }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSplitTunnelDialog = false }) {
                    Text("Готово")
                }
            }
        )
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    testTag: String,
    onAction: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.NotificationsActive,
                    contentDescription = null,
                    tint = if (granted) EmeraldShield else AmberHandshake
                )
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!granted) {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag(testTag)
                ) {
                    Text(actionLabel, color = AmberHandshake, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

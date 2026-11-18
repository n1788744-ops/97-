package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.crypto.Curve25519
import com.example.data.model.ServerProfile
import com.example.data.model.VpnCipherMode
import com.example.data.parser.WireGuardConfigParser
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.vpn.TunnelStatus

@Composable
fun ServersScreen(
    servers: List<ServerProfile>,
    selectedServerId: Long?,
    tunnelStatus: TunnelStatus,
    onSelectServer: (Long) -> Unit,
    onConnectServer: (Long) -> Unit,
    onDeleteServer: (Long) -> Unit,
    onPingServer: (ServerProfile) -> Unit,
    onSaveServer: (ServerProfile, () -> Unit) -> Unit,
    onImportFileClick: () -> Unit,
    onImportPaste: (String, String, VpnCipherMode, () -> Unit) -> Unit,
    onGenerateKeyPair: () -> Curve25519.KeyPair,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPasteDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<ServerProfile?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Серверы WireGuard & AES-256",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Приватные ключи зашифрованы через AndroidKeyStore AES-256-GCM.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onImportFileClick,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPulse),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("import_conf_file_btn")
                ) {
                    Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(".conf", color = Color(0xFF001F26))
                }
                OutlinedButton(
                    onClick = { showPasteDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("paste_conf_btn")
                ) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Вставить")
                }
                OutlinedButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("add_manual_server_btn")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Создать")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (servers.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileOpen,
                            contentDescription = null,
                            tint = CyanPulse,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Список серверов пуст",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Импортируйте конфигурацию .conf от вашего WireGuard-сервера или создайте новый профиль с генерацией X25519-ключей.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(servers, key = { it.id }) { server ->
                        val isSelected = server.id == selectedServerId
                        val isConnectedHere = (tunnelStatus as? TunnelStatus.Connected)?.serverId == server.id

                        ServerProfileCard(
                            server = server,
                            isSelected = isSelected,
                            isConnected = isConnectedHere,
                            onSelect = { onSelectServer(server.id) },
                            onConnect = { onConnectServer(server.id) },
                            onPing = { onPingServer(server) },
                            onEdit = { editingProfile = server },
                            onCopyConf = {
                                clipboard.setText(AnnotatedString(WireGuardConfigParser.serialize(server)))
                                onShowMessage("Конфигурация .conf скопирована в буфер")
                            },
                            onDelete = { onDeleteServer(server.id) }
                        )
                    }
                }
            }
        }
    }

    if (showPasteDialog) {
        PasteConfigDialog(
            onDismiss = { showPasteDialog = false },
            onImport = { raw, name, mode ->
                onImportPaste(raw, name, mode) { showPasteDialog = false }
            }
        )
    }

    if (showCreateDialog) {
        ServerEditorDialog(
            initialProfile = null,
            onGenerateKeyPair = onGenerateKeyPair,
            onDismiss = { showCreateDialog = false },
            onSave = { profile ->
                onSaveServer(profile) { showCreateDialog = false }
            }
        )
    }

    editingProfile?.let { target ->
        ServerEditorDialog(
            initialProfile = target,
            onGenerateKeyPair = onGenerateKeyPair,
            onDismiss = { editingProfile = null },
            onSave = { updated ->
                onSaveServer(updated) { editingProfile = null }
            }
        )
    }
}

@Composable
private fun ServerProfileCard(
    server: ServerProfile,
    isSelected: Boolean,
    isConnected: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
    onPing: () -> Unit,
    onEdit: () -> Unit,
    onCopyConf: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = when {
        isConnected -> EmeraldShield
        isSelected -> CyanPulse
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("server_card_${server.id}")
            .clickable(onClick = onSelect),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(if (isSelected || isConnected) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = (if (isConnected) EmeraldShield else CyanPulse).copy(alpha = 0.14f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = server.countryCode,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isConnected) EmeraldShield else CyanPulse
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = server.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = "Выбран",
                                    tint = if (isConnected) EmeraldShield else CyanPulse,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = "${server.city} • ${server.endpointHost}:${server.endpointPort}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                server.lastLatencyMs?.let { ms ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = EmeraldShield.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "$ms мс",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldShield,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${server.cipherMode.displayName} • MTU ${server.mtu} • AllowedIPs: ${server.allowedIps}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onConnect,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("connect_server_${server.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = if (isConnected) EmeraldShield else CyanPulse,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = if (isConnected) "Активен" else "Подключиться",
                        color = if (isConnected) EmeraldShield else CyanPulse,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPing,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NetworkCheck,
                            contentDescription = "Проверить отклик",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onCopyConf,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Копировать .conf",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Редактировать",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Удалить",
                            tint = CrimsonAlert.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.crypto.Curve25519
import com.example.data.model.ServerProfile
import com.example.data.model.VpnCipherMode
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun PasteConfigDialog(
    onDismiss: () -> Unit,
    onImport: (rawText: String, name: String, cipherMode: VpnCipherMode) -> Unit
) {
    var name by remember { mutableStateOf("Мой WireGuard Сервер") }
    var rawText by remember { mutableStateOf("") }
    var cipherMode by remember { mutableStateOf(VpnCipherMode.WIREGUARD_AES_256_GCM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Импорт конфигурации WireGuard") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название профиля") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paste_name_input")
                )

                Text(
                    text = "Протокол и шифр туннеля",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    VpnCipherMode.entries.forEach { mode ->
                        FilterChip(
                            selected = cipherMode == mode,
                            onClick = { cipherMode = mode },
                            label = { Text(mode.displayName) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    label = { Text("Содержимое .conf ([Interface] / [Peer])") },
                    placeholder = {
                        Text(
                            "[Interface]\nPrivateKey = ...\nAddress = 10.66.66.2/32\nDNS = 1.1.1.1\n\n[Peer]\nPublicKey = ...\nEndpoint = vpn.example.com:51820\nAllowedIPs = 0.0.0.0/0, ::/0",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
                        )
                    },
                    minLines = 6,
                    maxLines = 10,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paste_config_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(rawText, name, cipherMode) },
                enabled = rawText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyanPulse),
                modifier = Modifier.testTag("paste_confirm_btn")
            ) {
                Text("Импортировать", color = Color(0xFF001F26))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun ServerEditorDialog(
    initialProfile: ServerProfile?,
    onGenerateKeyPair: () -> Curve25519.KeyPair,
    onDismiss: () -> Unit,
    onSave: (ServerProfile) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val initialKeys = remember {
        if (initialProfile != null) {
            Curve25519.KeyPair(
                privateKey = Curve25519.decodeBase64(initialProfile.privateKeyBase64),
                publicKey = Curve25519.decodeBase64(initialProfile.publicKeyBase64)
            )
        } else {
            onGenerateKeyPair()
        }
    }

    var name by remember { mutableStateOf(initialProfile?.name ?: "") }
    var countryCode by remember { mutableStateOf(initialProfile?.countryCode ?: "NL") }
    var city by remember { mutableStateOf(initialProfile?.city ?: "") }
    var endpointHost by remember { mutableStateOf(initialProfile?.endpointHost ?: "") }
    var endpointPort by remember { mutableStateOf((initialProfile?.endpointPort ?: 51820).toString()) }
    var privateKey by remember { mutableStateOf(initialProfile?.privateKeyBase64 ?: initialKeys.privateKeyBase64) }
    var publicKey by remember { mutableStateOf(initialProfile?.publicKeyBase64 ?: initialKeys.publicKeyBase64) }
    var peerPublicKey by remember { mutableStateOf(initialProfile?.peerPublicKeyBase64 ?: "") }
    var presharedKey by remember { mutableStateOf(initialProfile?.presharedKeyBase64 ?: "") }
    var interfaceAddr by remember { mutableStateOf(initialProfile?.interfaceAddresses ?: "10.66.66.2/32") }
    var dnsServers by remember { mutableStateOf(initialProfile?.dnsServers ?: "1.1.1.1, 9.9.9.9") }
    var allowedIps by remember { mutableStateOf(initialProfile?.allowedIps ?: "0.0.0.0/0, ::/0") }
    var mtu by remember { mutableStateOf((initialProfile?.mtu ?: 1420).toString()) }
    var cipherMode by remember { mutableStateOf(initialProfile?.cipherMode ?: VpnCipherMode.WIREGUARD_AES_256_GCM) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialProfile == null) "Новый сервер WireGuard" else "Редактировать сервер")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название (например, Амстердам VPS)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_name_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = countryCode,
                        onValueChange = { countryCode = it.uppercase().take(2) },
                        label = { Text("Страна") },
                        singleLine = true,
                        modifier = Modifier.weight(0.35f)
                    )
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("Город / Локация") },
                        singleLine = true,
                        modifier = Modifier.weight(0.65f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = endpointHost,
                        onValueChange = { endpointHost = it },
                        label = { Text("Хост / IP сервера") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.68f)
                            .testTag("editor_host_input")
                    )
                    OutlinedTextField(
                        value = endpointPort,
                        onValueChange = { endpointPort = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Порт") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(0.32f)
                            .testTag("editor_port_input")
                    )
                }

                Text(
                    text = "Режим шифрования",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    VpnCipherMode.entries.forEach { mode ->
                        FilterChip(
                            selected = cipherMode == mode,
                            onClick = { cipherMode = mode },
                            label = { Text(mode.displayName) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Client Curve25519 Keypair Section
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Ключи клиента (X25519)",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanPulse
                            )
                            TextButton(
                                onClick = {
                                    val pair = onGenerateKeyPair()
                                    privateKey = pair.privateKeyBase64
                                    publicKey = pair.publicKeyBase64
                                },
                                modifier = Modifier.testTag("editor_generate_keys_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Новые ключи")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ваш PublicKey (добавьте на сервер):",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = publicKey,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { clipboard.setText(AnnotatedString(publicKey)) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Скопировать PublicKey",
                                    tint = CyanPulse
                                )
                            }
                        }

                        OutlinedTextField(
                            value = privateKey,
                            onValueChange = {
                                privateKey = it
                                runCatching {
                                    Curve25519.encodeBase64(
                                        Curve25519.publicKeyFromPrivate(Curve25519.decodeBase64(it))
                                    )
                                }.onSuccess { derived -> publicKey = derived }
                            },
                            label = { Text("PrivateKey (Base64)") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(
                    value = peerPublicKey,
                    onValueChange = { peerPublicKey = it },
                    label = { Text("PublicKey сервера [Peer]") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_peer_pub_input")
                )

                OutlinedTextField(
                    value = presharedKey,
                    onValueChange = { presharedKey = it },
                    label = { Text("PresharedKey (необязательно)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = interfaceAddr,
                    onValueChange = { interfaceAddr = it },
                    label = { Text("IP-адрес клиента в туннеле (CIDR)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dnsServers,
                    onValueChange = { dnsServers = it },
                    label = { Text("DNS-серверы") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = allowedIps,
                        onValueChange = { allowedIps = it },
                        label = { Text("AllowedIPs") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.weight(0.7f)
                    )
                    OutlinedTextField(
                        value = mtu,
                        onValueChange = { mtu = it.filter { ch -> ch.isDigit() } },
                        label = { Text("MTU") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.3f)
                    )
                }

                validationError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonAlert
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val portInt = endpointPort.toIntOrNull()
                    val mtuInt = mtu.toIntOrNull()?.coerceIn(1280, 1500) ?: 1420
                    when {
                        name.isBlank() -> validationError = "Введите название профиля"
                        endpointHost.isBlank() -> validationError = "Укажите IP или домен сервера"
                        portInt == null || portInt !in 1..65535 -> validationError = "Некорректный порт (1..65535)"
                        !Curve25519.isValidBase64Key(privateKey) -> validationError = "Некорректный PrivateKey (нужно 32 байта Base64)"
                        !Curve25519.isValidBase64Key(peerPublicKey) -> validationError = "Некорректный PublicKey сервера (нужно 32 байта Base64)"
                        presharedKey.isNotBlank() && !Curve25519.isValidBase64Key(presharedKey) ->
                            validationError = "Некорректный PresharedKey"
                        else -> {
                            onSave(
                                ServerProfile(
                                    id = initialProfile?.id ?: 0L,
                                    name = name.trim(),
                                    countryCode = countryCode.ifBlank { "WG" },
                                    city = city.trim().ifBlank { endpointHost.trim() },
                                    endpointHost = endpointHost.trim(),
                                    endpointPort = portInt,
                                    privateKeyBase64 = privateKey.trim(),
                                    publicKeyBase64 = publicKey.trim(),
                                    peerPublicKeyBase64 = peerPublicKey.trim(),
                                    presharedKeyBase64 = presharedKey.trim(),
                                    interfaceAddresses = interfaceAddr.trim().ifBlank { "10.66.66.2/32" },
                                    dnsServers = dnsServers.trim().ifBlank { "1.1.1.1" },
                                    allowedIps = allowedIps.trim().ifBlank { "0.0.0.0/0, ::/0" },
                                    mtu = mtuInt,
                                    persistentKeepaliveSec = initialProfile?.persistentKeepaliveSec ?: 25,
                                    cipherMode = cipherMode,
                                    lastLatencyMs = initialProfile?.lastLatencyMs,
                                    createdAt = initialProfile?.createdAt ?: System.currentTimeMillis()
                                )
                            )
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPulse),
                modifier = Modifier.testTag("editor_save_btn")
            ) {
                Text("Сохранить", color = Color(0xFF001F26))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

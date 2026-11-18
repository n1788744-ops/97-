package com.example.data.parser

import com.example.crypto.Curve25519
import com.example.data.model.ServerProfile
import com.example.data.model.VpnCipherMode

/**
 * Full WireGuard .conf parser and serializer supporting IPv4/IPv6 endpoints,
 * multi-CIDR Address/AllowedIPs, DNS, MTU, PresharedKey, PersistentKeepalive,
 * and optional CipherGuard metadata headers.
 */
object WireGuardConfigParser {

    fun parse(
        rawConfig: String,
        fallbackName: String = "WireGuard Сервер",
        defaultCipherMode: VpnCipherMode = VpnCipherMode.WIREGUARD_CHACHA20
    ): Result<ServerProfile> {
        return runCatching {
            var section = ""
            var name = fallbackName.trim().ifEmpty { "WireGuard Сервер" }
            var countryCode = ""
            var city = ""
            var cipherMode = defaultCipherMode

            var privateKey = ""
            var address = ""
            var dns = "1.1.1.1, 9.9.9.9"
            var mtu = 1420

            var peerPublicKey = ""
            var presharedKey = ""
            var endpoint = ""
            var allowedIps = "0.0.0.0/0, ::/0"
            var persistentKeepalive = 25

            for (rawLine in rawConfig.lines()) {
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty()) continue

                if (trimmed.startsWith("#") || trimmed.startsWith(";")) {
                    val comment = trimmed.drop(1).trim()
                    when {
                        comment.startsWith("Name:", ignoreCase = true) ->
                            name = comment.substringAfter(":").trim().ifEmpty { name }
                        comment.startsWith("Country:", ignoreCase = true) ->
                            countryCode = comment.substringAfter(":").trim().uppercase().take(2)
                        comment.startsWith("City:", ignoreCase = true) ->
                            city = comment.substringAfter(":").trim()
                        comment.startsWith("Cipher:", ignoreCase = true) -> {
                            val c = comment.substringAfter(":").trim()
                            if (c.contains("AES", ignoreCase = true)) {
                                cipherMode = VpnCipherMode.WIREGUARD_AES_256_GCM
                            }
                        }
                    }
                    continue
                }

                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    section = trimmed.substring(1, trimmed.length - 1).trim().lowercase()
                    continue
                }

                val eqIdx = trimmed.indexOf('=')
                if (eqIdx <= 0) continue
                val key = trimmed.substring(0, eqIdx).trim().lowercase()
                val value = trimmed.substring(eqIdx + 1).trim()

                when (section) {
                    "interface" -> when (key) {
                        "privatekey" -> privateKey = value
                        "address" -> address = if (address.isEmpty()) value else "$address, $value"
                        "dns" -> dns = value
                        "mtu" -> mtu = value.toIntOrNull()?.coerceIn(1280, 1500) ?: 1420
                    }
                    "peer" -> when (key) {
                        "publickey" -> peerPublicKey = value
                        "presharedkey" -> presharedKey = value
                        "endpoint" -> endpoint = value
                        "allowedips" -> allowedIps = if (allowedIps == "0.0.0.0/0, ::/0" && value.isNotEmpty()) value else "$allowedIps, $value"
                        "persistentkeepalive" -> persistentKeepalive = value.toIntOrNull()?.coerceIn(0, 300) ?: 25
                    }
                }
            }

            require(Curve25519.isValidBase64Key(privateKey)) {
                "Некорректный или отсутствующий PrivateKey в [Interface]"
            }
            require(Curve25519.isValidBase64Key(peerPublicKey)) {
                "Некорректный или отсутствующий PublicKey в [Peer]"
            }
            if (presharedKey.isNotBlank()) {
                require(Curve25519.isValidBase64Key(presharedKey)) {
                    "Некорректный PresharedKey в [Peer]"
                }
            }
            require(endpoint.isNotBlank()) {
                "Отсутствует Endpoint (хост:порт) в секции [Peer]"
            }

            val (host, port) = parseEndpoint(endpoint)
            require(host.isNotBlank() && port in 1..65535) {
                "Некорректный формат Endpoint: $endpoint"
            }

            val derivedPublic = Curve25519.encodeBase64(
                Curve25519.publicKeyFromPrivate(Curve25519.decodeBase64(privateKey))
            )

            ServerProfile(
                name = name,
                countryCode = countryCode.ifBlank { "WG" },
                city = city.ifBlank { host },
                endpointHost = host,
                endpointPort = port,
                privateKeyBase64 = privateKey,
                publicKeyBase64 = derivedPublic,
                peerPublicKeyBase64 = peerPublicKey,
                presharedKeyBase64 = presharedKey,
                interfaceAddresses = address.ifBlank { "10.66.66.2/32" },
                dnsServers = dns.ifBlank { "1.1.1.1, 9.9.9.9" },
                allowedIps = allowedIps.ifBlank { "0.0.0.0/0, ::/0" },
                mtu = mtu,
                persistentKeepaliveSec = persistentKeepalive,
                cipherMode = cipherMode
            )
        }
    }

    fun parseEndpoint(endpoint: String): Pair<String, Int> {
        val trimmed = endpoint.trim()
        return if (trimmed.startsWith("[")) {
            val closeBracket = trimmed.indexOf(']')
            require(closeBracket > 1) { "Некорректный IPv6 Endpoint" }
            val host = trimmed.substring(1, closeBracket)
            val portStr = trimmed.substring(closeBracket + 1).removePrefix(":")
            val port = portStr.toIntOrNull() ?: 51820
            host to port
        } else {
            val lastColon = trimmed.lastIndexOf(':')
            require(lastColon > 0) { "Укажите порт после двоеточия (например, host:51820)" }
            val host = trimmed.substring(0, lastColon).trim()
            val port = trimmed.substring(lastColon + 1).trim().toIntOrNull()
                ?: throw IllegalArgumentException("Некорректный порт в Endpoint")
            host to port
        }
    }

    fun serialize(profile: ServerProfile): String = buildString {
        appendLine("# Name: ${profile.name}")
        appendLine("# Country: ${profile.countryCode}")
        appendLine("# City: ${profile.city}")
        appendLine("# Cipher: ${profile.cipherMode.displayName}")
        appendLine("[Interface]")
        appendLine("PrivateKey = ${profile.privateKeyBase64}")
        appendLine("Address = ${profile.interfaceAddresses}")
        if (profile.dnsServers.isNotBlank()) {
            appendLine("DNS = ${profile.dnsServers}")
        }
        appendLine("MTU = ${profile.mtu}")
        appendLine()
        appendLine("[Peer]")
        appendLine("PublicKey = ${profile.peerPublicKeyBase64}")
        if (profile.presharedKeyBase64.isNotBlank()) {
            appendLine("PresharedKey = ${profile.presharedKeyBase64}")
        }
        val formattedHost = if (profile.endpointHost.contains(":")) {
            "[${profile.endpointHost}]"
        } else {
            profile.endpointHost
        }
        appendLine("Endpoint = $formattedHost:${profile.endpointPort}")
        appendLine("AllowedIPs = ${profile.allowedIps}")
        if (profile.persistentKeepaliveSec > 0) {
            appendLine("PersistentKeepalive = ${profile.persistentKeepaliveSec}")
        }
    }
}

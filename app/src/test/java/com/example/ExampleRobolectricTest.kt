package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.CryptoSelfTest
import com.example.crypto.Curve25519
import com.example.crypto.KeyVault
import com.example.crypto.ReplayWindow
import com.example.data.model.VpnCipherMode
import com.example.data.parser.WireGuardConfigParser
import com.example.vpn.WireGuardSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name and vpn permissions are configured`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("CipherGuard", context.getString(R.string.app_name))

        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
        )
        val perms = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertTrue(Manifest.permission.INTERNET in perms)
        assertTrue(Manifest.permission.FOREGROUND_SERVICE in perms)
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in perms)

        val vpnService = pkgInfo.services?.firstOrNull {
            it.name.endsWith("CipherGuardVpnService")
        }
        assertTrue(vpnService != null)
        assertEquals(Manifest.permission.BIND_VPN_SERVICE, vpnService?.permission)
    }

    @Test
    fun `cryptographic primitives pass RFC vectors and AES-256-GCM verification`() {
        val report = CryptoSelfTest.runFullAudit(KeyVault())
        assertTrue(
            "Expected all crypto checks to pass: ${report.items}",
            report.allPassed
        )
    }

    @Test
    fun `wireguard config parser parses and serializes valid conf`() {
        val clientKeys = Curve25519.generateKeyPair()
        val serverKeys = Curve25519.generateKeyPair()
        val psk = Curve25519.encodeBase64(Curve25519.generatePresharedKey())

        val rawConf = """
            # Name: Frankfurt Edge
            # Country: DE
            # City: Frankfurt
            # Cipher: AES-256-GCM
            [Interface]
            PrivateKey = ${clientKeys.privateKeyBase64}
            Address = 10.66.66.2/32
            DNS = 1.1.1.1, 9.9.9.9
            MTU = 1420

            [Peer]
            PublicKey = ${serverKeys.publicKeyBase64}
            PresharedKey = $psk
            Endpoint = 203.0.113.10:51820
            AllowedIPs = 0.0.0.0/0, ::/0
            PersistentKeepalive = 25
        """.trimIndent()

        val parsed = WireGuardConfigParser.parse(rawConf).getOrThrow()
        assertEquals("Frankfurt Edge", parsed.name)
        assertEquals("DE", parsed.countryCode)
        assertEquals("203.0.113.10", parsed.endpointHost)
        assertEquals(51820, parsed.endpointPort)
        assertEquals(clientKeys.publicKeyBase64, parsed.publicKeyBase64)
        assertEquals(VpnCipherMode.WIREGUARD_AES_256_GCM, parsed.cipherMode)

        val reserialized = WireGuardConfigParser.serialize(parsed)
        val reparsed = WireGuardConfigParser.parse(reserialized).getOrThrow()
        assertEquals(parsed.endpointHost, reparsed.endpointHost)
        assertEquals(parsed.peerPublicKeyBase64, reparsed.peerPublicKeyBase64)
    }

    @Test
    fun `wireguard handshake initiation produces valid 148 byte type 1 packet and replay window blocks duplicates`() {
        val client = Curve25519.generateKeyPair()
        val server = Curve25519.generateKeyPair()
        val session = WireGuardSession(
            localPrivateKey = client.privateKey,
            localPublicKey = client.publicKey,
            peerPublicKey = server.publicKey,
            presharedKey = null,
            cipherMode = VpnCipherMode.WIREGUARD_AES_256_GCM
        )
        val initiation = session.createHandshakeInitiation()
        assertEquals(148, initiation.size)
        assertEquals(1.toByte(), initiation[0])

        val replay = ReplayWindow()
        assertTrue(replay.checkAndAccept(0))
        assertFalse(replay.checkAndAccept(0))
        assertTrue(replay.checkAndAccept(5))
        assertTrue(replay.checkAndAccept(3))
        assertFalse(replay.checkAndAccept(3))
    }
}

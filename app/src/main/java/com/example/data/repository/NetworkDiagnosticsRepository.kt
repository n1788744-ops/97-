package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.network.IpGeoApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

data class PublicIpInfo(
    val ip: String,
    val country: String,
    val countryCode: String,
    val city: String,
    val isp: String,
    val asn: String,
    val fetchedAtMs: Long = System.currentTimeMillis()
)

data class InstalledAppItem(
    val packageName: String,
    val label: String
)

class NetworkDiagnosticsRepository(private val context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val ipGeoApi: IpGeoApi = Retrofit.Builder()
        .baseUrl("https://ipwho.is/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(IpGeoApi::class.java)

    suspend fun fetchPublicIpInfo(): Result<PublicIpInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val who = runCatching { ipGeoApi.fetchIpWhoIs() }.getOrNull()
            if (who != null && !who.ip.isNullOrBlank() && who.success != false) {
                PublicIpInfo(
                    ip = who.ip,
                    country = who.country ?: "Неизвестно",
                    countryCode = who.countryCode ?: "--",
                    city = who.city ?: who.region ?: "Неизвестно",
                    isp = who.connection?.isp ?: who.connection?.org ?: "Не определён",
                    asn = who.connection?.asn?.let { "AS$it" } ?: "--"
                )
            } else {
                val fallback = ipGeoApi.fetchIpify()
                val ip = fallback.ip?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Не удалось получить публичный IP")
                PublicIpInfo(
                    ip = ip,
                    country = "Определён IP",
                    countryCode = "--",
                    city = "--",
                    isp = "--",
                    asn = "--"
                )
            }
        }
    }

    /**
     * Checks DNS resolution & TCP/ICMP reachability latency to a VPN server host.
     */
    suspend fun measureEndpointLatency(host: String, port: Int): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val start = System.nanoTime()
            val addr = InetAddress.getByName(host)
            val reachable = addr.isReachable(1800)
            if (reachable) {
                ((System.nanoTime() - start) / 1_000_000L).toInt().coerceAtLeast(1)
            } else {
                // Fallback to DNS + TCP connect check (common if ICMP is blocked on VPS)
                val tcpStart = System.nanoTime()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(addr, port), 1800)
                }
                ((System.nanoTime() - tcpStart) / 1_000_000L).toInt().coerceAtLeast(1)
            }
        }.getOrNull()
    }

    fun isSystemVpnTransportActive(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    /**
     * Queries launcher-visible apps using the manifest <queries> intent filter
     * (zero QUERY_ALL_PACKAGES permission, Play-Policy compliant).
     */
    suspend fun loadInstalledLauncherApps(): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        resolved
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = info.loadLabel(pm).toString().ifBlank { pkg }
                InstalledAppItem(packageName = pkg, label = label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}

package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET

@JsonClass(generateAdapter = true)
data class IpWhoIsResponse(
    val ip: String? = null,
    val success: Boolean? = null,
    val country: String? = null,
    @Json(name = "country_code") val countryCode: String? = null,
    val region: String? = null,
    val city: String? = null,
    val connection: IpConnectionInfo? = null
)

@JsonClass(generateAdapter = true)
data class IpConnectionInfo(
    val asn: Long? = null,
    val org: String? = null,
    val isp: String? = null
)

@JsonClass(generateAdapter = true)
data class IpifyResponse(
    val ip: String? = null
)

interface IpGeoApi {
    @GET("https://ipwho.is/")
    suspend fun fetchIpWhoIs(): IpWhoIsResponse

    @GET("https://api.ipify.org?format=json")
    suspend fun fetchIpify(): IpifyResponse
}

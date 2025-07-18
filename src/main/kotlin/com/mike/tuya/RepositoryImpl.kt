package com.mike.tuya

import com.google.gson.JsonParser
import com.mike.domain.model.meter.Meter
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import java.time.LocalDateTime
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.slf4j.LoggerFactory


class TuyaRepositoryImpl(private val client: HttpClient) : TuyaRepository {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val accessId = System.getenv("ACCESS_ID") ?: ""
    private val accessSecret = System.getenv("ACCESS_SECRET") ?: ""
    private val tuyaEndpoint = System.getenv("TUYA_ENDPOINT") ?: "https://openapi.tuyaeu.com"


    override suspend fun fetchOnlineDevices(): List<Meter> {
        try {
            // First, get an access token
            val token = getAccessToken()
            if (token == null) {
                logger.error("Failed to obtain Tuya access token")
                return emptyList()
            }

            // Fetch devices
            val timestamp = System.currentTimeMillis().toString()
            val path = "/v1.0/devices"
            val stringToSign = buildStringToSign("GET", path, token.accessToken, timestamp, null)
            val signature = sign(stringToSign, token.accessSecret)

            // Updated Ktor client usage
            val response: HttpResponse = client.get("$tuyaEndpoint$path") {
                headers {
                    append("client_id", accessId)
                    append("sign", signature)
                    append("t", timestamp)
                    append("sign_method", "HMAC-SHA256")
                    append("access_token", token.accessToken)
                }
            }

            val responseBody = response.bodyAsText()

            // Parse response
            val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
            val success = jsonResponse.get("success").asBoolean

            if (!success) {
                logger.error("Failed to fetch devices: ${jsonResponse.get("msg").asString}")
                return emptyList()
            }

            val result = jsonResponse.get("result").asJsonArray
            return result.map { device ->
                val deviceObj = device.asJsonObject
                Meter(
                    meterId = deviceObj.get("id").asString,
                    name = deviceObj.get("name").asString,
                    productName = if (deviceObj.has("product_name")) deviceObj.get("product_name").asString else null,
                    description = null,
                    location = null,
                    active = deviceObj.get("online").asBoolean,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
            }.filter { it.active } // Only return online devices

        } catch (e: Exception) {
            logger.error("Error fetching online devices", e)
            return emptyList()
        }
    }

    private suspend fun getAccessToken(): AccessToken? {
        try {
            val timestamp = System.currentTimeMillis().toString()
            val path = "/v1.0/token?grant_type=1"
            val stringToSign = buildStringToSign("GET", path, "", timestamp, null)
            val signature = sign(stringToSign, accessSecret)

            // Updated Ktor client usage
            val response: HttpResponse = client.get("$tuyaEndpoint$path") {
                headers {
                    append("client_id", accessId)
                    append("sign", signature)
                    append("t", timestamp)
                    append("sign_method", "HMAC-SHA256")
                }
            }

            val responseBody = response.bodyAsText()
            val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
            val success = jsonResponse.get("success").asBoolean

            if (!success) {
                logger.error("Failed to get access token: ${jsonResponse.get("msg").asString}")
                return null
            }

            val result = jsonResponse.get("result").asJsonObject
            return AccessToken(
                result.get("access_token").asString,
                result.get("refresh_token").asString,
                result.get("expire_time").asInt,
                accessSecret
            )
        } catch (e: Exception) {
            logger.error("Error getting access token", e)
            return null
        }
    }

    private fun buildStringToSign(method: String, path: String, accessToken: String, timestamp: String, params: Map<String, String>?): String {
        val paramsStr = if (params != null) {
            params.entries.sortedBy { it.key }.joinToString("") { it.key + "=" + it.value }
        } else ""

        return method + "\n" +
                sha256Hex(paramsStr) + "\n" +
                "\n" +
                path
    }

    private fun sha256Hex(message: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(message.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun sign(message: String, secret: String): String {
        val algorithm = "HmacSHA256"
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(secret.toByteArray(), algorithm))
        val hash = mac.doFinal(message.toByteArray())
        return Base64.getEncoder().encodeToString(hash)
    }

    data class AccessToken(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Int,
        val accessSecret: String
    )
}

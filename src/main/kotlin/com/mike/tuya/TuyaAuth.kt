package com.mike.tuya

import org.slf4j.LoggerFactory
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import com.google.gson.JsonParser

class TuyaAuth(
    private val config: TuyaConfig,
    private val client: HttpClient
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private var accessToken: String? = null
    private var tokenExpireTime: Long = 0
    private var uid: String? = null

    suspend fun ensureValidToken() {
        if (accessToken.isNullOrEmpty() || System.currentTimeMillis() >= tokenExpireTime - 60000) {
            getAccessToken()
        }
    }

    fun getCurrentAccessToken(): String? = accessToken
    fun getUid(): String? = uid

    private suspend fun getAccessToken(): AccessToken? {
        try {
            val timestamp = System.currentTimeMillis().toString()
            val nonce = generateNonce()
            val path = "/v1.0/token?grant_type=1"

            val headers = createAuthHeaders(
                method = "GET",
                url = path,
                timestamp = timestamp,
                nonce = nonce
            )

            logger.info("Getting access token with: clientId=${config.accessId}, timestamp=$timestamp, path=$path")

            val response: HttpResponse = client.get("${config.tuyaEndpoint}$path") {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
            }

            val responseBody = response.bodyAsText()
            logger.info("Tuya token response: $responseBody")

            val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
            val success = jsonResponse.get("success").asBoolean

            if (!success) {
                val errorMsg = jsonResponse.get("msg").asString
                val errorCode = if (jsonResponse.has("code")) jsonResponse.get("code").asString else "unknown"
                logger.error("Failed to get access token: $errorMsg (code: $errorCode)")
                return null
            }

            val result = jsonResponse.get("result").asJsonObject
            accessToken = result.get("access_token").asString
            val expireTime = result.get("expire_time").asInt
            tokenExpireTime = System.currentTimeMillis() + (expireTime * 1000)
            uid = if (result.has("uid")) result.get("uid").asString else null

            return AccessToken(
                accessToken = accessToken!!,
                refreshToken = result.get("refresh_token").asString,
                expiresIn = expireTime,
                accessSecret = config.accessSecret
            )
        } catch (e: Exception) {
            logger.error("Error getting access token", e)
            return null
        }
    }

    fun createAuthHeaders(
        method: String,
        url: String,
        timestamp: String,
        nonce: String,
        accessToken: String? = null,
        body: String = ""
    ): Map<String, String> {
        val stringToSign = buildString {
            append(method)
            append("\n")
            append(sha256Hex(body))
            append("\n")
            append("")  // headers
            append("\n")
            append(url)
        }

        val signStr = buildString {
            append(config.accessId)
            if (accessToken != null) {
                append(accessToken)
            }
            append(timestamp)
            append(nonce)
            append(stringToSign)
        }

        val signature = sign(signStr, config.accessSecret)

        return mutableMapOf<String, String>().apply {
            put("client_id", config.accessId)
            put("sign", signature)
            put("t", timestamp)
            put("sign_method", "HMAC-SHA256")
            put("nonce", nonce)
            if (accessToken != null) {
                put("access_token", accessToken)
            }
        }
    }

    private fun generateNonce(): String =
        (1..32).map { "0123456789abcdef".random() }.joinToString("")

    private fun sha256Hex(message: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(message.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun sign(message: String, secret: String): String {
        if (secret.isBlank()) {
            logger.error("HMAC key is blank!")
            throw IllegalArgumentException("HMAC key cannot be blank")
        }

        val algorithm = "HmacSHA256"
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), algorithm))
        val hash = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }.uppercase()
    }

    data class AccessToken(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Int,
        val accessSecret: String
    )
}

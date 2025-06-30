package com.mike.tuya.client

import com.mike.tuya.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.nio.charset.StandardCharsets

class TuyaCloudClient(
    private val accessId: String,
    private val accessSecret: String,
    private val endpoint: String = "https://openapi.tuyaeu.com",
    private val projectCode: String? = null
) {
    init {
        
        if (accessId.isBlank() || accessSecret.isBlank()) {
            println("⚠️ WARNING: Empty credentials detected in TuyaCloudClient constructor!")
        }
    }
    
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            gson()
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    private var accessToken: String? = null
    private var tokenExpireTime: Long = 0

    suspend fun connect(): Boolean {
        return try {
            println("🌐 Connecting to Tuya Cloud API at $endpoint")
            println("🔑 Using credentials: ${accessId.take(5)}***")
            
            if (accessId.isBlank() || accessSecret.isBlank()) {
                println("❌ ERROR: Access ID or Access Secret is blank!")
                println("   Access ID length: ${accessId.length}")
                println("   Access Secret length: ${accessSecret.length}")
                return false
            }
            
            val response = refreshToken()
            
            if (response.success && !accessToken.isNullOrEmpty()) {
                println("✅ Successfully connected to Tuya Cloud API")
                true
            } else {
                println("❌ Connection failed: ${response.msg}")
                false
            }
        } catch (e: Exception) {
            println("❌ Connection error: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private suspend fun refreshToken(): TuyaApiResponse<Map<String, JsonElement>> {
        val timestamp = System.currentTimeMillis().toString()
        val nonce = generateNonce()
        
        val headers = createAuthHeaders(
            method = "GET",
            url = "/v1.0/token?grant_type=1",
            timestamp = timestamp,
            nonce = nonce
        )

        println("🔄 Refreshing access token...")
        
        val response: HttpResponse = httpClient.get("$endpoint/v1.0/token") {
            parameter("grant_type", "1")
            headers.forEach { (key, value) ->
                header(key, value)
            }
        }

        val apiResponse: TuyaApiResponse<Map<String, JsonElement>> = response.body()
        
        if (apiResponse.success && apiResponse.result != null) {
            val result = apiResponse.result
            accessToken = result["access_token"]?.toString()?.trim('"')
            val expireTime = result["expire_time"]?.toString()?.trim('"')?.toLongOrNull() ?: 0
            tokenExpireTime = System.currentTimeMillis() + (expireTime * 1000)
            
            println("✅ Token refreshed successfully! Expires in ${expireTime}s")
        } else {
            println("❌ Failed to refresh token: ${apiResponse.msg}")
        }

        return apiResponse
    }

    private suspend fun ensureValidToken() {
        if (accessToken.isNullOrEmpty() || System.currentTimeMillis() >= tokenExpireTime - 60000) {
            refreshToken()
        }
    }

    suspend fun getAllDevices(): List<Device> {
        return try {
            ensureValidToken()
            
            val url = if (projectCode != null) {
                "/v1.0/iot-01/associated-users/devices"
            } else {
                "/v1.0/devices"
            }
            
            val timestamp = System.currentTimeMillis().toString()
            val nonce = generateNonce()
            
            val headers = createAuthHeaders(
                method = "GET",
                url = if (projectCode != null) "$url?project_code=$projectCode" else url,
                timestamp = timestamp,
                nonce = nonce,
                accessToken = accessToken
            )

            val response: HttpResponse = httpClient.get("$endpoint$url") {
                if (projectCode != null) {
                    parameter("project_code", projectCode)
                }
                headers.forEach { (key, value) ->
                    header(key, value)
                }
            }

            val apiResponse: TuyaApiResponse<JsonElement> = response.body()
            
            if (apiResponse.success && apiResponse.result != null) {
                val result = apiResponse.result
                
                // Handle different response formats
                return try {
                    // Check if it's an empty list or contains non-device data
                    if (result is JsonObject && result.has("devices")) {
                        val devicesElement = result.get("devices")
                        if (devicesElement != null) {
                            val gson = com.google.gson.Gson()
                            gson.fromJson(devicesElement, Array<Device>::class.java).toList()
                        } else {
                            emptyList()
                        }
                    } else {
                        // Try to parse directly as list of devices
                        try {
                            val gson = com.google.gson.Gson()
                            gson.fromJson(result, Array<Device>::class.java).toList()
                        } catch (e: Exception) {
                            // If parsing fails, return empty list (this can happen with virtual devices)
                            println("ℹ️ Could not parse devices list (this is normal for accounts with only virtual devices)")
                            emptyList()
                        }
                    }
                } catch (e: Exception) {
                    println("❌ Failed to parse devices response: ${e.message}")
                    // Return empty list instead of failing
                    emptyList()
                }
            } else {
                println("❌ Failed to get devices: ${apiResponse.msg}")
                emptyList()
            }
        } catch (e: Exception) {
            println("❌ Error getting devices: ${e.message}")
            emptyList()
        }
    }

    suspend fun getDeviceInfo(deviceId: String): Device? {
        return try {
            ensureValidToken()
            
            val url = "/v1.0/devices/$deviceId"
            val timestamp = System.currentTimeMillis().toString()
            val nonce = generateNonce()
            
            val headers = createAuthHeaders(
                method = "GET",
                url = url,
                timestamp = timestamp,
                nonce = nonce,
                accessToken = accessToken
            )

            val response: HttpResponse = httpClient.get("$endpoint$url") {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
            }

            val apiResponse: TuyaApiResponse<Device> = response.body()
            
            if (apiResponse.success) {
                apiResponse.result
            } else {
                println("❌ Failed to get device info: ${apiResponse.msg}")
                null
            }
        } catch (e: Exception) {
            println("❌ Error getting device info: ${e.message}")
            null
        }
    }

    suspend fun getDeviceStatus(deviceId: String): List<DataPoint> {
        return try {
            ensureValidToken()
            
            val endpoints = listOf(
                "/v1.0/devices/$deviceId/status",
                "/v1.0/iot-03/devices/$deviceId/status"
            )
            
            for (urlPath in endpoints) {
                val timestamp = System.currentTimeMillis().toString()
                val nonce = generateNonce()
                
                val headers = createAuthHeaders(
                    method = "GET",
                    url = urlPath,
                    timestamp = timestamp,
                    nonce = nonce,
                    accessToken = accessToken
                )

                val response: HttpResponse = httpClient.get("$endpoint$urlPath") {
                    headers.forEach { (key, value) ->
                        header(key, value)
                    }
                }

                val apiResponse: TuyaApiResponse<List<DataPoint>> = response.body()
                
                if (apiResponse.success && !apiResponse.result.isNullOrEmpty()) {
                    return apiResponse.result
                }
            }
            
            println("ℹ️ No data points found for device $deviceId")
            emptyList()
        } catch (e: Exception) {
            println("❌ Error getting device status: ${e.message}")
            emptyList()
        }
    }

    suspend fun getDeviceSpecifications(deviceId: String): DeviceSpecs? {
        return try {
            ensureValidToken()
            
            val endpoints = listOf(
                "/v1.0/devices/$deviceId/specifications",
                "/v1.0/devices/$deviceId/functions"
            )
            
            for (urlPath in endpoints) {
                val timestamp = System.currentTimeMillis().toString()
                val nonce = generateNonce()
                
                val headers = createAuthHeaders(
                    method = "GET",
                    url = urlPath,
                    timestamp = timestamp,
                    nonce = nonce,
                    accessToken = accessToken
                )

                val response: HttpResponse = httpClient.get("$endpoint$urlPath") {
                    headers.forEach { (key, value) ->
                        header(key, value)
                    }
                }

                val apiResponse: TuyaApiResponse<DeviceSpecs> = response.body()
                
                if (apiResponse.success && apiResponse.result != null) {
                    return apiResponse.result
                }
            }
            
            null
        } catch (e: Exception) {
            println("❌ Error getting device specifications: ${e.message}")
            null
        }
    }

    suspend fun sendCommand(deviceId: String, code: String, value: JsonElement? = null): Boolean {
        return try {
            ensureValidToken()
            
            val command = DeviceCommand(
                commands = listOf(Command(code = code, value = value))
            )
            
            val url = "/v1.0/devices/$deviceId/commands"
            val timestamp = System.currentTimeMillis().toString()
            val nonce = generateNonce()
            
            val headers = createAuthHeaders(
                method = "POST",
                url = url,
                timestamp = timestamp,
                nonce = nonce,
                accessToken = accessToken,
                body = com.google.gson.Gson().toJson(command)
            )

            val response: HttpResponse = httpClient.post("$endpoint$url") {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
                contentType(ContentType.Application.Json)
                setBody(command)
            }

            val apiResponse: TuyaApiResponse<JsonElement> = response.body()
            
            if (apiResponse.success) {
                true
            } else {
                println("❌ Command failed: ${apiResponse.msg}")
                false
            }
        } catch (e: Exception) {
            println("❌ Error sending command: ${e.message}")
            false
        }
    }

    suspend fun addBalance(deviceId: String, amount: Double? = null): Boolean {
        val jsonObject = com.google.gson.JsonObject()
        if (amount != null) {
            jsonObject.addProperty("amount", amount)
        }
        return sendCommand(deviceId, "add_balance", jsonObject)
    }

    private fun createAuthHeaders(
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
            append(sha256(body))
            append("\n")
            append("")  // headers
            append("\n")
            append(url)
        }
        
        val signStr = buildString {
            append(accessId)
            if (accessToken != null) {
                append(accessToken)
            }
            append(timestamp)
            append(nonce)
            append(stringToSign)
        }
        
        val signature = hmacSha256(signStr, accessSecret)
        
        return mutableMapOf<String, String>().apply {
            put("client_id", accessId)
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

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hash.fold("") { str, it -> str + "%02x".format(it) }
    }

    private fun hmacSha256(data: String, key: String): String {
        try {
            if (key.isBlank()) {
                println("❌ ERROR: HMAC key is blank!")
                throw IllegalArgumentException("HMAC key cannot be blank")
            }
            
            val mac = Mac.getInstance("HmacSHA256")
            val secretKeySpec = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
            mac.init(secretKeySpec)
            val hash = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
            return hash.fold("") { str, it -> str + "%02x".format(it) }.uppercase()
        } catch (e: Exception) {
            println("❌ HMAC-SHA256 Error: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    fun close() {
        httpClient.close()
    }
}

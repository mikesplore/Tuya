package com.mike.tuya

import com.google.gson.JsonParser
import com.mike.domain.model.meter.Meter
import com.mike.domain.model.meter.MeterPayment
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory

class TuyaRepositoryImpl(
    private val client: HttpClient,
    private val config: TuyaConfig,
    private val auth: TuyaAuth
) : TuyaRepository {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun fetchOnlineDevices(): List<Meter> {
        try {
            auth.ensureValidToken()
            val timestamp = System.currentTimeMillis().toString()
            val nonce = generateNonce()

            val (path, url) = if (!config.projectCode.isNullOrBlank() && !auth.getUid().isNullOrBlank()) {
                val p = "/v1.0/iot-01/associated-users/devices"
                val u = "${config.tuyaEndpoint}$p?project_code=${config.projectCode}&uid=${auth.getUid()}"
                Pair(p, u)
            } else if (!config.projectCode.isNullOrBlank()) {
                val p = "/v1.0/iot-01/associated-users/devices"
                val u = "${config.tuyaEndpoint}$p?project_code=${config.projectCode}"
                Pair(p, u)
            } else {
                val p = "/v1.0/devices"
                val u = "${config.tuyaEndpoint}$p"
                Pair(p, u)
            }

            val headers = auth.createAuthHeaders(
                method = "GET",
                url = if (!config.projectCode.isNullOrBlank() && !auth.getUid().isNullOrBlank())
                    "$path?project_code=${config.projectCode}&uid=${auth.getUid()}"
                else if (!config.projectCode.isNullOrBlank())
                    "$path?project_code=${config.projectCode}"
                else path,
                timestamp = timestamp,
                nonce = nonce,
                accessToken = auth.getCurrentAccessToken()
            )

            val response: HttpResponse = client.get(url) {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
            }

            val responseBody = response.bodyAsText()
            val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
            val success = jsonResponse.get("success").asBoolean

            if (!success) {
                logger.error("Failed to fetch devices: ${jsonResponse.get("msg").asString}")
                return emptyList()
            }

            val result = jsonResponse.get("result")
            val devicesArray = when {
                result.isJsonObject && result.asJsonObject.has("devices") ->
                    result.asJsonObject.get("devices").asJsonArray
                result.isJsonArray -> result.asJsonArray
                else -> null
            }
            if (devicesArray == null) return emptyList()

            return devicesArray.map { device ->
                val deviceObj = device.asJsonObject
                val statusArray = if (deviceObj.has("status")) deviceObj.get("status").asJsonArray else null
                var balance: Int? = null
                var totalEnergy: Int? = null
                var price: Int? = null
                var chargeEnergy: Int? = null
                var switchPrepayment: Boolean? = null
                if (statusArray != null) {
                    for (status in statusArray) {
                        val statusObj = status.asJsonObject
                        when (statusObj.get("code").asString) {
                            "balance_energy" -> balance = statusObj.get("value").asInt
                            "forward_energy_total" -> totalEnergy = statusObj.get("value").asInt
                            "goods_price" -> price = statusObj.get("value").asInt
                            "charge_energy" -> chargeEnergy = statusObj.get("value").asInt
                            "switch_prepayment" -> switchPrepayment = statusObj.get("value").asBoolean
                        }
                    }
                }
                Meter(
                    meterId = deviceObj.get("id").asString,
                    name = deviceObj.get("name").asString,
                    productName = if (deviceObj.has("product_name")) deviceObj.get("product_name").asString else null,
                    online = deviceObj.get("online").asBoolean,
                    balance = balance,
                    totalEnergy = totalEnergy,
                    price = price,
                    chargeEnergy = chargeEnergy,
                    switchPrepayment = switchPrepayment,
                    updatedAt = if (deviceObj.has("update_time")) deviceObj.get("update_time").asLong else null
                )
            }.filter { it.online }
        } catch (e: Exception) {
            logger.error("Error fetching online devices", e)
            return emptyList()
        }
    }

    override suspend fun chargeMeter(meterId: String, amount: Int, userId: Int?): Boolean {
        try {
            auth.ensureValidToken()
            val timestamp = System.currentTimeMillis().toString()
            val nonce = generateNonce()

            val path = "/v1.0/devices/$meterId/commands"
            val commandBody = """
                {
                  "commands": [
                    {
                      "code": "charge_energy",
                      "value": $amount
                    }
                  ]
                }
            """.trimIndent()

            val headers = auth.createAuthHeaders(
                method = "POST",
                url = path,
                timestamp = timestamp,
                nonce = nonce,
                accessToken = auth.getCurrentAccessToken(),
                body = commandBody
            )

            val response: HttpResponse = client.post("${config.tuyaEndpoint}$path") {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
                contentType(ContentType.Application.Json)
                setBody(commandBody)
            }

            val responseBody = response.bodyAsText()
            val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
            val success = jsonResponse.get("success").asBoolean

            if (success) {
                // Optional: Store charge record in local database
                // This can be implemented separately
                return true
            } else {
                logger.error("Failed to charge meter: ${jsonResponse.get("msg").asString}")
                return false
            }
        } catch (e: Exception) {
            logger.error("Error charging meter", e)
            return false
        }
    }

    override suspend fun setMeterRate(meterId: String, price: Int): Boolean {
        try {
            auth.ensureValidToken()
            val timestamp = System.currentTimeMillis().toString()
            val nonce = generateNonce()

            val path = "/v1.0/devices/$meterId/commands"
            val commandBody = """
                {
                  "commands": [
                    {
                      "code": "goods_price",
                      "value": $price
                    }
                  ]
                }
            """.trimIndent()

            val headers = auth.createAuthHeaders(
                method = "POST",
                url = path,
                timestamp = timestamp,
                nonce = nonce,
                accessToken = auth.getCurrentAccessToken(),
                body = commandBody
            )

            val response: HttpResponse = client.post("${config.tuyaEndpoint}$path") {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
                contentType(ContentType.Application.Json)
                setBody(commandBody)
            }

            val responseBody = response.bodyAsText()
            val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
            val success = jsonResponse.get("success").asBoolean

            if (success) {
                return true
            } else {
                logger.error("Failed to set meter rate: ${jsonResponse.get("msg").asString}")
                return false
            }
        } catch (e: Exception) {
            logger.error("Error setting meter rate", e)
            return false
        }
    }

    override suspend fun getMeterBalance(meterId: String): Int? {
        try {
            val device = getMeterDetails(meterId) ?: return null
            return device.balance
        } catch (e: Exception) {
            logger.error("Error getting meter balance", e)
            return null
        }
    }

    override suspend fun getMeterRate(meterId: String): Int? {
        try {
            val device = getMeterDetails(meterId) ?: return null
            return device.price
        } catch (e: Exception) {
            logger.error("Error getting meter rate", e)
            return null
        }
    }

    override suspend fun togglePrepaymentMode(meterId: String, enabled: Boolean): Boolean {
        try {
            auth.ensureValidToken()
            val timestamp = System.currentTimeMillis().toString()
            val nonce = generateNonce()

            val path = "/v1.0/devices/$meterId/commands"
            val commandBody = """
                {
                  "commands": [
                    {
                      "code": "switch_prepayment",
                      "value": $enabled
                    }
                  ]
                }
            """.trimIndent()

            val headers = auth.createAuthHeaders(
                method = "POST",
                url = path,
                timestamp = timestamp,
                nonce = nonce,
                accessToken = auth.getCurrentAccessToken(),
                body = commandBody
            )

            val response: HttpResponse = client.post("${config.tuyaEndpoint}$path") {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
                contentType(ContentType.Application.Json)
                setBody(commandBody)
            }

            val responseBody = response.bodyAsText()
            val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
            val success = jsonResponse.get("success").asBoolean

            if (success) {
                return true
            } else {
                logger.error("Failed to toggle prepayment mode: ${jsonResponse.get("msg").asString}")
                return false
            }
        } catch (e: Exception) {
            logger.error("Error toggling prepayment mode", e)
            return false
        }
    }

    override suspend fun getMeterHistory(meterId: String): List<MeterPayment> {
        // This would typically be implemented using a database repository
        // For now, return an empty list
        return emptyList<MeterPayment>()
    }

    private suspend fun getMeterDetails(meterId: String): Meter? {
        try {
            auth.ensureValidToken()
            val timestamp = System.currentTimeMillis().toString()
            val nonce = generateNonce()

            val path = "/v1.0/devices/$meterId"

            val headers = auth.createAuthHeaders(
                method = "GET",
                url = path,
                timestamp = timestamp,
                nonce = nonce,
                accessToken = auth.getCurrentAccessToken()
            )

            val response: HttpResponse = client.get("${config.tuyaEndpoint}$path") {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
            }

            val responseBody = response.bodyAsText()
            val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
            val success = jsonResponse.get("success").asBoolean

            if (!success) {
                logger.error("Failed to get device details: ${jsonResponse.get("msg").asString}")
                return null
            }

            val deviceObj = jsonResponse.get("result").asJsonObject
            val statusArray = if (deviceObj.has("status")) deviceObj.get("status").asJsonArray else null
            var balance: Int? = null
            var totalEnergy: Int? = null
            var price: Int? = null
            var chargeEnergy: Int? = null
            var switchPrepayment: Boolean? = null

            if (statusArray != null) {
                for (status in statusArray) {
                    val statusObj = status.asJsonObject
                    when (statusObj.get("code").asString) {
                        "balance_energy" -> balance = statusObj.get("value").asInt
                        "forward_energy_total" -> totalEnergy = statusObj.get("value").asInt
                        "goods_price" -> price = statusObj.get("value").asInt
                        "charge_energy" -> chargeEnergy = statusObj.get("value").asInt
                        "switch_prepayment" -> switchPrepayment = statusObj.get("value").asBoolean
                    }
                }
            }

            return Meter(
                meterId = deviceObj.get("id").asString,
                name = deviceObj.get("name").asString,
                productName = if (deviceObj.has("product_name")) deviceObj.get("product_name").asString else null,
                online = deviceObj.get("online").asBoolean,
                balance = balance,
                totalEnergy = totalEnergy,
                price = price,
                chargeEnergy = chargeEnergy,
                switchPrepayment = switchPrepayment,
                updatedAt = if (deviceObj.has("update_time")) deviceObj.get("update_time").asLong else null
            )
        } catch (e: Exception) {
            logger.error("Error fetching device details", e)
            return null
        }
    }

    private fun generateNonce(): String =
        (1..32).map { "0123456789abcdef".random() }.joinToString("")
}
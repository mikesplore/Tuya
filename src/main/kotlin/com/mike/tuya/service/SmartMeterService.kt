package com.mike.tuya.service

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.mike.tuya.client.TuyaCloudClient
import com.mike.tuya.model.*
import kotlin.math.roundToInt

class SmartMeterService(
    accessId: String,
    accessSecret: String,
    endpoint: String = "https://openapi.tuyaeu.com",
    projectCode: String? = null
) {
    private val client = TuyaCloudClient(accessId, accessSecret, endpoint, projectCode)

    suspend fun connect(): Boolean = client.connect()

    suspend fun listAllDevices(): List<Device> {
        return client.getAllDevices()
    }

    // Alias for CLI compatibility
    suspend fun listAllMeters(): List<Device> {
        return listAllDevices()
    }

    suspend fun getDeviceDetails(deviceId: String): DeviceInfo? {
        val device = client.getDeviceInfo(deviceId) ?: return null
        val status = client.getDeviceStatus(deviceId)
        val specifications = client.getDeviceSpecifications(deviceId)
        val summary = generateSmartMeterSummary(status)

        return DeviceInfo(
            device = device,
            status = status,
            specifications = specifications,
            summary = summary
        )
    }

    private fun generateSmartMeterSummary(status: List<DataPoint>): SmartMeterSummary? {
        var currentReading: Double? = null
        var unitsRemaining: Int? = null
        var batteryPercentage: Int? = null
        var balance: Double? = null
        var forwardEnergyTotal: Double? = null
        var electricTotal: Double? = null

        for (dp in status) {
            when (dp.code) {
                "current_reading" -> {
                    // Apply scale factor (divide by 100) as in Python code
                    dp.value?.toDoubleOrNull()?.let { value ->
                        currentReading = value / 100.0
                    }
                }

                "units_remaining" -> {
                    unitsRemaining = dp.value?.toIntOrNull()
                }

                "battery_percentage" -> {
                    batteryPercentage = dp.value?.toIntOrNull()
                }

                "balance" -> {
                    dp.value?.toDoubleOrNull()?.let { value ->
                        balance = value
                    }
                }

                "balance_energy" -> {
                    dp.value?.toDoubleOrNull()?.let { value ->
                        unitsRemaining = value.toInt()
                    }
                }

                "forward_energy_total" -> {
                    dp.value?.toDoubleOrNull()?.let { value ->
                        forwardEnergyTotal = value
                    }
                }

                "electric_total" -> {
                    dp.value?.toDoubleOrNull()?.let { value ->
                        electricTotal = value
                    }
                }
            }
        }

        // Only create summary if we have meaningful data
        if (currentReading == null && unitsRemaining == null && batteryPercentage == null &&
            balance == null && forwardEnergyTotal == null && electricTotal == null
        ) {
            return null
        }

        val creditStatus = when (unitsRemaining) {
            null -> "Unknown"
            in 0..19 -> "Very Low Credit"
            in 20..49 -> "Low Credit"
            else -> "Credit Level Good"
        }

        val batteryStatus = when (batteryPercentage) {
            null -> "Unknown"
            in 0..19 -> "Battery Critically Low"
            in 20..49 -> "Battery Low"
            else -> "Battery Level Good"
        }

        return SmartMeterSummary(
            totalConsumption = currentReading ?: forwardEnergyTotal,
            creditRemaining = unitsRemaining,
            balance = balance,
            totalEnergyPurchased = electricTotal,
            creditStatus = creditStatus,
            batteryLevel = batteryPercentage,
            batteryStatus = batteryStatus
        )
    }

    // === BILLING OPERATIONS ===

    // Add Money to Meter (charge_money)
    suspend fun addMoney(deviceId: String, amount: Double): CommandResponse {
        val device = client.getDeviceInfo(deviceId) ?: return CommandResponse(
            success = false,
            message = "Device $deviceId not found",
            deviceId = deviceId,
            command = "charge_money"
        )

        // Get current balance first
        val currentBalance = getRemainingBalance(deviceId)
        val balanceBefore = currentBalance.moneyBalance ?: 0.0

        val success = client.sendCommand(deviceId, "charge_money", JsonPrimitive(amount))

        val result = CommandResponse(
            success = success,
            message = if (success) {
                "Added ${amount} money units to ${device.name ?: "device"}"
            } else {
                "Failed to add money"
            },
            deviceId = deviceId,
            command = "charge_money",
            value = JsonPrimitive(amount)
        )

        // If successful, get the new balance
        if (success) {
            // Wait a moment for the device to update
            kotlinx.coroutines.delay(1000)
            val newBalance = getRemainingBalance(deviceId)
            result.additionalData = mapOf(
                "balanceBefore" to balanceBefore,
                "balanceAfter" to (newBalance.moneyBalance ?: 0.0),
                "unitsAdded" to amount
            )
        }

        return result
    }

    // Add Energy Units (charge_token)
    suspend fun addToken(deviceId: String, token: String): CommandResponse {
        val device = client.getDeviceInfo(deviceId) ?: return CommandResponse(
            success = false,
            message = "Device $deviceId not found",
            deviceId = deviceId,
            command = "charge_token"
        )

        // Get current energy balance first
        val currentBalance = getRemainingBalance(deviceId)
        val balanceBefore = currentBalance.energyBalance ?: 0.0

        val success = client.sendCommand(deviceId, "charge_token", JsonPrimitive(token))

        val result = CommandResponse(
            success = success,
            message = if (success) {
                "Token applied successfully to ${device.name ?: "device"}"
            } else {
                "Failed to apply token"
            },
            deviceId = deviceId,
            command = "charge_token",
            value = JsonPrimitive(token)
        )

        // If successful, get the new balance
        if (success) {
            // Wait a moment for the device to update
            kotlinx.coroutines.delay(1000)
            val newBalance = getRemainingBalance(deviceId)
            result.additionalData = mapOf(
                "balanceBefore" to balanceBefore,
                "balanceAfter" to (newBalance.energyBalance ?: 0.0),
                "unitsAdded" to (newBalance.energyBalance?.minus(balanceBefore) ?: 0.0)
            )
        }

        return result
    }

    // Set Energy Charge Rate
    suspend fun setEnergyCharge(deviceId: String, chargeRate: Double): CommandResponse {
        return sendCustomCommand(
            deviceId = deviceId,
            code = "energy_charge",
            value = JsonPrimitive(chargeRate)
        )
    }

    // Reset Balance
    suspend fun resetBalance(deviceId: String): CommandResponse {
        return sendCustomCommand(
            deviceId = deviceId,
            code = "clear_energy",
            value = JsonPrimitive(true)
        )
    }

    // Get Remaining Balance
    suspend fun getRemainingBalance(deviceId: String): BalanceResponse {
        val status = client.getDeviceStatus(deviceId)

        var energyBalance: Double? = null
        var moneyBalance: Double? = null

        for (dp in status) {
            when (dp.code) {
                "balance_energy" -> {
                    dp.value?.toDoubleOrNull()?.let { energyBalance = it }
                }

                "balance" -> {
                    dp.value?.toDoubleOrNull()?.let { moneyBalance = it }
                }
            }
        }

        return BalanceResponse(
            success = energyBalance != null || moneyBalance != null,
            deviceId = deviceId,
            energyBalance = energyBalance,
            moneyBalance = moneyBalance
        )
    }

    // Get Usage Data
    suspend fun getEnergyUsage(deviceId: String): UsageResponse {
        val status = client.getDeviceStatus(deviceId)

        var totalEnergyUsed: Double? = null
        var totalEnergyPurchased: Double? = null
        var monthlyEnergy: Double? = null
        var dailyEnergy: Double? = null

        for (dp in status) {
            when (dp.code) {
                "forward_energy_total" -> {
                    dp.value?.toDoubleOrNull()?.let { totalEnergyUsed = it }
                }

                "electric_total" -> {
                    dp.value?.toDoubleOrNull()?.let { totalEnergyPurchased = it }
                }

                "month_energy" -> {
                    dp.value?.toDoubleOrNull()?.let { monthlyEnergy = it }
                }

                "daily_energy" -> {
                    dp.value?.toDoubleOrNull()?.let { dailyEnergy = it }
                }
            }
        }

        return UsageResponse(
            success = true,
            deviceId = deviceId,
            totalEnergyUsed = totalEnergyUsed,
            totalEnergyPurchased = totalEnergyPurchased,
            monthlyEnergy = monthlyEnergy,
            dailyEnergy = dailyEnergy
        )
    }

    // Set Unit Price
    suspend fun setUnitPrice(deviceId: String, price: Double, currencySymbol: String? = null): CommandResponse {
        // First try goods_price (numeric format)
        val numericResult = sendCustomCommand(
            deviceId = deviceId,
            code = "goods_price",
            value = JsonPrimitive(price)
        )

        // If supported and has currency symbol, also try unit_price (string format)
        if (numericResult.success && currencySymbol != null) {
            val priceString = "$currencySymbol $price/kWh"
            sendCustomCommand(
                deviceId = deviceId,
                code = "unit_price",
                value = JsonPrimitive(priceString)
            )
        }

        return numericResult
    }

    // Toggle Prepayment Mode
    suspend fun togglePrepaymentMode(deviceId: String, enabled: Boolean): CommandResponse {
        return sendCustomCommand(
            deviceId = deviceId,
            code = "prepayment_switch",
            value = JsonPrimitive(enabled)
        )
    }

    // Legacy method for compatibility
    suspend fun addBalance(deviceId: String, amount: Double? = null): CommandResponse {
        return if (amount != null) {
            addMoney(deviceId, amount)
        } else {
            CommandResponse(
                success = false,
                message = "Amount is required for adding balance",
                deviceId = deviceId,
                command = "charge_money"
            )
        }
    }

    suspend fun sendCustomCommand(deviceId: String, code: String, value: JsonElement? = null): CommandResponse {
        val device = client.getDeviceInfo(deviceId)
        if (device == null) {
            return CommandResponse(
                success = false,
                message = "Device $deviceId not found",
                deviceId = deviceId,
                command = code
            )
        }

        val success = client.sendCommand(deviceId, code, value)

        return CommandResponse(
            success = success,
            message = if (success) {
                "Command '$code' sent successfully to ${device.name ?: "device"}"
            } else {
                "Failed to send command '$code'"
            },
            deviceId = deviceId,
            command = code,
            value = value
        )
    }

    suspend fun setCurrentReading(deviceId: String, reading: Double): CommandResponse {
        // Convert to raw value (scale: 2) as in Python code
        val rawValue = (reading * 100).roundToInt()
        return sendCustomCommand(
            deviceId = deviceId,
            code = "current_reading",
            value = JsonPrimitive(rawValue)
        )
    }

    suspend fun setUnitsRemaining(deviceId: String, units: Int): CommandResponse {
        return sendCustomCommand(
            deviceId = deviceId,
            code = "units_remaining",
            value = JsonPrimitive(units)
        )
    }

    suspend fun setBattery(deviceId: String, percentage: Int): CommandResponse {
        return sendCustomCommand(
            deviceId = deviceId,
            code = "battery_percentage",
            value = JsonPrimitive(percentage)
        )
    }

    suspend fun setDeviceStatus(deviceId: String, status: String): CommandResponse {
        return sendCustomCommand(
            deviceId = deviceId,
            code = "device_status",
            value = JsonPrimitive(status)
        )
    }

    suspend fun addUnits(deviceId: String, units: Int): Boolean {
        try {
            // Use the correct client.sendCommand signature
            return client.sendCommand(deviceId, "add_units", JsonPrimitive(units))
        } catch (e: Exception) {
            println("Error adding units: ${e.message}")
            return false
        }
    }

    private suspend fun getCurrentBalance(deviceId: String): Int {
        val status = client.getDeviceStatus(deviceId)
        return status.find { it.code == "current_balance" }?.value?.toString()?.toIntOrNull() ?: 0
    }

    fun close() {
        client.close()
    }
}

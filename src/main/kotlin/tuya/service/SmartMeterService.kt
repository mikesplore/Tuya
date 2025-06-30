package com.mike.tuya.service

import com.mike.tuya.client.TuyaCloudClient
import com.mike.tuya.model.*
import kotlinx.datetime.Instant
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlin.math.roundToInt

class SmartMeterService(
    private val accessId: String,
    private val accessSecret: String,
    private val endpoint: String = "https://openapi.tuyaeu.com",
    private val projectCode: String? = null
) {
    private val client = TuyaCloudClient(accessId, accessSecret, endpoint, projectCode)
    
    suspend fun connect(): Boolean = client.connect()
    
    suspend fun listAllDevices(): List<Device> {
        return client.getAllDevices()
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
            }
        }
        
        // Only create summary if we have meaningful data
        if (currentReading == null && unitsRemaining == null && batteryPercentage == null) {
            return null
        }
        
        val creditStatus = when {
            unitsRemaining == null -> "Unknown"
            unitsRemaining < 20 -> "Very Low Credit"
            unitsRemaining < 50 -> "Low Credit"
            else -> "Credit Level Good"
        }
        
        val batteryStatus = when {
            batteryPercentage == null -> "Unknown"
            batteryPercentage < 20 -> "Battery Critically Low"
            batteryPercentage < 50 -> "Battery Low"
            else -> "Battery Level Good"
        }
        
        return SmartMeterSummary(
            totalConsumption = currentReading,
            creditRemaining = unitsRemaining,
            creditStatus = creditStatus,
            batteryLevel = batteryPercentage,
            batteryStatus = batteryStatus
        )
    }
    
    suspend fun addBalance(deviceId: String, amount: Double? = null): CommandResponse {
        val device = client.getDeviceInfo(deviceId)
        if (device == null) {
            return CommandResponse(
                success = false,
                message = "Device $deviceId not found",
                deviceId = deviceId,
                command = "add_balance"
            )
        }
        
        val success = client.addBalance(deviceId, amount)
        val amountStr = amount?.let { "$it units" } ?: "default amount"
        
        return CommandResponse(
            success = success,
            message = if (success) {
                "Balance command sent successfully! Added $amountStr to ${device.name ?: "device"}"
            } else {
                "Failed to add balance"
            },
            deviceId = deviceId,
            command = "add_balance",
            value = amount?.let { JsonPrimitive(it) }
        )
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
    
    fun close() {
        client.close()
    }
}

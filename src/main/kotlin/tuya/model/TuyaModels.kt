package com.mike.tuya.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class TuyaApiResponse<T>(
    val success: Boolean,
    val result: T? = null,
    val msg: String? = null,
    val code: Int? = null,
    val t: Long? = null
)

@Serializable
data class Device(
    val id: String,
    val name: String? = null,
    @SerialName("product_name")
    val productName: String? = null,
    val model: String? = null,
    val online: Boolean = false,
    val ip: String? = null,
    val category: String? = null,
    @SerialName("active_time")
    val activeTime: Long? = null,
    val status: List<DataPoint>? = null,
    @SerialName("biz_type")
    val bizType: String? = null
)

@Serializable
data class DevicesResponse(
    val devices: List<Device>? = null
)

@Serializable
data class DataPoint(
    val code: String,
    val value: String? = null,
    val time: Long? = null
)

@Serializable
data class DeviceCommand(
    val commands: List<Command>
)

@Serializable
data class Command(
    val code: String,
    val value: JsonElement? = null
)

@Serializable
data class DeviceSpecs(
    val functions: List<Function>? = null,
    val status: List<StatusSpec>? = null
)

@Serializable
data class Function(
    val code: String,
    val type: String,
    val values: String? = null
)

@Serializable
data class StatusSpec(
    val code: String,
    val type: String,
    val values: String? = null
)

@Serializable
data class DeviceStatus(
    @SerialName("current_reading")
    val currentReading: Double? = null,
    @SerialName("units_remaining")
    val unitsRemaining: Int? = null,
    @SerialName("battery_percentage")
    val batteryPercentage: Int? = null,
    @SerialName("device_status")
    val deviceStatus: String? = null
)

@Serializable
data class DeviceInfo(
    val device: Device,
    val status: List<DataPoint>,
    val specifications: DeviceSpecs? = null,
    val summary: SmartMeterSummary? = null
)

@Serializable
data class SmartMeterSummary(
    val totalConsumption: Double? = null,
    val creditRemaining: Int? = null,
    val creditStatus: String,
    val batteryLevel: Int? = null,
    val batteryStatus: String
)

@Serializable
data class CommandResponse(
    val success: Boolean,
    val message: String,
    val deviceId: String,
    val command: String,
    val value: JsonElement? = null
)

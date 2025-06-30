package com.mike.tuya.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class TuyaApiResponse<T>(
    val success: Boolean,
    val result: T? = null,
    val msg: String? = null,
    val code: Int? = null,
    val t: Long? = null
)

data class Device(
    val id: String,
    val name: String? = null,
    @SerializedName("product_name")
    val productName: String? = null,
    val model: String? = null,
    val online: Boolean = false,
    val ip: String? = null,
    val category: String? = null,
    @SerializedName("active_time")
    val activeTime: Long? = null,
    val status: List<DataPoint>? = null,
    @SerializedName("biz_type")
    val bizType: String? = null
)

data class DevicesResponse(
    val devices: List<Device>? = null
)

data class DataPoint(
    val code: String,
    val value: String? = null,
    val time: Long? = null
)

data class DeviceCommand(
    val commands: List<Command>
)

data class Command(
    val code: String,
    val value: JsonElement? = null
)

data class DeviceSpecs(
    val functions: List<Function>? = null,
    val status: List<StatusSpec>? = null
)

data class Function(
    val code: String,
    val type: String,
    val values: String? = null
)

data class StatusSpec(
    val code: String,
    val type: String,
    val values: String? = null
)

data class DeviceStatus(
    @SerializedName("current_reading")
    val currentReading: Double? = null,
    @SerializedName("units_remaining")
    val unitsRemaining: Int? = null,
    @SerializedName("battery_percentage")
    val batteryPercentage: Int? = null,
    @SerializedName("device_status")
    val deviceStatus: String? = null
)

data class DeviceInfo(
    val device: Device,
    val status: List<DataPoint>,
    val specifications: DeviceSpecs? = null,
    val summary: SmartMeterSummary? = null
)

data class SmartMeterSummary(
    val totalConsumption: Double? = null,
    val creditRemaining: Int? = null,
    val creditStatus: String,
    val batteryLevel: Int? = null,
    val batteryStatus: String
)

data class CommandResponse(
    val success: Boolean,
    val message: String,
    val deviceId: String,
    val command: String,
    val value: JsonElement? = null
)

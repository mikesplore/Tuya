package com.mike.domain.model.meter

import com.google.gson.JsonElement
import com.mike.database.tables.Meters
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.time.LocalDateTime
import java.util.UUID

class Meter(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Meter>(Meters)
    
    var deviceId by Meters.deviceId
    var name by Meters.name
    var productName by Meters.productName
    var description by Meters.description
    var location by Meters.location
    var active by Meters.active
    var createdAt by Meters.createdAt
    var updatedAt by Meters.updatedAt
    
    // Helper function to convert DAO to data class
    fun toMeterDto(): MeterDto = MeterDto(
        id = id.value.toString(),
        deviceId = deviceId,
        name = name,
        productName = productName,
        description = description,
        location = location,
        active = active,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

// Meter Data Transfer Object (for API responses)
data class MeterDto(
    val id: String,
    val deviceId: String,
    val name: String,
    val productName: String? = null,
    val description: String? = null,
    val location: String? = null,
    val active: Boolean = true,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)


data class DeviceListResponse(
    val devices: List<Any>,
    val count: Int
)

data class DeviceCreateRequest(
    val deviceId: String,
    val name: String,
    val productName: String? = null,
    val description: String? = null,
    val location: String? = null
)

data class DeviceUpdateRequest(
    val name: String? = null,
    val productName: String? = null,
    val description: String? = null,
    val location: String? = null,
    val active: Boolean? = null
)

data class DeviceUserAssignmentRequest(
    val userId: String
)

data class AddBalanceRequest(
    val amount: Double
)

data class CustomCommandRequest(
    val code: String,
    val value: JsonElement
)

data class ErrorResponse(
    val error: String,
    val message: String
)

data class MeterPaymentRequest(
    val amount: Double,
    val paymentType: String, // "MONEY" or "TOKEN"
    val token: String? = null, // Required for TOKEN payment type
    val userId: String? = null, // Optional - can be derived from auth token
    val mpesaTransactionId: String? = null, // Optional - for linking to MPesa transaction
    val description: String? = null
)

data class SetPriceRequest(
    val price: Double,
    val currencySymbol: String? = null
)

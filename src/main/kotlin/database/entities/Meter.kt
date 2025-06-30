package com.mike.database.entities

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

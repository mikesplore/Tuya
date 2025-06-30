package com.mike.database.repository

import com.mike.database.entities.Meter
import com.mike.database.entities.MeterDto
import com.mike.database.tables.Meters
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

class MeterRepository {

    fun findByDeviceId(deviceId: String): MeterDto? = transaction {
        Meter.find { Meters.deviceId eq deviceId }
            .singleOrNull()
            ?.toMeterDto()
    }
    
    fun findById(id: String): MeterDto? = transaction {
        Meter.findById(UUID.fromString(id))?.toMeterDto()
    }
    
    fun getAllMeters(): List<MeterDto> = transaction {
        Meter.all().map { it.toMeterDto() }
    }
    
    fun createMeter(deviceId: String, name: String, productName: String? = null, 
                    description: String? = null, location: String? = null): MeterDto = transaction {
        val now = LocalDateTime.now()
        
        Meter.new {
            this.deviceId = deviceId
            this.name = name
            this.productName = productName
            this.description = description
            this.location = location
            this.active = true
            this.createdAt = now
            this.updatedAt = now
        }.toMeterDto()
    }
    
    fun updateMeter(id: String, name: String? = null, productName: String? = null,
                    description: String? = null, location: String? = null, active: Boolean? = null): MeterDto? = transaction {
        val meter = Meter.findById(UUID.fromString(id)) ?: return@transaction null
        
        name?.let { meter.name = it }
        productName?.let { meter.productName = it }
        description?.let { meter.description = it }
        location?.let { meter.location = it }
        active?.let { meter.active = it }
        meter.updatedAt = LocalDateTime.now()
        
        meter.toMeterDto()
    }
    
    fun deleteMeter(id: String): Boolean = transaction {
        val meter = Meter.findById(UUID.fromString(id)) ?: return@transaction false
        meter.delete()
        true
    }
}

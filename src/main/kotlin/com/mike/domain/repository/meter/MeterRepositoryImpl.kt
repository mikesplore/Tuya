package com.mike.domain.repository.meter

import com.mike.database.tables.Meters
import com.mike.domain.model.meter.Meter
import com.mike.domain.model.meter.MeterDto
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*

class MeterRepositoryImpl : MeterRepository {

    override fun findByDeviceId(deviceId: String): MeterDto? = transaction {
        Meter.Companion.find { Meters.deviceId eq deviceId }
            .singleOrNull()
            ?.toMeterDto()
    }

    override fun findById(id: String): MeterDto? = transaction {
        Meter.Companion.findById(UUID.fromString(id))?.toMeterDto()
    }

    override fun getAllMeters(): List<MeterDto> = transaction {
        Meter.Companion.all().map { it.toMeterDto() }
    }

    override fun createMeter(
        deviceId: String, name: String, productName: String?,
        description: String?, location: String?
    ): MeterDto = transaction {
        val now = LocalDateTime.now()

        Meter.Companion.new {
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

    override fun updateMeter(
        id: String, name: String?, productName: String?,
        description: String?, location: String?, active: Boolean?
    ): MeterDto? =
        transaction {
            val meter = Meter.Companion.findById(UUID.fromString(id)) ?: return@transaction null

            name?.let { meter.name = it }
            productName?.let { meter.productName = it }
            description?.let { meter.description = it }
            location?.let { meter.location = it }
            active?.let { meter.active = it }
            meter.updatedAt = LocalDateTime.now()

            meter.toMeterDto()
        }

    override fun deleteMeter(id: String): Boolean = transaction {
        val meter = Meter.Companion.findById(UUID.fromString(id)) ?: return@transaction false
        meter.delete()
        true
    }
}

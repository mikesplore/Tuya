package com.mike.domain.repository.meter

import com.mike.domain.model.meter.Meter
import com.mike.domain.model.meter.Meters
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class MeterRepositoryImpl : MeterRepository {

    override fun findByDeviceId(deviceId: String): Meter? = transaction {
        Meters.selectAll().where { Meters.deviceId eq deviceId }
            .singleOrNull()
            ?.let { resultRow ->
                mapToMeter(resultRow)
            }
    }

    override fun findById(id: String): Meter? = transaction {
        Meters.selectAll().where { Meters.deviceId eq id }
            .singleOrNull()
            ?.let { resultRow ->
                mapToMeter(resultRow)
            }
    }

    override fun getAllMeters(): List<Meter> = transaction {
        Meters.selectAll()
            .map { resultRow ->
                mapToMeter(resultRow)
            }
    }

    override fun createMeter(meter: Meter): Meter = transaction {
        val now = LocalDateTime.now()

        Meters.insert {
            it[deviceId] = meter.deviceId
            it[name] = meter.name
            it[productName] = meter.productName
            it[description] = meter.description
            it[location] = meter.location
            it[active] = meter.active
            it[createdAt] = now
            it[updatedAt] = now
        }

        // Return the created meter with updated timestamps
        Meters.selectAll().where { Meters.deviceId eq meter.deviceId }
            .singleOrNull()
            ?.let { resultRow ->
                mapToMeter(resultRow)
            } ?: meter.copy(createdAt = now, updatedAt = now)
    }

    override fun updateMeter(meter: Meter): Meter = transaction {
        val now = LocalDateTime.now()
        
        val updateCount = Meters.update({ Meters.deviceId eq meter.deviceId }) {
            it[name] = meter.name
            it[productName] = meter.productName
            it[description] = meter.description
            it[location] = meter.location
            it[active] = meter.active
            it[updatedAt] = now
        }
        
        if (updateCount > 0) {
            // Return the updated meter with new updatedAt timestamp
            Meters.selectAll().where { Meters.deviceId eq meter.deviceId }
                .singleOrNull()
                ?.let { resultRow ->
                    mapToMeter(resultRow)
                } ?: meter.copy(updatedAt = now)
        } else {
            meter
        }
    }

    override fun deleteMeter(id: String): Boolean = transaction {
        val deleteCount = Meters.deleteWhere { deviceId eq id }
        deleteCount > 0
    }
    
    private fun mapToMeter(row: ResultRow): Meter {
        return Meter(
            deviceId = row[Meters.deviceId],
            name = row[Meters.name],
            productName = row[Meters.productName],
            description = row[Meters.description],
            location = row[Meters.location],
            active = row[Meters.active],
            createdAt = row[Meters.createdAt],
            updatedAt = row[Meters.updatedAt]
        )
    }
}

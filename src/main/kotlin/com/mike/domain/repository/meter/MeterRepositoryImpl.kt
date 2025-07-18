package com.mike.domain.repository.meter

import com.mike.domain.model.meter.Meter
import com.mike.domain.model.meter.MeterCreationRequest
import com.mike.domain.model.meter.Meters
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class MeterRepositoryImpl : MeterRepository {

    override fun findById(id: String): Meter? = transaction {
        Meters.selectAll().where { Meters.meterId eq id }
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

    override fun createMeter(meter: MeterCreationRequest): String? {
        return transaction {
            val now = LocalDateTime.now()
            val exists = Meters.selectAll().where { Meters.meterId eq meter.meterId }.any()
            if (exists) {
                return@transaction "Meter already exists."
            }
            Meters.insert {
                it[meterId] = meter.meterId
                it[name] = meter.name
                it[productName] = meter.productName
                it[description] = meter.description
                it[location] = meter.location
                it[active] = meter.active
                it[createdAt] = now
                it[updatedAt] = now
            }
            null
        }
    }

    override fun updateMeter(meter: MeterCreationRequest) {
        transaction {
            val now = LocalDateTime.now()

            Meters.update({ Meters.meterId eq meter.meterId }) {
                it[name] = meter.name
                it[productName] = meter.productName
                it[description] = meter.description
                it[location] = meter.location
                it[active] = meter.active
                it[updatedAt] = now
            }
        }
    }

    override fun deleteMeter(id: String): Boolean = transaction {
        val deleteCount = Meters.deleteWhere { meterId eq id }
        deleteCount > 0
    }

    private fun mapToMeter(row: ResultRow): Meter {
        return Meter(
            meterId = row[Meters.meterId],
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

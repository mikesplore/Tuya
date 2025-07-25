package com.mike.domain.repository.meter

import com.mike.domain.model.meter.Meter
import com.mike.domain.model.meter.MeterCreationRequest
import com.mike.domain.model.meter.MeterUserAssignments
import com.mike.domain.model.meter.Meters
import com.mike.tuya.TuyaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class MeterRepositoryImpl(
    private val tuyaRepository: TuyaRepository
) : MeterRepository {
    val scope = CoroutineScope(Dispatchers.IO)
    override fun findById(id: String): Meter? = transaction {
        Meters.selectAll().where { Meters.meterId eq id }
            .singleOrNull()
            ?.let { resultRow ->
                mapToMeter(resultRow)
            }
    }

    override fun getAllMeters(): List<Meter> = transaction {
        // Fetch all meters from the database first
        val meters = Meters.selectAll().map { resultRow ->
            mapToMeter(resultRow)
        }
        // Fetch online devices from Tuya and update the database in the background
        scope.launch {
            val onlineDevices = tuyaRepository.fetchOnlineDevices()
            transaction {
                onlineDevices.forEach { meter ->
                    val exists = Meters.selectAll().where { Meters.meterId eq meter.meterId }.any()
                    if (exists) {
                        Meters.update({ Meters.meterId eq meter.meterId }) {
                            it[online] = meter.online
                            it[balance] = meter.balance
                            it[totalEnergy] = meter.totalEnergy
                            it[price] = meter.price
                            it[chargeEnergy] = meter.chargeEnergy
                            it[switchPrepayment] = meter.switchPrepayment
                            it[updatedAtLong] = meter.updatedAt
                        }
                    } else {
                        Meters.insert {
                            it[meterId] = meter.meterId
                            it[name] = meter.name
                            it[productName] = meter.productName
                            it[online] = meter.online
                            it[balance] = meter.balance
                            it[totalEnergy] = meter.totalEnergy
                            it[price] = meter.price
                            it[chargeEnergy] = meter.chargeEnergy
                            it[switchPrepayment] = meter.switchPrepayment
                            it[updatedAtLong] = meter.updatedAt
                        }
                    }
                }
            }
        }
        meters
    }

    override fun getMetersForUser(userId: Int): List<Meter> {
        return transaction {
            val meterIds = MeterUserAssignments.selectAll().where { MeterUserAssignments.userId eq userId }
                .map { it[MeterUserAssignments.meterId] }

            val meters = Meters.selectAll().where { Meters.meterId inList meterIds }
                .mapNotNull { resultRow ->
                    mapToMeter(resultRow)
                }
            meters
        }

    }

    override fun createMeter(meter: MeterCreationRequest): String? {
        return transaction {
            val exists = Meters.selectAll().where { Meters.meterId eq meter.meterId }.any()
            if (exists) {
                return@transaction "Meter already exists."
            }
            Meters.insert {
                it[meterId] = meter.meterId
                it[name] = meter.name
                it[productName] = meter.productName
                it[online] = false
                it[balance] = null
                it[totalEnergy] = null
                it[price] = null
                it[chargeEnergy] = null
                it[switchPrepayment] = null
                it[updatedAtLong] = null
            }
            null
        }
    }

    override fun updateMeter(meter: MeterCreationRequest) {
        transaction {
            Meters.update({ Meters.meterId eq meter.meterId }) {
                it[name] = meter.name
                it[productName] = meter.productName
                // Only update static fields from the creation request
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
            online = row[Meters.online],
            balance = row[Meters.balance],
            totalEnergy = row[Meters.totalEnergy],
            price = row[Meters.price],
            chargeEnergy = row[Meters.chargeEnergy],
            switchPrepayment = row[Meters.switchPrepayment],
            updatedAt = row[Meters.updatedAtLong]
        )
    }
}

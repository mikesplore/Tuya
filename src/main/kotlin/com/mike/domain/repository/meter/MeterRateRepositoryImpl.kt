package com.mike.domain.repository.meter

import com.mike.domain.model.meter.MeterRate
import com.mike.domain.model.meter.MeterRates
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDateTime

class MeterRateRepositoryImpl : MeterRateRepository {

    override suspend fun createRate(meterRate: MeterRate): Int = transaction {
        val now = LocalDateTime.now()

        MeterRates.insert {
            it[name] = meterRate.name
            it[description] = meterRate.description
            it[ratePerUnit] = meterRate.ratePerUnit
            it[minAmount] = meterRate.minAmount
            it[maxAmount] = meterRate.maxAmount
            it[isActive] = meterRate.isActive
            it[createdAt] = now
            it[updatedAt] = now
        }[MeterRates.id]
    }

    override suspend fun getRateById(id: Int): MeterRate? = transaction {
        MeterRates.selectAll()
            .where { MeterRates.id eq id }
            .singleOrNull()?.let { mapToMeterRate(it) }
    }

    override suspend fun getAllRates(): List<MeterRate> = transaction {
        MeterRates.selectAll()
            .map { mapToMeterRate(it) }
    }

    override suspend fun getActiveRates(): List<MeterRate> = transaction {
        MeterRates.selectAll()
            .where { MeterRates.isActive eq true }
            .map { mapToMeterRate(it) }
    }

    // Fix: Explicitly define the return type as Unit
    override suspend fun updateRate(meterRate: MeterRate): Unit = transaction {
        meterRate.id?.let {
            MeterRates.update({ MeterRates.id eq it }) { updatedMeterRate ->
                updatedMeterRate[name] = meterRate.name
                updatedMeterRate[description] = meterRate.description
                updatedMeterRate[ratePerUnit] = meterRate.ratePerUnit
                updatedMeterRate[minAmount] = meterRate.minAmount
                updatedMeterRate[maxAmount] = meterRate.maxAmount
                updatedMeterRate[isActive] = meterRate.isActive
                updatedMeterRate[updatedAt] = LocalDateTime.now()
            }
        }
    }

    // Fix: Explicitly define the return type as Unit
    override suspend fun deleteRate(id: Int): Unit = transaction {
        MeterRates.update({ MeterRates.id eq id }) {
            it[isActive] = false
            it[updatedAt] = LocalDateTime.now()
        }
    }

    override suspend fun calculateUnitsForAmount(amount: BigDecimal): BigDecimal {
        // Fix: Get the rate outside transaction
        val rate = getRateForAmount(amount) ?: throw IllegalArgumentException("No applicable rate found for amount: $amount")

        // Now perform the calculation
        return amount.divide(rate.ratePerUnit, 2, java.math.RoundingMode.HALF_DOWN)
    }

    override suspend fun getRateForAmount(amount: BigDecimal): MeterRate? = transaction {
        MeterRates.selectAll()
            .where {
                (MeterRates.isActive eq true) and
                (MeterRates.minAmount lessEq amount) and
                (MeterRates.maxAmount.isNull() or (MeterRates.maxAmount greaterEq amount))
            }
            .orderBy(MeterRates.minAmount to SortOrder.DESC)  // Get the highest applicable rate range
            .firstOrNull()?.let { mapToMeterRate(it) }
    }

    private fun mapToMeterRate(row: ResultRow): MeterRate {
        return MeterRate(
            id = row[MeterRates.id],
            name = row[MeterRates.name],
            description = row[MeterRates.description],
            ratePerUnit = row[MeterRates.ratePerUnit],
            minAmount = row[MeterRates.minAmount],
            maxAmount = row[MeterRates.maxAmount],
            isActive = row[MeterRates.isActive],
            createdAt = row[MeterRates.createdAt],
            updatedAt = row[MeterRates.updatedAt]
        )
    }
}

package com.mike.domain.repository.meter

import com.mike.domain.model.meter.MeterRate

interface MeterRateRepository {
    suspend fun createRate(meterRate: MeterRate): Int
    suspend fun getRateById(id: Int): MeterRate?
    suspend fun getAllRates(): List<MeterRate>
    suspend fun getActiveRates(): List<MeterRate>
    suspend fun updateRate(meterRate: MeterRate)
    suspend fun deleteRate(id: Int)
    suspend fun calculateUnitsForAmount(amount: java.math.BigDecimal): java.math.BigDecimal
    suspend fun getRateForAmount(amount: java.math.BigDecimal): MeterRate?
}

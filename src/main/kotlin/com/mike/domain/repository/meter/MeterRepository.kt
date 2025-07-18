package com.mike.domain.repository.meter

import com.mike.domain.model.meter.Meter
import com.mike.domain.model.meter.MeterCreationRequest

interface MeterRepository {
    fun findById(id: String): Meter?
    fun getAllMeters(): List<Meter>
    fun createMeter(meter: MeterCreationRequest): String? = null
    fun updateMeter(meter: MeterCreationRequest)
    fun deleteMeter(id: String): Boolean
}

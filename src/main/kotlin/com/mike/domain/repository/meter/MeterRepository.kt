package com.mike.domain.repository.meter

import com.mike.domain.model.meter.Meter

interface MeterRepository {
    fun findByDeviceId(deviceId: String): Meter?
    fun findById(id: String): Meter?
    fun getAllMeters(): List<Meter>
    fun createMeter(meter: Meter): Meter
    fun updateMeter(meter: Meter): Meter
    fun deleteMeter(id: String): Boolean
}

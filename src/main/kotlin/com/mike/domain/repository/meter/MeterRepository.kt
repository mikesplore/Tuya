package com.mike.domain.repository.meter

import com.mike.domain.model.meter.MeterDto

interface MeterRepository {
    fun findByDeviceId(deviceId: String): MeterDto?
    fun findById(id: String): MeterDto?
    fun getAllMeters(): List<MeterDto>
    fun createMeter(
        deviceId: String,
        name: String,
        productName: String? = null,
        description: String? = null,
        location: String? = null
    ): MeterDto
    fun updateMeter(
        id: String,
        name: String? = null,
        productName: String? = null,
        description: String? = null,
        location: String? = null,
        active: Boolean? = null
    ): MeterDto?
    fun deleteMeter(id: String): Boolean
}

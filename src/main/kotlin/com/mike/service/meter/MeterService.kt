package com.mike.service.meter

import com.mike.domain.model.meter.Meter
import com.mike.domain.model.meter.MeterCreationRequest
import com.mike.domain.repository.meter.MeterRepository


class MeterService(
    private val meterRepository: MeterRepository,

    ) {

    fun findById(id: String): Meter? = meterRepository.findById(id)
    fun getAllMeters(): List<Meter> = meterRepository.getAllMeters()
    fun createMeter(meter: MeterCreationRequest): String? = meterRepository.createMeter(meter)
    fun updateMeter(meter: MeterCreationRequest) = meterRepository.updateMeter(meter)
    fun deleteMeter(id: String): Boolean = meterRepository.deleteMeter(id)


}

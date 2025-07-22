package com.mike.service.meter

import com.mike.domain.model.meter.MeterUserAssignment
import com.mike.domain.repository.meter.MeterUserAssignmentRepository

class MeterUserService(
    private val meterUserAssignmentRepository: MeterUserAssignmentRepository,
) {
    fun assignMeterToUser(meterUserAssignment: MeterUserAssignment): Boolean =
        meterUserAssignmentRepository.assignMeterToUser(meterUserAssignment)

    fun unassignMeterFromUser(meterUserAssignment: MeterUserAssignment): Boolean =
        meterUserAssignmentRepository.unassignMeterFromUser(meterUserAssignment)

    fun getAssignedMetersByUser(userId: Int) =
        meterUserAssignmentRepository.getAssignedMetersByUser(userId)

    fun getUnassignedMeters() =
        meterUserAssignmentRepository.getUnassignedMeters()

    fun getUsersByMeter(meterId: String) =
        meterUserAssignmentRepository.getUsersByMeter(meterId)

    fun getUsersWithoutMeter(meterId: String) =
        meterUserAssignmentRepository.getUsersWithoutMeter(meterId)

    fun getUserByMeter(meterId: String) =
        meterUserAssignmentRepository.getUserByMeter(meterId)

    fun isMeterAssignedToUser(meterUserAssignment: MeterUserAssignment) =
        meterUserAssignmentRepository.isMeterAssignedToUser(meterUserAssignment)
}
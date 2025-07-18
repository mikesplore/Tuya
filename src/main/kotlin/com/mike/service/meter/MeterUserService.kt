package com.mike.service.meter

import com.mike.domain.model.meter.MeterUserAssignment
import com.mike.domain.repository.meter.MeterUserAssignmentRepository

class MeterUserService(
    private val meterUserAssignmentRepository: MeterUserAssignmentRepository,
) {
    fun assignMeterToUser(meterUserAssignment: MeterUserAssignment) {
        meterUserAssignmentRepository.assignMeterToUser(meterUserAssignment)
    }

    fun unassignMeterFromUser(meterUserAssignment: MeterUserAssignment) {
        meterUserAssignmentRepository.unassignMeterFromUser(meterUserAssignment)
    }

    fun getAssignedMetersByUser(userId: Int) = meterUserAssignmentRepository.getAssignedMetersByUser(userId)

    fun getUsersByMeter(meterId: String) = meterUserAssignmentRepository.getUsersByMeter(meterId)

    fun isMeterAssignedToUser(meterUserAssignment: MeterUserAssignment) =
        meterUserAssignmentRepository.isMeterAssignedToUser(meterUserAssignment)

    fun generateUserMeterAssignment() = meterUserAssignmentRepository.generateUserMeterAssignment()
}
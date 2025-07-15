package com.mike.domain.repository.user

import com.mike.domain.model.meter.MeterDto
import com.mike.domain.model.user.UserDto
import com.mike.domain.model.user.UserMeterAssignmentDto

interface UserMeterAssignmentRepository {
    fun assignMeterToUser(userId: String, meterId: String): UserMeterAssignmentDto
    fun removeMeterFromUser(userId: String, meterId: String): Boolean
    fun getUserMeters(userId: String): List<MeterDto>
    fun getMeterUsers(meterId: String): List<UserDto>
    fun isMeterAssignedToUser(userId: String, meterId: String): Boolean
    fun getAllAssignments(): List<UserMeterAssignmentDto>
}

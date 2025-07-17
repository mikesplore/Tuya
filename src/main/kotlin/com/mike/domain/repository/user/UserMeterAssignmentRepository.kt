package com.mike.domain.repository.user

import com.mike.domain.model.meter.Meter
import com.mike.domain.model.user.Profile
import com.mike.domain.model.user.UserMeterAssignment


interface UserMeterAssignmentRepository {
    fun assignMeterToUser(userId: Int, meterId: String): UserMeterAssignment
    fun removeMeterFromUser(userId: Int, meterId: String): Boolean
    fun getUserMeters(userId: Int): List<Meter>
    fun getMeterUsers(meterId: String): List<Profile>
    fun isMeterAssignedToUser(userId: Int, meterId: String): Boolean
    fun getAllAssignments(): List<UserMeterAssignment>
}

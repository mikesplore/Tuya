package com.mike.domain.repository.user

import com.mike.domain.model.meter.Meter
import com.mike.domain.model.meter.Meters
import com.mike.domain.model.user.Profile
import com.mike.domain.model.user.Profiles
import com.mike.domain.model.user.UserMeterAssignment
import com.mike.domain.model.user.UserMeterAssignments
import com.mike.domain.model.user.Users
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class UserMeterAssignmentRepositoryImpl : UserMeterAssignmentRepository {

    override fun assignMeterToUser(userId: Int, meterId: String): UserMeterAssignment = transaction {
        // Check if user exists
        val userExists = Users.selectAll().where { Users.userId eq userId }.count() > 0
        if (!userExists) {
            throw IllegalArgumentException("User not found")
        }

        // Check if meter exists
        val meterExists = Meters.selectAll().where { Meters.deviceId eq meterId }.count() > 0
        if (!meterExists) {
            throw IllegalArgumentException("Meter not found")
        }

        // Check if meter is already assigned to any user
        val meterAssigned = UserMeterAssignments.selectAll().where {
            UserMeterAssignments.meterId eq meterId
        }.count() > 0

        if (meterAssigned) {
            throw IllegalArgumentException("This meter is already assigned to a user")
        }

        val now = LocalDateTime.now()

        UserMeterAssignments.insert {
            it[this.userId] = userId
            it[this.meterId] = meterId
            it[this.assignedAt] = now
        }

        UserMeterAssignment(
            userId = userId,
            meterId = meterId,
            assignedAt = now
        )
    }

    override fun removeMeterFromUser(userId: Int, meterId: String): Boolean = transaction {
        val deletedCount = UserMeterAssignments.deleteWhere {
            (UserMeterAssignments.userId eq userId) and
                    (UserMeterAssignments.meterId eq meterId)
        }
        deletedCount > 0
    }

    override fun getUserMeters(userId: Int): List<Meter> = transaction {
        (UserMeterAssignments innerJoin Meters)
            .selectAll().where { UserMeterAssignments.userId eq userId }
            .map {
                Meter(
                    deviceId = it[Meters.deviceId],
                    name = it[Meters.name],
                    productName = it[Meters.productName],
                    description = it[Meters.description],
                    location = it[Meters.location],
                    active = it[Meters.active],
                    createdAt = it[Meters.createdAt],
                    updatedAt = it[Meters.updatedAt]
                )
            }
    }

    override fun getMeterUsers(meterId: String): List<Profile> = transaction {
        (UserMeterAssignments innerJoin Users innerJoin Profiles)
            .selectAll().where { UserMeterAssignments.meterId eq meterId }
            .map {
                Profile(
                    userId = it[Users.userId],
                    phoneNumber = it[Profiles.phoneNumber],
                    firstName = it[Profiles.firstName],
                    lastName = it[Profiles.lastName],
                    userRole = it[Users.role],
                    createdAt = it[Profiles.createdAt],
                    updatedAt = it[Profiles.updatedAt]
                )
            }
    }

    override fun isMeterAssignedToUser(userId: Int, meterId: String): Boolean = transaction {
        UserMeterAssignments.selectAll().where {
            (UserMeterAssignments.userId eq userId) and
                    (UserMeterAssignments.meterId eq meterId)
        }.count() > 0
    }

    override fun getAllAssignments(): List<UserMeterAssignment> = transaction {
        UserMeterAssignments.selectAll().map {
            UserMeterAssignment(
                userId = it[UserMeterAssignments.userId],
                meterId = it[UserMeterAssignments.meterId],
                assignedAt = it[UserMeterAssignments.assignedAt]
            )
        }
    }
}

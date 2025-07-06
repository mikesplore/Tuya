package com.mike.database.repository

import com.mike.database.entities.*
import com.mike.database.tables.UserMeterAssignments
import com.mike.database.tables.Meters
import com.mike.database.tables.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

class UserMeterAssignmentRepository {

    fun assignMeterToUser(userId: String, meterId: String): UserMeterAssignmentDto = transaction {
        val userUUID = UUID.fromString(userId)
        val meterUUID = UUID.fromString(meterId)
        
        // Check if user exists
        val userExists = Users.selectAll().where { Users.id eq userUUID }.count() > 0
        if (!userExists) {
            throw IllegalArgumentException("User not found")
        }
        
        // Check if meter exists
        val meterExists = Meters.selectAll().where { Meters.id eq meterUUID }.count() > 0
        if (!meterExists) {
            throw IllegalArgumentException("Meter not found")
        }
        
        // Check if meter is already assigned to any user
        val meterAssigned = UserMeterAssignments.selectAll().where {
            UserMeterAssignments.meterId eq meterUUID
        }.count() > 0

        if (meterAssigned) {
            throw IllegalArgumentException("This meter is already assigned to a user")
        }
        
        val now = LocalDateTime.now()
        
        UserMeterAssignments.insert {
            it[this.userId] = userUUID
            it[this.meterId] = meterUUID
            it[this.assignedAt] = now
        }
        
        UserMeterAssignmentDto(
            userId = userId,
            meterId = meterId,
            assignedAt = now
        )
    }
    
    fun removeMeterFromUser(userId: String, meterId: String): Boolean = transaction {
        val userUUID = UUID.fromString(userId)
        val meterUUID = UUID.fromString(meterId)
        
        val deletedCount = UserMeterAssignments.deleteWhere { 
            (UserMeterAssignments.userId eq userUUID) and
            (UserMeterAssignments.meterId eq meterUUID)
        }
        
        deletedCount > 0
    }
    
    fun getUserMeters(userId: String): List<MeterDto> = transaction {
        val userUUID = UUID.fromString(userId)

        (UserMeterAssignments innerJoin Meters)
            .selectAll().where { UserMeterAssignments.userId eq userUUID }
            .map { 
                MeterDto(
                    id = it[Meters.id].value.toString(),
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
    
    fun getMeterUsers(meterId: String): List<UserDto> = transaction {
        val meterUUID = UUID.fromString(meterId)

        (UserMeterAssignments innerJoin Users)
            .selectAll().where { UserMeterAssignments.meterId eq meterUUID }
            .map { 
                UserDto(
                    id = it[Users.id].value.toString(),
                    email = it[Users.email],
                    phoneNumber = it[Users.phoneNumber],
                    firstName = it[Users.firstName],
                    lastName = it[Users.lastName],
                    role = it[Users.role],
                    active = it[Users.active],
                    createdAt = it[Users.createdAt],
                    updatedAt = it[Users.updatedAt]
                )
            }
    }

    //verify if a user has a meter assigned
    fun isMeterAssignedToUser(userId: String, meterId: String): Boolean = transaction {
        val userUUID = UUID.fromString(userId)
        val meterUUID = UUID.fromString(meterId)

        UserMeterAssignments.selectAll().where {
            (UserMeterAssignments.userId eq userUUID) and
            (UserMeterAssignments.meterId eq meterUUID)
        }.count() > 0
    }
    
    fun getAllAssignments(): List<UserMeterAssignmentDto> = transaction {
        UserMeterAssignments.selectAll().map {
            UserMeterAssignmentDto(
                userId = it[UserMeterAssignments.userId].value.toString(),
                meterId = it[UserMeterAssignments.meterId].value.toString(),
                assignedAt = it[UserMeterAssignments.assignedAt]
            )
        }
    }
}

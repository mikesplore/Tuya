package com.mike.domain.repository.meter

import com.mike.domain.model.meter.Meter
import com.mike.domain.model.meter.MeterUserAssignment
import com.mike.domain.model.meter.MeterUserAssignments
import com.mike.domain.model.user.Profile
import com.mike.domain.repository.user.UserRepository
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class MeterUserAssignmentRepositoryImpl(
    private val userRepository: UserRepository,
    private val meterRepository: MeterRepository
) : MeterUserAssignmentRepository {

    override fun assignMeterToUser(meterUserAssignment: MeterUserAssignment): Boolean {
        return transaction {
            // Check if this meter is already assigned to any user
            val existingAssignment = MeterUserAssignments
                .selectAll().where { MeterUserAssignments.meterId eq meterUserAssignment.meterId }
                .firstOrNull()

            // If already assigned, reject the operation
            if (existingAssignment != null) {
                return@transaction false
            }

            // Create a new assignment
            try {
                MeterUserAssignments.insertIgnore {
                    it[meterId] = meterUserAssignment.meterId
                    it[userId] = meterUserAssignment.userId
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    override fun unassignMeterFromUser(meterUserAssignment: MeterUserAssignment): Boolean {
        return transaction {
            val rowsDeleted = MeterUserAssignments.deleteWhere {
                (meterId eq meterUserAssignment.meterId) and
                        (userId eq meterUserAssignment.userId)
            }
            rowsDeleted > 0
        }
    }

    override fun getAssignedMetersByUser(userId: Int): List<Meter> {
        return transaction {
            MeterUserAssignments
                .selectAll().where { MeterUserAssignments.userId eq userId }
                .mapNotNull { row ->
                    meterRepository.findById(row[MeterUserAssignments.meterId])
                }
        }
    }

    override fun getUnassignedMeters(): List<Meter> {
        return transaction {
            // Get all meter IDs that are currently assigned to users
            val assignedMeterIds = MeterUserAssignments
                .selectAll()
                .mapNotNull { it[MeterUserAssignments.meterId] }
                .toSet()

            // Get all meters and filter out the assigned ones
            meterRepository.getAllMeters().filter { meter ->
                meter.meterId !in assignedMeterIds
            }
        }
    }

    override fun getUsersByMeter(meterId: String): List<Profile> {
        return transaction {
            MeterUserAssignments
                .selectAll().where { MeterUserAssignments.meterId eq meterId }
                .mapNotNull { row ->
                    userRepository.findUserProfile(row[MeterUserAssignments.userId])
                }
        }
    }

    override fun getUsersWithoutMeter(meterId: String): List<Profile> {
        return transaction {
            // Get all user IDs that are assigned to this meter
            val assignedUserIds = MeterUserAssignments
                .selectAll().where { MeterUserAssignments.meterId eq meterId }
                .mapNotNull { it[MeterUserAssignments.userId] }
                .toSet()

            // Get all users and filter out those assigned to this meter
            userRepository.getAllUsers().filter { user ->
                user.userId !in assignedUserIds
            }
        }
    }

    override fun getUserByMeter(meterId: String): Profile? {
        return transaction {
            MeterUserAssignments
                .selectAll().where { MeterUserAssignments.meterId eq meterId }
                .firstOrNull()
                ?.let { row ->
                    userRepository.findUserProfile(row[MeterUserAssignments.userId])
                }
        }
    }

    override fun isMeterAssignedToUser(meterUserAssignment: MeterUserAssignment): Boolean {
        return transaction {
            MeterUserAssignments
                .selectAll().where {
                    (MeterUserAssignments.meterId eq meterUserAssignment.meterId) and
                            (MeterUserAssignments.userId eq meterUserAssignment.userId)
                }
                .any()
        }
    }
}
package com.mike.domain.repository.meter

import com.mike.domain.model.meter.Meter
import com.mike.domain.model.meter.MeterUserAssignment
import com.mike.domain.model.meter.MeterUserAssignments
import com.mike.domain.model.user.Profile
import com.mike.domain.repository.user.UserRepository
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class MeterUserAssignmentRepositoryImpl(
    private val userRepository: UserRepository,
    private val meterRepository: MeterRepository
) : MeterUserAssignmentRepository {

    override fun assignMeterToUser(meterUserAssignment: MeterUserAssignment) {
        transaction {
            MeterUserAssignments.update({
                (MeterUserAssignments.meterId eq MeterUserAssignments.meterId) and (MeterUserAssignments.userId eq MeterUserAssignments.userId)
            }) {
                it[isAssigned] = true
            }
        }
    }

    override fun unassignMeterFromUser(meterUserAssignment: MeterUserAssignment) {
        transaction {
            MeterUserAssignments.update({
                (MeterUserAssignments.meterId eq MeterUserAssignments.meterId) and (MeterUserAssignments.userId eq MeterUserAssignments.userId)
            }) {
                it[isAssigned] = true
            }
        }
    }

    override fun getAssignedMetersByUser(userId: Int): List<Meter> {
        return transaction {
            MeterUserAssignments
                .selectAll().where {
                    (MeterUserAssignments.userId eq userId) and (MeterUserAssignments.isAssigned eq true)
                }
                .mapNotNull { row ->
                    meterRepository.findById(row[MeterUserAssignments.meterId])
                }
        }
    }

    override fun getUsersByMeter(meterId: String): List<Profile> {
        return transaction {
            MeterUserAssignments
                .selectAll().where {
                    (MeterUserAssignments.meterId eq meterId) and (MeterUserAssignments.isAssigned eq true)
                }
                .mapNotNull { row ->
                    userRepository.fundUserProfile(row[MeterUserAssignments.userId])
                }
        }
    }

    override fun isMeterAssignedToUser(meterUserAssignment: MeterUserAssignment): Boolean {
        return transaction {
            MeterUserAssignments
                .selectAll().where {
                    (MeterUserAssignments.meterId eq meterUserAssignment.meterId) and
                            (MeterUserAssignments.userId eq meterUserAssignment.userId) and
                            (MeterUserAssignments.isAssigned eq true)
                }
                .any()
        }
    }

    override fun generateUserMeterAssignment() {
        val users = userRepository.getAllUsers().filter { it.userRole.lowercase() != "admin" }
        val meters = meterRepository.getAllMeters()

        var userIterator = users.iterator()
        transaction {
            meters.forEach { meter ->
                if (!userIterator.hasNext()) {
                    userIterator = users.iterator()
                }
                val user = userIterator.next()
                MeterUserAssignments.insertIgnore {
                    it[meterId] = meter.meterId
                    it[userId] = user.userId
                    it[isAssigned] = true
                }
            }
        }
    }
}
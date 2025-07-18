package com.mike.domain.model.meter

import com.mike.domain.model.user.Users
import org.jetbrains.exposed.sql.Table

data class MeterUserAssignment(
    val meterId: String,
    val userId: Int,
    val isAssigned: Boolean
)


object MeterUserAssignments : Table() {
    val meterId = reference("meter_id", Meters.meterId)
    val userId = reference("user_id", Users.userId)
    val isAssigned = bool("is_assigned").default(false)
    override val primaryKey = PrimaryKey(meterId, userId, name = "PK_MeterUserAssignment")
}



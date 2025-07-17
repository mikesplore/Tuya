package com.mike.domain.model.user

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

// Simple data transfer object for assignments
data class UserMeterAssignment(
    val userId: Int,
    val meterId: String,
    val assignedAt: LocalDateTime
)

object UserMeterAssignments: Table(){
    val userId = integer("user_id").references(Users.userId)
    val meterId = varchar("meter_id", 50)
    val assignedAt = datetime("assigned_at")
    override val primaryKey = PrimaryKey(userId, meterId) // Composite primary key

}



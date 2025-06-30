package com.mike.database.entities

import com.mike.database.tables.UserMeterAssignments
import com.mike.database.tables.Users
import com.mike.database.tables.Meters
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

// Simple data transfer object for assignments
data class UserMeterAssignmentDto(
    val userId: String,
    val meterId: String,
    val assignedAt: LocalDateTime
)

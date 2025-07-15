package com.mike.domain.model.user

import java.time.LocalDateTime

// Simple data transfer object for assignments
data class UserMeterAssignmentDto(
    val userId: String,
    val meterId: String,
    val assignedAt: LocalDateTime
)

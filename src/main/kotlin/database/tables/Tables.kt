package com.mike.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

// Users table
object Users : UUIDTable() {
    val email = varchar("email", 255).uniqueIndex()
    val phoneNumber = varchar("phone_number", 20).nullable()
    val passwordHash = varchar("password_hash", 255)
    val firstName = varchar("first_name", 100).nullable()
    val lastName = varchar("last_name", 100).nullable()
    val role = varchar("role", 50).default("USER")
    val active = bool("active").default(true)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

// Smart meters table
object Meters : UUIDTable() {
    val deviceId = varchar("device_id", 100).uniqueIndex()
    val name = varchar("name", 255)
    val productName = varchar("product_name", 255).nullable()
    val description = text("description").nullable()
    val location = varchar("location", 255).nullable()
    val active = bool("active").default(true)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

// User-meter assignments table
object UserMeterAssignments : Table() {
    val userId = reference("user_id", Users)
    val meterId = reference("meter_id", Meters)
    val assignedAt = datetime("assigned_at")
    
    override val primaryKey = PrimaryKey(userId, meterId)
}

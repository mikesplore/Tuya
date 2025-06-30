package com.mike.database.entities

import com.mike.database.tables.Users
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.time.LocalDateTime
import java.util.UUID

class User(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<User>(Users)
    
    var email by Users.email
    var phoneNumber by Users.phoneNumber
    var passwordHash by Users.passwordHash
    var firstName by Users.firstName
    var lastName by Users.lastName
    var role by Users.role
    var active by Users.active
    var createdAt by Users.createdAt
    var updatedAt by Users.updatedAt
    
    // Helper function to convert DAO to data class
    fun toUserDto(): UserDto = UserDto(
        id = id.value.toString(),
        email = email,
        phoneNumber = phoneNumber,
        firstName = firstName,
        lastName = lastName,
        role = role,
        active = active,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

// User Data Transfer Object (for API responses)
data class UserDto(
    val id: String,
    val email: String,
    val phoneNumber: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val role: String = "USER",
    val active: Boolean = true,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

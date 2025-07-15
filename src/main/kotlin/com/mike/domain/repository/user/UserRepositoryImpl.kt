package com.mike.domain.repository.user

import at.favre.lib.crypto.bcrypt.BCrypt
import com.mike.database.tables.Users
import com.mike.domain.model.user.User
import com.mike.domain.model.user.UserDto
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

class UserRepositoryImpl : UserRepository {

    override fun findByEmail(email: String): UserDto? = transaction {
        User.Companion.find { Users.email eq email }
            .singleOrNull()
            ?.toUserDto()
    }

    override fun findById(id: String): UserDto? = transaction {
        User.Companion.findById(UUID.fromString(id))?.toUserDto()
    }

    override fun getAllUsers(): List<UserDto> = transaction {
        User.Companion.all().map { it.toUserDto() }
    }

    override fun createUser(
        email: String, 
        password: String, 
        firstName: String?, 
        lastName: String?,
        phoneNumber: String?, 
        role: String
    ): UserDto = transaction {
        val passwordHash = hashPassword(password)
        val now = LocalDateTime.now()

        User.Companion.new {
            this.email = email
            this.phoneNumber = phoneNumber
            this.passwordHash = passwordHash
            this.firstName = firstName
            this.lastName = lastName
            this.role = role
            this.active = true
            this.createdAt = now
            this.updatedAt = now
        }.toUserDto()
    }

    override fun updateUser(
        id: String, 
        email: String?, 
        firstName: String?, 
        lastName: String?,
        phoneNumber: String?, 
        role: String?, 
        active: Boolean?
    ): UserDto? = transaction {
        val user = User.Companion.findById(UUID.fromString(id)) ?: return@transaction null

        email?.let { user.email = it }
        firstName?.let { user.firstName = it }
        lastName?.let { user.lastName = it }
        phoneNumber?.let { user.phoneNumber = it }
        role?.let { user.role = it }
        active?.let { user.active = it }
        user.updatedAt = LocalDateTime.now()

        user.toUserDto()
    }

    override fun changePassword(id: String, newPassword: String): Boolean = transaction {
        val user = User.Companion.findById(UUID.fromString(id)) ?: return@transaction false

        user.passwordHash = hashPassword(newPassword)
        user.updatedAt = LocalDateTime.now()

        true
    }

    override fun deleteUser(id: String): Boolean = transaction {
        val user = User.Companion.findById(UUID.fromString(id)) ?: return@transaction false
        user.delete()
        true
    }

    override fun validateCredentials(email: String, password: String): UserDto? = transaction {
        val user = User.Companion.find { Users.email eq email }.singleOrNull() ?: return@transaction null

        if (verifyPassword(password, user.passwordHash)) {
            user.toUserDto()
        } else {
            null
        }
    }

    private fun hashPassword(password: String): String {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray())
    }

    private fun verifyPassword(password: String, hashedPassword: String): Boolean {
        val result = BCrypt.verifyer().verify(password.toCharArray(), hashedPassword)
        return result.verified
    }
}

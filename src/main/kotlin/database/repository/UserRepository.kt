package com.mike.database.repository

import com.mike.database.entities.User
import com.mike.database.entities.UserDto
import com.mike.database.tables.Users
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID
import at.favre.lib.crypto.bcrypt.BCrypt

class UserRepository {

    fun findByEmail(email: String): UserDto? = transaction {
        User.find { Users.email eq email }
            .singleOrNull()
            ?.toUserDto()
    }
    
    fun findById(id: String): UserDto? = transaction {
        User.findById(UUID.fromString(id))?.toUserDto()
    }
    
    fun getAllUsers(): List<UserDto> = transaction {
        User.all().map { it.toUserDto() }
    }
    
    fun createUser(email: String, password: String, firstName: String?, lastName: String?, 
                phoneNumber: String? = null, role: String = "USER"): UserDto = transaction {
        val passwordHash = hashPassword(password)
        val now = LocalDateTime.now()
        
        User.new {
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
    
    fun updateUser(id: String, email: String?, firstName: String?, lastName: String?, 
                  phoneNumber: String?, role: String?, active: Boolean?): UserDto? = transaction {
        val user = User.findById(UUID.fromString(id)) ?: return@transaction null
        
        email?.let { user.email = it }
        firstName?.let { user.firstName = it }
        lastName?.let { user.lastName = it }
        phoneNumber?.let { user.phoneNumber = it }
        role?.let { user.role = it }
        active?.let { user.active = it }
        user.updatedAt = LocalDateTime.now()
        
        user.toUserDto()
    }
    
    fun changePassword(id: String, newPassword: String): Boolean = transaction {
        val user = User.findById(UUID.fromString(id)) ?: return@transaction false
        
        user.passwordHash = hashPassword(newPassword)
        user.updatedAt = LocalDateTime.now()
        
        true
    }
    
    fun deleteUser(id: String): Boolean = transaction {
        val user = User.findById(UUID.fromString(id)) ?: return@transaction false
        user.delete()
        true
    }
    
    fun validateCredentials(email: String, password: String): UserDto? = transaction {
        val user = User.find { Users.email eq email }.singleOrNull() ?: return@transaction null
        
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

package com.mike.domain.repository.user

import com.mike.domain.model.user.UserDto

interface UserRepository {
    fun findByEmail(email: String): UserDto?
    fun findById(id: String): UserDto?
    fun getAllUsers(): List<UserDto>
    fun createUser(
        email: String, 
        password: String, 
        firstName: String?, 
        lastName: String?,
        phoneNumber: String? = null, 
        role: String = "USER"
    ): UserDto
    fun updateUser(
        id: String, 
        email: String? = null, 
        firstName: String? = null, 
        lastName: String? = null,
        phoneNumber: String? = null, 
        role: String? = null, 
        active: Boolean? = null
    ): UserDto?
    fun changePassword(id: String, newPassword: String): Boolean
    fun deleteUser(id: String): Boolean
    fun validateCredentials(email: String, password: String): UserDto?
}

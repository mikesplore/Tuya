package com.mike.service.user

import com.mike.domain.model.meter.MeterDto
import com.mike.domain.model.user.UserDto
import com.mike.domain.model.user.UserMeterAssignmentDto
import com.mike.domain.repository.user.UserMeterAssignmentRepository
import com.mike.domain.repository.user.UserRepository

class UserService(
    private val userRepository: UserRepository,
    private val userMeterAssignmentRepository: UserMeterAssignmentRepository
) {
    // User management functions
    fun getUserById(id: String): UserDto? {
        return userRepository.findById(id)
    }
    
    fun getUserByEmail(email: String): UserDto? {
        return userRepository.findByEmail(email)
    }
    
    fun getAllUsers(): List<UserDto> {
        return userRepository.getAllUsers()
    }
    
    fun createUser(
        email: String,
        password: String,
        firstName: String?,
        lastName: String?,
        phoneNumber: String? = null,
        role: String = "USER"
    ): UserDto {
        return userRepository.createUser(
            email = email,
            password = password,
            firstName = firstName,
            lastName = lastName,
            phoneNumber = phoneNumber,
            role = role
        )
    }
    
    fun updateUser(
        id: String,
        email: String? = null,
        firstName: String? = null,
        lastName: String? = null,
        phoneNumber: String? = null,
        role: String? = null,
        active: Boolean? = null
    ): UserDto? {
        return userRepository.updateUser(
            id = id,
            email = email,
            firstName = firstName,
            lastName = lastName,
            phoneNumber = phoneNumber,
            role = role,
            active = active
        )
    }
    
    fun changePassword(id: String, newPassword: String): Boolean {
        return userRepository.changePassword(id, newPassword)
    }
    
    fun deleteUser(id: String): Boolean {
        return userRepository.deleteUser(id)
    }
    
    fun authenticateUser(email: String, password: String): UserDto? {
        return userRepository.validateCredentials(email, password)
    }
    
    // User-Meter assignment functions
    fun assignMeterToUser(userId: String, meterId: String): UserMeterAssignmentDto {
        return userMeterAssignmentRepository.assignMeterToUser(userId, meterId)
    }
    
    fun removeMeterFromUser(userId: String, meterId: String): Boolean {
        return userMeterAssignmentRepository.removeMeterFromUser(userId, meterId)
    }
    
    fun getUserMeters(userId: String): List<MeterDto> {
        return userMeterAssignmentRepository.getUserMeters(userId)
    }
    
    fun getMeterUsers(meterId: String): List<UserDto> {
        return userMeterAssignmentRepository.getMeterUsers(meterId)
    }
    
    fun isMeterAssignedToUser(userId: String, meterId: String): Boolean {
        return userMeterAssignmentRepository.isMeterAssignedToUser(userId, meterId)
    }
    
    fun getAllMeterAssignments(): List<UserMeterAssignmentDto> {
        return userMeterAssignmentRepository.getAllAssignments()
    }
}

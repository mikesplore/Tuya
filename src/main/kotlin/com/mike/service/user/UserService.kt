package com.mike.service.user

//import com.mike.domain.model.meter.Meter
import com.mike.domain.model.user.*
import com.mike.domain.repository.user.UserMeterAssignmentRepository
import com.mike.domain.repository.user.UserRepository


class UserService(
    private val userRepository: UserRepository,
    private val userMeterAssignmentRepository: UserMeterAssignmentRepository
) {
    // User management functions
    fun getUserById(id: Int): User? {
        return userRepository.findById(id)
    }
    
    fun getUserByEmail(email: String): User? {
        return userRepository.findByEmail(email)
    }

    fun getUserProfile(userId: Int): Profile? {
        return userRepository.fundUserProfile(userId)
    }
    
    fun getAllUsers(): List<Profile> {
        return userRepository.getAllUsers()
    }
    
    fun createUser(registerRequest: RegisterRequest): Pair<Boolean, String?> {
        return userRepository.createUser(registerRequest)
    }
    
    fun updateUser(profile: Profile): Pair<Boolean, String?> {
        return userRepository.updateUser(profile)
    }

    
    fun deleteUser(id: Int): Pair<Boolean, String?> {
        return userRepository.deleteUser(id)
    }
    
//    fun authenticateUser(loginCredentials: LoginCredentials): Pair<Boolean, String?> {
//        return userRepository.validateCredentials(loginCredentials)
//    }
    
//    // User-Meter assignment functions
//    fun assignMeterToUser(userId: String, meterId: String): UserMeterAssignment {
//        return userMeterAssignmentRepository.assignMeterToUser(userId, meterId)
//    }
//
//    fun removeMeterFromUser(userId: String, meterId: String): Boolean {
//        return userMeterAssignmentRepository.removeMeterFromUser(userId, meterId)
//    }
//
//    fun getUserMeters(userId: String): List<Meter> {
//        return userMeterAssignmentRepository.getUserMeters(userId)
//    }
//
//    fun getMeterUsers(meterId: String): List<User> {
//        return userMeterAssignmentRepository.getMeterUsers(meterId)
//    }
//
//    fun isMeterAssignedToUser(userId: String, meterId: String): Boolean {
//        return userMeterAssignmentRepository.isMeterAssignedToUser(userId, meterId)
//    }
//
//    fun getAllMeterAssignments(): List<UserMeterAssignment> {
//        return userMeterAssignmentRepository.getAllAssignments()
//    }

//    fun verifyToken(token: String): Pair<Boolean, Int?> =
//        userRepository.verifyToken(token)
//
//    fun revokeAllUserRefreshTokens(userId: Int): Boolean =
//        userRepository.revokeAllUserRefreshTokens(userId)
//
//    fun revokeRefreshToken(token: String): Boolean =
//        userRepository.revokeRefreshToken(token)
//
//    fun getRefreshToken(token: String): RefreshToken? =
//        userRepository.getRefreshToken(token)
//
//    fun createRefreshToken(userId: Int, expiresInDays: Int = 30): RefreshToken =
//        userRepository.createRefreshToken(userId, expiresInDays)
}

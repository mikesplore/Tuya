package com.mike.service.user

import com.mike.domain.model.user.*
import com.mike.domain.repository.user.UserRepository


class UserService(
    private val userRepository: UserRepository,
) {
    fun getUserById(id: Int): User? {
        return userRepository.findById(id)
    }
    
    fun getUserByEmail(email: String): Profile? {
        return userRepository.findByEmail(email)
    }

    fun getUserProfile(userId: Int): Profile? {
        return userRepository.findUserProfile(userId)
    }
    
    fun getAllUsers(): List<Profile> {
        return userRepository.getAllUsers()
    }
    
    fun createUser(registerRequest: RegisterRequest): Pair<Boolean, String?> {
        return userRepository.createUser(registerRequest)
    }
    
    fun updateUser(profile: ProfileUpdateRequest): Pair<Boolean, String?> {
        return userRepository.updateUser(profile)
    }
    
    fun deleteUser(id: Int): Pair<Boolean, String?> {
        return userRepository.deleteUser(id)
    }

    /**
     * Uploads a profile picture for a user
     *
     * @param userId The ID of the user whose profile picture is being uploaded
     * @param filename The original filename of the image
     * @param contentType The MIME type of the image (e.g., "image/jpeg")
     * @param imageData The binary data of the image
     * @return A Pair where the first element is a Boolean indicating success,
     *         and the second element is an optional error message
     */
    fun uploadProfilePicture(userId: Int, filename: String, contentType: String, imageData: ByteArray): Pair<Boolean, String?> {
        return userRepository.uploadProfilePicture(userId, filename, contentType, imageData)
    }

    /**
     * Retrieves the profile picture for a user
     *
     * @param userId The ID of the user whose profile picture is being retrieved
     * @return The ProfilePicture object containing the image data and metadata, or null if no picture exists
     */
    fun getProfilePicture(userId: Int): ProfilePicture? {
        return userRepository.getProfilePicture(userId)
    }

    fun deleteProfilePicture(userId: Int): Pair<Boolean, String?> {
        val profilePicture = getProfilePicture(userId)
        return if (profilePicture != null) {
            userRepository.deleteProfilePicture(userId)
        } else {
            Pair(false, "No profile picture found for user with ID $userId")
        }
    }
}

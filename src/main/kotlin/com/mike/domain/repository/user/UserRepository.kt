package com.mike.domain.repository.user

import com.mike.domain.model.user.Profile
import com.mike.domain.model.user.ProfilePicture
import com.mike.domain.model.user.RegisterRequest
import com.mike.domain.model.user.User

interface UserRepository {
    fun findByEmail(email: String): User?
    fun findById(userId: Int): User?
    fun fundUserProfile(userId: Int): Profile?
    fun findUserRole(userId: Int): String?
    fun getAllUsers(): List<Profile>
    fun createUser(user: RegisterRequest): Pair<Boolean, String?> 
    fun updateUser(updatedUser: Profile): Pair<Boolean, String?>
    fun deleteUser(userId: Int): Pair<Boolean, String?>
    fun uploadProfilePicture(userId: Int, pictureUrl: String): Pair<Boolean, String?>
    fun getProfilePicture(userId: Int): ProfilePicture?

}

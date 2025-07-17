package com.mike.domain.repository.user

import com.mike.domain.model.user.*
import com.mike.domain.repository.auth.AuthRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class UserRepositoryImpl(
    private val authRepository: AuthRepository
) : UserRepository {

    override fun findByEmail(email: String): User? = transaction {
        try {
            (Users innerJoin Profiles)
                .selectAll().where { Users.email eq email }
                .singleOrNull()
                ?.let { resultRow ->
                    mapToUser(resultRow)
                }
        } catch (e: Exception) {
            println("Error finding user by email: ${e.message}")
            null
        }
    }

    override fun findById(userId: Int): User? = transaction {
        try {
            (Users innerJoin Profiles)
                .selectAll().where { Users.userId eq userId }
                .singleOrNull()
                ?.let { resultRow ->
                    mapToUser(resultRow)
                }
        } catch (e: Exception) {
            println("Error finding user by ID: ${e.message}")
            null
        }
    }

    override fun fundUserProfile(userId: Int): Profile? = transaction {
        try {
            (Profiles innerJoin Users)
                .selectAll().where { Profiles.userId eq userId }
                .singleOrNull()
                ?.let { resultRow ->
                    mapToProfile(resultRow)
                }
        } catch (e: Exception) {
            println("Error finding user profile: ${e.message}")
            null
        }
    }

    override fun findUserRole(userId: Int): String? = transaction {
        try {
            Users.selectAll().where { Users.userId eq userId }
                .singleOrNull()?.get(Users.role)
        } catch (e: Exception) {
            println("Error finding user role: ${e.message}")
            null
        }
    }

    override fun getAllUsers(): List<Profile> = transaction {
        try {
            (Profiles innerJoin Users)
                .selectAll()
                .map { resultRow ->
                    mapToProfile(resultRow)
                }
        } catch (e: Exception) {
            println("Error retrieving all users: ${e.message}")
            emptyList()
        }
    }

    override fun createUser(user: RegisterRequest): Pair<Boolean, String?> = transaction {
        try {
            val existingUser = Users.selectAll().where { Users.email eq user.email }.singleOrNull()
            if (existingUser != null) {
                return@transaction Pair(false, "User with email ${user.email} already exists")
            }
            if (user.email.isBlank()) {
                return@transaction Pair(false, "Email is required")
            }
            if (user.password.isBlank()) {
                return@transaction Pair(false, "Password is required")
            }
            val now = LocalDateTime.now()
            val userId = Users.insert {
                it[email] = user.email
                it[passwordHash] = authRepository.hashPassword(user.password)
                it[role] = "USER"
            } get Users.userId
            Profiles.insert {
                it[Profiles.userId] = userId
                it[firstName] = user.firstName
                it[lastName] = user.lastName
                it[phoneNumber] = user.phoneNumber
                it[email] = user.email
                it[createdAt] = now
                it[updatedAt] = now
            }
            Pair(true, null)
        } catch (e: Exception) {
            Pair(false, "Failed to create user: ${e.message}")
        }
    }

    override fun updateUser(updatedUser: Profile): Pair<Boolean, String?> = transaction {
        try {
            Users.selectAll().where { Users.userId eq updatedUser.userId }
                .singleOrNull() ?: return@transaction Pair(false, "User not found")
            Profiles.update({ Profiles.userId eq updatedUser.userId }) {
                it[firstName] = updatedUser.firstName
                it[lastName] = updatedUser.lastName
                it[phoneNumber] = updatedUser.phoneNumber
                it[updatedAt] = LocalDateTime.now()
            }
            Pair(true, null)
        } catch (e: Exception) {
            Pair(false, "Failed to update user: ${e.message}")
        }
    }

    override fun deleteUser(userId: Int): Pair<Boolean, String?> = transaction {
        try {
            Users.selectAll().where { Users.userId eq userId }
                .singleOrNull() ?: return@transaction Pair(false, "User not found")
            ProfilePictures.deleteWhere { ProfilePictures.userId eq userId }
            Profiles.deleteWhere { Profiles.userId eq userId }
            Users.deleteWhere { Users.userId eq userId }
            Pair(true, null)
        } catch (e: Exception) {
            Pair(false, "Failed to delete user: ${e.message}")
        }
    }

    override fun uploadProfilePicture(userId: Int, pictureUrl: String): Pair<Boolean, String?> = transaction {
        try {
            Users.selectAll().where { Users.userId eq userId }
                .singleOrNull() ?: return@transaction Pair(false, "User not found")
            val now = LocalDateTime.now()
            val existingPicture = ProfilePictures.selectAll().where { ProfilePictures.userId eq userId }.singleOrNull()
            if (existingPicture != null) {
                ProfilePictures.update({ ProfilePictures.userId eq userId }) {
                    it[ProfilePictures.pictureUrl] = pictureUrl
                    it[updatedAt] = now
                }
            } else {
                ProfilePictures.insert {
                    it[ProfilePictures.userId] = userId
                    it[ProfilePictures.pictureUrl] = pictureUrl
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
            Pair(true, null)
        } catch (e: Exception) {
            Pair(false, "Failed to upload profile picture: ${e.message}")
        }
    }

    override fun getProfilePicture(userId: Int): ProfilePicture? = transaction {
        try {
            ProfilePictures.selectAll().where { ProfilePictures.userId eq userId }
                .singleOrNull()
                ?.let { resultRow ->
                    ProfilePicture(
                        userId = resultRow[ProfilePictures.userId],
                        pictureUrl = resultRow[ProfilePictures.pictureUrl],
                        createdAt = resultRow[ProfilePictures.createdAt],
                        updatedAt = resultRow[ProfilePictures.updatedAt]
                    )
                }
        } catch (e: Exception) {
            println("Error retrieving profile picture: ${e.message}")
            null
        }
    }

    private fun mapToUser(row: ResultRow): User {
        return User(
            id = row[Users.userId],
            email = row[Users.email],
            passwordHash = row[Users.passwordHash],
            role = row[Users.role],
            active = true, // Assuming users are active by default
        )
    }

    private val mapToProfile: (row: ResultRow) -> Profile = { row ->
        val profilePicture = ProfilePictures
            .selectAll().where { ProfilePictures.userId eq row[Users.userId] }
            .singleOrNull()
            ?.let { it[ProfilePictures.pictureUrl] }
        Profile(
            userId = row[Users.userId],
            email = row[Users.email],
            firstName = row[Profiles.firstName],
            lastName = row[Profiles.lastName],
            phoneNumber = row[Profiles.phoneNumber],
            userRole = row[Users.role],
            createdAt = row[Profiles.createdAt],
            updatedAt = row[Profiles.updatedAt],
            profilePictureUrl = profilePicture
        )
    }
}

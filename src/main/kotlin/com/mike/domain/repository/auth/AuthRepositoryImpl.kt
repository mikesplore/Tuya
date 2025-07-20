package com.mike.domain.repository.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.mike.auth.JwtService
import com.mike.domain.model.auth.LoginCredentials
import com.mike.domain.model.auth.RefreshToken
import com.mike.domain.model.auth.RefreshTokens
import com.mike.domain.model.user.Profile
import com.mike.domain.model.user.Users
import com.mike.domain.model.user.User
import com.mike.domain.model.user.Profiles
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class AuthRepositoryImpl(
    private val jwtService: JwtService,
) : AuthRepository {

    override fun changePassword(id: String, newPassword: String): Pair<Boolean, String?> = transaction {
        val hashed = hashPassword(newPassword)
        val rows = Users.update({ Users.userId eq id.toInt() }) {
            it[passwordHash] = hashed
        }
        if (rows > 0) true to null else false to "User not found"
    }

    override fun login(loginCredentials: LoginCredentials): Triple<Profile?, String?, String?> = transaction {
        val userRow = Users.selectAll().where { Users.email eq loginCredentials.email }.singleOrNull()
        if (userRow == null) return@transaction Triple(null, null, "User not found")

        val hashedPassword = userRow[Users.passwordHash]
        if (!verifyPassword(loginCredentials.password, hashedPassword)) {
            return@transaction Triple(null, null, "Invalid password")
        }

        val profileRow = (Users innerJoin Profiles)
            .select { Users.userId eq userRow[Users.userId] }
            .singleOrNull()

        // Fetch profile picture URL from profilePictures table
        var profilePictureUrl: String? = null
        val profilePictureRow = Profiles
            .select { Profiles.userId eq userRow[Users.userId] }
            .singleOrNull()
        if (profilePictureRow != null) {
            profilePictureUrl = "/users/${userRow[Users.userId]}/profile-picture"
        }

        if (profileRow == null) {
            Triple(null, null, "Profile not found")
        } else {
            val profile = Profile(
                userId = profileRow[Profiles.userId],
                firstName = profileRow[Profiles.firstName],
                lastName = profileRow[Profiles.lastName],
                phoneNumber = profileRow[Profiles.phoneNumber],
                createdAt = profileRow[Profiles.createdAt],
                updatedAt = profileRow[Profiles.updatedAt],
                userRole = profileRow[Users.role],
                profilePictureUrl = profilePictureUrl
            )

            // Build User domain model for JWT
            val user = User(
                id = userRow[Users.userId],
                email = userRow[Users.email],
                passwordHash = userRow[Users.passwordHash],
                role = userRow[Users.role],

            )

            val accessToken = jwtService.generateAccessToken(user)
            val refreshToken = jwtService.generateRefreshToken(user)

            // Store refresh token in DB
            val now = LocalDateTime.now()
            val expiresAt = now.plusDays(1) // 24 hours from now
            RefreshTokens.insert {
                it[RefreshTokens.userId] = user.id ?: 0
                it[RefreshTokens.token] = refreshToken
                it[RefreshTokens.expiresAt] = expiresAt
                it[RefreshTokens.createdAt] = now
            }

            Triple(profile, accessToken, refreshToken)
        }
    }

    override fun createRefreshToken(userId: Int): Unit = transaction {
        // Get user info for JWT generation
        val userRow = (Users innerJoin Profiles).selectAll().where { Users.userId eq userId }.singleOrNull()
        if (userRow != null) {
            val user = User(
                id = userRow[Users.userId],
                email = userRow[Users.email],
                passwordHash = userRow[Users.passwordHash],
                role = userRow[Users.role],

            )

            val refreshToken = jwtService.generateRefreshToken(user)
            val now = LocalDateTime.now()
            val expiresAt = now.plusDays(1) // 24 hours from now

            RefreshTokens.insert {
                it[RefreshTokens.userId] = userId
                it[RefreshTokens.token] = refreshToken
                it[RefreshTokens.expiresAt] = expiresAt
                it[RefreshTokens.createdAt] = now
            }
        }
    }

    override fun getRefreshToken(token: String): RefreshToken? = transaction {
        RefreshTokens.selectAll().where { RefreshTokens.token eq token }
            .singleOrNull()
            ?.let { row ->
                RefreshToken(
                    id = row[RefreshTokens.id],
                    token = row[RefreshTokens.token],
                    userId = row[RefreshTokens.userId],
                    expiresAt = row[RefreshTokens.expiresAt],
                    createdAt = row[RefreshTokens.createdAt]
                )
            }
    }

    override fun updateRefreshToken(userId: Int): String? = transaction {
        // Get user info for JWT generation
        val userRow = (Users innerJoin Profiles).selectAll().where { Users.userId eq userId }.singleOrNull()
        if (userRow == null) return@transaction null

        val user = User(
            id = userRow[Users.userId],
            email = userRow[Users.email],
            passwordHash = userRow[Users.passwordHash],
            role = userRow[Users.role],
        )

        val newRefreshToken = jwtService.generateRefreshToken(user)
        val now = LocalDateTime.now()
        val newExpiresAt = now.plusDays(1) // 24 hours from now

        // Update all refresh tokens for this user
        val rowsUpdated = RefreshTokens.update({ RefreshTokens.userId eq userId }) {
            it[token] = newRefreshToken
            it[expiresAt] = newExpiresAt
        }

        if (rowsUpdated > 0) newRefreshToken else null
    }

    override fun revokeRefreshToken(token: String): Boolean = transaction {
        val deleted = RefreshTokens.deleteWhere { RefreshTokens.token eq token }
        deleted > 0
    }

    override fun revokeAllUserRefreshTokens(userId: Int): Boolean = transaction {
        val deleted = RefreshTokens.deleteWhere { RefreshTokens.userId eq userId }
        deleted > 0
    }

    override fun verifyToken(token: String): Pair<Boolean, Int?> = transaction {
        val tokenRow = RefreshTokens.selectAll().where { RefreshTokens.token eq token }
            .singleOrNull()
        if (tokenRow != null && tokenRow[RefreshTokens.expiresAt].isAfter(LocalDateTime.now())) {
            true to tokenRow[RefreshTokens.userId]
        } else {
            false to null
        }
    }

    override fun generateAccessTokenForUser(userId: Int): String? = transaction {
        val userRow = (Users innerJoin Profiles).selectAll().where { Users.userId eq userId }.singleOrNull()
        if (userRow == null) return@transaction null

        val user = User(
            id = userRow[Users.userId],
            email = userRow[Users.email],
            passwordHash = userRow[Users.passwordHash],
            role = userRow[Users.role],
        )

        return@transaction jwtService.generateAccessToken(user)
    }

    override fun hashPassword(password: String): String {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray())
    }

    override fun verifyPassword(password: String, hashedPassword: String): Boolean {
        val result = BCrypt.verifyer().verify(password.toCharArray(), hashedPassword)
        return result.verified
    }
}
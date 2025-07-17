package com.mike.service.auth

import com.mike.auth.JwtService
import com.mike.domain.model.auth.LoginCredentials
import com.mike.domain.model.auth.TokenPayload
import com.mike.domain.model.user.Profile
import com.mike.domain.model.user.User
import com.mike.domain.model.user.Users
import com.mike.domain.repository.auth.AuthRepository
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class AuthService(
    private val authRepository: AuthRepository,
    private val jwtService: JwtService
) {

    fun login(loginCredentials: LoginCredentials): Triple<Profile?, String?, String?> {
        return authRepository.login(loginCredentials)
    }

    fun changePassword(userId: String, newPassword: String): Pair<Boolean, String?> {
        return authRepository.changePassword(userId, newPassword)
    }

    fun refreshToken(refreshToken: String): TokenPayload? {
        // Verify the refresh token
        val (isValid, userId) = authRepository.verifyToken(refreshToken)
        if (!isValid || userId == null) {
            return null
        }

        // Generate new refresh token
        val newRefreshToken = authRepository.updateRefreshToken(userId)
        if (newRefreshToken == null) {
            return null
        }

        // Generate new access token
        val newAccessToken = generateAccessTokenForUser(userId)
        if (newAccessToken == null) {
            return null
        }

        return TokenPayload(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken
        )
    }

    fun revokeToken(refreshToken: String): Boolean {
        return authRepository.revokeRefreshToken(refreshToken)
    }

    fun revokeAllUserTokens(userId: Int): Boolean {
        return authRepository.revokeAllUserRefreshTokens(userId)
    }

    fun validateRefreshToken(token: String): Pair<Boolean, Int?> {
        return authRepository.verifyToken(token)
    }

    private fun generateAccessTokenForUser(userId: Int): String? = transaction {
        val userRow = Users.selectAll().where { Users.userId eq userId }.singleOrNull()
        if (userRow == null) return@transaction null

        val user = User(
            id = userRow[Users.userId],
            email = userRow[Users.email],
            passwordHash = userRow[Users.passwordHash],
            role = userRow[Users.role]
        )

        return@transaction jwtService.generateAccessToken(user)
    }
}
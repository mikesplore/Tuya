package com.mike.domain.repository.auth

import com.mike.domain.model.user.*
import java.time.LocalDateTime

interface AuthRepository {
    fun changePassword(id: String, newPassword: String): Pair<Boolean, String?>
    fun login(loginCredentials: LoginCredentials): Triple<Profile?, String?, String?> // Profile, accessToken, refreshToken
    fun createRefreshToken(userId: Int): Unit
    fun getRefreshToken(token: String): RefreshToken?
    fun revokeRefreshToken(token: String): Boolean
    fun revokeAllUserRefreshTokens(userId: Int): Boolean
    fun verifyToken(token: String): Pair<Boolean, Int?> // Returns isValid and userId if valid
    fun updateRefreshToken(userId: Int): String? // Returns new refresh token or null if failed
    fun hashPassword(password: String): String
    fun verifyPassword(password: String, hashedPassword: String): Boolean
    fun generateAccessTokenForUser(userId: Int): String?
}
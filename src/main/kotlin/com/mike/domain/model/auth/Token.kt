package com.mike.domain.model.auth

import com.mike.domain.model.user.Users
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object RefreshTokens : Table() {
    val id = integer("id").autoIncrement()
    val userId = reference("user_id", Users.userId)
    val token = varchar("token", 500)
    val expiresAt = datetime("expires_at")
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}


data class RefreshToken(
    val id: Int? = null,
    val userId: Int,
    val token: String,
    val expiresAt: LocalDateTime,
    val createdAt: LocalDateTime,
    val isRevoked: Boolean = false
)


data class TokenPayload(
    val accessToken: String,
    val refreshToken: String,
)

data class LoginResponse(
    val profile: Any?,
    val accessToken: String,
    val refreshToken: String
)
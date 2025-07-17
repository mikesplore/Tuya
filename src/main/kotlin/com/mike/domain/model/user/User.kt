package com.mike.domain.model.user

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object Users : Table() {
    val userId = integer("user_id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val role = varchar("role", 50).default("USER")
    override val primaryKey = PrimaryKey(userId, name = "PK_User_Id")
}

object Profiles : Table() {
    val userId = reference("user_id", Users.userId)
    val firstName = varchar("first_name", 100).nullable()
    val lastName = varchar("last_name", 100).nullable()
    val phoneNumber = varchar("phone_number", 20).nullable()
    val email = varchar("email", 255).nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    override val primaryKey = PrimaryKey(userId, name = "PK_Profile_User_Id")
}

data class RegisterRequest(
    val email: String,
    val password: String,
    val phoneNumber: String? = null,
    val firstName: String? = null,
    val lastName: String? = null
)


object ProfilePictures : Table() {
    val userId = reference("user_id", Users.userId)
    val pictureUrl = varchar("picture_url", 255)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    override val primaryKey = PrimaryKey(userId, name = "PK_ProfilePicture_User_Id")
}

data class ProfilePicture(
    val userId: Int,
    val pictureUrl: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class Profile(
    val userId: Int,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val userRole: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val profilePictureUrl: String? = null
)

data class User(
    val id: Int? = null,
    val email: String,
    val passwordHash: String,
    val role: String = "USER",
    val active: Boolean = true
)
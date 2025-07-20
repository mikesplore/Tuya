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
    val filename = varchar("filename", 255)
    val contentType = varchar("content_type", 100)
    val data = binary("data")  // For storing the actual image data
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    override val primaryKey = PrimaryKey(userId, name = "PK_ProfilePicture_User_Id")
}

data class ProfilePicture(
    val userId: Int,
    val filename: String,
    val contentType: String,
    val data: ByteArray,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProfilePicture

        if (userId != other.userId) return false
        if (filename != other.filename) return false
        if (contentType != other.contentType) return false
        if (!data.contentEquals(other.data)) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = userId
        result = 31 * result + filename.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}

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

data class ProfileUpdateRequest(
    val userId: Int,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val userRole: String,
    val profilePicture: ByteArray? = null,
    val profilePictureContentType: String? = null,
    val profilePictureFilename: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProfileUpdateRequest

        if (userId != other.userId) return false
        if (firstName != other.firstName) return false
        if (lastName != other.lastName) return false
        if (email != other.email) return false
        if (phoneNumber != other.phoneNumber) return false
        if (userRole != other.userRole) return false
        if (profilePicture != null && other.profilePicture != null) {
            if (!profilePicture.contentEquals(other.profilePicture)) return false
        } else if (!profilePicture.contentEquals(other.profilePicture)) return false
        if (profilePictureContentType != other.profilePictureContentType) return false
        if (profilePictureFilename != other.profilePictureFilename) return false

        return true
    }

    override fun hashCode(): Int {
        var result = userId
        result = 31 * result + (firstName?.hashCode() ?: 0)
        result = 31 * result + (lastName?.hashCode() ?: 0)
        result = 31 * result + (email?.hashCode() ?: 0)
        result = 31 * result + (phoneNumber?.hashCode() ?: 0)
        result = 31 * result + userRole.hashCode()
        result = 31 * result + (profilePicture?.contentHashCode() ?: 0)
        result = 31 * result + (profilePictureContentType?.hashCode() ?: 0)
        result = 31 * result + (profilePictureFilename?.hashCode() ?: 0)
        return result
    }
}

data class User(
    val id: Int? = null,
    val email: String,
    val passwordHash: String,
    val role: String = UserRole.USER.name,
    val active: Boolean = true
)

enum class UserRole {
    ADMIN,
    USER,
}
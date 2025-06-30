package com.mike.database

import at.favre.lib.crypto.bcrypt.BCrypt
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import com.mike.database.tables.Users
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDateTime
import java.util.UUID

/**
 * This class provides a way to initialize the database with sample data.
 * Run this manually when needed, for example when setting up a new environment.
 */
object DatabaseSampleData {
    
    fun init() {
        transaction {
            addLogger(StdOutSqlLogger)
            
            // Create tables if they don't exist
            SchemaUtils.create(Users)
            
            // Add sample users
            val sampleUsers = listOf(
                SampleUser(
                    id = "f47ac10b-58cc-4372-a567-0e02b2c3d479",
                    email = "mikepremium8@gmail.com",
                    phoneNumber = "0799013845",
                    firstName = "Michael",
                    lastName = "Odhiambo",
                    passwordHash = "\$2a\$10\$rZErm5o30bS90JawTrdSI.cAeKr..Z/EDbNY9x2zy8PPqtX42ue0O",
                    role = "ADMIN",
                    isActive = true,
                    createdAt = LocalDateTime.parse("2025-06-29T11:12:02.834364"),
                    updatedAt = LocalDateTime.parse("2025-06-29T11:12:02.834364")
                ),
                SampleUser(
                    id = "e47ac10b-58cc-4372-a567-0e02b2c3d480",
                    email = "tuya@admin.com",
                    phoneNumber = "0700000000",
                    firstName = "Tuya",
                    lastName = "Admin",
                    passwordHash = "\$2a\$10\$rZErm5o30bS90JawTrdSI.cAeKr..Z/EDbNY9x2zy8PPqtX42ue0O",
                    role = "ADMIN",
                    isActive = true,
                    createdAt = LocalDateTime.parse("2025-06-29T11:26:52.630098"),
                    updatedAt = LocalDateTime.parse("2025-06-29T11:26:52.630098")
                ),
                SampleUser(
                    id = "d47ac10b-58cc-4372-a567-0e02b2c3d481",
                    email = "ericotienoa@gmail.com",
                    phoneNumber = "0725698340",
                    firstName = "Eric",
                    lastName = "Akumu",
                    passwordHash = "\$2a\$10\$B9Y1qxA9X/QhOMJ5ysWn0eRCp8sSe3GAX83gSHV.MsWchNWgj8jey",
                    role = "ADMIN",
                    isActive = true,
                    createdAt = LocalDateTime.parse("2025-06-29T11:41:20.215765"),
                    updatedAt = LocalDateTime.parse("2025-06-29T11:41:20.215765")
                ),
                SampleUser(
                    id = "c47ac10b-58cc-4372-a567-0e02b2c3d482",
                    email = "admin@tuya.com",
                    phoneNumber = "254700000000",
                    firstName = "Admin",
                    lastName = "User",
                    passwordHash = "\$2a\$10\$tiZVxDy.PWqJBz0LVT2BbODyh3cW.sLHALT/x5wYJ4AoJi20sctca",
                    role = "CUSTOMER",
                    isActive = true,
                    createdAt = LocalDateTime.parse("2025-06-29T13:27:09.843195"),
                    updatedAt = LocalDateTime.parse("2025-06-29T13:27:09.843195")
                )
            )
            
            // Insert users if they don't already exist
            sampleUsers.forEach { user ->
                val count = Users.selectAll().where { Users.email eq user.email }.count()
                if (count == 0L) {
                    Users.insert {
                        it[id] = UUID.fromString(user.id)
                        it[email] = user.email
                        it[phoneNumber] = user.phoneNumber
                        it[passwordHash] = user.passwordHash
                        it[firstName] = user.firstName
                        it[lastName] = user.lastName
                        it[role] = user.role
                        it[active] = user.isActive
                        it[createdAt] = user.createdAt
                        it[updatedAt] = user.updatedAt
                    }
                    println("Added user: ${user.email}")
                } else {
                    println("User already exists: ${user.email}")
                }
            }
        }
        
        println("Database sample data initialization completed")
    }
    
    /**
     * Helper method to hash a password using BCrypt
     */
    fun hashPassword(password: String): String {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray())
    }
    
    /**
     * Data class to hold sample user information
     */
    data class SampleUser(
        val id: String,
        val email: String,
        val phoneNumber: String,
        val firstName: String,
        val lastName: String,
        val passwordHash: String,
        val role: String,
        val isActive: Boolean,
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime
    )
}

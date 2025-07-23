package com.mike.database

import com.mike.domain.model.auth.RefreshTokens
import com.mike.domain.model.meter.MeterPayments
import com.mike.domain.model.meter.MeterUserAssignments
import com.mike.domain.model.meter.Meters
import com.mike.domain.model.mpesa.MpesaTransactions
import com.mike.domain.model.user.ProfilePictures
import com.mike.domain.model.user.Profiles
import com.mike.domain.model.user.Users
import io.ktor.server.config.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(this::class.java)
    
    fun init(config: ApplicationConfig) {
        val driver = config.property("database.driver").getString()
        var url = config.property("database.url").getString()
        val user = config.property("database.user").getString()
        val password = config.property("database.password").getString()
        
        // Ensure the URL is in the correct JDBC format
        if (!url.startsWith("jdbc:")) {
            // Convert from postgresql:// format to jdbc:postgresql:// format
            if (url.startsWith("postgresql://")) {
                url = "jdbc:" + url
            }
        }

        // Extract username:password if present in the URL
        var extractedUser = user
        var extractedPassword = password

        // Pattern to match username:password in JDBC URL
        val credentialsPattern = "jdbc:postgresql://(.*?):(.*?)@(.*)".toRegex()
        val match = credentialsPattern.find(url)

        if (match != null && match.groupValues.size >= 4) {
            extractedUser = match.groupValues[1]
            extractedPassword = match.groupValues[2]
            val hostPart = match.groupValues[3]
            url = "jdbc:postgresql://$hostPart"
            logger.info("Extracted credentials from URL. Using host: $hostPart")
        }

        logger.info("Connecting to database at: $url")
        logger.info("Using driver: $driver")
        logger.info("Using user: $extractedUser")

        try {
            // Connection using Exposed
            Database.connect(
                url = url,
                driver = driver,
                user = extractedUser,
                password = extractedPassword
            )
            
            // Create tables if they don't exist
            transaction {
                SchemaUtils.create(
                    Users,
                    Profiles,
                    Meters,
                    MpesaTransactions,
                    MeterPayments,
                    ProfilePictures,
                    RefreshTokens,
                    MeterUserAssignments
                )
            }
            logger.info("Database connected and tables created successfully")
        } catch (e: Exception) {
            logger.error("Failed to connect to database using the credentials: User: $user and password: $password: ${e.message}", e)
            throw e
        }
    }
}

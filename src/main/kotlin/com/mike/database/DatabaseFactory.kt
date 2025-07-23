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

        logger.info("Connecting to database at: $url")
        
        try {
            // Connection using Exposed
            Database.connect(
                url = url,
                driver = driver,
                user = user,
                password = password
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
            logger.error("Failed to connect to database: ${e.message}", e)
            throw e
        }
    }
}

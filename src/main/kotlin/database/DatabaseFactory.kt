package com.mike.database

import io.ktor.server.config.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import com.mike.database.tables.*
import org.slf4j.LoggerFactory

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(this::class.java)
    
    fun init(config: ApplicationConfig) {
        val driver = config.property("database.driver").getString()
        val url = config.property("database.url").getString()
        val user = config.property("database.user").getString()
        val password = config.property("database.password").getString()
        
        logger.info("Connecting to database at: $url")
        
        try {
            // Simple direct connection using Exposed
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
                    Meters,
                    UserMeterAssignments
                )
            }
            logger.info("Database connected and tables created successfully")
        } catch (e: Exception) {
            logger.error("Failed to connect to database: ${e.message}", e)
            throw e
        }
    }
}

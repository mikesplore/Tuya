package com.mike.database

import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.config.*
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("DatabaseInit")
    //logger.info("Starting database initialization...")
    
    // Load .env file if it exists
    val dotenv = dotenv {
        ignoreIfMissing = true
    }
    
    // Create a config object with the database settings
    val config = MapApplicationConfig().apply {
        // Database configuration
        put("database.driver", "org.postgresql.Driver")
        put("database.url", dotenv["DATABASE_URL"] ?: "jdbc:postgresql://localhost:5432/tuya")
        put("database.user", dotenv["DATABASE_USER"] ?: "mike")
        put("database.password", dotenv["DATABASE_PASSWORD"] ?: "mikemike")
    }
    
    try {
        // Initialize the database connection
        DatabaseFactory.init(config)
        
       // logger.info("Database initialization completed successfully")
    } catch (e: Exception) {
        logger.error("Error initializing database: ${e.message}", e)
    }
}

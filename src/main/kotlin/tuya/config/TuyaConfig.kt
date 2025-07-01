package com.mike.tuya.config

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.*
import java.io.File

// Initialize dotenv for use throughout the application
val dotenv: Dotenv by lazy {
    // Ensure we're in the project root directory
    val currentDir = System.getProperty("user.dir")

    dotenv {
        ignoreIfMissing = true
        directory = currentDir
    }
}

data class TuyaConfig(
    val accessId: String,
    val accessSecret: String,
    val endpoint: String = "https://openapi.tuyaeu.com",
    val projectCode: String? = null,
    val deviceId: String? = null
)

fun Application.getTuyaConfig(): TuyaConfig {
    val config = environment.config
    
    // Load .env directly to ensure it's properly initialized
    val directDotenv = io.github.cdimascio.dotenv.dotenv {
        ignoreIfMissing = true
        directory = System.getProperty("user.dir")
    }
    
    // Try to load from direct dotenv first, then config, then system env
    val accessId = directDotenv["ACCESS_ID"] 
        ?: config.propertyOrNull("tuya.access_id")?.getString()
        ?: System.getenv("ACCESS_ID") 
        ?: ""
    
    val accessSecret = directDotenv["ACCESS_SECRET"]
        ?: config.propertyOrNull("tuya.access_secret")?.getString() 
        ?: System.getenv("ACCESS_SECRET") 
        ?: ""
    
    val endpoint = directDotenv["TUYA_ENDPOINT"]
        ?: config.propertyOrNull("tuya.endpoint")?.getString() 
        ?: System.getenv("TUYA_ENDPOINT") 
        ?: "https://openapi.tuyaeu.com"
    
    // Check if credentials are missing
    if (accessId.isBlank() || accessSecret.isBlank()) {
        println("⚠️ WARNING: Missing Tuya Cloud credentials!")
    }
    
    return TuyaConfig(
        accessId = accessId,
        accessSecret = accessSecret,
        endpoint = endpoint,
        projectCode = config.propertyOrNull("tuya.project_code")?.getString() 
            ?: dotenv["PROJECT_CODE"]
            ?: System.getenv("PROJECT_CODE"),
        deviceId = config.propertyOrNull("tuya.device_id")?.getString() 
            ?: dotenv["DEVICE_ID"]
            ?: System.getenv("DEVICE_ID")
    )
}

package com.mike.tuya.config

import io.github.cdimascio.dotenv.dotenv
import java.io.File

/**
 * A simple utility to test .env file loading
 * This is just for debugging purposes
 */
fun main() {
    println("📂 Current directory: ${System.getProperty("user.dir")}")
    
    // Try direct file read first
    val envFile = File(".env")
    if (envFile.exists()) {
        println("✅ .env file exists at ${envFile.absolutePath}")
        println("📄 File size: ${envFile.length()} bytes")
        println("📄 Content preview:")
        
        envFile.readLines().forEach { line ->
            when {
                line.startsWith("ACCESS_ID=") -> println("   ACCESS_ID=${line.substringAfter("=").take(5)}*** (${line.substringAfter("=").length} chars)")
                line.startsWith("ACCESS_SECRET=") -> println("   ACCESS_SECRET=${line.substringAfter("=").take(5)}*** (${line.substringAfter("=").length} chars)")
                line.startsWith("#") || line.isBlank() -> println("   $line") // Show comments and empty lines
                else -> println("   ${line.substringBefore("=")}=*** (${line.substringAfter("=").length} chars)")
            }
        }
    } else {
        println("❌ .env file NOT found at ${envFile.absolutePath}")
    }
    
    // Now try with dotenv library
    println("\n🔧 Testing dotenv library:")
    try {
        val dotenv = dotenv {
            ignoreIfMissing = true
            directory = "."
        }
        
        val accessId = dotenv["ACCESS_ID"] ?: ""
        val accessSecret = dotenv["ACCESS_SECRET"] ?: ""
        
        println("   ACCESS_ID from dotenv: ${if (accessId.isNotBlank()) "${accessId.take(5)}*** (${accessId.length} chars)" else "NOT FOUND"}")
        println("   ACCESS_SECRET from dotenv: ${if (accessSecret.isNotBlank()) "${accessSecret.take(5)}*** (${accessSecret.length} chars)" else "NOT FOUND"}")
    } catch (e: Exception) {
        println("❌ Error loading with dotenv: ${e.message}")
        e.printStackTrace()
    }
    
    // Try system environment
    println("\n🔧 Testing System.getenv():")
    val sysAccessId = System.getenv("ACCESS_ID") ?: ""
    val sysAccessSecret = System.getenv("ACCESS_SECRET") ?: ""
    
    println("   ACCESS_ID from System.getenv: ${if (sysAccessId.isNotBlank()) "${sysAccessId.take(5)}*** (${sysAccessId.length} chars)" else "NOT FOUND"}")
    println("   ACCESS_SECRET from System.getenv: ${if (sysAccessSecret.isNotBlank()) "${sysAccessSecret.take(5)}*** (${sysAccessSecret.length} chars)" else "NOT FOUND"}")
}

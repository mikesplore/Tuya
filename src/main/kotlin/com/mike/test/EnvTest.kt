package com.mike.test

import io.github.cdimascio.dotenv.dotenv
import java.io.File

/**
 * Simple utility to test .env file loading without Ktor dependencies
 * Run with: ./gradlew testEnvDirect
 */
object EnvTest {
    @JvmStatic
    fun main(args: Array<String>) {
        println("\n🔍 DIRECT ENV TEST")
        println("=================")
        
        // Check current directory
        val currentDir = System.getProperty("user.dir")
        println("📂 Current directory: $currentDir")
        
        // Check .env file exists
        val envFile = File("$currentDir/.env")
        if (envFile.exists()) {
            println("✅ .env file exists at: ${envFile.absolutePath}")
            println("📄 File size: ${envFile.length()} bytes")
            
            // Read file directly without dotenv
            println("\n📄 Direct file content (first 3 lines):")
            envFile.readLines().take(3).forEach { line ->
                if (line.contains("=")) {
                    val key = line.substringBefore("=")
                    val value = line.substringAfter("=")
                    println("   $key=${value.take(5)}*** (${value.length} chars)")
                } else {
                    println("   $line")
                }
            }
            
            // Test with dotenv library
            println("\n🔧 Testing dotenv library:")
            try {
                val dotenv = dotenv {
                    ignoreIfMissing = true
                    directory = currentDir
                }
                
                val accessId = dotenv["ACCESS_ID"]
                val accessSecret = dotenv["ACCESS_SECRET"]
                
                println("   ACCESS_ID from dotenv: ${if (!accessId.isNullOrBlank()) "${accessId.take(5)}*** (${accessId.length} chars)" else "NOT FOUND"}")
                println("   ACCESS_SECRET from dotenv: ${if (!accessSecret.isNullOrBlank()) "${accessSecret.take(5)}*** (${accessSecret.length} chars)" else "NOT FOUND"}")
                
                // Test all env variables
                println("\n📋 All .env variables loaded by dotenv:")
                dotenv.entries().forEach { entry ->
                    val key = entry.key
                    val value = entry.value
                    println("   $key: ${if (value.isNotBlank()) "${value.take(5)}*** (${value.length} chars)" else "EMPTY"}")
                }
                
            } catch (e: Exception) {
                println("❌ Error using dotenv: ${e.message}")
                e.printStackTrace()
            }
        } else {
            println("❌ .env file not found at expected location: ${envFile.absolutePath}")
            
            // Check parent directories
            var parent = File(currentDir).parentFile
            while (parent != null) {
                val parentEnvFile = File(parent, ".env")
                if (parentEnvFile.exists()) {
                    println("✅ Found .env file in parent directory: ${parentEnvFile.absolutePath}")
                    break
                }
                parent = parent.parentFile
            }
        }
    }
}

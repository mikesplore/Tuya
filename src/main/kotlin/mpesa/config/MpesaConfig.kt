package com.mike.mpesa.config

import io.github.cdimascio.dotenv.dotenv
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

data class MpesaConfig(
    val consumerKey: String,
    val consumerSecret: String,
    val shortCode: String,
    val passkey: String = "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919", // Sandbox passkey
    val baseUrl: String = "https://sandbox.safaricom.co.ke", // Sandbox URL
    val callbackUrl: String = "https://your-domain.com/api/mpesa/callback" // Update with your actual callback URL
)

fun getMpesaConfig(): MpesaConfig {
    val dotenv = dotenv {
        directory = "."
        ignoreIfMissing = true
    }

    return MpesaConfig(
        consumerKey = dotenv["CONSUMER_KEY"] ?: throw IllegalStateException("CONSUMER_KEY not found in .env"),
        consumerSecret = dotenv["CONSUMER_SECRET"] ?: throw IllegalStateException("CONSUMER_SECRET not found in .env"),
        shortCode = dotenv["SHORT_CODE"] ?: throw IllegalStateException("SHORT_CODE not found in .env"),
        callbackUrl = dotenv["MPESA_CALLBACK_URL"] ?: "http://localhost:8080/api/mpesa/callback"
    )
}

// Utility function to generate M-Pesa password
fun generateMpesaPassword(shortCode: String, passkey: String, timestamp: String): String {
    val data = "$shortCode$passkey$timestamp"
    return Base64.getEncoder().encodeToString(data.toByteArray())
}

// Utility function to generate timestamp
fun generateTimestamp(): String {
    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    return java.time.LocalDateTime.now().format(formatter)
}

// Utility function to format phone number for M-Pesa
fun formatPhoneNumber(phoneNumber: String): String {
    // Remove any non-digit characters
    val digits = phoneNumber.replace(Regex("[^0-9]"), "")

    return when {
        digits.startsWith("254") -> digits
        digits.startsWith("0") -> "254${digits.substring(1)}"
        digits.startsWith("7") || digits.startsWith("1") -> "254$digits"
        else -> digits
    }
}

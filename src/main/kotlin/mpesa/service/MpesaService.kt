package com.mike.mpesa.service

import com.google.gson.Gson
import com.mike.database.entities.MpesaTransactionDto
import com.mike.database.repository.MpesaTransactionRepository
import com.mike.mpesa.config.*
import com.mike.mpesa.model.*
import database.repository.MeterPaymentRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class MpesaService(
    private val httpClient: HttpClient,
    private val mpesaConfig: MpesaConfig,
    private val mpesaTransactionRepository: MpesaTransactionRepository,
    private val meterPaymentRepository: MeterPaymentRepository
) {
    private val gson = Gson()
    private var accessToken: String? = null
    private var tokenExpiresAt: LocalDateTime? = null

    // Get access token from M-Pesa API
    private suspend fun getAccessToken(): String {
        // Check if we have a valid token
        if (accessToken != null && tokenExpiresAt != null && LocalDateTime.now().isBefore(tokenExpiresAt)) {
            return accessToken!!
        }

        val credentials = Base64.getEncoder().encodeToString(
            "${mpesaConfig.consumerKey}:${mpesaConfig.consumerSecret}".toByteArray()
        )

        val response: HttpResponse = httpClient.get("${mpesaConfig.baseUrl}/oauth/v1/generate?grant_type=client_credentials") {
            headers {
                append(HttpHeaders.Authorization, "Basic $credentials")
            }
        }

        if (response.status == HttpStatusCode.OK) {
            val tokenResponse = gson.fromJson(response.bodyAsText(), AccessTokenResponse::class.java)
            accessToken = tokenResponse.access_token
            // M-Pesa tokens expire in 3599 seconds, we'll refresh 1 minute early
            tokenExpiresAt = LocalDateTime.now().plusSeconds(3539)
            return accessToken!!
        } else {
            throw Exception("Failed to get access token: ${response.status}")
        }
    }

    // Initiate STK Push payment with retry mechanism
    suspend fun initiatePayment(
        amount: BigDecimal,
        phoneNumber: String,
        meterId: UUID,
        userId: UUID,
        accountReference: String = "MeterPayment",
        description: String = "Meter top-up payment",
        maxRetries: Int = 2
    ): PaymentResponse {
        var retryCount = 0
        var lastException: Exception? = null
        
        while (retryCount <= maxRetries) {
            try {
                println("Initiating payment attempt ${retryCount + 1}/${maxRetries + 1}")
                val token = getAccessToken()
                val timestamp = generateTimestamp()
                val password = generateMpesaPassword(mpesaConfig.shortCode, mpesaConfig.passkey, timestamp)
                val formattedPhone = formatPhoneNumber(phoneNumber)

                val stkRequest = StkPushRequest(
                    businessShortCode = mpesaConfig.shortCode,
                    password = password,
                    timestamp = timestamp,
                    amount = amount,
                    partyA = formattedPhone,
                    partyB = mpesaConfig.shortCode,
                    phoneNumber = formattedPhone,
                    callBackURL = mpesaConfig.callbackUrl,
                    accountReference = accountReference,
                    transactionDesc = description
                )

                val response: HttpResponse = httpClient.post("${mpesaConfig.baseUrl}/mpesa/stkpush/v1/processrequest") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $token")
                        append(HttpHeaders.ContentType, "application/json")
                    }
                    setBody(gson.toJson(stkRequest))
                    timeout {
                        requestTimeoutMillis = 30000 // 30 seconds timeout
                    }
                }

                val responseBody = response.bodyAsText()
                println("M-Pesa STK push response: $responseBody")
                val stkResponse = gson.fromJson(responseBody, StkPushResponse::class.java)

                // Check if response indicates success or a retry-able error
                if (stkResponse.responseCode == null) {
                    throw Exception("M-Pesa API returned null response code")
                }

                // Create M-Pesa transaction record
                val mpesaTransaction = mpesaTransactionRepository.createTransaction(
                    amount = amount,
                    phoneNumber = formattedPhone,
                    merchantRequestId = stkResponse.merchantRequestID,
                    checkoutRequestId = stkResponse.checkoutRequestID,
                    responseCode = stkResponse.responseCode,
                    responseDescription = stkResponse.responseDescription,
                    customerMessage = stkResponse.customerMessage
                )

                // Create meter payment record if M-Pesa transaction was created successfully
                val meterPayment = if (stkResponse.responseCode == "0") {
                    meterPaymentRepository.createPayment(
                        userId = userId,
                        meterId = meterId,
                        mpesaTransactionId = UUID.fromString(mpesaTransaction.id),
                        amount = amount,
                        description = description
                    )
                    mpesaTransaction.id
                } else {
                    null
                }

                return PaymentResponse(
                    success = stkResponse.responseCode == "0",
                    message = stkResponse.customerMessage ?: stkResponse.responseDescription ?: "Payment initiated",
                    merchantRequestId = stkResponse.merchantRequestID,
                    checkoutRequestId = stkResponse.checkoutRequestID,
                    mpesaTransactionId = meterPayment
                )

            } catch (e: Exception) {
                lastException = e
                println("Payment attempt ${retryCount + 1} failed: ${e.message}")
                
                if (retryCount < maxRetries) {
                    val backoffMs = (1000 * (retryCount + 1)).toLong() // Simple backoff strategy
                    println("Retrying in ${backoffMs/1000} seconds...")
                    kotlinx.coroutines.delay(backoffMs)
                    retryCount++
                } else {
                    println("All retry attempts failed")
                    break
                }
            }
        }

        return PaymentResponse(
            success = false,
            message = "Payment initiation failed after $maxRetries retries: ${lastException?.message}",
            merchantRequestId = null,
            checkoutRequestId = null,
            mpesaTransactionId = null
        )
    }

    // Process M-Pesa callback - Enhanced version with better logging
    fun processCallback(callbackData: StkCallbackResponse): Boolean {
        println("=== CALLBACK ENDPOINT REACHED ===") // Log: callback reached
        return try {
            val callback = callbackData.body.stkCallback
            println("=== M-PESA CALLBACK PROCESSING ===")
            println("Processing callback with result code: ${callback.resultCode}")
            println("Result description: ${callback.resultDesc}")
            println("Checkout Request ID: ${callback.checkoutRequestID}")
            println("Merchant Request ID: ${callback.merchantRequestID}")

            // Extract callback metadata
            var mpesaReceiptNumber: String? = null
            var transactionDate: LocalDateTime? = null
            var amount: BigDecimal?
            var phoneNumber: String?

            // User cancellation or failure codes
            val cancellationCodes = setOf(1032, 1031, 1037)
            val isUserCancellation = cancellationCodes.contains(callback.resultCode)
            
            // Only process metadata if payment was successful
            if (callback.resultCode == 0) {
                println("Payment has been made successfully!") // Log: payment successful
                
                if (callback.callbackMetadata == null) {
                    println("WARNING: Callback metadata is null even though result code is 0")
                    // Even with a success code, metadata might be missing - handle gracefully
                } else {
                    // Map callback items by name for easier access
                    val metadataMap = callback.callbackMetadata.item.associateBy { it.name }
                    println("Available metadata items: ${metadataMap.keys}")

                    // Extract M-Pesa receipt number
                    metadataMap["MpesaReceiptNumber"]?.value?.let { value ->
                        mpesaReceiptNumber = value.toString()
                        println("Extracted M-Pesa receipt number: $mpesaReceiptNumber")
                    }

                    // Extract and parse transaction date
                    metadataMap["TransactionDate"]?.value?.let { value ->
                        println("Raw transaction date value type: ${value::class.java.name}")
                        println("Raw transaction date value: $value")

                        try {
                            // Convert value to Long, then to a 14-digit string
                            val dateLong = when (value) {
                                is Number -> value.toLong()
                                is String -> value.toLongOrNull() ?: throw IllegalArgumentException("Invalid TransactionDate string: $value")
                                else -> throw IllegalArgumentException("Unsupported TransactionDate type: ${value::class.java.name}")
                            }
                            val dateStr = String.format("%014d", dateLong)
                            println("Normalized date string: $dateStr")

                            val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                            transactionDate = LocalDateTime.parse(dateStr, formatter)
                            println("Successfully parsed transaction date: $transactionDate")
                        } catch (e: Exception) {
                            println("Failed to parse TransactionDate: $value - ${e.message}")
                            e.printStackTrace()
                        }
                    }

                    // Extract amount
                    metadataMap["Amount"]?.value?.toString()?.toBigDecimalOrNull()?.let {
                        amount = it
                        println("Extracted amount: $amount")
                    }

                    // Extract phone number
                    metadataMap["PhoneNumber"]?.value?.toString()?.let {
                        phoneNumber = it
                        println("Extracted phone number: $phoneNumber")
                    }
                }
            } else if (isUserCancellation) {
                println("Payment was CANCELLED by user. Result code: ${callback.resultCode}") // Log: payment cancelled
            } else {
                println("Payment FAILED with code ${callback.resultCode}: ${callback.resultDesc}") // Log: payment failed
                println("Skipping metadata extraction for failed payment")
            }

            // Update M-Pesa transaction
            println("Updating transaction for checkout request ID: ${callback.checkoutRequestID}")
            val updatedTransaction = mpesaTransactionRepository.updateTransactionFromCallback(
                checkoutRequestId = callback.checkoutRequestID,
                resultCode = callback.resultCode.toString(),
                resultDesc = callback.resultDesc,
                mpesaReceiptNumber = mpesaReceiptNumber,
                transactionDate = transactionDate
            )

            println("Transaction update result: $updatedTransaction")
            if (updatedTransaction == null) {
                println("WARNING: No transaction found with CheckoutRequestID: ${callback.checkoutRequestID}")
                return false
            }

            // Update meter payment status
            println("Looking up meter payment for transaction ID: ${updatedTransaction.id}")
            val meterPayment = meterPaymentRepository.getPaymentsByMpesaTransactionId(
                UUID.fromString(updatedTransaction.id)
            )

            if (meterPayment != null) {
                val newStatus = if (callback.resultCode == 0) "COMPLETED" else "FAILED"
                println("Found meter payment: ${meterPayment.id}, updating status to $newStatus")

                meterPaymentRepository.updatePaymentStatus(
                    paymentId = UUID.fromString(meterPayment.id),
                    status = newStatus
                    // TODO: Add logic to calculate units added and update meter balance
                )
                println("Payment status updated successfully to $newStatus")
            } else {
                println("WARNING: No meter payment found for transaction ID: ${updatedTransaction.id}")
            }

            println("=== CALLBACK PROCESSING COMPLETED SUCCESSFULLY ===")
            true
        } catch (e: Exception) {
            println("=== CALLBACK PROCESSING FAILED ===")
            println("Error processing M-Pesa callback: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    // Query payment status
    fun queryPaymentStatus(checkoutRequestId: String): MpesaTransactionDto? {
        return mpesaTransactionRepository.getTransactionByCheckoutRequestId(checkoutRequestId)
    }

    // Additional helper methods

    /**
     * Extract M-Pesa common result codes and their meanings
     */
    private fun getResultCodeMeaning(resultCode: Int): String {
        return when (resultCode) {
            0 -> "Success"
            1 -> "General failure"
            1001 -> "Payment rejected by customer"
            1002 -> "Insufficient funds"
            1003 -> "Limit exceeded"
            1004 -> "Transaction canceled by system"
            1031 -> "Ongoing transaction"
            1032 -> "Request cancelled by user"
            1037 -> "Customer timeout"
            2001 -> "Invalid access token"
            1024 -> "API rate limit reached"
            else -> "Unknown error code"
        }
    }

    /**
     * Extract a specific metadata value from the M-Pesa callback items
     */
    private fun extractMetadataValue(items: List<CallbackItem>?, name: String): Any? {
        if (items == null) return null
        return items.find { it.name == name }?.value
    }

    /**
     * Check for transactions that haven't received callbacks and mark them as timed out
     * This should be called periodically by a scheduled job
     */
    fun handleStalledTransactions(timeoutMinutes: Int = 5): Int {
        println("Checking for stalled M-Pesa transactions...")
        val cutoffTime = LocalDateTime.now().minusMinutes(timeoutMinutes.toLong())
        
        // Get pending transactions that were created before the cutoff time
        val stalledTransactions = mpesaTransactionRepository.getPendingTransactionsOlderThan(cutoffTime)
        
        if (stalledTransactions.isEmpty()) {
            println("No stalled transactions found")
            return 0
        }
        
        println("Found ${stalledTransactions.size} stalled transactions")
        var updatedCount = 0
        
        stalledTransactions.forEach { transaction ->
            println("Processing stalled transaction: ${transaction.id}, checkout request ID: ${transaction.checkoutRequestId}")
            
            try {
                // Update transaction status
                val updatedTransaction = transaction.checkoutRequestId?.let {
                    mpesaTransactionRepository.updateTransactionFromTimeout(
                        checkoutRequestId = it,
                        resultDesc = "Transaction timed out waiting for callback"
                    )
                }
                
                if (updatedTransaction != null) {
                    // Update corresponding payment
                    val payment = meterPaymentRepository.getPaymentsByMpesaTransactionId(
                        UUID.fromString(updatedTransaction.id)
                    )
                    
                    if (payment != null) {
                        meterPaymentRepository.updatePaymentStatus(
                            paymentId = UUID.fromString(payment.id),
                            status = "FAILED"
                        )
                        println("Updated payment ${payment.id} status to FAILED due to timeout")
                    }
                    
                    updatedCount++
                }
            } catch (e: Exception) {
                println("Error handling stalled transaction ${transaction.id}: ${e.message}")
                e.printStackTrace()
            }
        }
        
        println("Updated $updatedCount stalled transactions")
        return updatedCount
    }

    /**
     * Query transaction status directly from M-Pesa API
     * This is a "pull" approach instead of waiting for callback
     */
    suspend fun queryTransactionFromMpesa(checkoutRequestId: String): Boolean {
        try {
            println("Querying transaction status from M-Pesa for checkout request ID: $checkoutRequestId")
            val token = getAccessToken()
            
            // Find the local transaction to get the business short code
            val localTransaction = mpesaTransactionRepository.getTransactionByCheckoutRequestId(checkoutRequestId)
            if (localTransaction == null) {
                println("No local transaction found with checkout request ID: $checkoutRequestId")
                return false
            }
            
            val timestamp = generateTimestamp()
            val password = generateMpesaPassword(mpesaConfig.shortCode, mpesaConfig.passkey, timestamp)
            
            // Create query request
            val queryRequest = mapOf(
                "BusinessShortCode" to mpesaConfig.shortCode,
                "Password" to password,
                "Timestamp" to timestamp,
                "CheckoutRequestID" to checkoutRequestId
            )
            
            // Make API call to M-Pesa
            val response: HttpResponse = httpClient.post("${mpesaConfig.baseUrl}/mpesa/stkpushquery/v1/query") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append(HttpHeaders.ContentType, "application/json")
                }
                setBody(gson.toJson(queryRequest))
                timeout {
                    requestTimeoutMillis = 30000 // 30 seconds timeout
                }
            }
            
            val responseBody = response.bodyAsText()
            println("M-Pesa query response: $responseBody")
            
            // Parse response
            val queryResponse = gson.fromJson(responseBody, Map::class.java)
            val resultCode = queryResponse["ResultCode"]?.toString()
            val resultDesc = queryResponse["ResultDesc"]?.toString()
            
            if (resultCode == "0") {
                // Transaction found and was successful
                println("Transaction query successful: $resultDesc")
                
                // Update local transaction if not already updated by callback
                val currentTransaction = mpesaTransactionRepository.getTransactionByCheckoutRequestId(checkoutRequestId)
                if (currentTransaction?.status == "PENDING") {
                    println("Updating transaction from query response")
                    val updatedTransaction = mpesaTransactionRepository.updateTransactionFromQuery(
                        checkoutRequestId = checkoutRequestId,
                        resultCode = resultCode,
                        resultDesc = resultDesc ?: "Transaction completed successfully"
                    )
                    
                    // Update meter payment status
                    updatedTransaction?.let { transaction ->
                        val meterPayment = meterPaymentRepository.getPaymentsByMpesaTransactionId(
                            UUID.fromString(transaction.id)
                        )
                        
                        if (meterPayment != null) {
                            meterPaymentRepository.updatePaymentStatus(
                                paymentId = UUID.fromString(meterPayment.id),
                                status = "COMPLETED"
                            )
                            println("Updated payment status to COMPLETED from query")
                        }
                    }
                }
                
                return true
            } else if (resultCode != null) {
                // Transaction found but failed
                println("Transaction query returned failure: $resultDesc")
                
                // Update local transaction status if needed
                val currentTransaction = mpesaTransactionRepository.getTransactionByCheckoutRequestId(checkoutRequestId)
                if (currentTransaction?.status == "PENDING") {
                    println("Updating transaction status to FAILED from query")
                    val updatedTransaction = mpesaTransactionRepository.updateTransactionFromQuery(
                        checkoutRequestId = checkoutRequestId,
                        resultCode = resultCode,
                        resultDesc = resultDesc ?: "Transaction failed"
                    )
                    
                    // Update meter payment status
                    updatedTransaction?.let { transaction ->
                        val meterPayment = meterPaymentRepository.getPaymentsByMpesaTransactionId(
                            UUID.fromString(transaction.id)
                        )
                        
                        if (meterPayment != null) {
                            meterPaymentRepository.updatePaymentStatus(
                                paymentId = UUID.fromString(meterPayment.id),
                                status = "FAILED"
                            )
                            println("Updated payment status to FAILED from query")
                        }
                    }
                }
            }
            
            return false
        } catch (e: Exception) {
            println("Error querying transaction from M-Pesa: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
}

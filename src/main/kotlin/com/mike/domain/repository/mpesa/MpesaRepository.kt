package com.mike.domain.repository.mpesa

import com.mike.domain.model.mpesa.*
import java.math.BigDecimal
import java.time.LocalDateTime

interface MpesaRepository {
    // Configuration
    fun getMpesaConfig(): MpesaConfig
    
    // Authentication
    suspend fun getAccessToken(): String
    
    // Transaction operations
    suspend fun initiateStk(
        amount: BigDecimal,
        phoneNumber: String,
        accountReference: String,
        description: String
    ): StkPushResponse

    suspend fun initiatePayment(
        amount: BigDecimal,
        phoneNumber: String,
        meterId: String,
        userId: Int,
        accountReference: String = "MeterPayment",
        description: String = "Meter top-up payment",
        maxRetries: Int = 2
    ): PaymentResponse

    // Utility functions
    fun generateMpesaPassword(shortCode: String, passkey: String, timestamp: String): String
    fun generateTimestamp(): String
    fun formatPhoneNumber(phoneNumber: String): String
    
    // Callback processing
    fun processCallback(callbackData: StkCallbackResponse): Boolean
    
    // Transaction status
    suspend fun queryTransactionStatus(checkoutRequestId: String): Boolean
    
    // Handle stalled transactions
    fun handleStalledTransactions(timeoutMinutes: Int): Int
    
    // Migrated from MpesaTransactionRepository
    fun createTransaction(request: MpesaTransactionCreateRequest)

    fun updateTransactionFromCallback(request: MpesaTransactionCallbackUpdate)

    fun startMpesaPendingTransactionMonitor()

    fun updateTransactionFromTimeout(request: MpesaTransactionTimeoutUpdate)

    fun updateTransactionFromQuery(request: MpesaTransactionQueryUpdate)

    fun getTransactionById(id: Int): MpesaTransaction?

    fun getTransactionByCheckoutRequestId(checkoutRequestId: String): MpesaTransaction?

    fun getTransactionsByPhoneNumber(phoneNumber: String): List<MpesaTransaction>

    fun getTransactionsByStatus(status: String): List<MpesaTransaction>

    fun getPendingTransactionsOlderThan(cutoffTime: LocalDateTime): List<MpesaTransaction>

    fun getAllTransactions(): List<MpesaTransaction>
}

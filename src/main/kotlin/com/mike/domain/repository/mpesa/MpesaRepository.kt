package com.mike.domain.repository.mpesa

import com.mike.domain.model.mpesa.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

interface MpesaRepository {
    // Configuration
    fun getMpesaConfig(): MpesaRepositoryImpl.MpesaConfig
    
    // Authentication
    suspend fun getAccessToken(): String
    
    // Transaction operations
    suspend fun initiateStk(
        amount: BigDecimal,
        phoneNumber: String,
        accountReference: String,
        description: String
    ): StkPushResponse
    
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
    fun createTransaction(
        amount: BigDecimal,
        phoneNumber: String,
        merchantRequestId: String? = null,
        checkoutRequestId: String? = null,
        responseCode: String? = null,
        responseDescription: String? = null,
        customerMessage: String? = null
    ): MpesaTransactionDto
    
    fun updateTransactionFromCallback(
        checkoutRequestId: String,
        resultCode: String,
        resultDesc: String,
        mpesaReceiptNumber: String?,
        transactionDate: LocalDateTime?
    ): MpesaTransactionDto?
    
    fun updateTransactionFromTimeout(
        checkoutRequestId: String,
        resultDesc: String = "Transaction timed out waiting for callback"
    ): MpesaTransactionDto?
    
    fun updateTransactionFromQuery(
        checkoutRequestId: String,
        resultCode: String,
        resultDesc: String
    ): MpesaTransactionDto?
    
    fun getTransactionById(id: UUID): MpesaTransactionDto?
    
    fun getTransactionByCheckoutRequestId(checkoutRequestId: String): MpesaTransactionDto?
    
    fun getTransactionsByPhoneNumber(phoneNumber: String): List<MpesaTransactionDto>
    
    fun getTransactionsByStatus(status: String): List<MpesaTransactionDto>
    
    fun getPendingTransactionsOlderThan(cutoffTime: LocalDateTime): List<MpesaTransactionDto>
    
    fun getAllTransactions(): List<MpesaTransactionDto>
}

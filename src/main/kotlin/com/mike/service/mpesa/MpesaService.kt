package com.mike.service.mpesa

import com.mike.domain.model.mpesa.MpesaTransaction
import com.mike.domain.model.mpesa.PaymentResponse
import com.mike.domain.model.mpesa.StkCallbackResponse
import com.mike.domain.repository.mpesa.MpesaRepository
import java.math.BigDecimal

class MpesaService(
    private val mpesaRepository: MpesaRepository
) {
    // Initiate payment - delegate to repository
    suspend fun initiatePayment(
        amount: BigDecimal,
        phoneNumber: String,
        meterId: String,
        userId: Int,
        accountReference: String = "MeterPayment",
        description: String = "Meter top-up payment",
        maxRetries: Int = 2
    ): PaymentResponse {
        return mpesaRepository.initiatePayment(
            amount, phoneNumber, meterId, userId,
            accountReference, description, maxRetries
        )
    }

    // Process M-Pesa callback - delegate directly to repository
    fun processCallback(callbackData: StkCallbackResponse): Boolean {
        return mpesaRepository.processCallback(callbackData)
    }

    // Query payment status - delegate to repository
    fun queryPaymentStatus(checkoutRequestId: String): MpesaTransaction? {
        return mpesaRepository.getTransactionByCheckoutRequestId(checkoutRequestId)
    }

    // Query transaction from M-Pesa API - delegate to repository
    suspend fun queryTransactionFromMpesa(checkoutRequestId: String): Boolean {
        return mpesaRepository.queryTransactionStatus(checkoutRequestId)
    }

    // Check for stalled transactions - delegate to repository
    fun handleStalledTransactions(timeoutMinutes: Int = 5): Int {
        return mpesaRepository.handleStalledTransactions(timeoutMinutes)
    }

    // Get all transactions - delegate to repository
    fun getAllTransactions(): List<MpesaTransaction> {
        return mpesaRepository.getAllTransactions()
    }

    fun startMpesaPendingTransactionMonitor() =
        mpesaRepository.startMpesaPendingTransactionMonitor()

    fun getUserTransactions(userId: Int): List<MpesaTransaction> =
        mpesaRepository.getUserTransactions(userId)

    /**
     * Save raw callback data directly without using the StkCallbackResponse class
     */
    fun saveRawCallback(
        checkoutRequestId: String,
        merchantRequestId: String?,
        resultCode: Int,
        resultDesc: String?,
        mpesaReceiptNumber: String?,
        amount: Double?,
        phoneNumber: String?
    ): Boolean {
        return mpesaRepository.saveRawCallback(
            checkoutRequestId = checkoutRequestId,
            merchantRequestId = merchantRequestId,
            resultCode = resultCode,
            resultDesc = resultDesc,
            mpesaReceiptNumber = mpesaReceiptNumber,
            amount = amount,
            phoneNumber = phoneNumber
        )
    }
}
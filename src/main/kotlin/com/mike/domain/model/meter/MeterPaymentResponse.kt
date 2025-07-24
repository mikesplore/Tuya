package com.mike.domain.model.meter

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Comprehensive response for meter payment operations
 * Contains information about payment status, transaction details, and unit addition
 */
data class MeterPaymentResponse(
    val success: Boolean,
    val message: String,
    val paymentId: Int? = null,
    val meterId: String? = null,
    val mpesaTransactionId: String? = null,
    val mpesaReceiptNumber: String? = null,
    val amount: BigDecimal? = null,
    val unitsAdded: BigDecimal? = null,
    val balanceBefore: BigDecimal? = null,
    val balanceAfter: BigDecimal? = null,
    val paymentDate: LocalDateTime? = null,
    val status: String? = null,
    val tuyaUpdateSuccess: Boolean = false,
    val tuyaUpdateMessage: String? = null
)

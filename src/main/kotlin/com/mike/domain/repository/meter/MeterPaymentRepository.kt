package com.mike.domain.repository.meter

import com.mike.domain.model.meter.MeterPaymentDto


interface MeterPaymentRepository {
    suspend fun createPayment(
        userId: String,
        meterId: String,
        mpesaTransactionId: String,
        amount: Double,
        description: String? = null
    ): MeterPaymentDto

    suspend fun updatePaymentStatus(
        paymentId: String,
        status: String,
        unitsAdded: Double? = null,
        balanceBefore: Double? = null,
        balanceAfter: Double? = null
    ): MeterPaymentDto?

    // Add declarations for all functions in the implementation class
    suspend fun getPaymentById(id: String): MeterPaymentDto?
    suspend fun getPaymentsByUserId(userId: String): List<MeterPaymentDto>
    suspend fun getPaymentsByMeterId(meterId: String): List<MeterPaymentDto>
    suspend fun getPaymentsByStatus(status: String): List<MeterPaymentDto>
    suspend fun getPaymentsByMpesaTransactionId(mpesaTransactionId: String): MeterPaymentDto?
    suspend fun getAllPayments(): List<MeterPaymentDto>
    suspend fun getUserPaymentHistory(userId: String, limit: Int = 50): List<MeterPaymentDto>
    suspend fun createDirectPayment(
        meterId: String,
        amount: Double,
        description: String? = null,
        balanceBefore: Double? = null,
        balanceAfter: Double? = null,
        unitsAdded: Double? = null
    ): MeterPaymentDto
}

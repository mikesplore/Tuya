package com.mike.domain.repository.meter

import com.mike.domain.model.meter.MeterPayment

interface MeterPaymentRepository {
    suspend fun createPayment(meterPayment: MeterPayment)
    suspend fun updatePaymentStatus(meterPayment: MeterPayment)
    suspend fun getPaymentById(id: Int): MeterPayment?
    suspend fun getPaymentsByUserId(userId: Int): List<MeterPayment>
    suspend fun getPaymentsByMeterId(meterId: String): List<MeterPayment>
    suspend fun getPaymentsByStatus(status: String): List<MeterPayment>
    suspend fun getPaymentsByMpesaTransactionId(mpesaTransactionId: String): MeterPayment?
    suspend fun getAllPayments(): List<MeterPayment>
    suspend fun getUserPaymentHistory(userId: Int, limit: Int = 50): List<MeterPayment>
    suspend fun createDirectPayment(meterPayment: MeterPayment)
}

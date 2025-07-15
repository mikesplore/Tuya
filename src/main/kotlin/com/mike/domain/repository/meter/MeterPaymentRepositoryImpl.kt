package com.mike.domain.repository.meter

import com.mike.domain.model.mpesa.MpesaTransaction
import com.mike.domain.model.user.User
import com.mike.database.tables.MeterPayments
import com.mike.domain.model.meter.Meter
import com.mike.domain.model.meter.MeterPayment
import com.mike.domain.model.meter.MeterPaymentDto
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class MeterPaymentRepositoryImpl: MeterPaymentRepository {

    override suspend fun createPayment(
        userId: String,
        meterId: String,
        mpesaTransactionId: String,
        amount: Double,
        description: String?
    ): MeterPaymentDto = transaction {
        val now = LocalDateTime.now()
        val user = User.Companion.findById(UUID.fromString(userId)) ?: throw IllegalArgumentException("User not found")
        val meter = Meter.Companion.findById(UUID.fromString(meterId)) ?: throw IllegalArgumentException("Meter not found")
        val mpesaTransaction = MpesaTransaction.Companion.findById(UUID.fromString(mpesaTransactionId))
            ?: throw IllegalArgumentException("M-Pesa transaction not found")

        val payment = MeterPayment.Companion.new {
            this.user = user
            this.meter = meter
            this.mpesaTransaction = mpesaTransaction
            this.amount = BigDecimal.valueOf(amount)
            this.paymentDate = now
            this.status = "PENDING"
            this.description = description
            this.createdAt = now
            this.updatedAt = now
        }
        payment.toDto()
    }

    override suspend fun updatePaymentStatus(
        paymentId: String,
        status: String,
        unitsAdded: Double?,
        balanceBefore: Double?,
        balanceAfter: Double?
    ): MeterPaymentDto? = transaction {
        val payment = MeterPayment.Companion.findById(UUID.fromString(paymentId))
        payment?.let {
            it.status = status
            it.unitsAdded = unitsAdded?.let { BigDecimal.valueOf(it) }
            it.balanceBefore = balanceBefore?.let { BigDecimal.valueOf(it) }
            it.balanceAfter = balanceAfter?.let { BigDecimal.valueOf(it) }
            it.updatedAt = LocalDateTime.now()
            it.toDto()
        }
    }

    override suspend fun getPaymentById(id: String): MeterPaymentDto? = transaction {
        MeterPayment.Companion.findById(UUID.fromString(id))?.toDto()
    }

    override suspend fun getPaymentsByUserId(userId: String): List<MeterPaymentDto> = transaction {
        MeterPayment.Companion.find { MeterPayments.userId eq UUID.fromString(userId) }.map { it.toDto() }
    }

    override suspend fun getPaymentsByMeterId(meterId: String): List<MeterPaymentDto> = transaction {
        MeterPayment.Companion.find { MeterPayments.meterId eq UUID.fromString(meterId) }.map { it.toDto() }
    }

    override suspend fun getPaymentsByStatus(status: String): List<MeterPaymentDto> = transaction {
        MeterPayment.Companion.find { MeterPayments.status eq status }.map { it.toDto() }
    }

    override suspend fun getPaymentsByMpesaTransactionId(mpesaTransactionId: String): MeterPaymentDto? = transaction {
        MeterPayment.Companion.find {
            MeterPayments.mpesaTransactionId eq UUID.fromString(mpesaTransactionId)
        }.firstOrNull()?.toDto()
    }

    override suspend fun getAllPayments(): List<MeterPaymentDto> = transaction {
        MeterPayment.Companion.all().map { it.toDto() }
    }

    override suspend fun getUserPaymentHistory(userId: String, limit: Int): List<MeterPaymentDto> = transaction {
        MeterPayment.Companion.find { MeterPayments.userId eq UUID.fromString(userId) }
            .orderBy(MeterPayments.paymentDate to SortOrder.DESC)
            .limit(limit)
            .map { it.toDto() }
    }

    override suspend fun createDirectPayment(
        meterId: String,
        amount: Double,
        description: String?,
        balanceBefore: Double?,
        balanceAfter: Double?,
        unitsAdded: Double?
    ): MeterPaymentDto = transaction {
        val now = LocalDateTime.now()
        val meter = Meter.Companion.findById(UUID.fromString(meterId)) ?: throw IllegalArgumentException("Meter not found")

        val payment = MeterPayment.Companion.new {
            this.user = null
            this.meter = meter
            this.mpesaTransaction = null
            this.amount = BigDecimal.valueOf(amount)
            this.paymentDate = now
            this.status = "COMPLETED"
            this.description = description ?: "Direct payment"
            this.balanceBefore = balanceBefore?.let { BigDecimal.valueOf(it) }
            this.balanceAfter = balanceAfter?.let { BigDecimal.valueOf(it) }
            this.unitsAdded = unitsAdded?.let { BigDecimal.valueOf(it) }
            this.createdAt = now
            this.updatedAt = now
        }
        payment.toDto()
    }
}

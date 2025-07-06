package database.repository

import com.mike.database.entities.Meter
import com.mike.database.entities.MpesaTransaction
import com.mike.database.entities.User
import com.mike.database.tables.MeterPayments
import database.entities.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

class MeterPaymentRepository {

    fun createPayment(
        userId: UUID,
        meterId: UUID,
        mpesaTransactionId: UUID,
        amount: BigDecimal,
        description: String? = null
    ): MeterPaymentDto = transaction {
        val now = LocalDateTime.now()
        val user = User.findById(userId) ?: throw IllegalArgumentException("User not found")
        val meter = Meter.findById(meterId) ?: throw IllegalArgumentException("Meter not found")
        val mpesaTransaction = MpesaTransaction.findById(mpesaTransactionId)
            ?: throw IllegalArgumentException("M-Pesa transaction not found")

        val payment = MeterPayment.new {
            this.user = user
            this.meter = meter
            this.mpesaTransaction = mpesaTransaction
            this.amount = amount
            this.paymentDate = now
            this.status = "PENDING"
            this.description = description
            this.createdAt = now
            this.updatedAt = now
        }
        payment.toDto()
    }

    fun updatePaymentStatus(
        paymentId: UUID,
        status: String,
        unitsAdded: BigDecimal? = null,
        balanceBefore: BigDecimal? = null,
        balanceAfter: BigDecimal? = null
    ): MeterPaymentDto? = transaction {
        val payment = MeterPayment.findById(paymentId)
        payment?.let {
            it.status = status
            it.unitsAdded = unitsAdded
            it.balanceBefore = balanceBefore
            it.balanceAfter = balanceAfter
            it.updatedAt = LocalDateTime.now()
            it.toDto()
        }
    }

    fun getPaymentById(id: UUID): MeterPaymentDto? = transaction {
        MeterPayment.findById(id)?.toDto()
    }

    fun getPaymentsByUserId(userId: UUID): List<MeterPaymentDto> = transaction {
        MeterPayment.find { MeterPayments.userId eq userId }.map { it.toDto() }
    }

    fun getPaymentsByMeterId(meterId: UUID): List<MeterPaymentDto> = transaction {
        MeterPayment.find { MeterPayments.meterId eq meterId }.map { it.toDto() }
    }

    fun getPaymentsByStatus(status: String): List<MeterPaymentDto> = transaction {
        MeterPayment.find { MeterPayments.status eq status }.map { it.toDto() }
    }

    fun getPaymentsByMpesaTransactionId(mpesaTransactionId: UUID): MeterPaymentDto? = transaction {
        MeterPayment.find {
            MeterPayments.mpesaTransactionId eq mpesaTransactionId
        }.firstOrNull()?.toDto()
    }

    fun getAllPayments(): List<MeterPaymentDto> = transaction {
        MeterPayment.all().map { it.toDto() }
    }

    fun getUserPaymentHistory(userId: UUID, limit: Int = 50): List<MeterPaymentDto> = transaction {
        MeterPayment.find { MeterPayments.userId eq userId }
            .orderBy(MeterPayments.paymentDate to org.jetbrains.exposed.sql.SortOrder.DESC)
            .limit(limit)
            .map { it.toDto() }
    }

    fun createDirectPayment(
        meterId: UUID,
        amount: BigDecimal,
        description: String? = null,
        balanceBefore: BigDecimal? = null,
        balanceAfter: BigDecimal? = null,
        unitsAdded: BigDecimal? = null
    ): MeterPaymentDto = transaction {
        val now = LocalDateTime.now()
        val meter = Meter.findById(meterId) ?: throw IllegalArgumentException("Meter not found")

        val payment = MeterPayment.new {
            // For direct payments, we don't have a user association yet
            // You can manually set this later if needed
            this.user = null
            this.meter = meter
            this.mpesaTransaction = null // Direct payment without M-Pesa
            this.amount = amount
            this.paymentDate = now
            this.status = "COMPLETED" // Direct payments are always completed
            this.description = description ?: "Direct payment"
            this.balanceBefore = balanceBefore
            this.balanceAfter = balanceAfter
            this.unitsAdded = unitsAdded
            this.createdAt = now
            this.updatedAt = now
        }
        payment.toDto()
    }
}

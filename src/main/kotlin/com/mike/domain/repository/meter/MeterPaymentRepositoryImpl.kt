package com.mike.domain.repository.meter

import com.mike.domain.model.meter.MeterPayment
import com.mike.domain.model.meter.MeterPayments
import com.mike.domain.model.meter.Meters
import com.mike.domain.model.mpesa.MpesaTransactions
import com.mike.domain.model.user.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class MeterPaymentRepositoryImpl : MeterPaymentRepository {

    override suspend fun createPayment(meterPayment: MeterPayment) { transaction {
        val now = LocalDateTime.now()

        // Verify meter exists
        Meters.selectAll().where { Meters.meterId eq meterPayment.meterId }
            .singleOrNull() ?: throw IllegalArgumentException("Meter not found")

        // Verify user exists if userId is provided
        if (meterPayment.userId != null) {
            Users.selectAll().where { Users.userId eq meterPayment.userId }
                .singleOrNull() ?: throw IllegalArgumentException("User not found")
        }

        // Get transaction ID from checkoutRequestId
        val mpesaTransactionIdValue = if (meterPayment.mpesaTransactionId != null) {
            val mpesaTransaction = MpesaTransactions.selectAll()
                .where { MpesaTransactions.checkoutRequestId eq meterPayment.mpesaTransactionId }
                .singleOrNull()

            if (mpesaTransaction == null) {
                throw IllegalArgumentException("M-Pesa transaction not found")
            }

            mpesaTransaction[MpesaTransactions.id]
        } else {
            null
        }

        MeterPayments.insert {
            it[userId] = meterPayment.userId
            it[meterId] = meterPayment.meterId
            it[mpesaTransactionId] = mpesaTransactionIdValue
            it[amount] = meterPayment.amount
            it[unitsAdded] = meterPayment.unitsAdded
            it[balanceBefore] = meterPayment.balanceBefore
            it[balanceAfter] = meterPayment.balanceAfter
            it[paymentDate] = meterPayment.paymentDate
            it[status] = meterPayment.status
            it[description] = meterPayment.description
            it[createdAt] = now
            it[updatedAt] = now
        }
    }}

    override suspend fun updatePaymentStatus(meterPayment: MeterPayment) { transaction {
        meterPayment.id?.let {
        MeterPayments.update({ MeterPayments.id eq meterPayment.id }) {
            it[status] = meterPayment.status
            it[unitsAdded] = meterPayment.unitsAdded
            it[balanceBefore] = meterPayment.balanceBefore
            it[balanceAfter] = meterPayment.balanceAfter
            it[updatedAt] = LocalDateTime.now()
        }}
    }}

    override suspend fun getPaymentById(id: Int): MeterPayment? = transaction {
        MeterPayments.selectAll().where { MeterPayments.id eq id }
            .singleOrNull()?.let { mapToMeterPayment(it) }
    }

    override suspend fun getPaymentsByUserId(userId: Int): List<MeterPayment> = transaction {
        MeterPayments.selectAll().where { MeterPayments.userId eq userId }
            .map { mapToMeterPayment(it) }
    }

    override suspend fun getPaymentsByMeterId(meterId: String): List<MeterPayment> = transaction {
        MeterPayments.selectAll().where { MeterPayments.meterId eq meterId }
            .map { mapToMeterPayment(it) }
    }

    override suspend fun getPaymentsByStatus(status: String): List<MeterPayment> = transaction {
        MeterPayments.selectAll().where { MeterPayments.status eq status }
            .map { mapToMeterPayment(it) }
    }

    override suspend fun getPaymentsByMpesaTransactionId(mpesaTransactionId: String): MeterPayment? = transaction {
        // First, find the mpesa transaction ID (integer) from the checkoutRequestId (string)
        val mpesaTransaction = MpesaTransactions.selectAll()
            .where { MpesaTransactions.checkoutRequestId eq mpesaTransactionId }
            .singleOrNull()

        if (mpesaTransaction == null) return@transaction null

        val transactionId = mpesaTransaction[MpesaTransactions.id]

        // Now find the payment using the integer ID
        MeterPayments.selectAll()
            .where { MeterPayments.mpesaTransactionId eq transactionId }
            .firstOrNull()?.let { mapToMeterPayment(it) }
    }

    override suspend fun getAllPayments(): List<MeterPayment> = transaction {
        MeterPayments.selectAll()
            .map { mapToMeterPayment(it) }
    }

    override suspend fun getUserPaymentHistory(userId: Int, limit: Int): List<MeterPayment> = transaction {
        MeterPayments.selectAll().where { MeterPayments.userId eq userId }
            .orderBy(MeterPayments.paymentDate to SortOrder.DESC)
            .limit(limit)
            .map { mapToMeterPayment(it) }
    }

    override suspend fun createDirectPayment(meterPayment: MeterPayment) { transaction {
        val now = LocalDateTime.now()

        // Verify meter exists
        Meters.selectAll().where { Meters.meterId eq meterPayment.meterId }
            .singleOrNull() ?: throw IllegalArgumentException("Meter not found")

        MeterPayments.insert {
            it[userId] = null // Direct payments don't have a user
            it[meterId] = meterPayment.meterId
            it[mpesaTransactionId] = null // Direct payments don't have an mpesa transaction
            it[amount] = meterPayment.amount
            it[unitsAdded] = meterPayment.unitsAdded
            it[balanceBefore] = meterPayment.balanceBefore
            it[balanceAfter] = meterPayment.balanceAfter
            it[paymentDate] = meterPayment.paymentDate
            it[status] = "COMPLETED" // Direct payments are always completed
            it[description] = meterPayment.description ?: "Direct payment"
            it[createdAt] = now
            it[updatedAt] = now
        }
    }}

    private fun mapToMeterPayment(row: ResultRow): MeterPayment {
        // Get the checkoutRequestId from mpesaTransactionId
        val checkoutRequestId = row[MeterPayments.mpesaTransactionId]?.let { mpesaTransactionId ->
            MpesaTransactions.selectAll()
                .where { MpesaTransactions.id eq mpesaTransactionId }
                .singleOrNull()?.get(MpesaTransactions.checkoutRequestId)
        }

        return MeterPayment(
            id = row[MeterPayments.id],
            userId = row[MeterPayments.userId],
            meterId = row[MeterPayments.meterId],
            mpesaTransactionId = checkoutRequestId,
            amount = row[MeterPayments.amount],
            unitsAdded = row[MeterPayments.unitsAdded],
            balanceBefore = row[MeterPayments.balanceBefore],
            balanceAfter = row[MeterPayments.balanceAfter],
            paymentDate = row[MeterPayments.paymentDate],
            status = row[MeterPayments.status],
            description = row[MeterPayments.description],
            createdAt = row[MeterPayments.createdAt],
            updatedAt = row[MeterPayments.updatedAt]
        )
    }
}

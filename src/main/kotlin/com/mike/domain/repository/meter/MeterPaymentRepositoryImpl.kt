package com.mike.domain.repository.meter

import com.mike.domain.model.meter.MeterPayment
import com.mike.domain.model.meter.MeterPayments
import com.mike.domain.model.meter.Meters
import com.mike.domain.model.mpesa.MpesaTransactions
import com.mike.domain.model.user.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class MeterPaymentRepositoryImpl(
    private val meterRateRepository: MeterRateRepository
) : MeterPaymentRepository {

    override suspend fun createPayment(meterPayment: MeterPayment) {
        val now = LocalDateTime.now() // Define now outside transaction so it's available throughout the function

        transaction {
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

            // Calculate units based on amount if not already provided
            val calculatedUnits = if (meterPayment.unitsAdded == null || meterPayment.unitsAdded.compareTo(java.math.BigDecimal.ZERO) == 0) {
                try {
                    // This will be calculated outside the transaction block
                    null
                } catch (e: Exception) {
                    println("Error calculating units: ${e.message}")
                    null
                }
            } else {
                meterPayment.unitsAdded
            }

            MeterPayments.insert {
                it[userId] = meterPayment.userId
                it[meterId] = meterPayment.meterId
                it[mpesaTransactionId] = mpesaTransactionIdValue
                it[amount] = meterPayment.amount
                it[unitsAdded] = calculatedUnits
                it[balanceBefore] = meterPayment.balanceBefore
                it[balanceAfter] = meterPayment.balanceAfter
                it[paymentDate] = meterPayment.paymentDate
                it[status] = meterPayment.status
                it[description] = meterPayment.description
                it[createdAt] = now
                it[updatedAt] = now
            }
        }

        // Now calculate units outside transaction block if needed
        if (meterPayment.unitsAdded == null || meterPayment.unitsAdded.compareTo(java.math.BigDecimal.ZERO) == 0) {
            try {
                val calculatedUnits = meterRateRepository.calculateUnitsForAmount(meterPayment.amount)

                // Update the payment with the calculated units
                transaction {
                    MeterPayments.update({
                        (MeterPayments.meterId eq meterPayment.meterId) and
                                (MeterPayments.amount eq meterPayment.amount) and
                                (MeterPayments.createdAt eq now)
                    }) {
                        it[unitsAdded] = calculatedUnits
                    }
                }
                println("Successfully calculated and updated units: $calculatedUnits for payment amount: ${meterPayment.amount}")
            } catch (e: Exception) {
                println("Failed to calculate units after payment creation: ${e.message}")
            }
        }
    }

    override suspend fun updatePaymentStatus(meterPayment: MeterPayment) { transaction {
        meterPayment.id?.let {
        MeterPayments.update({ MeterPayments.id eq meterPayment.id }) {
            it[status] = meterPayment.status
            it[updatedAt] = LocalDateTime.now()

            // Calculate units if payment is being marked as COMPLETED and units not set
            if (meterPayment.status == "COMPLETED" &&
                (meterPayment.unitsAdded == null || meterPayment.unitsAdded.compareTo(java.math.BigDecimal.ZERO) == 0)) {

                // We'll handle unit calculation outside this transaction
            } else {
                it[unitsAdded] = meterPayment.unitsAdded
                it[balanceBefore] = meterPayment.balanceBefore
                it[balanceAfter] = meterPayment.balanceAfter
            }
        }}
    }

    // Calculate units outside transaction if needed
    if (meterPayment.status == "COMPLETED" &&
        (meterPayment.unitsAdded == null || meterPayment.unitsAdded.compareTo(java.math.BigDecimal.ZERO) == 0)) {

        try {
            val calculatedUnits = meterRateRepository.calculateUnitsForAmount(meterPayment.amount)

            // Update the payment with calculated units in a new transaction
            transaction {
                meterPayment.id?.let {
                    MeterPayments.update({ MeterPayments.id eq it }) {
                        it[unitsAdded] = calculatedUnits
                    }
                }
            }
            println("Successfully calculated and updated units: $calculatedUnits for completed payment: ${meterPayment.id}")
        } catch (e: Exception) {
            println("Failed to calculate units for completed payment: ${e.message}")
        }
    }
    }

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

    override suspend fun createDirectPayment(meterPayment: MeterPayment) {
        val now = LocalDateTime.now() // Define now outside transaction so it's available throughout the function

        transaction {
            // Verify meter exists
            Meters.selectAll().where { Meters.meterId eq meterPayment.meterId }
                .singleOrNull() ?: throw IllegalArgumentException("Meter not found")

            // Calculate units based on amount if not provided
            val calculatedUnits = if (meterPayment.unitsAdded == null || meterPayment.unitsAdded.compareTo(java.math.BigDecimal.ZERO) == 0) {
                // Units will be calculated outside this transaction
                null
            } else {
                meterPayment.unitsAdded
            }

            MeterPayments.insert {
                it[userId] = null // Direct payments don't have a user
                it[meterId] = meterPayment.meterId
                it[mpesaTransactionId] = null // Direct payments don't have an mpesa transaction
                it[amount] = meterPayment.amount
                it[unitsAdded] = calculatedUnits
                it[balanceBefore] = meterPayment.balanceBefore
                it[balanceAfter] = meterPayment.balanceAfter
                it[paymentDate] = meterPayment.paymentDate
                it[status] = "COMPLETED" // Direct payments are always completed
                it[description] = meterPayment.description ?: "Direct payment"
                it[createdAt] = now
                it[updatedAt] = now
            }
        }

        // Calculate and update units outside transaction if needed
        if (meterPayment.unitsAdded == null || meterPayment.unitsAdded.compareTo(java.math.BigDecimal.ZERO) == 0) {
            try {
                val calculatedUnits = meterRateRepository.calculateUnitsForAmount(meterPayment.amount)

                // Update the payment with the calculated units
                transaction {
                    MeterPayments.update({
                        (MeterPayments.meterId eq meterPayment.meterId) and
                        (MeterPayments.amount eq meterPayment.amount) and
                        (MeterPayments.createdAt eq now)
                    }) {
                        it[unitsAdded] = calculatedUnits
                    }
                }
                println("Successfully calculated units: $calculatedUnits for direct payment amount: ${meterPayment.amount}")
            } catch (e: Exception) {
                println("Failed to calculate units for direct payment: ${e.message}")
            }
        }
    }

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

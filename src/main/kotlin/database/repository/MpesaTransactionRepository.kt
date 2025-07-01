package com.mike.database.repository

import com.mike.database.entities.MpesaTransaction
import com.mike.database.entities.MpesaTransactionDto
import com.mike.database.tables.MpesaTransactions
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

class MpesaTransactionRepository {

    fun createTransaction(
        amount: BigDecimal,
        phoneNumber: String,
        merchantRequestId: String? = null,
        checkoutRequestId: String? = null,
        responseCode: String? = null,
        responseDescription: String? = null,
        customerMessage: String? = null
    ): MpesaTransactionDto = transaction {
        val now = LocalDateTime.now()
        val mpesaTransaction = MpesaTransaction.new {
            this.merchantRequestId = merchantRequestId
            this.checkoutRequestId = checkoutRequestId
            this.responseCode = responseCode
            this.responseDescription = responseDescription
            this.customerMessage = customerMessage
            this.amount = amount
            this.phoneNumber = phoneNumber
            this.status = "PENDING"
            this.callbackReceived = false
            this.createdAt = now
            this.updatedAt = now
        }
        mpesaTransaction.toDto()
    }

    fun updateTransactionFromCallback(
        checkoutRequestId: String,
        resultCode: String,
        resultDesc: String,
        mpesaReceiptNumber: String?,
        transactionDate: LocalDateTime?
    ): MpesaTransactionDto? = transaction {
        val mpesaTransaction = MpesaTransaction.find {
            MpesaTransactions.checkoutRequestId eq checkoutRequestId
        }.firstOrNull()

        mpesaTransaction?.let {
            it.responseCode = resultCode
            it.responseDescription = resultDesc
            it.mpesaReceiptNumber = mpesaReceiptNumber
            it.transactionDate = transactionDate
            it.status = when (resultCode) {
                "0" -> "SUCCESS"
                else -> "FAILED"
            }
            it.callbackReceived = true
            it.updatedAt = LocalDateTime.now()
            it.toDto()
        }
    }

    fun getTransactionById(id: UUID): MpesaTransactionDto? = transaction {
        MpesaTransaction.findById(id)?.toDto()
    }

    fun getTransactionByCheckoutRequestId(checkoutRequestId: String): MpesaTransactionDto? = transaction {
        MpesaTransaction.find {
            MpesaTransactions.checkoutRequestId eq checkoutRequestId
        }.firstOrNull()?.toDto()
    }

    fun getTransactionsByPhoneNumber(phoneNumber: String): List<MpesaTransactionDto> = transaction {
        MpesaTransaction.find {
            MpesaTransactions.phoneNumber eq phoneNumber
        }.map { it.toDto() }
    }

    fun getTransactionsByStatus(status: String): List<MpesaTransactionDto> = transaction {
        MpesaTransaction.find {
            MpesaTransactions.status eq status
        }.map { it.toDto() }
    }

    fun getAllTransactions(): List<MpesaTransactionDto> = transaction {
        MpesaTransaction.all().map { it.toDto() }
    }
}

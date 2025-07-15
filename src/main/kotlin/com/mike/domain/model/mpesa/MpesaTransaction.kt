package com.mike.domain.model.mpesa

import com.mike.database.tables.MpesaTransactions
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class MpesaTransaction(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<MpesaTransaction>(MpesaTransactions)

    var merchantRequestId by MpesaTransactions.merchantRequestId
    var checkoutRequestId by MpesaTransactions.checkoutRequestId
    var responseCode by MpesaTransactions.responseCode
    var responseDescription by MpesaTransactions.responseDescription
    var customerMessage by MpesaTransactions.customerMessage
    var amount by MpesaTransactions.amount
    var phoneNumber by MpesaTransactions.phoneNumber
    var mpesaReceiptNumber by MpesaTransactions.mpesaReceiptNumber
    var transactionDate by MpesaTransactions.transactionDate
    var status by MpesaTransactions.status
    var callbackReceived by MpesaTransactions.callbackReceived
    var createdAt by MpesaTransactions.createdAt
    var updatedAt by MpesaTransactions.updatedAt

    fun toDto(): MpesaTransactionDto = MpesaTransactionDto(
        id = id.value.toString(),
        merchantRequestId = merchantRequestId,
        checkoutRequestId = checkoutRequestId,
        responseCode = responseCode,
        responseDescription = responseDescription,
        customerMessage = customerMessage,
        amount = amount,
        phoneNumber = phoneNumber,
        mpesaReceiptNumber = mpesaReceiptNumber,
        transactionDate = transactionDate,
        status = status,
        callbackReceived = callbackReceived,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

data class MpesaTransactionDto(
    val id: String,
    val merchantRequestId: String?,
    val checkoutRequestId: String?,
    val responseCode: String?,
    val responseDescription: String?,
    val customerMessage: String?,
    val amount: BigDecimal,
    val phoneNumber: String,
    val mpesaReceiptNumber: String?,
    val transactionDate: LocalDateTime?,
    val status: String,
    val callbackReceived: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

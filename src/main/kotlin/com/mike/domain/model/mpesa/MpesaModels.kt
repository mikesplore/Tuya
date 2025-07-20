package com.mike.domain.model.mpesa

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.javatime.datetime
import java.math.BigDecimal
import java.time.LocalDateTime

object MpesaTransactions : Table() {
    val id = integer("id").autoIncrement()
    val merchantRequestId = varchar("merchant_request_id", 100).nullable()
    val checkoutRequestId = varchar("checkout_request_id", 100).nullable()
    val responseCode = varchar("response_code", 20).nullable()
    val responseDescription = varchar("response_description", 255).nullable()
    val customerMessage = varchar("customer_message", 255).nullable()
    val amount = decimal("amount", 10, 2)
    val phoneNumber = varchar("phone_number", 20)
    val mpesaReceiptNumber = varchar("mpesa_receipt_number", 100).nullable()
    val transactionDate = datetime("transaction_date").nullable()
    val status = varchar("status", 50)
    val callbackReceived = bool("callback_received").default(false)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    override val primaryKey = PrimaryKey(id)
}

data class MpesaTransaction(
    val id: Int? = null,
    val merchantRequestId: String? = null,
    val checkoutRequestId: String? = null,
    val responseCode: String? = null,
    val responseDescription: String? = null,
    val customerMessage: String? = null,
    val amount: BigDecimal,
    val phoneNumber: String,
    val mpesaReceiptNumber: String? = null,
    val transactionDate: LocalDateTime? = null,
    val status: String,
    val callbackReceived: Boolean = false,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)


data class MpesaTransactionCreateRequest(
    val amount: BigDecimal,
    val phoneNumber: String,
    val merchantRequestId: String? = null,
    val checkoutRequestId: String? = null,
    val responseCode: String? = null,
    val responseDescription: String? = null,
    val customerMessage: String? = null
)

data class MpesaTransactionCallbackUpdate(
    val checkoutRequestId: String,
    val resultCode: String,
    val resultDesc: String,
    val mpesaReceiptNumber: String?,
    val transactionDate: LocalDateTime?
)

data class MpesaTransactionTimeoutUpdate(
    val checkoutRequestId: String,
    val resultDesc: String = "Transaction timed out waiting for callback"
)

data class MpesaTransactionQueryUpdate(
    val checkoutRequestId: String,
    val resultCode: String,
    val resultDesc: String,
    val receiptNumber: String,
    val transactionDate: LocalDateTime?
)

// Access token response
data class AccessTokenResponse(
    val access_token: String,
    val expires_in: String
)


data class PaymentResponse(
    val success: Boolean,
    val message: String,
    val merchantRequestId: String?,
    val checkoutRequestId: String?,
    val mpesaTransactionId: String?
)


data class MpesaConfig(
    val consumerKey: String,
    val consumerSecret: String,
    val shortCode: String,
    val passkey: String, // Sandbox passkey
    val baseUrl: String,
    val callbackUrl: String // Default callback URL
)


fun toMpesaTransaction(row: ResultRow): MpesaTransaction =
    MpesaTransaction(
        id = row[MpesaTransactions.id],
        merchantRequestId = row[MpesaTransactions.merchantRequestId],
        checkoutRequestId = row[MpesaTransactions.checkoutRequestId],
        responseCode = row[MpesaTransactions.responseCode],
        responseDescription = row[MpesaTransactions.responseDescription],
        customerMessage = row[MpesaTransactions.customerMessage],
        amount = row[MpesaTransactions.amount],
        phoneNumber = row[MpesaTransactions.phoneNumber],
        mpesaReceiptNumber = row[MpesaTransactions.mpesaReceiptNumber],
        transactionDate = row[MpesaTransactions.transactionDate],
        status = row[MpesaTransactions.status],
        callbackReceived = row[MpesaTransactions.callbackReceived],
        createdAt = row[MpesaTransactions.createdAt],
        updatedAt = row[MpesaTransactions.updatedAt]
    )

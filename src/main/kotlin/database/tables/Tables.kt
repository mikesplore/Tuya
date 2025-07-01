package com.mike.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

// Users table
object Users : UUIDTable() {
    val email = varchar("email", 255).uniqueIndex()
    val phoneNumber = varchar("phone_number", 20).nullable()
    val passwordHash = varchar("password_hash", 255)
    val firstName = varchar("first_name", 100).nullable()
    val lastName = varchar("last_name", 100).nullable()
    val role = varchar("role", 50).default("USER")
    val active = bool("active").default(true)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

// Smart meters table
object Meters : UUIDTable() {
    val deviceId = varchar("device_id", 100).uniqueIndex()
    val name = varchar("name", 255)
    val productName = varchar("product_name", 255).nullable()
    val description = text("description").nullable()
    val location = varchar("location", 255).nullable()
    val active = bool("active").default(true)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

// User-meter assignments table
object UserMeterAssignments : Table() {
    val userId = reference("user_id", Users)
    val meterId = reference("meter_id", Meters)
    val assignedAt = datetime("assigned_at")

    override val primaryKey = PrimaryKey(userId, meterId)
}

// M-Pesa transactions table
object MpesaTransactions : UUIDTable() {
    val merchantRequestId = varchar("merchant_request_id", 255).nullable()
    val checkoutRequestId = varchar("checkout_request_id", 255).nullable()
    val responseCode = varchar("response_code", 10).nullable()
    val responseDescription = text("response_description").nullable()
    val customerMessage = text("customer_message").nullable()
    val amount = decimal("amount", 10, 2)
    val phoneNumber = varchar("phone_number", 20)
    val mpesaReceiptNumber = varchar("mpesa_receipt_number", 255).nullable()
    val transactionDate = datetime("transaction_date").nullable()
    val status = varchar("status", 50).default("PENDING") // PENDING, SUCCESS, FAILED, CANCELLED
    val callbackReceived = bool("callback_received").default(false)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

// Meter payments table
object MeterPayments : UUIDTable() {
    val userId = reference("user_id", Users)
    val meterId = reference("meter_id", Meters)
    val mpesaTransactionId = reference("mpesa_transaction_id", MpesaTransactions)
    val amount = decimal("amount", 10, 2)
    val unitsAdded = decimal("units_added", 10, 2).nullable()
    val balanceBefore = decimal("balance_before", 10, 2).nullable()
    val balanceAfter = decimal("balance_after", 10, 2).nullable()
    val paymentDate = datetime("payment_date")
    val status = varchar("status", 50).default("PENDING") // PENDING, COMPLETED, FAILED
    val description = text("description").nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}


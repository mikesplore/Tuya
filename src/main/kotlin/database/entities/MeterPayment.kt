package com.mike.database.entities

import com.mike.database.tables.MeterPayments
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class MeterPayment(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<MeterPayment>(MeterPayments)

    var user by User referencedOn MeterPayments.userId
    var meter by Meter referencedOn MeterPayments.meterId
    var mpesaTransaction by MpesaTransaction referencedOn MeterPayments.mpesaTransactionId
    var amount by MeterPayments.amount
    var unitsAdded by MeterPayments.unitsAdded
    var balanceBefore by MeterPayments.balanceBefore
    var balanceAfter by MeterPayments.balanceAfter
    var paymentDate by MeterPayments.paymentDate
    var status by MeterPayments.status
    var description by MeterPayments.description
    var createdAt by MeterPayments.createdAt
    var updatedAt by MeterPayments.updatedAt

    fun toDto(): MeterPaymentDto = MeterPaymentDto(
        id = id.value.toString(),
        userId = user.id.value.toString(),
        meterId = meter.id.value.toString(),
        mpesaTransactionId = mpesaTransaction.id.value.toString(),
        amount = amount,
        unitsAdded = unitsAdded,
        balanceBefore = balanceBefore,
        balanceAfter = balanceAfter,
        paymentDate = paymentDate,
        status = status,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt,
        meterName = meter.name,
        userEmail = user.email
    )
}

data class MeterPaymentDto(
    val id: String,
    val userId: String,
    val meterId: String,
    val mpesaTransactionId: String,
    val amount: BigDecimal,
    val unitsAdded: BigDecimal?,
    val balanceBefore: BigDecimal?,
    val balanceAfter: BigDecimal?,
    val paymentDate: LocalDateTime,
    val status: String,
    val description: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val meterName: String,
    val userEmail: String
)

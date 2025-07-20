package com.mike.domain.model.meter

import com.mike.domain.model.mpesa.MpesaTransactions
import com.mike.domain.model.user.Users
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.math.BigDecimal
import java.time.LocalDateTime

object Meters : Table() {
    val meterId = varchar("device_id", 100).uniqueIndex()
    val name = varchar("name", 255)
    val productName = varchar("product_name", 255).nullable()
    val online = bool("online").default(false)
    val balance = integer("balance").nullable()
    val totalEnergy = integer("total_energy").nullable()
    val price = integer("price").nullable()
    val chargeEnergy = integer("charge_energy").nullable()
    val switchPrepayment = bool("switch_prepayment").nullable()
    val updatedAtLong = long("updated_at_long").nullable()
    override val primaryKey = PrimaryKey(meterId, name = "PK_Meter_DeviceId")
}

data class Meter(
    val meterId: String,
    val name: String,
    val productName: String?,
    val online: Boolean = false,
    val balance: Int? = null,
    val totalEnergy: Int? = null,
    val price: Int? = null,
    val chargeEnergy: Int? = null,
    val switchPrepayment: Boolean? = null,
    val updatedAt: Long? = null
)

data class MeterCreationRequest(
    val meterId: String,
    val name: String,
    val productName: String? = null,
    val description: String? = null,
    val location: String? = null,
    val active: Boolean = true
)


object MeterPayments : Table() {
    val id = integer("id").autoIncrement()
    val userId = reference("user_id", Users.userId).nullable()
    val meterId = reference("meter_id", Meters.meterId)
    val mpesaTransactionId = reference("mpesa_transaction_id", MpesaTransactions.id).nullable()
    val amount = decimal("amount", 10, 2)
    val unitsAdded = decimal("units_added", 10, 2).nullable()
    val balanceBefore = decimal("balance_before", 10, 2).nullable()
    val balanceAfter = decimal("balance_after", 10, 2).nullable()
    val paymentDate = datetime("payment_date")
    val status = varchar("status", 50).default("PENDING")
    val description = text("description").nullable()
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    override val primaryKey = PrimaryKey(id, name = "PK_MeterPayment_ID")
}

data class MeterPayment(
    val id: Int? = null,
    val userId: Int? = null,
    val meterId: String,
    val mpesaTransactionId: String? = null,
    val amount: BigDecimal,
    val unitsAdded: BigDecimal? = null,
    val balanceBefore: BigDecimal? = null,
    val balanceAfter: BigDecimal? = null,
    val paymentDate: LocalDateTime = LocalDateTime.now(),
    val status: String = "PENDING",
    val description: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

package com.mike.domain.model.meter

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.math.BigDecimal
import java.time.LocalDateTime

object MeterRates : Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100)
    val description = text("description").nullable()
    val ratePerUnit = decimal("rate_per_unit", 10, 2)
    val minAmount = decimal("min_amount", 10, 2)
    val maxAmount = decimal("max_amount", 10, 2).nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    override val primaryKey = PrimaryKey(id, name = "PK_MeterRate_ID")
}

data class MeterRate(
    val id: Int? = null,
    val name: String,
    val description: String? = null,
    val ratePerUnit: BigDecimal,
    val minAmount: BigDecimal,
    val maxAmount: BigDecimal? = null,
    val isActive: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

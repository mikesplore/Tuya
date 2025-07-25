package com.mike.domain.model.stats

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * System-wide statistics visible to administrators
 */
data class AdminStatistics(
    val totalUsers: Int,
    val totalMeters: Int,
    val totalTransactions: Int,
    val totalSuccessfulTransactions: Int,
    val totalFailedTransactions: Int,
    val totalRevenue: BigDecimal,
    val revenueLastSevenDays: BigDecimal,
    val revenueLastThirtyDays: BigDecimal,
    val transactionsLastSevenDays: Int,
    val transactionsLastThirtyDays: Int,
    val activeMeters: Int,
    val inactiveMeters: Int,
    val averageTransactionValue: BigDecimal,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * User-specific statistics visible to individual users
 */
data class UserStatistics(
    val userId: Int,
    val totalMeters: Int,
    val totalTransactions: Int,
    val totalSuccessfulTransactions: Int,
    val totalFailedTransactions: Int,
    val totalSpent: BigDecimal,
    val spentLastSevenDays: BigDecimal,
    val spentLastThirtyDays: BigDecimal,
    val transactionsLastSevenDays: Int,
    val transactionsLastThirtyDays: Int,
    val activeMeters: Int,
    val inactiveMeters: Int,
    val averageTransactionValue: BigDecimal,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * Transaction count per day for chart visualization
 */
data class DailyTransactionStats(
    val date: LocalDateTime,
    val count: Int,
    val value: BigDecimal
)

/**
 * Meter payment statistics
 */
data class MeterPaymentStats(
    val totalPayments: Int,
    val successfulPayments: Int,
    val failedPayments: Int,
    val pendingPayments: Int,
    val totalAmount: BigDecimal,
    val averageAmount: BigDecimal
)

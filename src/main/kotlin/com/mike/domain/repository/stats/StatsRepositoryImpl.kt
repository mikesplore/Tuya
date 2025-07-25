package com.mike.domain.repository.stats

import com.mike.domain.model.meter.MeterPayments
import com.mike.domain.model.meter.Meters
import com.mike.domain.model.mpesa.MpesaTransactions
import com.mike.domain.model.stats.AdminStatistics
import com.mike.domain.model.stats.DailyTransactionStats
import com.mike.domain.model.stats.MeterPaymentStats
import com.mike.domain.model.stats.UserStatistics
import com.mike.domain.model.user.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.day
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class StatsRepositoryImpl : StatsRepository {
    override fun getAdminStatistics(): AdminStatistics = transaction {
        val now = LocalDateTime.now()
        val sevenDaysAgo = now.minusDays(7)
        val thirtyDaysAgo = now.minusDays(30)

        // Count total users
        val totalUsers = Users.selectAll().count()

        // Count total meters
        val totalMeters = Meters.selectAll().count()

        // Get meters active/inactive counts
        val (activeMeters, inactiveMeters) = getMetersActiveInactiveCount()

        // Get transaction stats
        val totalTransactions = MpesaTransactions.selectAll().count()

        val successfulTransactions = MpesaTransactions.select {
            (MpesaTransactions.status eq "COMPLETED") or (MpesaTransactions.status eq "SUCCESS")
        }.count()

        val failedTransactions = totalTransactions - successfulTransactions

        // Calculate total revenue
        val totalRevenueResult = MpesaTransactions
            .select { (MpesaTransactions.status eq "COMPLETED") or (MpesaTransactions.status eq "SUCCESS") }
            .sumOf { it[MpesaTransactions.amount] }

        val totalRevenue = totalRevenueResult ?: BigDecimal.ZERO

        // Calculate revenue for last 7 days
        val revenueLastSevenDaysResult = MpesaTransactions
            .select {
                ((MpesaTransactions.status eq "COMPLETED") or (MpesaTransactions.status eq "SUCCESS")) and
                (MpesaTransactions.createdAt greaterEq sevenDaysAgo)
            }
            .sumOf { it[MpesaTransactions.amount] }

        val revenueLastSevenDays = revenueLastSevenDaysResult ?: BigDecimal.ZERO

        // Calculate revenue for last 30 days
        val revenueLastThirtyDaysResult = MpesaTransactions
            .select {
                ((MpesaTransactions.status eq "COMPLETED") or (MpesaTransactions.status eq "SUCCESS")) and
                (MpesaTransactions.createdAt greaterEq thirtyDaysAgo)
            }
            .sumOf { it[MpesaTransactions.amount] }

        val revenueLastThirtyDays = revenueLastThirtyDaysResult ?: BigDecimal.ZERO

        // Count transactions for last 7 days
        val transactionsLastSevenDays = MpesaTransactions
            .select { MpesaTransactions.createdAt greaterEq sevenDaysAgo }
            .count()

        // Count transactions for last 30 days
        val transactionsLastThirtyDays = MpesaTransactions
            .select { MpesaTransactions.createdAt greaterEq thirtyDaysAgo }
            .count()

        // Calculate average transaction value
        val averageTransactionValue = if (successfulTransactions > 0) {
            totalRevenue.divide(BigDecimal(successfulTransactions), 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        AdminStatistics(
            totalUsers = totalUsers.toInt(),
            totalMeters = totalMeters.toInt(),
            totalTransactions = totalTransactions.toInt(),
            totalSuccessfulTransactions = successfulTransactions.toInt(),
            totalFailedTransactions = failedTransactions.toInt(),
            totalRevenue = totalRevenue,
            revenueLastSevenDays = revenueLastSevenDays,
            revenueLastThirtyDays = revenueLastThirtyDays,
            transactionsLastSevenDays = transactionsLastSevenDays.toInt(),
            transactionsLastThirtyDays = transactionsLastThirtyDays.toInt(),
            activeMeters = activeMeters,
            inactiveMeters = inactiveMeters,
            averageTransactionValue = averageTransactionValue
        )
    }

    override fun getUserStatistics(userId: Int): UserStatistics = transaction {
        val now = LocalDateTime.now()
        val sevenDaysAgo = now.minusDays(7)
        val thirtyDaysAgo = now.minusDays(30)

        // Get user-specific meters
        val userMeterCount = Meters
            .innerJoin(MeterPayments)
            .slice(Meters.meterId.countDistinct())
            .select { MeterPayments.userId eq userId }
            .first()[Meters.meterId.countDistinct()]

        // Get meters active/inactive counts for this user
        val (activeMeters, inactiveMeters) = getMetersActiveInactiveCount(userId)

        // Get user's transactions
        val userTransactionsQuery = MpesaTransactions
            .innerJoin(MeterPayments)
            .select { MeterPayments.userId eq userId }

        val totalTransactions = userTransactionsQuery.count()

        // Get successful transactions
        val successfulTransactions = userTransactionsQuery
            .andWhere { (MpesaTransactions.status eq "COMPLETED") or (MpesaTransactions.status eq "SUCCESS") }
            .count()

        val failedTransactions = totalTransactions - successfulTransactions

        // Calculate total spent
        val totalSpentResult = userTransactionsQuery
            .andWhere { (MpesaTransactions.status eq "COMPLETED") or (MpesaTransactions.status eq "SUCCESS") }
            .sumOf { it[MpesaTransactions.amount] }

        val totalSpent = totalSpentResult ?: BigDecimal.ZERO

        // Calculate spent for last 7 days
        val spentLastSevenDaysResult = userTransactionsQuery
            .andWhere {
                ((MpesaTransactions.status eq "COMPLETED") or (MpesaTransactions.status eq "SUCCESS")) and
                (MpesaTransactions.createdAt greaterEq sevenDaysAgo)
            }
            .sumOf { it[MpesaTransactions.amount] }

        val spentLastSevenDays = spentLastSevenDaysResult ?: BigDecimal.ZERO

        // Calculate spent for last 30 days
        val spentLastThirtyDaysResult = userTransactionsQuery
            .andWhere {
                ((MpesaTransactions.status eq "COMPLETED") or (MpesaTransactions.status eq "SUCCESS")) and
                (MpesaTransactions.createdAt greaterEq thirtyDaysAgo)
            }
            .sumOf { it[MpesaTransactions.amount] }

        val spentLastThirtyDays = spentLastThirtyDaysResult ?: BigDecimal.ZERO

        // Count transactions for last 7 days
        val transactionsLastSevenDays = userTransactionsQuery
            .andWhere { MpesaTransactions.createdAt greaterEq sevenDaysAgo }
            .count()

        // Count transactions for last 30 days
        val transactionsLastThirtyDays = userTransactionsQuery
            .andWhere { MpesaTransactions.createdAt greaterEq thirtyDaysAgo }
            .count()

        // Calculate average transaction value
        val averageTransactionValue = if (successfulTransactions > 0) {
            totalSpent.divide(BigDecimal(successfulTransactions), 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        UserStatistics(
            userId = userId,
            totalMeters = userMeterCount.toInt(),
            totalTransactions = totalTransactions.toInt(),
            totalSuccessfulTransactions = successfulTransactions.toInt(),
            totalFailedTransactions = failedTransactions.toInt(),
            totalSpent = totalSpent,
            spentLastSevenDays = spentLastSevenDays,
            spentLastThirtyDays = spentLastThirtyDays,
            transactionsLastSevenDays = transactionsLastSevenDays.toInt(),
            transactionsLastThirtyDays = transactionsLastThirtyDays.toInt(),
            activeMeters = activeMeters,
            inactiveMeters = inactiveMeters,
            averageTransactionValue = averageTransactionValue
        )
    }

    override fun getDailyTransactionStats(startDate: LocalDateTime, endDate: LocalDateTime, userId: Int?): List<DailyTransactionStats> = transaction {
        // Create a date sequence for the entire range
        val dateSequence = generateSequence(startDate.toLocalDate()) { date ->
            val next = date.plusDays(1)
            if (next.isAfter(endDate.toLocalDate())) null else next
        }.toList()

        // Base query
        val baseQuery = if (userId != null) {
            MpesaTransactions
                .innerJoin(MeterPayments)
                .slice(MpesaTransactions.createdAt.date(), MpesaTransactions.amount.sum(), MpesaTransactions.id.count())
                .select { MeterPayments.userId eq userId }
        } else {
            MpesaTransactions
                .slice(MpesaTransactions.createdAt.date(), MpesaTransactions.amount.sum(), MpesaTransactions.id.count())
                .selectAll()
        }

        // Filter by date range
        val query = baseQuery
            .andWhere {
                (MpesaTransactions.createdAt greaterEq startDate) and
                (MpesaTransactions.createdAt lessEq endDate)
            }
            .groupBy(MpesaTransactions.createdAt.date())
            .orderBy(MpesaTransactions.createdAt.date())

        // Execute the query and build results
        val dbResults = query.map { row ->
            Triple(
                row[MpesaTransactions.createdAt.date()],
                row[MpesaTransactions.id.count()],
                row[MpesaTransactions.amount.sum()] ?: BigDecimal.ZERO
            )
        }.associateBy { it.first }

        // Map the date sequence to statistics, providing zeros for dates with no transactions
        dateSequence.map { date ->
            val result = dbResults[date]
            DailyTransactionStats(
                date = date.atStartOfDay(),
                count = result?.second?.toInt() ?: 0,
                value = result?.third ?: BigDecimal.ZERO
            )
        }
    }

    override fun getMeterPaymentStats(userId: Int?): MeterPaymentStats = transaction {
        val baseQuery = if (userId != null) {
            MeterPayments.select { MeterPayments.userId eq userId }
        } else {
            MeterPayments.selectAll()
        }

        val totalPayments = baseQuery.count().toInt()

        val successfulPayments = baseQuery.andWhere { MeterPayments.status eq "COMPLETED" }.count().toInt()
        val failedPayments = baseQuery.andWhere { MeterPayments.status eq "FAILED" }.count().toInt()
        val pendingPayments = baseQuery.andWhere { MeterPayments.status eq "PENDING" }.count().toInt()

        val totalAmount = baseQuery
            .andWhere { MeterPayments.status eq "COMPLETED" }
            .sumOf { it[MeterPayments.amount] } ?: BigDecimal.ZERO

        val averageAmount = if (successfulPayments > 0) {
            totalAmount.divide(BigDecimal(successfulPayments), 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        MeterPaymentStats(
            totalPayments = totalPayments,
            successfulPayments = successfulPayments,
            failedPayments = failedPayments,
            pendingPayments = pendingPayments,
            totalAmount = totalAmount,
            averageAmount = averageAmount
        )
    }

    override fun getTotalMetersCount(userId: Int?): Int = transaction {
        if (userId != null) {
            Meters
                .innerJoin(MeterPayments)
                .slice(Meters.meterId.countDistinct())
                .select { MeterPayments.userId eq userId }
                .first()[Meters.meterId.countDistinct()].toInt()
        } else {
            Meters.selectAll().count().toInt()
        }
    }

    override fun getMetersActiveInactiveCount(userId: Int?): Pair<Int, Int> = transaction {
        val baseQuery = if (userId != null) {
            val userMeterIds = MeterPayments
                .slice(MeterPayments.meterId)
                .select { MeterPayments.userId eq userId }
                .map { it[MeterPayments.meterId] }

            Meters.select { Meters.meterId inList userMeterIds }
        } else {
            Meters.selectAll()
        }

        val activeCount = baseQuery.andWhere { Meters.online eq true }.count().toInt()
        val inactiveCount = baseQuery.andWhere { Meters.online eq false }.count().toInt()

        Pair(activeCount, inactiveCount)
    }
}

package com.mike.domain.repository.stats

import com.mike.domain.model.stats.AdminStatistics
import com.mike.domain.model.stats.DailyTransactionStats
import com.mike.domain.model.stats.MeterPaymentStats
import com.mike.domain.model.stats.UserStatistics
import java.time.LocalDateTime

/**
 * Repository interface for fetching statistics data
 */
interface StatsRepository {
    /**
     * Get system-wide statistics for admin dashboard
     */
    fun getAdminStatistics(): AdminStatistics

    /**
     * Get user-specific statistics
     * @param userId The ID of the user
     */
    fun getUserStatistics(userId: Int): UserStatistics

    /**
     * Get daily transaction counts for charting
     * @param startDate The start date for the data range
     * @param endDate The end date for the data range
     * @param userId Optional user ID to filter by specific user
     */
    fun getDailyTransactionStats(startDate: LocalDateTime, endDate: LocalDateTime, userId: Int? = null): List<DailyTransactionStats>

    /**
     * Get meter payment statistics
     * @param userId Optional user ID to filter by specific user
     */
    fun getMeterPaymentStats(userId: Int? = null): MeterPaymentStats

    /**
     * Get count of total meters in the system
     * @param userId Optional user ID to filter by specific user
     */
    fun getTotalMetersCount(userId: Int? = null): Int

    /**
     * Get count of active and inactive meters
     * @param userId Optional user ID to filter by specific user
     * @return Pair of (active count, inactive count)
     */
    fun getMetersActiveInactiveCount(userId: Int? = null): Pair<Int, Int>
}

package com.mike.service.stats

import com.mike.domain.model.stats.AdminStatistics
import com.mike.domain.model.stats.DailyTransactionStats
import com.mike.domain.model.stats.MeterPaymentStats
import com.mike.domain.model.stats.UserStatistics
import com.mike.domain.repository.stats.StatsRepository
import java.time.LocalDateTime

/**
 * Implementation of StatsService that delegates to the StatsRepository
 */
class StatsServiceImpl(private val statsRepository: StatsRepository) : StatsService {

    override fun getAdminStatistics(): AdminStatistics {
        return statsRepository.getAdminStatistics()
    }

    override fun getUserStatistics(userId: Int): UserStatistics {
        return statsRepository.getUserStatistics(userId)
    }

    override fun getDailyTransactionStats(startDate: LocalDateTime, endDate: LocalDateTime, userId: Int?): List<DailyTransactionStats> {
        return statsRepository.getDailyTransactionStats(startDate, endDate, userId)
    }

    override fun getMeterPaymentStats(userId: Int?): MeterPaymentStats {
        return statsRepository.getMeterPaymentStats(userId)
    }

    override fun getTotalMetersCount(userId: Int?): Int {
        return statsRepository.getTotalMetersCount(userId)
    }

    override fun getMetersActiveInactiveCount(userId: Int?): Pair<Int, Int> {
        return statsRepository.getMetersActiveInactiveCount(userId)
    }
}

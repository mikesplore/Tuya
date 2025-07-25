package com.mike.routes

import com.mike.auth.JwtService
import com.mike.domain.model.stats.AdminStatistics
import com.mike.domain.model.stats.DailyTransactionStats
import com.mike.domain.model.stats.MeterPaymentStats
import com.mike.domain.model.stats.UserStatistics
import com.mike.domain.model.user.UserRole
import com.mike.routes.rbac.extractUserFromToken
import com.mike.routes.rbac.withRole
import com.mike.service.stats.StatsService
import com.mike.service.user.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Routes for statistics endpoints with role-based access control
 */
fun Route.statsRoutes(jwtService: JwtService, statsService: StatsService, userService: UserService) {

    route("/stats") {
        // Get admin dashboard statistics - Only for ADMIN role
        get("/admin/dashboard") {
            withRole(call, jwtService, UserRole.ADMIN) {
                val adminStats = statsService.getAdminStatistics()
                call.respond(adminStats)
            }
        }

        // Get user dashboard statistics - For both USER and ADMIN roles
        get("/user/dashboard") {
            val user = extractUserFromToken(call, jwtService, userService) ?: return@get

            val stats = statsService.getUserStatistics(user.userId)
            call.respond(stats)
        }

        // Get daily transaction stats for charting - Role-specific access
        get("/transactions/daily") {
            val user = extractUserFromToken(call, jwtService, userService) ?: return@get

            // Parse date parameters with defaults
            val startDateStr = call.request.queryParameters["startDate"]
            val endDateStr = call.request.queryParameters["endDate"]

            val formatter = DateTimeFormatter.ISO_DATE_TIME
            val now = LocalDateTime.now()
            val startDate = if (startDateStr != null) LocalDateTime.parse(startDateStr, formatter) else now.minusDays(30)
            val endDate = if (endDateStr != null) LocalDateTime.parse(endDateStr, formatter) else now

            // For admins, allow requesting stats for any user or all users
            val requestedUserId = call.request.queryParameters["userId"]?.toIntOrNull()

            val dailyStats = when {
                // Admin can see any user's stats or all users' stats
                user.userRole == UserRole.ADMIN.name && requestedUserId != null -> {
                    statsService.getDailyTransactionStats(startDate, endDate, requestedUserId)
                }
                // Admin requesting all users' stats
                user.userRole == UserRole.ADMIN.name && requestedUserId == null -> {
                    statsService.getDailyTransactionStats(startDate, endDate, null)
                }
                // Regular user can only see their own stats
                else -> {
                    statsService.getDailyTransactionStats(startDate, endDate, user.userId)
                }
            }

            call.respond(dailyStats)
        }

        // Get meter payment statistics - Role-specific access
        get("/payments") {
            val user = extractUserFromToken(call, jwtService, userService) ?: return@get

            // For admins, allow requesting stats for any user or all users
            val requestedUserId = call.request.queryParameters["userId"]?.toIntOrNull()

            val paymentStats = when {
                // Admin can see any user's stats or all users' stats
                user.userRole == UserRole.ADMIN.name && requestedUserId != null -> {
                    statsService.getMeterPaymentStats(requestedUserId)
                }
                // Admin requesting all users' stats
                user.userRole == UserRole.ADMIN.name && requestedUserId == null -> {
                    statsService.getMeterPaymentStats(null)
                }
                // Regular user can only see their own stats
                else -> {
                    statsService.getMeterPaymentStats(user.userId)
                }
            }

            call.respond(paymentStats)
        }

        // Get meters count - Role-specific access
        get("/meters/count") {
            val user = extractUserFromToken(call, jwtService, userService) ?: return@get

            // For admins, allow requesting stats for any user or all users
            val requestedUserId = call.request.queryParameters["userId"]?.toIntOrNull()

            val metersCount = when {
                // Admin can see any user's stats or all users' stats
                user.userRole == UserRole.ADMIN.name && requestedUserId != null -> {
                    statsService.getTotalMetersCount(requestedUserId)
                }
                // Admin requesting all users' stats
                user.userRole == UserRole.ADMIN.name && requestedUserId == null -> {
                    statsService.getTotalMetersCount(null)
                }
                // Regular user can only see their own stats
                else -> {
                    statsService.getTotalMetersCount(user.userId)
                }
            }

            call.respond(mapOf("totalMeters" to metersCount))
        }

        // Get active/inactive meters count - Role-specific access
        get("/meters/status") {
            val user = extractUserFromToken(call, jwtService, userService) ?: return@get

            // For admins, allow requesting stats for any user or all users
            val requestedUserId = call.request.queryParameters["userId"]?.toIntOrNull()

            val (activeCount, inactiveCount) = when {
                // Admin can see any user's stats or all users' stats
                user.userRole == UserRole.ADMIN.name && requestedUserId != null -> {
                    statsService.getMetersActiveInactiveCount(requestedUserId)
                }
                // Admin requesting all users' stats
                user.userRole == UserRole.ADMIN.name && requestedUserId == null -> {
                    statsService.getMetersActiveInactiveCount(null)
                }
                // Regular user can only see their own stats
                else -> {
                    statsService.getMetersActiveInactiveCount(user.userId)
                }
            }

            call.respond(mapOf(
                "activeMeters" to activeCount,
                "inactiveMeters" to inactiveCount
            ))
        }
    }
}

package com.mike.routes

import com.mike.domain.model.meter.MeterUserAssignment
import com.mike.service.meter.MeterUserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.meterUserRoutes(meterUserService: MeterUserService) {

    post("/meter-user/assign") {
        try {
            val assignment = call.receive<MeterUserAssignment>()
            val success = meterUserService.assignMeterToUser(assignment)
            if (success) {
                call.respond(mapOf("success" to true, "message" to "Meter assigned successfully"))
            } else {
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Meter has already been assigned to another user")
                )
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Failed to assign meter to user: ${e.message}")
            )
            e.printStackTrace()
        }
    }

    post("/meter-user/unassign") {
        try {
            val assignment = call.receive<MeterUserAssignment>()
            val success = meterUserService.unassignMeterFromUser(assignment)
            if (success) {
                call.respond(mapOf("success" to true, "message" to "Meter unassigned successfully"))
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Meter was not assigned to this user")
                )
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Failed to unassign meter from user: ${e.message}")
            )
            e.printStackTrace()
        }
    }

    get("/meter-user/user/{userId}/meters") {
        try {
            val userId = call.parameters["userId"]?.toIntOrNull()
            if (userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid userId"))
                return@get
            }
            val meters = meterUserService.getAssignedMetersByUser(userId)
            call.respond(mapOf("meters" to meters, "total" to meters.size))
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to get meters for user: ${e.message}")
            )
            e.printStackTrace()
        }
    }

    get("/meter-user/meters/unassigned") {
        try {
            val meters = meterUserService.getUnassignedMeters()
            call.respond(mapOf("meters" to meters, "total" to meters.size))
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to get unassigned meters: ${e.message}")
            )
            e.printStackTrace()
        }
    }

    get("/meter-user/meter/{meterId}/users") {
        try {
            val meterId = call.parameters["meterId"] ?: ""
            val users = meterUserService.getUsersByMeter(meterId)
            call.respond(mapOf("users" to users, "total" to users.size))
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to get users for meter: ${e.message}")
            )
            e.printStackTrace()
        }
    }

    get("/meter-user/meter/{meterId}/user") {
        try {
            val meterId = call.parameters["meterId"] ?: ""
            val user = meterUserService.getUserByMeter(meterId)
            if (user != null) {
                call.respond(user)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("message" to "No user is assigned to this meter"))
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to get user for meter: ${e.message}")
            )
            e.printStackTrace()
        }
    }

    get("/meter-user/meter/{meterId}/users-without") {
        try {
            val meterId = call.parameters["meterId"] ?: ""
            val users = meterUserService.getUsersWithoutMeter(meterId)
            call.respond(mapOf("users" to users, "total" to users.size))
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to get users without meter: ${e.message}")
            )
            e.printStackTrace()
        }
    }

    get("/meter-user/is-assigned") {
        try {
            val meterId = call.request.queryParameters["meterId"] ?: ""
            val userId = call.request.queryParameters["userId"]?.toIntOrNull()
            if (meterId.isBlank() || userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing meterId or userId"))
                return@get
            }
            val isAssigned = meterUserService.isMeterAssignedToUser(
                MeterUserAssignment(meterId, userId)
            )
            call.respond(mapOf("isAssigned" to isAssigned))
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to check assignment: ${e.message}")
            )
            e.printStackTrace()
        }
    }
}
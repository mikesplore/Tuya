package com.mike.routes

import com.mike.domain.model.meter.MeterUserAssignment
import com.mike.service.meter.MeterUserService
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.meterUserRoutes(meterUserService: MeterUserService) {

    post("/meter-user/assign") {
        val assignment = call.receive<MeterUserAssignment>()
        meterUserService.assignMeterToUser(assignment)
        call.respond(mapOf("success" to true))
    }

    post("/meter-user/unassign") {
        val assignment = call.receive<MeterUserAssignment>()
        meterUserService.unassignMeterFromUser(assignment)
        call.respond(mapOf("success" to true))
    }

    get("/meter-user/user/{userId}/meters") {
        val userId = call.parameters["userId"]?.toIntOrNull()
        if (userId == null) {
            call.respond(mapOf("error" to "Invalid userId"))
            return@get
        }
        val meters = meterUserService.getAssignedMetersByUser(userId)
        call.respond(meters)
    }

    get("/meter-user/meter/{meterId}/users") {
        val meterId = call.parameters["meterId"] ?: ""
        val users = meterUserService.getUsersByMeter(meterId)
        call.respond(users)
    }

    get("/meter-user/is-assigned") {
        val meterId = call.request.queryParameters["meterId"] ?: ""
        val userId = call.request.queryParameters["userId"]?.toIntOrNull()
        if (meterId.isBlank() || userId == null) {
            call.respond(mapOf("error" to "Missing meterId or userId"))
            return@get
        }
        val isAssigned = meterUserService.isMeterAssignedToUser(
            MeterUserAssignment(meterId, userId, true)
        )
        call.respond(mapOf("isAssigned" to isAssigned))
    }

    post("/meter-user/generate-assignments") {
        meterUserService.generateUserMeterAssignment()
        call.respond(mapOf("success" to true))
    }
}
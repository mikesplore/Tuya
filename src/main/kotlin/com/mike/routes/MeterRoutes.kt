package com.mike.routes

import com.mike.domain.model.meter.MeterCreationRequest
import com.mike.service.meter.MeterService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.meterRoutes(meterService: MeterService) {
    /**
     * GET /meters - Retrieve all meters
     */
    get("/meters") {
        val meters = meterService.getAllMeters()
        call.respond(meters)
    }

    /**
     * GET /meters/{id} - Retrieve a meter by ID
     */
    get("/meters/{id}") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val meter = meterService.findById(id)
        if (meter != null) {
            call.respond(meter)
        } else {
            call.respond(HttpStatusCode.NotFound, mapOf("message" to "Meter not found"))
        }
    }

    /**
     * POST /meters - Create a new meter
     */
    post("/meters") {
        val meter = call.receive<MeterCreationRequest>()
        val error = meterService.createMeter(meter)
        if (error != null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to error))
            return@post
        }
        call.respond(HttpStatusCode.Created, mapOf("message" to "Meter created"))
    }

    /**
     * PUT /meters/{id} - Update a meter
     */
    put("/meters/{id}") {
        val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
        val meter = call.receive<MeterCreationRequest>()
        meterService.updateMeter(meter)
        call.respond(HttpStatusCode.OK, mapOf("message" to "Meter updated"))
    }

    /**
     * DELETE /meters/{id} - Delete a meter
     */
    delete("/meters/{id}") {
        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
        val success = meterService.deleteMeter(id)
        if (success) {
            call.respond(HttpStatusCode.OK, mapOf("message" to "Meter deleted"))
        } else {
            call.respond(HttpStatusCode.NotFound, mapOf("message" to "Meter not found or could not be deleted"))
        }
    }
}
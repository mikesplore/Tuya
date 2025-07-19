package com.mike.routes

import com.mike.domain.model.meter.Meter
import com.mike.service.tuya.TuyaService
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.request.receiveParameters

fun Route.tuyaRoutes(tuyaService: TuyaService) {
    route("/tuya") {
        get("/devices") {
            try {
                val onlineDevices = tuyaService.getOnlineDevices()
                if (onlineDevices.isEmpty()) {
                    call.respond(emptyList<Meter>())
                } else {
                    call.respond(onlineDevices)
                }
            } catch (e: Exception) {
                call.respondText(
                    "Error fetching devices: ${e.message}",
                    status = HttpStatusCode.InternalServerError
                )
            }
        }
        post("/meters/{meterId}/charge") {
            val meterId = call.parameters["meterId"]
            if (meterId == null || meterId == "{meterId}") {
                return@post call.respondText("Missing or invalid meterId", status = HttpStatusCode.BadRequest)
            }
            val params = call.receiveParameters()
            val amount = params["amount"]?.toIntOrNull() ?: return@post call.respondText("Missing or invalid amount", status = HttpStatusCode.BadRequest)
            val userId = params["userId"]?.toIntOrNull()
            println("Meter ID: $meterId, Amount: $amount, User ID: $userId")
            val result = tuyaService.chargeMeter(meterId, amount, userId)
            if (result) {
                call.respond(mapOf("message" to "Meter charged successfully"))
            } else {
                call.respondText("Failed to charge meter", status = HttpStatusCode.InternalServerError)
            }
        }
        post("/meters/{meterId}/rate") {
            val meterId = call.parameters["meterId"] ?: return@post call.respondText("Missing meterId", status = HttpStatusCode.BadRequest)
            val params = call.receiveParameters()
            val price = params["price"]?.toIntOrNull() ?: return@post call.respondText("Missing or invalid price", status = HttpStatusCode.BadRequest)
            val result = tuyaService.setMeterRate(meterId, price)
            if (result) {
                call.respond(mapOf("message" to "Meter rate updated successfully"))
            } else {
                call.respondText("Failed to update meter rate", status = HttpStatusCode.InternalServerError)
            }
        }
        get("/meters/{meterId}/balance") {
            val meterId = call.parameters["meterId"] ?: return@get call.respondText("Missing meterId", status = HttpStatusCode.BadRequest)
            val balance = tuyaService.getMeterBalance(meterId)
            call.respond(mapOf("balance" to balance))
        }
        get("/meters/{meterId}/rate") {
            val meterId = call.parameters["meterId"] ?: return@get call.respondText("Missing meterId", status = HttpStatusCode.BadRequest)
            val rate = tuyaService.getMeterRate(meterId)
            call.respond(mapOf("rate" to rate))
        }
        post("/meters/{meterId}/prepayment") {
            val meterId = call.parameters["meterId"] ?: return@post call.respondText("Missing meterId", status = HttpStatusCode.BadRequest)
            val params = call.receiveParameters()
            val enabled = params["enabled"]?.toBooleanStrictOrNull() ?: return@post call.respondText("Missing or invalid enabled", status = HttpStatusCode.BadRequest)
            val result = tuyaService.togglePrepaymentMode(meterId, enabled)
            if (result) {
                call.respond(mapOf("message" to "Prepayment mode updated successfully"))
            } else {
                call.respondText("Failed to update prepayment mode", status = HttpStatusCode.InternalServerError)
            }
        }
        get("/meters/{meterId}/history") {
            val meterId = call.parameters["meterId"] ?: return@get call.respondText("Missing meterId", status = HttpStatusCode.BadRequest)
            val history = tuyaService.getMeterHistory(meterId)
            call.respond(history)
        }
    }
}
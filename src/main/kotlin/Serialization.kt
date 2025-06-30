package com.mike

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.time.LocalDateTime

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        gson {
            // Configure Gson with pretty printing and lenient parsing
            serializeNulls()
            disableHtmlEscaping()
        }
    }
    
    routing {
        get("/json/gson") {
            call.respond(mapOf("hello" to "world"))
        }
    }
}

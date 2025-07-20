package com.mike.apidocumentation

import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.swagger.*

fun Application.configureSwagger() {
    routing {
        // Serve the Swagger UI for the authentication API
        swaggerUI(path = "swagger/authentication", swaggerFile = "openapi/authentication.yaml")
        openAPI(path = "openapi/authentication", swaggerFile = "openapi/authentication.yaml")
        openAPI(path = "swagger/meters", swaggerFile = "openapi/meters.yaml")
        openAPI(path = "swagger/meteruser", swaggerFile = "openapi/meteruser.yaml")
        swaggerUI(path = "swagger/meteruser", swaggerFile = "openapi/meteruser.yaml")
        swaggerUI(path = "swagger/tuya", swaggerFile = "openapi/tuya.yaml")
        openAPI(path = "openapi/tuya", swaggerFile = "openapi/tuya.yaml")

        swaggerUI(path = "swagger/mpesa", swaggerFile = "openapi/mpesa.yaml")
        openAPI(path = "openapi/mpesa", swaggerFile = "openapi/mpesa.yaml")

        swaggerUI(path = "swagger/user", swaggerFile = "openapi/users.yaml")
        openAPI(path = "openapi/user", swaggerFile = "openapi/users.yaml")
        swaggerUI(path = "swagger/meter", swaggerFile = "openapi/meters.yaml")

    }
}
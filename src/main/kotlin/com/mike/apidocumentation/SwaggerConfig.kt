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

        swaggerUI(path = "swagger/user", swaggerFile = "openapi/users.yaml")
        openAPI(path = "openapi/user", swaggerFile = "openapi/users.yaml")

    }
}
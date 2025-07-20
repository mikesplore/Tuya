package com.mike.routes.rbac

import com.mike.auth.JwtService
import com.mike.domain.model.user.UserRole
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

suspend fun extractUserRoleFromToken(call: ApplicationCall, jwtService: JwtService): UserRole? {
    val authorizationHeader = call.request.headers[HttpHeaders.Authorization]
    if (authorizationHeader.isNullOrBlank() || !authorizationHeader.startsWith("Bearer ")) {
        println("No Authorization header found or invalid format")
        return null
    }

    val accessToken = authorizationHeader.removePrefix("Bearer ").trim()
    return try {
        val jwt = jwtService.jwtVerifier.verify(accessToken)
        val role = jwt.getClaim("role").asString()
        println("Extracted role from token: $role for path: ${call.request.path()}")
        if (role.isNullOrEmpty()) {
            call.respondText("Invalid token for $role", status = HttpStatusCode.Unauthorized)
            null
        } else {
            UserRole.valueOf(role)
        }
    } catch (e: Exception) {
        println("Error in extractUserRoleFromToken: ${e.message}")
        call.respondText("Invalid token", status = HttpStatusCode.Unauthorized)
        null
    }
}


suspend fun extractUserEmailFromToken(call: ApplicationCall, jwtService: JwtService): String? {
    val authorizationHeader = call.request.headers[HttpHeaders.Authorization]
    if (authorizationHeader.isNullOrBlank() || !authorizationHeader.startsWith("Bearer ")) {
        println("No Authorization header found or invalid format")
        return null
    }

    val accessToken = authorizationHeader.removePrefix("Bearer ").trim()
    return try {
        val jwt = jwtService.jwtVerifier.verify(accessToken)
        val email = jwt.getClaim("email").asString()
        println("Extracted email from token: $email for path: ${call.request.path()}")
        if (email.isNullOrEmpty()) {
            call.respondText("Invalid token for $email", status = HttpStatusCode.Unauthorized)
            null
        } else {
            email
        }
    } catch (e: Exception) {
        println("Error in extractUserEmailFromToken: ${e.message}")
        call.respondText("Invalid token", status = HttpStatusCode.Unauthorized)
        null
    }
}

suspend fun withRole(
    call: ApplicationCall,
    jwtService: JwtService,
    vararg allowedRoles: UserRole,
    handler: suspend () -> Unit
) {
    try {
        val userRole = extractUserRoleFromToken(call, jwtService)
        println("Fetched user role: ${userRole ?: "Unknown"} for path: ${call.request.path()}")
        if (userRole == null || userRole !in allowedRoles) {
            call.respondText("Access denied", status = HttpStatusCode.Forbidden)
            return
        }
        handler()
    } catch (e: Exception) {
        println("Error in withRole for path ${call.request.path()}: ${e.message}")
        call.respondText(
            "Internal server error: ${e.message}",
            status = HttpStatusCode.InternalServerError
        )
    }
}
package com.mike.routes

import com.mike.domain.model.user.Profile
import com.mike.domain.model.user.RegisterRequest
//import com.mike.service.meter.MeterService
import com.mike.service.user.UserService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class PasswordChangeRequest(
    val currentPassword: String,
    val newPassword: String
)

fun Route.userRoutes(userService: UserService) {

    // Get all users (admin only)
    get("/users") {
        val principal = call.principal<JWTPrincipal>()
        val role = principal?.payload?.getClaim("role")?.asString()

        if (role != "ADMIN") {
            call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Admin access required"))
            return@get
        }

        val users = userService.getAllUsers()
        call.respond(users)
    }

    post("/users") {
        val principal = call.principal<JWTPrincipal>()
        val role = principal?.payload?.getClaim("role")?.asString()

        if (role != "ADMIN") {
            call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Admin access required"))
            return@post
        }

        try {
            val registerRequest = call.receive<RegisterRequest>()

            val (success, error) = userService.createUser(registerRequest)
            if (success) {
                val user = userService.getUserByEmail(registerRequest.email)
                call.respond(HttpStatusCode.Created, user!!)
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to error))
            }

        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("message" to "Failed to create user: ${e.message}")
            )
        }
    }

    // Get current user
    get("/users/me") {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.payload?.subject?.toIntOrNull()

        if (userId != null) {
            val user = userService.getUserById(userId)
            if (user != null) {
                call.respond(user)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("message" to "User not found"))
            }
        } else {
            call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Invalid authentication"))
        }
    }

    // Update user
    put("/users/{id}") {
        val principal = call.principal<JWTPrincipal>()
        val currentUserId = principal?.payload?.subject?.toIntOrNull()
        val role = principal?.payload?.getClaim("role")?.asString()
        val id = call.parameters["id"]?.toIntOrNull() ?: return@put call.respond(HttpStatusCode.BadRequest)

        // Only allow users to update their own info, unless they're an admin
        if (id != currentUserId && role != "ADMIN") {
            call.respond(HttpStatusCode.Forbidden, mapOf("message" to "You can only update your own profile"))
            return@put
        }

        try {
            val profile = call.receive<Profile>()

            val (success, error) = userService.updateUser(profile)
            if (success) {
                val updatedUser = userService.getUserById(id)
                call.respond(updatedUser!!)
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to error))
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Failed to update user: ${e.message}"))
        }
    }


    // Delete user (admin only)
    delete("/users/{id}") {
        val principal = call.principal<JWTPrincipal>()
        val role = principal?.payload?.getClaim("role")?.asString()

        if (role != "ADMIN") {
            call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Admin access required"))
            return@delete
        }

        val id = call.parameters["id"]?.toIntOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest)
        val (success, error) = userService.deleteUser(id)

        if (success) {
            call.respond(HttpStatusCode.OK, mapOf("message" to "User deleted"))
        } else {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to error))
        }
    }
}

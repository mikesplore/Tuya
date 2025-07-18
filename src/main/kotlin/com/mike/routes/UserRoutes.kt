package com.mike.routes

import com.mike.domain.model.user.Profile
import com.mike.domain.model.user.RegisterRequest
import com.mike.service.user.UserService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userRoutes(userService: UserService) {

    // Get all users (admin only)
    get("/users") {

        val role = getUserRoleFromCall(call) ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Unauthorized"))

        if (role != "ADMIN") {
            call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Admin access required"))
            return@get
        }

        val users = userService.getAllUsers()
        call.respond(users)
    }

    get("/users/{id}") {
        val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)

        val user = userService.getUserProfile(id)
        if (user != null) {
            call.respond(user)
        } else {
            call.respond(HttpStatusCode.NotFound, mapOf("message" to "User not found"))
        }
    }

    post("/users") {
        val role = getUserRoleFromCall(call) ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Unauthorized"))

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

    // Update user
    put("/users/{id}") {
        val principal = call.principal<JWTPrincipal>()
        val currentUserId = principal?.payload?.subject?.toIntOrNull()
        val role = getUserRoleFromCall(call) ?: return@put call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Unauthorized"))
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
        val role = getUserRoleFromCall(call) ?: return@delete call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Unauthorized"))

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


fun getUserRoleFromCall(call: RoutingCall): String? {
    val principal = call.principal<JWTPrincipal>()
    return principal?.payload?.getClaim("role")?.asString()
}
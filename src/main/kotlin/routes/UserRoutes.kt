package com.mike.routes

import com.mike.database.entities.UserDto
import com.mike.database.repository.UserRepository
import com.mike.database.repository.UserMeterAssignmentRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class UserUpdateRequest(
    val email: String? = null,
    val phoneNumber: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val role: String? = null,
    val active: Boolean? = null
)

data class PasswordChangeRequest(
    val currentPassword: String,
    val newPassword: String
)

fun Route.userRoutes(userRepository: UserRepository) {
    // Create an instance of UserMeterAssignmentRepository
    val userMeterAssignmentRepository = UserMeterAssignmentRepository()
    
    // Get all users (admin only)
    get("/users") {
        val principal = call.principal<JWTPrincipal>()
        val role = principal?.payload?.getClaim("role")?.asString()
        
        if (role != "ADMIN") {
            call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Admin access required"))
            return@get
        }
        
        val users = userRepository.getAllUsers()
        if (users.isEmpty()) {
            println("No users found")
        }
        call.respond(users)
    }
    
    // Create new user (admin only)
    post("/users") {
        val principal = call.principal<JWTPrincipal>()
        val role = principal?.payload?.getClaim("role")?.asString()
        
        if (role != "ADMIN") {
            call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Admin access required"))
            return@post
        }
        
        try {
            val request = call.receive<Map<String, String>>()
            
            // Extract required parameters
            val email = request["email"] ?: return@post call.respond(
                HttpStatusCode.BadRequest, 
                mapOf("message" to "Email is required")
            )
            
            val password = request["password"] ?: return@post call.respond(
                HttpStatusCode.BadRequest, 
                mapOf("message" to "Password is required")
            )
            
            val firstName = request["firstName"] ?: request["name"]?.split(" ")?.getOrNull(0)
            val lastName = request["lastName"] ?: request["name"]?.split(" ")?.getOrNull(1)
            val role = request["role"] ?: "USER"
            val phoneNumber = request["phoneNumber"]
            
            // Check if user with email already exists
            val existingUser = userRepository.findByEmail(email)
            if (existingUser != null) {
                call.respond(
                    HttpStatusCode.Conflict,
                    mapOf("message" to "Email already registered", "userId" to existingUser.id)
                )
                return@post
            }
            
            // Create the user
            val user = userRepository.createUser(
                email = email,
                password = password,
                firstName = firstName,
                lastName = lastName,
                phoneNumber = phoneNumber,
                role = role
            )
            
            call.respond(HttpStatusCode.Created, user)
            
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
        val userId = principal?.payload?.subject
        
        if (userId != null) {
            val user = userRepository.findById(userId)
            if (user != null) {
                call.respond(user)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("message" to "User not found"))
            }
        } else {
            call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Invalid authentication"))
        }
    }
    
    // Get user by ID (admin only)
    get("/users/{id}") {
        val principal = call.principal<JWTPrincipal>()
        val role = principal?.payload?.getClaim("role")?.asString()
        
        if (role != "ADMIN") {
            call.respond(HttpStatusCode.Forbidden, mapOf("message" to "Admin access required"))
            return@get
        }
        
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val user = userRepository.findById(id)
        
        if (user != null) {
            call.respond(user)
        } else {
            call.respond(HttpStatusCode.NotFound, mapOf("message" to "User not found"))
        }
    }
    
    // Get user's devices
    get("/users/{id}/devices") {
        val principal = call.principal<JWTPrincipal>()
        val currentUserId = principal?.payload?.subject
        val role = principal?.payload?.getClaim("role")?.asString()
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        
        // Only allow users to view their own devices, unless they're an admin
        if (id != currentUserId && role != "ADMIN") {
            call.respond(HttpStatusCode.Forbidden, mapOf("message" to "You can only view your own devices"))
            return@get
        }
        
        try {
            val devices = userMeterAssignmentRepository.getUserMeters(id)
            call.respond(devices)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Failed to get user devices: ${e.message}"))
        }
    }
    
    // Update user
    put("/users/{id}") {
        val principal = call.principal<JWTPrincipal>()
        val currentUserId = principal?.payload?.subject
        val role = principal?.payload?.getClaim("role")?.asString()
        val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
        
        // Only allow users to update their own info, unless they're an admin
        if (id != currentUserId && role != "ADMIN") {
            call.respond(HttpStatusCode.Forbidden, mapOf("message" to "You can only update your own profile"))
            return@put
        }
        
        val request = call.receive<UserUpdateRequest>()
        val updatedUser = userRepository.updateUser(
            id = id,
            email = request.email,
            firstName = request.firstName,
            lastName = request.lastName,
            phoneNumber = request.phoneNumber,
            role = if (role == "ADMIN") request.role else null,
            active = if (role == "ADMIN") request.active else null
        )
        
        if (updatedUser != null) {
            call.respond(updatedUser)
        } else {
            call.respond(HttpStatusCode.NotFound, mapOf("message" to "User not found"))
        }
    }
    
    // Change password
    post("/users/{id}/change-password") {
        val principal = call.principal<JWTPrincipal>()
        val currentUserId = principal?.payload?.subject
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        
        // Only allow users to change their own password
        if (id != currentUserId) {
            call.respond(HttpStatusCode.Forbidden, mapOf("message" to "You can only change your own password"))
            return@post
        }
        
        val request = call.receive<PasswordChangeRequest>()
        
        // Verify current password
        val user = userRepository.findById(id)?.let { dto ->
            userRepository.validateCredentials(dto.email, request.currentPassword)
        }
        
        if (user == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Current password is incorrect"))
            return@post
        }
        
        val success = userRepository.changePassword(id, request.newPassword)
        if (success) {
            call.respond(HttpStatusCode.OK, mapOf("message" to "Password updated successfully"))
        } else {
            call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Failed to update password"))
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
        
        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
        val success = userRepository.deleteUser(id)
        
        if (success) {
            call.respond(HttpStatusCode.OK, mapOf("message" to "User deleted"))
        } else {
            call.respond(HttpStatusCode.NotFound, mapOf("message" to "User not found"))
        }
    }
}

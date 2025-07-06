package com.mike

import com.mike.auth.JwtService
import com.mike.database.repository.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

data class LoginRequest(val email: String, val password: String)

data class RegisterRequest(
    val email: String, 
    val password: String,
    val phoneNumber: String? = null,
    val firstName: String? = null,
    val lastName: String? = null
)

data class AuthResponse(val token: String, val userId: String, val email: String)

fun Application.configureSecurity() {
    val config = environment.config
    val jwtService = JwtService(config)
    val userRepository = UserRepository()
    
    // Configure JWT authentication
    authentication {
        jwtService.configureJwtAuthentication(config, this)
    }
    
    // Auth routes (login, register)
    routing {
        post("/auth/login") {
            val loginRequest = call.receive<LoginRequest>()
            
            val rulesResult = checkRules(loginRequest)
            if (rulesResult != "Valid") {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to rulesResult))
                return@post
            }

            val user = userRepository.validateCredentials(loginRequest.email, loginRequest.password)
            if (user != null) {
                val token = jwtService.generateToken(user)
                call.respond(AuthResponse(token, user.id, user.email))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("message" to "Invalid credentials"))
            }
        }
        
        post("/auth/register") {
            val registerRequest = call.receive<RegisterRequest>()
            
            val existingUser = userRepository.findByEmail(registerRequest.email)
            if (existingUser != null) {
                call.respond(HttpStatusCode.Conflict, mapOf("message" to "Email already registered"))
                return@post
            }
            
            val user = userRepository.createUser(
                email = registerRequest.email,
                password = registerRequest.password,
                firstName = registerRequest.firstName,
                lastName = registerRequest.lastName,
                phoneNumber = registerRequest.phoneNumber
            )
            
            val token = jwtService.generateToken(user)
            call.respond(AuthResponse(token, user.id, user.email))
        }
    }
}

fun checkRules(loginRequest: LoginRequest): String {
    if (loginRequest.email.isBlank() && loginRequest.password.isBlank()) {
        return "Email and password cannot be empty"
    }
    if (loginRequest.email.isBlank()) {
        return "Email cannot be empty"
    }

    if (loginRequest.password.isBlank()) {
        return "Password cannot be empty"
    }
    val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    if (!emailRegex.matches(loginRequest.email)) {
        return "Invalid email format"
    }
    return "Valid"
}

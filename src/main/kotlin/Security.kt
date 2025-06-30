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
            
            val user = userRepository.validateCredentials(loginRequest.email, loginRequest.password)
            if (user != null) {
                val token = jwtService.generateToken(user)
                call.respond(AuthResponse(token, user.id, user.email))
            } else {
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Invalid credentials"))
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

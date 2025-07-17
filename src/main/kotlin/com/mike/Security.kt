//package com.mike
//
//import com.mike.auth.JwtService
//import com.mike.domain.model.auth.AuthResponse
//import com.mike.domain.model.auth.TokenRequest
//import com.mike.domain.model.auth.VerifyTokenRequest
//import com.mike.domain.model.auth.VerifyTokenResponse
//import com.mike.domain.model.user.LoginCredentials
//import com.mike.domain.model.user.RegisterRequest
//import com.mike.service.user.UserService
//import io.ktor.http.*
//import io.ktor.server.application.*
//import io.ktor.server.auth.*
//import io.ktor.server.auth.jwt.JWTPrincipal
//import io.ktor.server.request.*
//import io.ktor.server.response.*
//import io.ktor.server.routing.*
//
//
//
//fun Application.configureSecurity(userService: UserService) {
//    val config = environment.config
//    val jwtService = JwtService(config)
//
//    // Configure JWT authentication
//    authentication {
//        jwtService.configureJwtAuthentication(config, this)
//    }
//
//    // Auth routes (login, register)
//    routing {
//        post("/login") {
//            val loginRequest = call.receive<LoginCredentials>()
//
//            val rulesResult = checkRules(loginRequest)
//            if (!rulesResult.first) {
//                call.respond(HttpStatusCode.BadRequest, mapOf("message" to rulesResult.second))
//                return@post
//            }
//
//            val (success, result) = userService.authenticateUser(loginRequest)
//            if (success) {
//                val user = userService.getUserById(result!!)
//                if (user != null) {
//                    val token = jwtService.generateToken(user)
//                    val refreshToken = userService.createRefreshToken(user.id?:0)
//                    call.respond(AuthResponse(token, refreshToken.token, user.id?:0, user.email))
//                } else {
//                    call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "User data retrieval failed"))
//                }
//            } else {
//                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to result))
//            }
//        }
//
//        post("/register") {
//            val registerRequest = call.receive<RegisterRequest>()
//
//            val existingUser = userService.getUserByEmail(registerRequest.email)
//            if (existingUser != null) {
//                call.respond(HttpStatusCode.Conflict, mapOf("message" to "Email already registered"))
//                return@post
//            }
//
//            val (success, error) = userService.createUser(registerRequest)
//            if (success) {
//                val user = userService.getUserByEmail(registerRequest.email)
//                if (user != null) {
//                    val token = jwtService.generateToken(user)
//                    val refreshToken = userService.createRefreshToken(user.id?:0)
//                    call.respond(AuthResponse(token, refreshToken.token, user.id?:0, user.email))
//                } else {
//                    call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "User creation succeeded but retrieval failed"))
//                }
//            } else {
//                call.respond(HttpStatusCode.BadRequest, mapOf("message" to error))
//            }
//        }
//
//        // Token refresh endpoint - does not require authentication
//        post("/refresh-token") {
//            val tokenRequest = call.receive<TokenRequest>()
//            val (isValid, userId) = userService.verifyToken(tokenRequest.refreshToken)
//
//            if (isValid && userId != null) {
//                val user = userService.getUserById(userId.toString())
//                if (user != null) {
//                    val newAccessToken = jwtService.generateToken(user)
//                    // Optionally issue a new refresh token and revoke the old one for enhanced security
//                    val newRefreshToken = userService.createRefreshToken(userId)
//                    userService.revokeRefreshToken(tokenRequest.refreshToken)
//
//                    call.respond(AuthResponse(newAccessToken, newRefreshToken.token, user.id?:0, user.email))
//                } else {
//                    call.respond(HttpStatusCode.NotFound, mapOf("message" to "User not found"))
//                }
//            } else {
//                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Invalid refresh token"))
//            }
//        }
//
//        // Token verification endpoint - useful for client-side validation
//        post("/verify-token") {
//            val verifyRequest = call.receive<VerifyTokenRequest>()
//            val (isValid, userId) = userService.verifyToken(verifyRequest.token)
//
//            call.respond(VerifyTokenResponse(isValid, userId?.toString()))
//        }
//
//        // Protected routes that require authentication
//        authenticate {
//            get("/me") {
//                val principal = call.principal<JWTPrincipal>()
//                val userId = principal?.payload?.getClaim("userId")?.asString()
//
//                if (userId != null) {
//                    val user = userService.getUserById(userId)
//                    if (user != null) {
//                        call.respond(user)
//                    } else {
//                        call.respond(HttpStatusCode.NotFound, mapOf("message" to "User not found"))
//                    }
//                } else {
//                    call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Invalid token"))
//                }
//            }
//
//            post("/logout") {
//                val principal = call.principal<JWTPrincipal>()
//                val userId = principal?.payload?.getClaim("userId")?.asString()
//
//                if (userId != null) {
//                    // Revoke all refresh tokens for this user
//                    userService.revokeAllUserRefreshTokens(userId.toInt())
//                    call.respond(HttpStatusCode.OK, mapOf("message" to "Successfully logged out"))
//                } else {
//                    call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Invalid token"))
//                }
//            }
//        }
//    }
//}
//
//fun checkRules(loginRequest: LoginCredentials): Pair<Boolean, String?> {
//    if (loginRequest.email.isBlank()) {
//        return Pair(false, "Email is required")
//    }
//    if (loginRequest.password.isBlank()) {
//        return Pair(false, "Password is required")
//    }
//    // Add more validation rules as needed
//    return Pair(true, null)
//}

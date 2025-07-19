package com.mike.routes

import com.mike.domain.model.auth.ChangePasswordRequest
import com.mike.domain.model.auth.ErrorResponse
import com.mike.domain.model.auth.LoginCredentials
import com.mike.domain.model.auth.LoginResponse
import com.mike.domain.model.auth.MessageResponse
import com.mike.domain.model.auth.RefreshTokenRequest
import com.mike.domain.model.auth.TokenPayload
import com.mike.domain.model.auth.VerifyTokenRequest
import com.mike.domain.model.auth.VerifyTokenResponse
import com.mike.service.auth.AuthService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


fun Route.authRoutes(authService: AuthService) {
    val scope = CoroutineScope(Dispatchers.IO)

    route("/auth") {

        // POST /auth/login
        post("/login") {
            try {
                val request = call.receive<LoginCredentials>()
                val loginCredentials = LoginCredentials(
                    email = request.email,
                    password = request.password
                )

                val (profile, accessToken, refreshToken) = authService.login(loginCredentials)

                if (profile != null && accessToken != null && refreshToken != null) {
                    call.respond(
                        HttpStatusCode.OK,
                        LoginResponse(
                            profile = profile,
                            accessToken = accessToken,
                            refreshToken = refreshToken
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse("Invalid credentials")
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid request format")
                )
            }
        }

        // POST /auth/refresh-token
        post("/refresh-token") {
            try {
                val request = call.receive<RefreshTokenRequest>()
                val tokenPayload = authService.refreshToken(request.refreshToken)

                if (tokenPayload != null) {
                    call.respond(
                        HttpStatusCode.OK,
                        TokenPayload(
                            accessToken = tokenPayload.accessToken,
                            refreshToken = tokenPayload.refreshToken
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse("Invalid or expired refresh token")
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid request format")
                )
            }
        }

        // POST /auth/verify-token
        post("/verify-token") {
            try {
                val request = call.receive<VerifyTokenRequest>()
                val (isValid, userId) = authService.validateRefreshToken(request.token)

                call.respond(
                    HttpStatusCode.OK,
                    VerifyTokenResponse(
                        isValid = isValid,
                        userId = userId
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid request format")
                )
            }
        }

        // POST /auth/revoke-token
        post("/revoke-token") {
            try {
                val request = call.receive<RefreshTokenRequest>()
                val revoked = authService.revokeToken(request.refreshToken)

                if (revoked) {
                    call.respond(
                        HttpStatusCode.OK,
                        MessageResponse("Token revoked successfully")
                    )
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse("Token not found")
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid request format")
                )
            }
        }

        // POST /auth/logout - Revoke the current user's refresh token
        post("/logout") {
            scope.launch {
                try {
                    val request = call.receive<RefreshTokenRequest>()
                    val revoked = authService.revokeToken(request.refreshToken)

                    if (revoked) {
                        call.respond(
                            HttpStatusCode.OK,
                            MessageResponse("Logged out successfully")
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Failed to logout")
                        )
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Invalid request format")
                    )
                }
            }

        }

        // DELETE /auth/revoke-user-tokens - Revoke all tokens for current user
        delete("/revoke-user-tokens") {
            authenticate("auth-jwt") {
                scope.launch {
                    try {
                        val principal = call.principal<JWTPrincipal>()
                        val userId = principal?.payload?.subject?.toIntOrNull()

                        if (userId != null) {
                            val revoked = authService.revokeAllUserTokens(userId)

                            if (revoked) {
                                call.respond(
                                    HttpStatusCode.OK,
                                    MessageResponse("All user tokens revoked successfully")
                                )
                            } else {
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    ErrorResponse("Failed to revoke tokens")
                                )
                            }
                        } else {
                            call.respond(
                                HttpStatusCode.Unauthorized,
                                ErrorResponse("Invalid user token")
                            )
                        }
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse("Internal server error")
                        )
                    }
                }
            }
        }

        // POST /auth/change-password
        post("/change-password") {
            authenticate("auth-jwt") {
                scope.launch {
                    try {
                        val principal = call.principal<JWTPrincipal>()
                        val userId = principal?.payload?.subject
                        val request = call.receive<ChangePasswordRequest>()

                        if (userId != null) {
                            val (success, error) = authService.changePassword(userId, request.newPassword)

                            if (success) {
                                call.respond(
                                    HttpStatusCode.OK,
                                    MessageResponse("Password changed successfully")
                                )
                            } else {
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    ErrorResponse(error ?: "Failed to change password")
                                )
                            }
                        } else {
                            call.respond(
                                HttpStatusCode.Unauthorized,
                                ErrorResponse("Invalid user token")
                            )
                        }
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("Invalid request format")
                        )
                    }
                }
            }
        }
    }
}
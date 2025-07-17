package com.mike.domain.model.auth

data class AuthResponse(val token: String, val refreshToken: String, val userId: Int, val email: String)
data class TokenRequest(val refreshToken: String)
data class LoginCredentials(val email: String, val password: String)
data class RefreshTokenRequest(val refreshToken: String)
data class ChangePasswordRequest(val newPassword: String)
data class MessageResponse(val message: String)
data class ErrorResponse(val error: String)
data class VerifyTokenRequest(val token: String)
data class VerifyTokenResponse(val isValid: Boolean, val userId: Int?)
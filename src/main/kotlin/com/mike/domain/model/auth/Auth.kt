package com.mike.domain.model.auth

data class AuthResponse(val token: String, val refreshToken: String, val userId: Int, val email: String)
data class TokenRequest(val refreshToken: String)
data class VerifyTokenRequest(val token: String)
data class VerifyTokenResponse(val isValid: Boolean, val userId: String?)
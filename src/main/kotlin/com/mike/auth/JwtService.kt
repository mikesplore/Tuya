package com.mike.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import com.mike.domain.model.user.User
import io.ktor.server.config.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.util.*

class JwtService(config: ApplicationConfig) {
    private val secret: String = config.property("security.jwt.secret").getString()
    private val issuer: String = config.property("security.jwt.issuer").getString()
    private val audience: String = config.property("security.jwt.audience").getString()

    // Access token: 30 minutes, Refresh token: 24 hours (in ms)
    private val accessTokenExpirationMs: Long = 30 * 60 * 1000 // 30 minutes
    private val refreshTokenExpirationMs: Long = 24 * 60 * 60 * 1000 // 24 hours

    val jwtVerifier: JWTVerifier = JWT
        .require(Algorithm.HMAC256(secret))
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun generateAccessToken(user: User): String {
        val now = Date()
        val expiration = Date(now.time + accessTokenExpirationMs)
        return JWT.create()
            .withSubject(user.id.toString())
            .withIssuedAt(now)
            .withIssuer(issuer)
            .withAudience(audience)
            .withExpiresAt(expiration)
            .withClaim("email", user.email)
            .withClaim("role", user.role)
            .sign(Algorithm.HMAC256(secret))
    }

    fun generateRefreshToken(user: User): String {
        val now = Date()
        val expiration = Date(now.time + refreshTokenExpirationMs)
        return JWT.create()
            .withSubject(user.id.toString())
            .withIssuedAt(now)
            .withIssuer(issuer)
            .withAudience(audience)
            .withExpiresAt(expiration)
            .withClaim("token_type", "refresh")
            .sign(Algorithm.HMAC256(secret))
    }

    fun configureJwtAuthentication(config: ApplicationConfig, authConfig: AuthenticationConfig) {
        val realm = config.property("security.jwt.realm").getString()

        authConfig.jwt("auth-jwt") {
            this.realm = realm
            verifier(jwtVerifier)
            validate { credential ->
                if (
                    credential.payload.audience.contains(audience) &&
                    credential.payload.issuer == issuer &&
                    !credential.payload.expiresAt.before(Date())
                ) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}

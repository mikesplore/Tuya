package com.mike.com.mike.auth

import com.mike.domain.model.user.UserDto
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import io.ktor.server.config.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.util.*

class JwtService(config: ApplicationConfig) {
    private val secret: String = config.property("security.jwt.secret").getString()
    private val issuer: String = config.property("security.jwt.issuer").getString()
    private val audience: String = config.property("security.jwt.audience").getString()
    private val expirationInMs: Long = config.property("security.jwt.token_expiration").getString().toLong()
    
    // Create JWT verifier
    private val jwtVerifier: JWTVerifier = JWT
        .require(Algorithm.HMAC256(secret))
        .withIssuer(issuer)
        .withAudience(audience)
        .build()
    
    fun generateToken(user: UserDto): String {
        val now = Date()
        val expiration = Date(now.time + expirationInMs)
        
        return JWT.create()
            .withSubject(user.id)
            .withIssuedAt(now)
            .withIssuer(issuer)
            .withAudience(audience)
            .withExpiresAt(expiration)
            .withClaim("email", user.email)
            .withClaim("role", user.role)
            .sign(Algorithm.HMAC256(secret))
    }
    
    fun configureJwtAuthentication(config: ApplicationConfig, authConfig: AuthenticationConfig) {
        val realm = config.property("security.jwt.realm").getString()
        
        authConfig.jwt("auth-jwt") {
            this.realm = realm
            verifier(jwtVerifier)
            validate { credential ->
                if (credential.payload.audience.contains(audience) && 
                    credential.payload.issuer == issuer && 
                    !credential.payload.expiresAt.before(Date())) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}

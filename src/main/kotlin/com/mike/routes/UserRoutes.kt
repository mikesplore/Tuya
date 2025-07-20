package com.mike.routes

import com.mike.auth.JwtService
import com.mike.domain.model.user.Profile
import com.mike.domain.model.user.RegisterRequest
import com.mike.domain.model.user.UserRole
import com.mike.routes.rbac.extractUserEmailFromToken
import com.mike.routes.rbac.withRole
import com.mike.service.user.UserService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userRoutes(userService: UserService, jwtService: JwtService) {
    // Get all users (admin only)
    route("/users") {
        get {
            withRole(call, jwtService, UserRole.ADMIN, UserRole.USER) {
                val users = userService.getAllUsers()
                call.respond(users)
            }
        }

        get("/{id}") {
            withRole(call, jwtService, UserRole.ADMIN) {
                val id = call.parameters["id"]?.toIntOrNull() ?: return@withRole call.respond(HttpStatusCode.BadRequest)

                val user = userService.getUserProfile(id)
                if (user != null) {
                    call.respond(user)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("message" to "User not found"))
                }
            }
        }

        get("/me") {
            try {
                val userEmail = extractUserEmailFromToken(call, jwtService) ?: return@get call.respond("Unauthorized")
                val user = userService.getUserByEmail(userEmail)
                if (user != null) {
                    call.respond(user)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("message" to "User not found"))
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("message" to "Failed to fetch user profile: ${e.message}")
                )
            }
        }

        post {

            withRole(call, jwtService, UserRole.ADMIN) {
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
        }

        // Update user
        put("/{id}") {
            withRole(call, jwtService, UserRole.ADMIN) {
                val id = call.parameters["id"]?.toIntOrNull() ?: return@withRole call.respond(HttpStatusCode.BadRequest)
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
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("message" to "Failed to update user: ${e.message}")
                    )
                }
            }
        }


        // Delete user (admin only)
        delete("/{id}") {
            withRole(call, jwtService, UserRole.ADMIN) {
                val id = call.parameters["id"]?.toIntOrNull() ?: return@withRole call.respond(HttpStatusCode.BadRequest)
                val (success, error) = userService.deleteUser(id)

                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "User deleted"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to error))
                }
            }
        }

        // Serve user profile image
        get("/{id}/profile-picture") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
            val acceptHeader = call.request.headers[HttpHeaders.Accept]
            val profilePicture = userService.getProfilePicture(id)
            if (profilePicture != null) {
                val contentType = ContentType.parse(profilePicture.contentType)
                if (acceptHeader == null || acceptHeader == "*/*" || acceptHeader.contains(
                        contentType.toString(),
                        ignoreCase = true
                    ) || acceptHeader.startsWith("image/")
                ) {
                    call.respondBytes(
                        profilePicture.data,
                        contentType,
                        HttpStatusCode.OK
                    )
                } else {
                    call.respond(
                        HttpStatusCode.NotAcceptable,
                        mapOf("message" to "Requested content type not acceptable")
                    )
                }
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("message" to "Profile picture not found"))
            }
        }

        // Upload user profile image
        post("/{id}/profile-picture") {
            val id = call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
            val multipart = call.receiveMultipart()
            var filename: String? = null
            var contentType: String? = null
            var imageBytes: ByteArray? = null
            var part = multipart.readPart()
            while (part != null) {
                when (part) {
                    is PartData.FileItem -> {
                        filename = part.originalFileName
                        contentType = part.contentType?.toString() ?: "image/jpeg"
                        imageBytes = part.streamProvider().readBytes()
                    }

                    else -> {}
                }
                part.dispose()
                part = multipart.readPart()
            }
            if (filename == null || contentType == null || imageBytes == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No image file provided"))
                return@post
            }
            val (success, error) = userService.uploadProfilePicture(id, filename, contentType, imageBytes)
            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Profile picture uploaded successfully"))
            } else {
                call.respond(HttpStatusCode.InternalServerError, mapOf("message" to error))
            }
        }
    }
}
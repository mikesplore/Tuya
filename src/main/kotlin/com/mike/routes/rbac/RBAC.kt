//package com.mike.routes.rbac
//
//import com.mike.domain.model.user.Profile
//import io.ktor.http.*
//import io.ktor.server.application.*
//import io.ktor.server.request.*
//import io.ktor.server.response.*
//
//enum class UserRole{
//    ADMIN,
//    USER
//}
//
//suspend fun withRole(
//    call: ApplicationCall,
//    vararg allowedRoles: UserRole,
//    handler: suspend (Profile) -> Unit
//) {
//
//    try {
//        val user = tokenService.extractUserFromToken(call)
//        logMessage("Fetched user role: ${user ?: "Unknown"} for path: ${call.request.path()}")
//
//        if (user == null || user.userRole.uppercase() !in allowedRoles.map { it.name }) {
//            call.respondText("Access denied", status = HttpStatusCode.Forbidden)
//            return
//        }
//        handler(user)
//    } catch (e: Exception) {
//        logMessage("Error in withRole for path ${call.request.path()}: ${e.message}")
//        call.respondText(
//            "Internal server error: ${e.message}",
//            status = HttpStatusCode.InternalServerError
//        )
//    }
//}
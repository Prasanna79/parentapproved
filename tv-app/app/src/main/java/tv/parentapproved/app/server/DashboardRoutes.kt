package tv.parentapproved.app.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private fun ApplicationCall.addSecurityHeaders() {
    response.headers.append("Content-Security-Policy", "default-src 'self'; img-src 'self' https://img.youtube.com; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'")
    response.headers.append("X-Content-Type-Options", "nosniff")
    response.headers.append("X-Frame-Options", "DENY")
    response.headers.append("Referrer-Policy", "no-referrer")
}

fun Route.dashboardRoutes() {
    get("/") {
        val html = javaClass.classLoader?.getResourceAsStream("assets/index.html")
            ?.bufferedReader()?.readText()
        if (html != null) {
            call.addSecurityHeaders()
            call.respondText(html, ContentType.Text.Html)
        } else {
            call.respondText("<html><body><h1>ParentApproved Dashboard</h1><p>Assets not found</p></body></html>", ContentType.Text.Html)
        }
    }

    // Serve assets at both /assets/* (legacy) and root-relative paths.
    // Root-relative paths allow the same HTML to work on both local Ktor and the relay.
    val assetFiles = mapOf(
        "app.js" to ContentType("application", "javascript"),
        "style.css" to ContentType.Text.CSS,
        "favicon.svg" to ContentType("image", "svg+xml"),
    )

    for ((fileName, contentType) in assetFiles) {
        for (path in listOf("/assets/$fileName", "/$fileName")) {
            get(path) {
                val content = javaClass.classLoader?.getResourceAsStream("assets/$fileName")
                    ?.bufferedReader()?.readText()
                if (content != null) {
                    call.addSecurityHeaders()
                    call.respondText(content, contentType)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}

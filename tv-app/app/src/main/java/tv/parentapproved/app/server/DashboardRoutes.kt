package tv.parentapproved.app.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.dashboardRoutes() {
    get("/") {
        val html = javaClass.classLoader?.getResourceAsStream("assets/index.html")
            ?.bufferedReader()?.readText()
        if (html != null) {
            call.respondText(html, ContentType.Text.Html)
        } else {
            call.respondText("<html><body><h1>ParentApproved Dashboard</h1><p>Assets not found</p></body></html>", ContentType.Text.Html)
        }
    }

    get("/assets/{path...}") {
        val path = call.parameters.getAll("path")?.joinToString("/") ?: return@get
        val content = javaClass.classLoader?.getResourceAsStream("assets/$path")
            ?.bufferedReader()?.readText()
        if (content != null) {
            val contentType = when {
                path.endsWith(".css") -> ContentType.Text.CSS
                path.endsWith(".js") -> ContentType("application", "javascript")
                path.endsWith(".svg") -> ContentType("image", "svg+xml")
                else -> ContentType.Text.Plain
            }
            call.respondText(content, contentType)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}

package tv.parentapproved.app.server

import android.content.Context
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tv.parentapproved.app.CrashHandler
import tv.parentapproved.app.auth.SessionManager

fun Route.crashLogRoutes(sessionManager: SessionManager, appContext: Context) {
    get("/crash-log") {
        if (!validateSession(sessionManager)) return@get
        val log = CrashHandler.readCrashLog(appContext)
        if (log == null) {
            call.respond(mapOf("hasCrash" to false, "log" to ""))
        } else {
            call.respond(mapOf("hasCrash" to true, "log" to log))
        }
    }
}

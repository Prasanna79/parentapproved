package com.kidswatch.tv.server

import android.content.Context
import com.kidswatch.tv.ServiceLocator
import com.kidswatch.tv.util.AppLogger
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

class KidsWatchServer(private val context: Context, private val port: Int = 8080) {
    private var server: ApplicationEngine? = null

    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) return
        server = embeddedServer(Netty, port = port) {
            configureServer(this)
        }.start(wait = false)
        isRunning = true
        AppLogger.log("Ktor server started on port $port")
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        isRunning = false
        AppLogger.log("Ktor server stopped")
    }

    companion object {
        fun configureServer(application: Application) {
            application.apply {
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                    })
                }

                install(StatusPages) {
                    exception<Throwable> { call, cause ->
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to (cause.message ?: "Unknown error"))
                        )
                    }
                }

                routing {
                    authRoutes(ServiceLocator.pinManager, ServiceLocator.sessionManager)
                    playlistRoutes(ServiceLocator.sessionManager, ServiceLocator.database)
                    playbackRoutes(ServiceLocator.sessionManager)
                    statsRoutes(ServiceLocator.sessionManager, ServiceLocator.database)
                    statusRoutes()
                    dashboardRoutes()
                }
            }
        }
    }
}

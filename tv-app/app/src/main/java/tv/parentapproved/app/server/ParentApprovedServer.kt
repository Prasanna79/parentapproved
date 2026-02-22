package tv.parentapproved.app.server

import android.content.Context
import tv.parentapproved.app.BuildConfig
import tv.parentapproved.app.ServiceLocator
import tv.parentapproved.app.util.AppLogger
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

class ParentApprovedServer(private val context: Context, private val port: Int = 8080) {
    private var server: ApplicationEngine? = null

    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) return
        server = embeddedServer(Netty, port = port) {
            configureServer(this, context)
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
        fun configureServer(application: Application, appContext: Context? = null) {
            application.apply {
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                    })
                }

                install(CORS) {
                    // Restrict CORS to known origins: local dashboard and official relay.
                    // (anyHost() is too permissive for production)
                    allowHost("localhost:8080")
                    allowHost("relay.parentapproved.tv", schemes = listOf("https"))

                    allowMethod(HttpMethod.Get)
                    allowMethod(HttpMethod.Post)
                    allowMethod(HttpMethod.Put)
                    allowMethod(HttpMethod.Delete)
                    allowHeader(HttpHeaders.Authorization)
                    allowHeader(HttpHeaders.ContentType)
                }

                install(StatusPages) {
                    exception<Throwable> { call, cause ->
                        val errorMessage = if (BuildConfig.IS_DEBUG) {
                            cause.message ?: "Unknown error"
                        } else {
                            "An unexpected error occurred"
                        }
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to errorMessage)
                        )
                    }
                }

                routing {
                    authRoutes(ServiceLocator.pinManager, ServiceLocator.sessionManager)
                    playlistRoutes(ServiceLocator.sessionManager, ServiceLocator.database)
                    playbackRoutes(ServiceLocator.sessionManager)
                    statsRoutes(ServiceLocator.sessionManager, ServiceLocator.database)
                    timeLimitRoutes(ServiceLocator.sessionManager, ServiceLocator.timeLimitManager)
                    statusRoutes(ServiceLocator.sessionManager)
                    if (appContext != null) {
                        crashLogRoutes(ServiceLocator.sessionManager, appContext)
                    }
                    dashboardRoutes()
                }
            }
        }
    }
}

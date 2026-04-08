package tv.parentapproved.app.server

import tv.parentapproved.app.auth.SessionManager
import tv.parentapproved.app.data.cache.CacheDatabase
import tv.parentapproved.app.data.cache.KioskConfigEntity
import tv.parentapproved.app.data.cache.WhitelistEntity
import tv.parentapproved.app.kiosk.KioskManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class AppListItem(
    val packageName: String,
    val displayName: String,
    val isSystemApp: Boolean,
    val whitelisted: Boolean,
)

@Serializable
data class WhitelistUpdateRequest(
    val packageName: String,
    val whitelisted: Boolean,
)

@Serializable
data class KioskConfigResponse(
    val kioskEnabled: Boolean,
    val isDeviceOwner: Boolean,
    val enforceTimeLimitsOnAllApps: Boolean,
)

@Serializable
data class KioskToggleRequest(
    val enabled: Boolean,
    val enforceTimeLimitsOnAllApps: Boolean? = null,
)

fun Route.appsRoutes(
    sessionManager: SessionManager,
    kioskManager: KioskManager,
    db: CacheDatabase,
) {
    get("/apps") {
        if (!validateSession(sessionManager)) return@get

        val installedApps = kioskManager.getInstalledApps()
        val whitelistEntities = db.whitelistDao().getAll()
        val whitelistMap = whitelistEntities.associateBy { it.packageName }

        // Sync installed apps into DB (insert new ones, preserve existing whitelist state)
        val newApps = installedApps.filter { app -> whitelistMap[app.packageName] == null }
        if (newApps.isNotEmpty()) {
            db.whitelistDao().insertAll(newApps.map { app ->
                WhitelistEntity(
                    packageName = app.packageName,
                    displayName = app.displayName,
                )
            })
        }

        val response = installedApps.map { app ->
            val entity = whitelistMap[app.packageName]
            AppListItem(
                packageName = app.packageName,
                displayName = app.displayName,
                isSystemApp = app.isSystemApp,
                whitelisted = entity?.whitelisted ?: false,
            )
        }
        call.respond(response)
    }

    put("/apps/whitelist") {
        if (!validateSession(sessionManager)) return@put

        val body = try {
            call.receive<WhitelistUpdateRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
            return@put
        }

        // Update in DB
        db.whitelistDao().setWhitelisted(body.packageName, body.whitelisted)

        // Update DPC lock task packages
        if (kioskManager.isDeviceOwner()) {
            val config = db.kioskDao().getConfig()
            if (config?.kioskEnabled == true) {
                val whitelisted = db.whitelistDao().getWhitelisted()
                kioskManager.setLockTaskPackages(whitelisted.map { it.packageName })
            }
        }

        call.respond(HttpStatusCode.OK, mapOf("success" to "true"))
    }

    get("/apps/kiosk") {
        if (!validateSession(sessionManager)) return@get

        val config = db.kioskDao().getConfig()
        call.respond(KioskConfigResponse(
            kioskEnabled = config?.kioskEnabled ?: false,
            isDeviceOwner = kioskManager.isDeviceOwner(),
            enforceTimeLimitsOnAllApps = config?.enforceTimeLimitsOnAllApps ?: false,
        ))
    }

    post("/apps/kiosk") {
        if (!validateSession(sessionManager)) return@post

        val body = try {
            call.receive<KioskToggleRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
            return@post
        }

        if (body.enabled) {
            if (kioskManager.isDeviceOwner()) {
                // Full DPC kiosk: lock task + restrictions
                val whitelisted = db.whitelistDao().getWhitelisted()
                kioskManager.enableKiosk(db, whitelisted.map { it.packageName })
            } else {
                // Launcher-only kiosk: just set DB flag, UI handles the rest
                db.kioskDao().insertOrUpdate(
                    KioskConfigEntity(kioskEnabled = true, enforceTimeLimitsOnAllApps = body.enforceTimeLimitsOnAllApps ?: false)
                )
            }
            if (body.enforceTimeLimitsOnAllApps != null) {
                db.kioskDao().setEnforceTimeLimitsOnAllApps(body.enforceTimeLimitsOnAllApps)
            }
        } else {
            if (kioskManager.isDeviceOwner()) {
                kioskManager.disableKiosk(db)
            } else {
                db.kioskDao().insertOrUpdate(
                    KioskConfigEntity(kioskEnabled = false, enforceTimeLimitsOnAllApps = false)
                )
            }
        }

        val config = db.kioskDao().getConfig()
        call.respond(KioskConfigResponse(
            kioskEnabled = config?.kioskEnabled ?: false,
            isDeviceOwner = kioskManager.isDeviceOwner(),
            enforceTimeLimitsOnAllApps = config?.enforceTimeLimitsOnAllApps ?: false,
        ))
    }
}

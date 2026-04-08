package tv.parentapproved.app.kiosk

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.util.Log
import tv.parentapproved.app.MainActivity
import tv.parentapproved.app.data.cache.CacheDatabase
import tv.parentapproved.app.data.cache.KioskConfigEntity

class KioskManager(private val context: Context) {

    companion object {
        private const val TAG = "KioskManager"
        private val OWN_PACKAGE = "tv.parentapproved.app"
    }

    private val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent = ComponentName(context, ParentApprovedAdmin::class.java)

    private val activityManager: ActivityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    // --- Device owner checks ---

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    fun isLockTaskPermitted(): Boolean = dpm.isLockTaskPermitted(context.packageName)

    fun isInLockTaskMode(): Boolean =
        activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    // --- Lock task package management ---

    fun setLockTaskPackages(packages: List<String>) {
        if (!isDeviceOwner()) return
        val allPackages = (packages + OWN_PACKAGE).distinct().toTypedArray()
        dpm.setLockTaskPackages(adminComponent, allPackages)
        Log.i(TAG, "Lock task packages set: ${allPackages.toList()}")
    }

    fun setLockTaskFeatures(features: Int) {
        if (!isDeviceOwner()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dpm.setLockTaskFeatures(adminComponent, features)
            Log.i(TAG, "Lock task features set: $features")
        }
    }

    // --- Persistent HOME activity ---

    fun setPersistentHomeActivity() {
        if (!isDeviceOwner()) return
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val activity = ComponentName(context, MainActivity::class.java)
        dpm.addPersistentPreferredActivity(adminComponent, filter, activity)
        Log.i(TAG, "Persistent HOME activity set to MainActivity")
    }

    fun clearPersistentHomeActivity() {
        if (!isDeviceOwner()) return
        dpm.clearPackagePersistentPreferredActivities(adminComponent, context.packageName)
        Log.i(TAG, "Persistent HOME activity cleared")
    }

    // --- User restrictions ---

    fun applyKioskRestrictions() {
        if (!isDeviceOwner()) return
        val restrictions = listOf(
            UserManager.DISALLOW_INSTALL_APPS,
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_CREATE_WINDOWS,
        )
        for (restriction in restrictions) {
            dpm.addUserRestriction(adminComponent, restriction)
        }
        // DISALLOW_SYSTEM_ERROR_DIALOGS requires API 28
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dpm.addUserRestriction(adminComponent, "no_system_error_dialogs")
        }
        Log.i(TAG, "Kiosk restrictions applied")
    }

    fun clearKioskRestrictions() {
        if (!isDeviceOwner()) return
        val restrictions = listOf(
            UserManager.DISALLOW_INSTALL_APPS,
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_CREATE_WINDOWS,
        )
        for (restriction in restrictions) {
            dpm.clearUserRestriction(adminComponent, restriction)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dpm.clearUserRestriction(adminComponent, "no_system_error_dialogs")
        }
        Log.i(TAG, "Kiosk restrictions cleared")
    }

    // --- App listing ---

    fun getInstalledApps(): List<AppInfo> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)
        return apps.mapNotNull { appInfo ->
            val pkg = appInfo.packageName
            if (pkg == OWN_PACKAGE) return@mapNotNull null
            // Must have a leanback or standard launch intent
            val hasLaunchIntent = pm.getLeanbackLaunchIntentForPackage(pkg) != null
                || pm.getLaunchIntentForPackage(pkg) != null
            if (!hasLaunchIntent) return@mapNotNull null

            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            AppInfo(
                packageName = pkg,
                displayName = pm.getApplicationLabel(appInfo).toString(),
                isSystemApp = isSystem,
            )
        }.sortedBy { it.displayName }
    }

    fun getLeanbackLaunchIntent(packageName: String): Intent? {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
        if (intent != null) return intent

        // Fallback: manually resolve LEANBACK_LAUNCHER activity
        val leanbackIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            setPackage(packageName)
        }
        val resolveInfo = pm.resolveActivity(leanbackIntent, 0)
        if (resolveInfo != null) {
            return leanbackIntent.apply {
                component = android.content.ComponentName(
                    resolveInfo.activityInfo.packageName,
                    resolveInfo.activityInfo.name,
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        // Last fallback: try standard LAUNCHER
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }
        val launcherResolve = pm.resolveActivity(launcherIntent, 0)
        if (launcherResolve != null) {
            return launcherIntent.apply {
                component = android.content.ComponentName(
                    launcherResolve.activityInfo.packageName,
                    launcherResolve.activityInfo.name,
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        Log.w(TAG, "No launch intent found for $packageName")
        return null
    }

    // --- Enable / disable kiosk ---

    suspend fun enableKiosk(db: CacheDatabase, whitelistedPackages: List<String>) {
        if (!isDeviceOwner()) {
            Log.w(TAG, "Cannot enable kiosk: not device owner")
            return
        }

        // 1. Set lock task packages
        setLockTaskPackages(whitelistedPackages)

        // 2. Set lock task features (HOME + power button)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setLockTaskFeatures(
                DevicePolicyManager.LOCK_TASK_FEATURE_HOME
                    or DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
            )
        }

        // 3. Set persistent HOME
        setPersistentHomeActivity()

        // 4. Apply user restrictions
        applyKioskRestrictions()

        // 5. Persist config
        db.kioskDao().insertOrUpdate(
            KioskConfigEntity(kioskEnabled = true, enforceTimeLimitsOnAllApps = true)
        )

        Log.i(TAG, "Kiosk enabled with ${whitelistedPackages.size} whitelisted packages")
    }

    suspend fun disableKiosk(db: CacheDatabase) {
        if (!isDeviceOwner()) return

        // 1. Clear restrictions
        clearKioskRestrictions()

        // 2. Clear persistent HOME
        clearPersistentHomeActivity()

        // 3. Remove ALL packages from lock task allowlist (including ourselves).
        // This forces the system to exit lock task mode for any running activity.
        dpm.setLockTaskPackages(adminComponent, emptyArray())
        Log.i(TAG, "Lock task packages cleared (forces lock task exit)")

        // 4. Reset lock task features
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setLockTaskFeatures(DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
        }

        // 5. Persist config
        db.kioskDao().insertOrUpdate(
            KioskConfigEntity(kioskEnabled = false, enforceTimeLimitsOnAllApps = false)
        )

        Log.i(TAG, "Kiosk disabled")
    }

    // --- Time limit enforcement ---

    fun enforceTimeLimitExpiry() {
        if (!isDeviceOwner()) return
        // Remove all third-party packages from lock task allowlist.
        // This forces any running third-party app's lock task to exit back to PA.
        setLockTaskPackages(emptyList())
        Log.i(TAG, "Time limit expired: removed third-party packages from lock task allowlist")

        // Optionally suspend packages (belt and suspenders)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val whitelisted = getInstalledApps().map { it.packageName }.toTypedArray()
                dpm.setPackagesSuspended(adminComponent, whitelisted, true)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to suspend packages", e)
            }
        }
    }

    fun restoreAfterTimeLimitExpiry(whitelistedPackages: List<String>) {
        if (!isDeviceOwner()) return
        // Re-add packages to lock task allowlist
        setLockTaskPackages(whitelistedPackages)
        Log.i(TAG, "Time limit restored: re-added ${whitelistedPackages.size} packages to allowlist")

        // Unsuspend packages
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val pkgs = whitelistedPackages.toTypedArray()
                dpm.setPackagesSuspended(adminComponent, pkgs, false)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unsuspend packages", e)
            }
        }
    }
}

data class AppInfo(
    val packageName: String,
    val displayName: String,
    val isSystemApp: Boolean,
)

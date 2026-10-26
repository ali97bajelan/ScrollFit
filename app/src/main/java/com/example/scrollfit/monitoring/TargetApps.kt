package com.example.scrollfit.monitoring

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

data class TargetApp(val packageName: String, val label: String)

/** Conservative safety filter at both setup and service validation; no QUERY_ALL_PACKAGES. */
object TargetApps {
    @Suppress("DEPRECATION")
    fun protectedPackages(context: Context): Set<String> {
        val excluded = mutableSetOf(context.packageName, "com.android.systemui", "android",
            "com.android.settings", "com.android.permissioncontroller", "com.google.android.permissioncontroller")
        listOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), Intent(Settings.ACTION_SETTINGS),
            Intent(Intent.ACTION_DIAL)).forEach { intent ->
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .forEach { it.activityInfo?.packageName?.let(excluded::add) }
        }
        // Installed third-party keyboards are critical input surfaces too.
        context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList
            ?.forEach { excluded.add(it.packageName) }
        return excluded
    }

    @Suppress("DEPRECATION")
    fun list(context: Context): List<TargetApp> {
        val manager = context.packageManager
        val excluded = protectedPackages(context)
        return launcherApps(context, excluded)
    }

    /** Collects package and dynamic-role metadata as one revisioned, off-callback snapshot. */
    @Suppress("DEPRECATION")
    internal fun safetySnapshot(context: Context, revision: Long): SafetySnapshot {
        val protected = protectedPackages(context)
        val eligible = launcherApps(context, protected).map { it.packageName }.toSet()
        return SafetySnapshot.ready(revision, eligible, protected)
    }

    @Suppress("DEPRECATION")
    private fun launcherApps(context: Context, excluded: Set<String>): List<TargetApp> {
        val manager = context.packageManager
        return manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .mapNotNull { resolve ->
                val info = resolve.activityInfo ?: return@mapNotNull null
                val app = info.applicationInfo
                val system = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                if (system || !info.enabled || !app.enabled || info.packageName in excluded) null
                else TargetApp(info.packageName, app.loadLabel(manager).toString())
            }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }
}

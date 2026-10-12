package com.example.scrollfit.spike

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import com.example.scrollfit.monitoring.TargetApps

data class SpikeApp(val packageName: String, val label: String)

/** Conservative spike: only launchable, non-system apps; no broad package visibility. */
object SpikeApps {
    @Suppress("DEPRECATION")
    fun list(context: Context): List<SpikeApp> {
        val manager = context.packageManager
        val excluded = TargetApps.protectedPackages(context).toMutableSet()
        listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            Intent(Settings.ACTION_SETTINGS),
            Intent(Intent.ACTION_DIAL),
        ).forEach { intent ->
            manager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .forEach { it.activityInfo?.packageName?.let(excluded::add) }
        }
        return manager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        ).mapNotNull { resolve ->
            val info = resolve.activityInfo ?: return@mapNotNull null
            val app = info.applicationInfo
            val system = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (system || !info.enabled || !app.enabled || info.packageName in excluded) null
            else SpikeApp(info.packageName, app.loadLabel(manager).toString())
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }
}

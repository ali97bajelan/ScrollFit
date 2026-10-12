package com.example.scrollfit.monitoring

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object MonitorAccess {
    @Suppress("DEPRECATION")
    fun usage(context: Context): Boolean = requireNotNull(context.getSystemService(AppOpsManager::class.java))
        .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

    fun accessibility(context: Context): Boolean {
        val expected = ComponentName(context, MonitoringService::class.java)
        return requireNotNull(context.getSystemService(AccessibilityManager::class.java))
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
                val info = it.resolveInfo.serviceInfo
                ComponentName(info.packageName, info.name) == expected
            }
    }

    /** Never substitute a process UUID: cursor identity must survive same-boot process restart. */
    fun bootIdentity(context: Context): String {
        val count = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        check(count >= 0)
        return "android-boot-$count"
    }
}

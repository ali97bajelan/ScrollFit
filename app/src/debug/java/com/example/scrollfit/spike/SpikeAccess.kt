package com.example.scrollfit.spike

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.view.accessibility.AccessibilityManager

object SpikeAccess {
    fun hasUsageAccess(context: Context): Boolean {
        val manager = requireNotNull(context.getSystemService(AppOpsManager::class.java))
        @Suppress("DEPRECATION")
        return manager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    fun accessibilityEnabled(context: Context): Boolean {
        val component = ComponentName(context, SpikeAccessibilityService::class.java)
        return requireNotNull(context.getSystemService(AccessibilityManager::class.java))
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val service = info.resolveInfo.serviceInfo
                ComponentName(service.packageName, service.name) == component
            }
    }
}

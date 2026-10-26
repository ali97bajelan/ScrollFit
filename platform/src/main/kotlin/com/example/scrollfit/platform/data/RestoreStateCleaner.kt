package com.example.scrollfit.platform.data

import android.content.Context
import java.io.File
import java.io.IOException

/** Removes imported app-owned data for a genuinely fresh installation before repositories open. */
object RestoreStateCleaner {
    private val preservedDirectories = setOf("cache", "code_cache", "lib", "no_backup")

    fun clear(context: Context) {
        val application = context.applicationContext
        val storageContexts = listOf(application, application.createDeviceProtectedStorageContext())
            .distinctBy { it.dataDir.absolutePath }
        storageContexts.forEach { storage ->
            clearDirectoryContents(storage.dataDir, preservedDirectories)
            storage.getExternalFilesDirs(null).filterNotNull().forEach { external ->
                clearDirectoryContents(external, emptySet())
            }
        }
    }

    internal fun clearDirectoryContents(directory: File, preserved: Set<String> = preservedDirectories) {
        if (!directory.exists()) return
        val children = directory.listFiles() ?: throw IOException("Unable to inspect restored app data")
        children.filterNot { it.name in preserved }.forEach { child ->
            if (!child.deleteRecursively()) throw IOException("Unable to clear restored app data")
        }
    }
}

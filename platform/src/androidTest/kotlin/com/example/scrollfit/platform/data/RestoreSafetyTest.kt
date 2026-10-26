package com.example.scrollfit.platform.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RestoreSafetyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun restoredInstallGetsFreshDeviceLocalTimelineAndReadiness() {
        val directory = File(context.noBackupFilesDir, "restore-safety-${UUID.randomUUID()}")
        try {
            val restored = LocalInstallIdentity(File(directory, "identity"), existingInstall = false)
            val timeline = restored.bootIdentity(7)

            assertTrue(restored.requiresFreshMonitoringReadiness)
            assertTrue(timeline.endsWith("android-boot-7"))
            assertFalse(timeline.startsWith("android-boot-"))
            assertEquals(timeline, LocalInstallIdentity(File(directory, "identity"), false).bootIdentity(7))
            restored.markMonitoringReadinessPrepared()
            assertFalse(LocalInstallIdentity(File(directory, "identity"), false)
                .requiresFreshMonitoringReadiness)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun firstUpdateOfExistingInstallationPreservesReadinessState() {
        val directory = File(context.noBackupFilesDir, "legacy-identity-${UUID.randomUUID()}")
        try {
            val upgraded = LocalInstallIdentity(File(directory, "identity"), existingInstall = true)
            assertTrue(upgraded.bootIdentity(12).endsWith("android-boot-12"))
            assertFalse(upgraded.requiresFreshMonitoringReadiness)
            assertFalse(upgraded.requiresRestoredStateClear)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun independentRestoreMarkersCannotReuseTheSameBootCursor() {
        val root = File(context.noBackupFilesDir, "isolated-identities-${UUID.randomUUID()}")
        try {
            val firstDevice = LocalInstallIdentity(File(root, "device-a/identity"), existingInstall = false)
            val secondDevice = LocalInstallIdentity(File(root, "device-b/identity"), existingInstall = false)
            assertNotEquals(firstDevice.bootIdentity(4), secondDevice.bootIdentity(4))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun restoreCleanupRemovesImportedStoresAndKeepsTheLocalMarker() {
        val root = File(context.noBackupFilesDir, "restore-cleanup-${UUID.randomUUID()}")
        val database = File(root, "databases/exercise-gate.db")
        val wal = File(root, "databases/exercise-gate.db-wal")
        val preferences = File(root, "files/datastore/exercise-gate-preferences.preferences_pb")
        val marker = File(root, "no_backup/scrollfit-install-identity")
        try {
            listOf(database, wal, preferences, marker).forEach { file ->
                file.parentFile?.mkdirs()
                file.writeText("synthetic restored state")
            }

            RestoreStateCleaner.clearDirectoryContents(root)

            assertFalse(database.exists())
            assertFalse(wal.exists())
            assertFalse(preferences.exists())
            assertTrue(marker.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}

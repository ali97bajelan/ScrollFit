package com.example.scrollfit.platform.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID

/**
 * Device-local timeline identity. The marker lives under noBackupFilesDir, so a restore or device
 * transfer creates a fresh timeline even when an older backup contains a cursor with the same boot
 * count. Existing installs keep their durable balances when the first marker is created on update.
 */
class LocalInstallIdentity internal constructor(
    private val file: File,
    existingInstall: Boolean,
) {
    private val atomicFile = AtomicFile(file)
    private var state: State = synchronized(identityLock) { load() ?: create(existingInstall) }

    val requiresFreshMonitoringReadiness: Boolean
        get() = synchronized(identityLock) { !state.readinessPrepared }

    val requiresRestoredStateClear: Boolean
        get() = synchronized(identityLock) { state.freshInstallation && !state.readinessPrepared }

    fun bootIdentity(bootCount: Int): String = synchronized(identityLock) {
        require(bootCount >= 0)
        "${state.installId}-android-boot-$bootCount"
    }

    fun markMonitoringReadinessPrepared() = synchronized(identityLock) {
        if (!state.readinessPrepared) {
            state = state.copy(readinessPrepared = true)
            persist(state)
        }
    }

    private fun load(): State? {
        val text = try {
            atomicFile.openRead().bufferedReader().use { it.readText() }
        } catch (_: FileNotFoundException) {
            return null
        }
        val fields = text.lineSequence().toList()
        if (fields.size != 4 || fields[0] != FORMAT) return null
        val installId = fields[1].takeIf { it.isNotBlank() } ?: return null
        val fresh = when (fields[2]) {
            "fresh" -> true
            "upgrade" -> false
            else -> return null
        }
        val prepared = when (fields[3]) {
            "prepared" -> true
            "pending" -> false
            else -> return null
        }
        return State(installId, fresh, prepared)
    }

    private fun create(existingInstall: Boolean): State {
        file.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) {
                throw IOException("Unable to create local app state directory")
            }
        }
        val next = State(UUID.randomUUID().toString(), freshInstallation = !existingInstall,
            readinessPrepared = existingInstall)
        persist(next)
        return next
    }

    private fun persist(value: State) {
        val output = atomicFile.startWrite()
        try {
            output.write(buildString {
                append(FORMAT).append('\n')
                append(value.installId).append('\n')
                append(if (value.freshInstallation) "fresh" else "upgrade").append('\n')
                append(if (value.readinessPrepared) "prepared" else "pending").append('\n')
            }.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (failure: Throwable) {
            atomicFile.failWrite(output)
            throw failure
        }
    }

    private data class State(
        val installId: String,
        val freshInstallation: Boolean,
        val readinessPrepared: Boolean,
    )

    companion object {
        private const val FORMAT = "scrollfit-install-v1"
        private val identityLock = Any()
        private const val FILE_NAME = "scrollfit-install-identity"

        fun open(context: Context): LocalInstallIdentity {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            // A normal update retains firstInstallTime and advances lastUpdateTime. A restored
            // installation has no no-backup marker and starts with a fresh install timestamp.
            val existingInstall = info.firstInstallTime > 0 && info.firstInstallTime < info.lastUpdateTime
            return LocalInstallIdentity(File(context.noBackupFilesDir, FILE_NAME), existingInstall)
        }
    }
}

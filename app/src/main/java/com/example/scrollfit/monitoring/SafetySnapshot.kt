package com.example.scrollfit.monitoring

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.LinkedHashSet

/** Immutable foreground eligibility view. An unready view always fails closed. */
internal class SafetySnapshot private constructor(
    eligiblePackages: Set<String>,
    protectedPackages: Set<String>,
    val revision: Long,
    val ready: Boolean,
) {
    val eligiblePackages: Set<String> = immutable(eligiblePackages)
    val protectedPackages: Set<String> = immutable(protectedPackages)

    fun allows(packageName: String?): Boolean = ready && packageName != null &&
        packageName in eligiblePackages && packageName !in protectedPackages

    companion object {
        fun pending(revision: Long) = SafetySnapshot(emptySet(), emptySet(), revision, ready = false)

        fun ready(revision: Long, eligiblePackages: Set<String>, protectedPackages: Set<String>) =
            SafetySnapshot(eligiblePackages, protectedPackages, revision, ready = true)

        private fun immutable(values: Set<String>): Set<String> =
            Collections.unmodifiableSet(LinkedHashSet(values))
    }
}

/** Publishes only a result for the newest requested generation. */
internal class SafetySnapshotState {
    @Volatile private var current = SafetySnapshot.pending(0)

    fun current(): SafetySnapshot = current

    @Synchronized
    fun beginRefresh(): Long {
        val revision = Math.addExact(current.revision, 1L)
        current = SafetySnapshot.pending(revision)
        return revision
    }

    @Synchronized
    fun publish(snapshot: SafetySnapshot): Boolean {
        if (!snapshot.ready || current.ready || current.revision != snapshot.revision) return false
        current = SafetySnapshot.ready(snapshot.revision, snapshot.eligiblePackages, snapshot.protectedPackages)
        return true
    }
}

/** Runs metadata lookup after synchronous invalidation and rejects superseded results. */
internal class SafetySnapshotRefresher(
    private val scope: CoroutineScope,
    private val state: SafetySnapshotState,
    private val load: suspend (revision: Long) -> SafetySnapshot,
    private val onInvalidated: (revision: Long) -> Unit,
    private val onPublished: (SafetySnapshot) -> Unit,
    private val onFailure: (Exception) -> Unit,
) {
    private var job: Job? = null

    fun refresh(): Long {
        val revision = state.beginRefresh()
        onInvalidated(revision)
        job?.cancel()
        job = scope.launch {
            try {
                val refreshed = load(revision)
                if (state.publish(refreshed)) onPublished(state.current())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val current = state.current()
                if (current.revision == revision && !current.ready) onFailure(failure)
            }
        }
        return revision
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}

internal fun hasTrustedObservationAge(nowElapsedMs: Long, observedElapsedMs: Long): Boolean =
    nowElapsedMs >= observedElapsedMs &&
        nowElapsedMs - observedElapsedMs <= com.example.scrollfit.core.usage.MonitoringController.MAX_OBSERVED_GAP_MS

package com.example.scrollfit.monitoring

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetySnapshotTest {
    @Test fun slowMetadataDoesNotDelayDismissal() = runBlocking {
        val state = SafetySnapshotState()
        val lookupEntered = CompletableDeferred<Unit>()
        val releaseLookup = CompletableDeferred<Unit>()
        var dismissed = false
        val refresher = SafetySnapshotRefresher(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            state = state,
            load = { revision ->
                lookupEntered.complete(Unit)
                releaseLookup.await()
                SafetySnapshot.ready(revision, setOf("eligible"), setOf("dialer"))
            },
            onInvalidated = { dismissed = true },
            onPublished = {},
            onFailure = { throw it },
        )

        refresher.refresh()
        lookupEntered.await()
        assertTrue(dismissed)
        assertFalse(state.current().allows("eligible"))

        releaseLookup.complete(Unit)
        assertTrue(state.current().allows("eligible"))
        refresher.cancel()
    }

    @Test fun roleChangeWithholdsEligibilityUntilFreshSnapshotPublishes() {
        val state = SafetySnapshotState()
        val firstRevision = state.beginRefresh()
        assertTrue(state.publish(SafetySnapshot.ready(firstRevision, setOf("dialer"), emptySet())))
        assertTrue(state.current().allows("dialer"))

        val changedRoleRevision = state.beginRefresh()
        assertFalse(state.current().allows("dialer"))
        assertTrue(state.publish(SafetySnapshot.ready(changedRoleRevision, emptySet(), setOf("dialer"))))
        assertFalse(state.current().allows("dialer"))
    }

    @Test fun staleSafetyRefreshIgnored() {
        val state = SafetySnapshotState()
        val older = state.beginRefresh()
        val newer = state.beginRefresh()

        assertFalse(state.publish(SafetySnapshot.ready(older, setOf("stale"), emptySet())))
        assertTrue(state.publish(SafetySnapshot.ready(newer, setOf("current"), emptySet())))
        assertEquals(newer, state.current().revision)
        assertFalse(state.current().allows("stale"))
        assertTrue(state.current().allows("current"))
    }
}

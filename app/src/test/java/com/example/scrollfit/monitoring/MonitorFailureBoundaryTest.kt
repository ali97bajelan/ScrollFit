package com.example.scrollfit.monitoring

import com.example.scrollfit.core.usage.MonitorOwnership
import com.example.scrollfit.core.usage.MonitorPresentation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorFailureBoundaryTest {
    @Test fun nonSecurityReconcileFailureIsTypedAndSanitized() = runBlocking {
        val cause = IllegalStateException("private package payload")
        val failure = try {
            monitorBoundary(MonitorFailure.METADATA, MonitorFailurePhase.RECONCILIATION,
                MonitorFailureCode.FOREGROUND_RECONCILIATION) { throw cause }
            null
        } catch (caught: MonitorBoundaryException) { caught }

        assertEquals(MonitorFailurePhase.RECONCILIATION, failure?.detail?.phase)
        assertEquals(MonitorFailureCode.FOREGROUND_RECONCILIATION, failure?.detail?.code)
        assertEquals(MonitorFailure.METADATA, failure?.category)
        assertSame(cause, failure?.cause)
    }

    @Test fun secondRegistrationFailureCleansFirstSuccessfulRegistration() {
        var watcherStarts = 0
        var watcherStops = 0
        var receiverStarts = 0
        var receiverStops = 0
        val reported = mutableListOf<MonitorFailureCode>()
        val registrations = MonitorRegistrations(
            registerUsageWatcher = { watcherStarts++ },
            unregisterUsageWatcher = { watcherStops++ },
            registerScreenReceiver = { receiverStarts++; error("injected registration failure") },
            unregisterScreenReceiver = { receiverStops++ },
            registerPackageReceiver = {}, unregisterPackageReceiver = {},
            registerSystemReceiver = {}, unregisterSystemReceiver = {},
            onFailure = { code, _ -> reported += code },
        )

        assertFalse(registrations.register())
        assertEquals(1, watcherStarts)
        assertEquals(1, watcherStops)
        assertEquals(1, receiverStarts)
        assertEquals(0, receiverStops)
        assertEquals(listOf(MonitorFailureCode.SCREEN_RECEIVER_REGISTER), reported)
    }

    @Test fun cleanupContinuesAfterUnregisterThrowsAndDoesNotRepeat() {
        var watcherStops = 0
        var receiverStops = 0
        val reported = mutableListOf<MonitorFailureCode>()
        val registrations = MonitorRegistrations(
            registerUsageWatcher = {},
            unregisterUsageWatcher = { watcherStops++ },
            registerScreenReceiver = {},
            unregisterScreenReceiver = { receiverStops++; error("injected unregister failure") },
            registerPackageReceiver = {}, unregisterPackageReceiver = {},
            registerSystemReceiver = {}, unregisterSystemReceiver = {},
            onFailure = { code, _ -> reported += code },
        )
        assertTrue(registrations.register())

        registrations.unregisterAll()
        registrations.unregisterAll()

        assertEquals(1, receiverStops)
        assertEquals(1, watcherStops)
        assertEquals(listOf(MonitorFailureCode.SCREEN_RECEIVER_UNREGISTER), reported)
    }

    @Test fun cancellationPropagatesFromAsynchronousBoundary() = runBlocking {
        val cancellation = CancellationException("cancel retry")
        val caught = try {
            monitorBoundary(MonitorFailure.STORAGE, MonitorFailurePhase.PREFERENCES,
                MonitorFailureCode.PREFERENCE_STREAM) { throw cancellation }
            null
        } catch (failure: CancellationException) { failure }
        assertSame(cancellation, caught)
    }

    @Test fun removalFailureRetainsOwnershipLease() {
        val ownership = MonitorOwnership()
        val firstOwner = Any()
        var visible = true
        val presentation = MonitorPresentation({ _, _, _ -> visible = true; true }, { false })
        assertTrue(ownership.acquire(firstOwner))

        assertFalse(presentation.pause())
        assertTrue(visible)
        assertFalse(ownership.acquire(Any()))
    }
}

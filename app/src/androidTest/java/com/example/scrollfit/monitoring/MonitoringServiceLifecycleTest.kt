package com.example.scrollfit.monitoring

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Android-run startup fixture. Real AccessibilityService binding and owner permissions remain device QA. */
@RunWith(AndroidJUnit4::class)
class MonitoringServiceLifecycleTest {
    @Test fun typedStartupFailureCrossesTheServiceLifecycleWithoutItsExceptionText() = runBlocking {
        val detail = MonitorFailureDetail(MonitorFailurePhase.RECONCILIATION,
            MonitorFailureCode.FOREGROUND_RECONCILIATION)
        var published: MonitorFailureDetail? = null
        val lifecycle = MonitorLifecycle(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            {}, { throw MonitorBoundaryException(MonitorFailure.METADATA, detail,
                IllegalStateException("private foreground payload")) },
            { _, failure -> published = (failure as MonitorBoundaryException).detail })

        lifecycle.startOrRetry()!!.join()

        assertEquals(MonitorInitState.FAILED, lifecycle.state)
        assertEquals(detail, published)
        assertFalse(published.toString().contains("private foreground payload"))
    }

    @Test fun retriesBootFailureWithoutCreatingASecondConsumerOrLease() = runBlocking {
        var bootAttempts = 0
        var metadataAttempts = 0
        var consumerCount = 0
        var leaseAcquisitions = 0
        val states = mutableListOf<MonitorInitState>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val lifecycle = MonitorLifecycle(scope, { states += it }, { _ ->
            bootAttempts++
            if (bootAttempts == 1) error("injected boot identity failure")
            metadataAttempts++
        }, { _, _ -> })

        // Mirrors the service's connection-scoped collector: it is started outside retry.
        consumerCount++
        lifecycle.startOrRetry()!!.join()
        assertEquals(MonitorInitState.FAILED, lifecycle.state)
        assertEquals(0, leaseAcquisitions)

        lifecycle.startOrRetry()!!.join()

        assertEquals(MonitorInitState.READY, lifecycle.state)
        assertEquals(2, bootAttempts)
        assertEquals(1, metadataAttempts)
        assertEquals(1, consumerCount)
        assertEquals(0, leaseAcquisitions)
        assertTrue(states.contains(MonitorInitState.FAILED))
    }
}

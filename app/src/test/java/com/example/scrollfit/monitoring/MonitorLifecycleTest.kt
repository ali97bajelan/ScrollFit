package com.example.scrollfit.monitoring

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorLifecycleTest {
    @Test fun failOnceThenRetryReachesReady() = runBlocking {
        var attempts = 0
        val states = mutableListOf<MonitorInitState>()
        val lifecycle = MonitorLifecycle(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            { states += it }, { attempts++; if (attempts == 1) error("first attempt") }, { _, _ -> })

        lifecycle.startOrRetry()!!.join()
        assertEquals(MonitorInitState.FAILED, lifecycle.state)
        lifecycle.startOrRetry()!!.join()

        assertEquals(2, attempts)
        assertEquals(MonitorInitState.READY, lifecycle.state)
        assertEquals(listOf(MonitorInitState.INITIALIZING, MonitorInitState.FAILED,
            MonitorInitState.INITIALIZING, MonitorInitState.READY), states)
    }

    @Test fun retryBurstCreatesOneWorker() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var attempts = 0
        val lifecycle = MonitorLifecycle(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            {}, { attempts++; entered.complete(Unit); release.await() }, { _, _ -> })

        val first = lifecycle.startOrRetry()!!
        entered.await()
        repeat(20) { assertSame(first, lifecycle.startOrRetry()) }
        assertEquals(1, attempts)
        release.complete(Unit)
        first.join()
        assertEquals(MonitorInitState.READY, lifecycle.state)
    }

    @Test fun disableDuringInitRejectsLateResult() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var applied = false
        lateinit var lifecycle: MonitorLifecycle
        lifecycle = MonitorLifecycle(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            {}, { token ->
                entered.complete(Unit)
                withContext(NonCancellable) { release.await() }
                lifecycle.commitIfCurrent(token) { applied = true }
            }, { _, _ -> })

        val attempt = lifecycle.startOrRetry()!!
        entered.await()
        lifecycle.stop()
        release.complete(Unit)
        attempt.join()

        assertFalse(applied)
        assertEquals(MonitorInitState.STOPPED, lifecycle.state)
    }

    @Test fun retryPreservesBudgetAndGap() = runBlocking {
        val durableWalletMs = 4_000L
        MonitorRuntime.publish(MonitorStatus(failure = MonitorFailure.STORAGE, hasUnmonitoredGap = true))
        var attempts = 0
        val lifecycle = MonitorLifecycle(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            {}, {
                attempts++
                if (attempts == 1) error("temporary persistence failure")
                // Initialization receives existing durable state without running an accounting operation.
                assertEquals(4_000L, durableWalletMs)
            }, { _, _ -> })

        lifecycle.startOrRetry()!!.join()
        lifecycle.startOrRetry()!!.join()

        assertEquals(4_000L, durableWalletMs)
        assertTrue(MonitorRuntime.status.value.hasUnmonitoredGap)
        assertEquals(MonitorInitState.READY, lifecycle.state)
    }
}

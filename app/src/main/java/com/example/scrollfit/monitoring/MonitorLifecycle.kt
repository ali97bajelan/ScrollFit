package com.example.scrollfit.monitoring

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class MonitorInitState { STOPPED, INITIALIZING, FAILED, READY }

/** Serializes initialization attempts and rejects results from a stopped or superseded attempt. */
internal class MonitorLifecycle(
    private val scope: CoroutineScope,
    private val publish: (MonitorInitState) -> Unit,
    private val initialize: suspend (generation: Long) -> Unit,
    private val onFailure: (generation: Long, Exception) -> Unit,
) {
    private val lock = Any()
    private var generation = 0L
    private var currentState = MonitorInitState.STOPPED
    private var initialization: Job? = null

    val state: MonitorInitState get() = synchronized(lock) { currentState }

    fun isCurrent(attemptGeneration: Long): Boolean = synchronized(lock) {
        generation == attemptGeneration && currentState == MonitorInitState.INITIALIZING
    }

    fun isLatest(attemptGeneration: Long): Boolean = synchronized(lock) {
        generation == attemptGeneration
    }

    /** Applies the complete initialization result only while this generation is still active. */
    fun commitIfCurrent(attemptGeneration: Long, commit: () -> Unit): Boolean = synchronized(lock) {
        if (generation != attemptGeneration || currentState != MonitorInitState.INITIALIZING) return false
        commit()
        true
    }

    /** Returns the shared in-flight attempt, or starts one when stopped/failed. */
    fun startOrRetry(): Job? = synchronized(lock) {
        if (currentState == MonitorInitState.READY) return null
        if (currentState == MonitorInitState.INITIALIZING) {
            initialization?.takeIf { it.isActive }?.let { return it }
        }

        val attemptGeneration = ++generation
        currentState = MonitorInitState.INITIALIZING
        publish(currentState)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                initialize(attemptGeneration)
                complete(attemptGeneration, MonitorInitState.READY)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (complete(attemptGeneration, MonitorInitState.FAILED)) onFailure(attemptGeneration, failure)
            }
        }
        initialization = job
        job.invokeOnCompletion {
            synchronized(lock) {
                if (initialization === job) initialization = null
            }
        }
        job.start()
        job
    }

    /** Invalidates partial initialization immediately; a late dependency result cannot publish READY. */
    fun stop() {
        val job = synchronized(lock) {
            generation++
            currentState = MonitorInitState.STOPPED
            publish(currentState)
            initialization.also { initialization = null }
        }
        job?.cancel()
    }

    private fun complete(attemptGeneration: Long, state: MonitorInitState): Boolean = synchronized(lock) {
        if (generation != attemptGeneration || currentState != MonitorInitState.INITIALIZING) return false
        currentState = state
        publish(state)
        true
    }
}

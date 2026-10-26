package com.example.scrollfit.monitoring

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult

/** Fixed-capacity service queue with an accurate pending count for bounded diagnostics. */
internal class MonitorCommandQueue<T>(val capacity: Int = CAPACITY) {
    private val channel = Channel<T>(capacity)
    private val lock = Any()
    private var pending = 0

    val receiveChannel: Channel<T> get() = channel
    val pendingCount: Int get() = synchronized(lock) { pending }

    /** Captures accepted depth before a fast receiver can consume the channel element. */
    fun trySend(value: T, onAcceptedDepth: (Int) -> Unit = {}): Boolean {
        var acceptedDepth = 0
        val accepted = synchronized(lock) {
            pending++
            acceptedDepth = pending
            if (channel.trySend(value).isFailure) {
                pending--
                false
            } else {
                true
            }
        }
        if (accepted) onAcceptedDepth(acceptedDepth)
        return accepted
    }

    fun received() = synchronized(lock) { pending = (pending - 1).coerceAtLeast(0) }

    fun tryReceive(): ChannelResult<T> {
        val result = channel.tryReceive()
        if (result.isSuccess) received()
        return result
    }

    fun close() = channel.close()
    fun cancel() = channel.cancel()

    companion object { const val CAPACITY = 64 }
}

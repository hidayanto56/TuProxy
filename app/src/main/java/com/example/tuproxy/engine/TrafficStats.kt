package com.example.tuproxy.engine

import java.util.concurrent.atomic.AtomicLong

/**
 * Global byte counters. All proxy listeners report here.
 * rx = bytes received FROM local clients (traffic in).
 * tx = bytes sent back TO local clients (traffic out).
 */
object TrafficStats {
    private val rxBytes = AtomicLong(0L)
    private val txBytes = AtomicLong(0L)

    fun addRx(n: Long) {
        if (n > 0) rxBytes.addAndGet(n)
    }

    fun addTx(n: Long) {
        if (n > 0) txBytes.addAndGet(n)
    }

    fun snapshot(): Pair<Long, Long> = rxBytes.get() to txBytes.get()

    fun reset() {
        rxBytes.set(0L)
        txBytes.set(0L)
    }
}

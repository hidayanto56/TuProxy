package com.tustudio.tuproxy.engine

import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

data class ConnEntry(
    val id: Long,
    val type: String,
    val target: String,
    val startMs: Long,
    var rx: Long = 0L,
    var tx: Long = 0L,
    var done: Boolean = false,
    var failed: Boolean = false,
)

/**
 * Ring buffer (max 50) of recent proxied connections, exposed as a
 * StateFlow for the dashboard. All methods are thread-safe.
 */
object ConnectionLog {
    private const val MAX = 50
    private val idGen = AtomicLong(0L)
    private val lock = Any()
    private val entries = ArrayDeque<ConnEntry>()

    private val _flow = MutableStateFlow<List<ConnEntry>>(emptyList())
    val flow: StateFlow<List<ConnEntry>> = _flow.asStateFlow()

    fun start(type: String, target: String): Long {
        val e = ConnEntry(
            id = idGen.incrementAndGet(),
            type = type,
            target = target,
            startMs = System.currentTimeMillis(),
        )
        synchronized(lock) {
            entries.addLast(e)
            while (entries.size > MAX) entries.removeFirst()
            publishLocked()
        }
        return e.id
    }

    fun failed(type: String, target: String) {
        val e = ConnEntry(
            id = idGen.incrementAndGet(),
            type = type,
            target = target,
            startMs = System.currentTimeMillis(),
            done = true,
            failed = true,
        )
        synchronized(lock) {
            entries.addLast(e)
            while (entries.size > MAX) entries.removeFirst()
            publishLocked()
        }
    }

    fun addBytes(id: Long, rx: Long, tx: Long) {
        if (id < 0) return
        synchronized(lock) {
            val e = entries.find { it.id == id } ?: return
            e.rx += rx
            e.tx += tx
        }
    }

    fun finish(id: Long) {
        if (id < 0) return
        synchronized(lock) {
            val e = entries.find { it.id == id } ?: return
            e.done = true
            publishLocked()
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            publishLocked()
        }
    }

    private fun publishLocked() {
        _flow.value = entries.toList().asReversed()
    }
}

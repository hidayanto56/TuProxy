package com.example.tuproxy.engine

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

private const val CONNECT_TIMEOUT_MS = 15_000
private const val IDLE_TIMEOUT_MS = 60_000
private const val MAX_HEAD_BYTES = 65_536
private const val BUF_SIZE = 8 * 1024

/** Pumps bytes from [input] to [output]. Counts towards rx (client->net) or tx (net->client). */
private fun pump(input: InputStream, output: OutputStream, toRx: Boolean) {
    val buf = ByteArray(BUF_SIZE)
    try {
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            output.flush()
            if (toRx) TrafficStats.addRx(n.toLong()) else TrafficStats.addTx(n.toLong())
        }
    } catch (_: Exception) {
        // peer closed / timeout / listener stopped — normal for a proxy
    }
}

/** Reads bytes until the end of the HTTP head (CRLFCRLF). Returns head + any over-read bytes. */
private fun readHead(input: InputStream): Pair<ByteArray, ByteArray>? {
    val head = ByteArrayOutputStream()
    val window = ByteArrayDeque()
    val one = ByteArray(1)
    var total = 0
    while (true) {
        val n = input.read(one)
        if (n < 0) return null
        head.write(one[0].toInt())
        window.addLast(one[0])
        if (window.size > 4) window.removeFirst()
        total++
        if (total > MAX_HEAD_BYTES) return null
        if (window.size == 4 &&
            window[0] == '\r'.code.toByte() && window[1] == '\n'.code.toByte() &&
            window[2] == '\r'.code.toByte() && window[3] == '\n'.code.toByte()
        ) break
    }
    return head.toByteArray() to ByteArray(0)
}

private class ByteArrayDeque {
    private val d = ArrayDeque<Byte>()
    val size get() = d.size
    operator fun get(i: Int) = d[i]
    fun addLast(b: Byte) = d.addLast(b)
    fun removeFirst() = d.removeFirst()
}

/** Base class: ServerSocket on 0.0.0.0 + accept loop + pooled handlers. */
private abstract class TcpListener(val port: Int) {
    private var server: ServerSocket? = null
    private var pool: ExecutorService? = null
    private var acceptThread: Thread? = null
    @Volatile var running = false
        private set

    @Synchronized
    fun start(): Boolean {
        if (running) return true
        return try {
            val ss = ServerSocket(port, 128, InetAddress.getByName("0.0.0.0"))
            val counter = AtomicInteger(0)
            val factory = ThreadFactory { r ->
                Thread(r, "tuproxy-${port}-${counter.incrementAndGet()}").apply { isDaemon = true }
            }
            pool = Executors.newCachedThreadPool(factory)
            server = ss
            running = true
            acceptThread = Thread({
                while (running) {
                    try {
                        val client = ss.accept()
                        pool?.execute { handleSafely(client) }
                    } catch (_: Exception) {
                        break // socket closed on stop()
                    }
                }
            }, "tuproxy-accept-$port").apply { isDaemon = true; start() }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun handleSafely(client: Socket) {
        try {
            handle(client)
        } catch (_: Exception) {
        } finally {
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    protected abstract fun handle(client: Socket)

    @Synchronized
    fun stop() {
        running = false
        try {
            server?.close()
        } catch (_: Exception) {
        }
        pool?.shutdownNow()
        server = null
        pool = null
    }
}

private fun dial(host: String, port: Int): Socket {
    val s = Socket()
    s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
    s.soTimeout = IDLE_TIMEOUT_MS
    return s
}

/** HTTP proxy. Also answers CONNECT (https tunneling). connectOnly=true -> CONNECT saja. */
private class HttpProxyListener(port: Int, private val connectOnly: Boolean) : TcpListener(port) {
    override fun handle(client: Socket) {
        client.soTimeout = 20_000
        val cin = client.getInputStream()
        val cout = client.getOutputStream()
        val (headBytes, _) = readHead(cin) ?: return
        val head = String(headBytes, Charsets.ISO_8859_1)
        val requestLine = head.lineSequence().firstOrNull() ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0].uppercase()
        val target = parts[1]

        if (method == "CONNECT") {
            val hp = target.split(":")
            if (hp.size != 2) return
            val remote = try {
                dial(hp[0], hp[1].toInt())
            } catch (_: Exception) {
                return
            }
            remote.use {
                val ok = "HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray()
                cout.write(ok); cout.flush()
                TrafficStats.addTx(ok.size.toLong())
                client.soTimeout = IDLE_TIMEOUT_MS
                val t1 = Thread { pump(cin, it.getOutputStream(), toRx = true) }.apply { isDaemon = true }
                val t2 = Thread { pump(it.getInputStream(), cout, toRx = false) }.apply { isDaemon = true }
                t1.start(); t2.start(); t1.join(); t2.join()
            }
            return
        }

        if (connectOnly) {
            val msg = "HTTP/1.1 405 Method Not Allowed\r\nContent-Length: 0\r\n\r\n".toByteArray()
            cout.write(msg); cout.flush()
            TrafficStats.addTx(msg.size.toLong())
            return
        }

        // Origin-form biasa: target harus URL absolut http://host[:port]/path
        val url = try {
            java.net.URI(target)
        } catch (_: Exception) {
            return
        }
        val host = url.host ?: return
        val remotePort = if (url.port > 0) url.port else 80
        var path = url.rawPath ?: "/"
        if (path.isEmpty()) path = "/"
        if (url.rawQuery != null) path += "?" + url.rawQuery
        val firstLine = "$method $path HTTP/1.1\r\n"
        val rest = head.substringAfter("\r\n")
        val fwdHead = (firstLine + rest).toByteArray(Charsets.ISO_8859_1)

        val remote = try {
            dial(host, remotePort)
        } catch (_: Exception) {
            return
        }
        remote.use {
            val rout = it.getOutputStream()
            rout.write(fwdHead); rout.flush()
            TrafficStats.addRx(fwdHead.size.toLong())
            // Teruskan body request bila ada (POST/PUT). Sisa buffer over-read = 0 di implementasi ini.
            val tUp = Thread { pump(cin, rout, toRx = true) }.apply { isDaemon = true }
            tUp.start()
            try {
                pump(it.getInputStream(), cout, toRx = false)
            } finally {
                try {
                    remote.shutdownOutput()
                } catch (_: Exception) {
                }
                tUp.join(2_000)
            }
        }
    }
}

/** SOCKS5 (RFC 1928) tanpa auth, perintah CONNECT saja. */
private class Socks5Listener(port: Int) : TcpListener(port) {
    override fun handle(client: Socket) {
        client.soTimeout = 20_000
        val cin = client.getInputStream()
        val cout = client.getOutputStream()

        val greet = ByteArray(2)
        if (readFully(cin, greet) != 2 || greet[0] != 0x05.toByte()) return
        val nMethods = greet[1].toInt() and 0xFF
        val methods = ByteArray(nMethods)
        if (nMethods > 0 && readFully(cin, methods) != nMethods) return
        cout.write(byteArrayOf(0x05, 0x00)); cout.flush() // NO AUTHENTICATION REQUIRED

        val req = ByteArray(4)
        if (readFully(cin, req) != 4 || req[0] != 0x05.toByte()) return
        if (req[1] != 0x01.toByte()) { // hanya CONNECT
            cout.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0)); cout.flush()
            return
        }
        val host: String = when (req[3]) {
            0x01.toByte() -> {
                val b = ByteArray(4)
                if (readFully(cin, b) != 4) return
                b.joinToString(".") { (it.toInt() and 0xFF).toString() }
            }
            0x03.toByte() -> {
                val len = cin.read()
                if (len < 0) return
                val b = ByteArray(len)
                if (readFully(cin, b) != len) return
                String(b, Charsets.ISO_8859_1)
            }
            0x04.toByte() -> {
                val b = ByteArray(16)
                if (readFully(cin, b) != 16) return
                InetAddress.getByAddress(b).hostAddress ?: return
            }
            else -> {
                cout.write(byteArrayOf(0x05, 0x08, 0x00, 0x01, 0, 0, 0, 0, 0, 0)); cout.flush()
                return
            }
        }
        val pb = ByteArray(2)
        if (readFully(cin, pb) != 2) return
        val remotePort = ((pb[0].toInt() and 0xFF) shl 8) or (pb[1].toInt() and 0xFF)

        val remote = try {
            dial(host, remotePort)
        } catch (_: Exception) {
            cout.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0)); cout.flush()
            return
        }
        remote.use {
            cout.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0)); cout.flush()
            client.soTimeout = IDLE_TIMEOUT_MS
            val t1 = Thread { pump(cin, it.getOutputStream(), toRx = true) }.apply { isDaemon = true }
            val t2 = Thread { pump(it.getInputStream(), cout, toRx = false) }.apply { isDaemon = true }
            t1.start(); t2.start(); t1.join(); t2.join()
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) break
            off += n
        }
        return off
    }
}

/** Manajer ketiga listener. Dipakai dari ProxyService. */
object ProxyEngine {
    const val TYPE_HTTP = "http"
    const val TYPE_HTTPS = "https"
    const val TYPE_SOCKS = "socks"

    const val HTTP_PORT = 8080
    const val HTTPS_PORT = 8443
    const val SOCKS_PORT = 1080

    private val lock = Any()
    private val listeners = mutableMapOf<String, TcpListener>()

    fun portOf(type: String): Int = when (type) {
        TYPE_HTTP -> HTTP_PORT
        TYPE_HTTPS -> HTTPS_PORT
        TYPE_SOCKS -> SOCKS_PORT
        else -> -1
    }

    /** @return true bila listener jalan (atau sudah jalan). */
    fun start(type: String): Boolean {
        synchronized(lock) {
            listeners[type]?.let { return it.running }
            val port = portOf(type)
            if (port <= 0) return false
            val listener: TcpListener = when (type) {
                TYPE_HTTP -> HttpProxyListener(port, connectOnly = false)
                TYPE_HTTPS -> HttpProxyListener(port, connectOnly = true)
                TYPE_SOCKS -> Socks5Listener(port)
                else -> return false
            }
            return if (listener.start()) {
                listeners[type] = listener
                true
            } else {
                false
            }
        }
    }

    fun stop(type: String) {
        synchronized(lock) {
            listeners.remove(type)?.stop()
        }
    }

    fun stopAll() {
        synchronized(lock) {
            listeners.values.forEach { it.stop() }
            listeners.clear()
        }
    }

    fun isRunning(type: String): Boolean = synchronized(lock) {
        listeners[type]?.running == true
    }

    fun runningTypes(): Set<String> = synchronized(lock) {
        listeners.filterValues { it.running }.keys.toSet()
    }
}

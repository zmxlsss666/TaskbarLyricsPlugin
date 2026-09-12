package com.zmxl.taskbarlyrics.server
import com.zmxl.taskbarlyrics.Log
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 极简 HTTP/1.1 服务器（自写，零第三方依赖），仅支持插件所需的 GET 端点与 SSE 长连接。
 *
 * SPW 的运行时是 Compose 打包的精简 JRE，不含 JDK 的 `jdk.httpserver` 模块，因此
 * `com.sun.net.httpserver.HttpServer` 不可用（NoClassDefFoundError）。这里用原生
 * [ServerSocket] 实现同等能力，彻底避免依赖缺失问题。
 */
class MiniHttpServer(private val port: Int) {
    private val routes = HashMap<String, (HttpRequest, HttpConnection) -> Unit>()
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private var ioThread: Thread? = null
    @Volatile private var executor: ExecutorService? = null

    fun addRoute(path: String, handler: (HttpRequest, HttpConnection) -> Unit) {
        routes[path] = handler
    }

    fun start() {
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
        serverSocket = ss
        running.set(true)
        executor = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }
        ioThread = Thread { acceptLoop(ss) }.apply {
            isDaemon = true
            name = "taskbar-lyrics-http"
            start()
        }
        Log.i("HTTP服务器已启动，端口: $port")
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try { serverSocket?.close() } catch (_: Exception) {}
        executor?.shutdownNow()
        ioThread?.join(1000)
        Log.i("HTTP服务器已停止")
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (running.get()) {
            try {
                val client = ss.accept()
                executor?.execute { handleConnection(client) }
            } catch (e: IOException) {
                if (!running.get()) break
            } catch (e: Exception) {
                if (!running.get()) break
            }
        }
    }

    private fun handleConnection(socket: Socket) {
        try {
            socket.soTimeout = 30000
            val conn = HttpConnection(socket)
            val req = readRequest(socket) ?: run { conn.close(); return }
            val handler = routes[req.path]
            if (handler == null) {
                conn.sendJson(404, JSONObject().put("status", "error").put("message", "not found"))
            } else {
                handler(req, conn)
            }
        } catch (e: Exception) {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun readRequest(socket: Socket): HttpRequest? {
        val stream = BufferedInputStream(socket.getInputStream())
        val reader = BufferedReader(InputStreamReader(stream, Charsets.US_ASCII))
        val requestLine = reader.readLine() ?: return null
        if (requestLine.isEmpty()) return null
        val parts = requestLine.split(" ")
        if (parts.size < 2) return null
        val method = parts[0]
        val rawPath = parts[1]

        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
        }

        if (method != "GET") return null

        val queryIndex = rawPath.indexOf('?')
        val path = if (queryIndex >= 0) rawPath.substring(0, queryIndex) else rawPath
        val query = if (queryIndex >= 0) rawPath.substring(queryIndex + 1) else ""

        val params = HashMap<String, String>()
        if (query.isNotEmpty()) {
            query.split("&").forEach { kv ->
                val idx = kv.indexOf('=')
                if (idx >= 0) {
                    val key = URLDecoder.decode(kv.substring(0, idx), "UTF-8")
                    val value = URLDecoder.decode(kv.substring(idx + 1), "UTF-8")
                    params[key] = value
                }
            }
        }
        return HttpRequest(method, path, params)
    }
}

/** 已解析的 GET 请求。 */
data class HttpRequest(val method: String, val path: String, val params: Map<String, String>)

/** 一个客户端连接的响应句柄。 */
class HttpConnection(private val socket: Socket) {
    private val out = BufferedOutputStream(socket.getOutputStream())

    /** 发送完整 JSON 响应并关闭连接。 */
    fun sendJson(status: Int, obj: JSONObject) {
        val body = obj.toString()
        val length = body.toByteArray(Charsets.UTF_8).size
        synchronized(this) {
            out.write(
                ("HTTP/1.1 $status ${reason(status)}\r\n" +
                    "Content-Type: application/json;charset=UTF-8\r\n" +
                    "Content-Length: $length\r\n" +
                    "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
            )
            out.write(body.toByteArray(Charsets.UTF_8))
            out.flush()
        }
        close()
    }

    /** 开启 SSE 流式响应（chunked），连接在流关闭前保持打开。 */
    fun openSseStream(): ChunkedWriter {
        synchronized(this) {
            out.write(
                ("HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/event-stream;charset=UTF-8\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "Connection: keep-alive\r\n" +
                    "Transfer-Encoding: chunked\r\n\r\n").toByteArray(Charsets.US_ASCII)
            )
            out.flush()
        }
        return ChunkedWriter(out)
    }

    fun close() {
        try { socket.close() } catch (_: Exception) {}
    }

    private fun reason(status: Int): String = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        404 -> "Not Found"
        else -> "Internal Server Error"
    }
}

/** HTTP chunked 分块写入器：Rust 客户端已兼容 chunk-size 帧。 */
class ChunkedWriter(private val out: OutputStream) {
    /** 写入一个 SSE `data:` 分块。 */
    fun write(data: ByteArray) {
        synchronized(this) {
            out.write((Integer.toHexString(data.size) + "\r\n").toByteArray(Charsets.US_ASCII))
            out.write(data)
            out.write("\r\n".toByteArray(Charsets.US_ASCII))
            out.flush()
        }
    }

    /** 写入终止块并关闭底层连接。 */
    fun close() {
        synchronized(this) {
            try { out.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII)); out.flush() } catch (_: Exception) {}
            try { out.close() } catch (_: Exception) {}
        }
    }
}
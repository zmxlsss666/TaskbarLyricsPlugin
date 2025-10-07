package com.zmxl.taskbarlyrics.server
import com.google.gson.Gson
import com.zmxl.taskbarlyrics.playback.PlaybackStateHolder
import com.zmxl.taskbarlyrics.config.ConfigManager
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException
class HttpServer(private val port: Int) {
    private lateinit var server: Server
    fun start() {
        server = Server(port)
        val context = ServletContextHandler(ServletContextHandler.SESSIONS)
        context.contextPath = "/"
        server.handler = context
        context.addServlet(ServletHolder(LyricSpwServlet()), "/api/lyricspw")
        context.addServlet(ServletHolder(ConfigServlet()), "/api/config")
        try {
            server.start()
            println("HTTP服务器已启动，端口: $port")
        } catch (e: Exception) {
            println("HTTP服务器启动失败: ${e.message}")
            e.printStackTrace()
        }
    }
    fun stop() {
        try {
            server.stop()
            println("HTTP服务器已停止")
        } catch (e: Exception) {
            println("HTTP服务器停止失败: ${e.message}")
            e.printStackTrace()
        }
    }
    class LyricSpwServlet : HttpServlet() {
        private val gson = Gson()
        @Throws(IOException::class)
        override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
            resp.contentType = "application/json;charset=UTF-8"
            try {
                val currentPosition = PlaybackStateHolder.currentPosition
                val (currentLine, nextLine) = PlaybackStateHolder.getCurrentAndNextLyrics(currentPosition)
                if (currentLine != null || nextLine != null) {
                    val simplifiedLyrics = buildString {
                        if (currentLine != null) {
                            append(formatTimeTag(currentLine.time))
                            append(currentLine.text)
                            append("\n")
                        }
                        if (nextLine != null) {
                            append(formatTimeTag(nextLine.time))
                            append(nextLine.text)
                        }
                    }
                    val response = mapOf(
                        "status" to "success",
                        "lyric" to simplifiedLyrics,
                        "source" to "spw",
                        "simplified" to true
                    )
                    resp.writer.write(gson.toJson(response))
                } else {
                    resp.status = HttpServletResponse.SC_NOT_FOUND
                    resp.writer.write(gson.toJson(mapOf(
                        "status" to "error",
                        "message" to "未找到SPW歌词"
                    )))
                }
            } catch (e: Exception) {
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                resp.writer.write(gson.toJson(mapOf(
                    "status" to "error",
                    "message" to "获取SPW歌词失败: ${e.message}"
                )))
            }
        }
        private fun formatTimeTag(timeMs: Long): String {
            val minutes = timeMs / 60000
            val seconds = (timeMs % 60000) / 1000
            val millis = timeMs % 1000
            return String.format("[%02d:%02d.%03d]", minutes, seconds, millis)
        }
    }
    class ConfigServlet : HttpServlet() {
        private val gson = Gson()
        @Throws(IOException::class)
        override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
            resp.contentType = "application/json;charset=UTF-8"
            try {
                val forceRefresh = req.getParameter("forceRefresh")?.toBoolean() ?: false
                if (forceRefresh) {
                    ConfigManager.refreshConfig()
                }
                val config = ConfigManager.getAllConfig()
                val response = mapOf(
                    "status" to "success",
                    "config" to config,
                    "timestamp" to System.currentTimeMillis()
                )
                resp.writer.write(gson.toJson(response))
            } catch (e: Exception) {
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                resp.writer.write(gson.toJson(mapOf(
                    "status" to "error",
                    "message" to "获取配置失败: ${e.message}"
                )))
            }
        }
    }
}
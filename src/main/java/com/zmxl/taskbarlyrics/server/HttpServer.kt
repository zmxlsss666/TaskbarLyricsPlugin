package com.zmxl.taskbarlyrics.server
import com.zmxl.taskbarlyrics.Log
import com.xuncorp.spw.workshop.api.WorkshopApi
import com.zmxl.taskbarlyrics.config.ConfigManager
import com.zmxl.taskbarlyrics.playback.PlaybackStateHolder
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 极简 HTTP 服务：基于自写的 [MiniHttpServer]（ServerSocket），零第三方服务器依赖。
 * 仅保留任务栏歌词（Rust 客户端）真正需要的端点。
 */
class HttpServer(private val port: Int) {

    private val mini = MiniHttpServer(port)

    // ---- SSE 长连接会话 ----
    private val sseSessions = CopyOnWriteArrayList<SseSession>()
    private var configListener: (() -> Unit)? = null

    // 周期性对账：重载配置、与上次推送作比较，仅在实际上有新内容时沿 /api/stream 补推一次。
    // 用来兜底 SPW 变更回调偶尔不触发的场景（如改色），且不新增任何连接。
    private var reconcileTimer: Timer? = null
    @Volatile private var lastConfigJson: String? = null

    init {
        configListener = { broadcastConfig() }
        ConfigManager.addConfigChangeListener(configListener!!)
    }

    fun start() {
        mini.addRoute("/api/lyric") { req, conn -> handleLyric(conn) }
        mini.addRoute("/api/lyricfile") { req, conn -> handleLyricFile(conn) }
        mini.addRoute("/api/config") { req, conn -> handleConfig(req, conn) }
        mini.addRoute("/api/config/set") { req, conn -> handleConfigSet(req, conn) }
        mini.addRoute("/api/stream") { req, conn -> handleStream(conn) }
        mini.addRoute("/api/play-pause") { req, conn -> handlePlayPause(conn) }
        mini.addRoute("/api/next-track") { req, conn -> handleNextTrack(conn) }
        mini.addRoute("/api/previous-track") { req, conn -> handlePreviousTrack(conn) }

        mini.start()

        reconcileTimer = Timer("config-reconcile", true).apply {
            schedule(object : TimerTask() {
                override fun run() = reconcileConfig()
            }, 5000, 5000)
        }
    }

    fun stop() {
        configListener?.let { ConfigManager.removeConfigChangeListener(it) }
        configListener = null
        reconcileTimer?.cancel()
        reconcileTimer = null
        sseSessions.forEach { it.close() }
        mini.stop()
    }

    // ---- 播放控制：直接调用官方播放 API，一次操作只触发一次 ----

    private fun handlePlayPause(conn: HttpConnection) {
        val targetPlaying = !PlaybackStateHolder.isPlaying
        try {
            if (targetPlaying) WorkshopApi.playback.play() else WorkshopApi.playback.pause()
            conn.sendJson(200, JSONObject().apply {
                put("status", "success")
                put("action", "play_pause_toggled")
                put("isPlaying", targetPlaying)
                put("message", if (targetPlaying) "已开始播放" else "已暂停")
            })
        } catch (e: Exception) {
            conn.sendJson(200, JSONObject().apply {
                put("status", "error")
                put("message", "播放/暂停操作失败: ${e.message}")
            })
        }
    }

    private fun handleNextTrack(conn: HttpConnection) {
        try {
            WorkshopApi.playback.next()
            conn.sendJson(200, JSONObject().apply {
                put("status", "success")
                put("action", "next_track")
                put("message", "已切换到下一曲")
            })
        } catch (e: Exception) {
            conn.sendJson(200, JSONObject().apply {
                put("status", "error")
                put("message", "下一曲操作失败: ${e.message}")
            })
        }
    }

    private fun handlePreviousTrack(conn: HttpConnection) {
        try {
            WorkshopApi.playback.previous()
            conn.sendJson(200, JSONObject().apply {
                put("status", "success")
                put("action", "previous_track")
                put("message", "已切换到上一曲")
            })
        } catch (e: Exception) {
            conn.sendJson(200, JSONObject().apply {
                put("status", "error")
                put("message", "上一曲操作失败: ${e.message}")
            })
        }
    }

    // ---- 配置 ----

    private fun handleConfig(req: HttpRequest, conn: HttpConnection) {
        try {
            val forceRefresh = req.params["forceRefresh"] == "true"
            if (forceRefresh) {
                ConfigManager.refreshConfig()
            }
            conn.sendJson(200, JSONObject().apply {
                put("status", "success")
                put("config", JSONObject(ConfigManager.getAllConfig()))
                put("timestamp", System.currentTimeMillis())
            })
        } catch (e: Exception) {
            conn.sendJson(200, JSONObject().apply {
                put("status", "error")
                put("message", "获取配置失败: ${e.message}")
            })
        }
    }

    // ---- 配置：设置单个配置项（任务栏歌词拖拽定位后写入偏移） ----

    private fun handleConfigSet(req: HttpRequest, conn: HttpConnection) {
        val key = req.params["key"] ?: ""
        val rawValue = req.params["value"] ?: ""
        val value = rawValue.toIntOrNull()
        if (key.isEmpty() || value == null) {
            conn.sendJson(400, JSONObject().apply {
                put("status", "error")
                put("message", "无效的参数: key=$key, value=$rawValue")
            })
            return
        }
        // setValue 会保存到 config.json，并触发官方配置变更监听器 → SSE 推送回任务栏歌词。
        val ok = ConfigManager.setValue(key, value)
        conn.sendJson(200, JSONObject().apply {
            put("status", if (ok) "success" else "error")
            put("key", key)
            put("value", value)
            put("message", if (ok) "已保存到配置文件" else "保存失败")
        })
    }

    // ---- 歌词：本地 .lrc 文件 ----

    private fun handleLyricFile(conn: HttpConnection) {
        val media = PlaybackStateHolder.currentMedia
        if (media == null || media.path.isNullOrBlank()) {
            conn.sendJson(400, JSONObject().apply {
                put("status", "error")
                put("message", "没有当前播放媒体或媒体路径为空")
            })
            return
        }

        val mediaPath = media.path
        try {
            val lrcFile = findLrcFile(mediaPath)
            if (lrcFile != null && lrcFile.exists() && lrcFile.isFile) {
                val lyricContent = readLrcFile(lrcFile)
                if (lyricContent.isNotBlank()) {
                    conn.sendJson(200, JSONObject().apply {
                        put("status", "success")
                        put("lyric", lyricContent)
                        put("source", "local_lrc_file")
                        put("file", lrcFile.absolutePath)
                    })
                } else {
                    conn.sendJson(404, JSONObject().apply {
                        put("status", "error")
                        put("message", "LRC文件为空或无法读取")
                        put("file", lrcFile.absolutePath)
                    })
                }
            } else {
                conn.sendJson(404, JSONObject().apply {
                    put("status", "error")
                    put("message", "未找到同名的LRC歌词文件")
                    put("searchedPath", generateLrcFilePath(mediaPath))
                })
            }
        } catch (e: Exception) {
            conn.sendJson(500, JSONObject().apply {
                put("status", "error")
                put("message", "查找或读取LRC文件失败: ${e.message}")
            })
            Log.e(e)
        }
    }

    // ---- 歌词：音频元数据内嵌歌词 ----

    private fun handleLyric(conn: HttpConnection) {
        val media = PlaybackStateHolder.currentMedia
        if (media == null || media.path.isNullOrBlank()) {
            conn.sendJson(400, JSONObject().apply {
                put("status", "error")
                put("message", "没有当前播放媒体或媒体路径为空")
            })
            return
        }

        val filePath = media.path
        try {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                conn.sendJson(404, JSONObject().apply {
                    put("status", "error")
                    put("message", "文件不存在: $filePath")
                })
                return
            }

            val extension = file.extension.lowercase()
            val supportedFormats = listOf("mp3", "flac", "wav", "ogg", "m4a", "aac", "wma", "opus")
            if (!supportedFormats.contains(extension)) {
                conn.sendJson(400, JSONObject().apply {
                    put("status", "error")
                    put("message", "不支持的音频文件格式: $extension")
                })
                return
            }

            val lyrics = extractLyricsFromFile(file)
            if (lyrics.isNotBlank()) {
                conn.sendJson(200, JSONObject().apply {
                    put("status", "success")
                    put("lyric", lyrics)
                    put("source", "file_metadata")
                    put("file", filePath)
                    put("format", extension)
                })
            } else {
                conn.sendJson(404, JSONObject().apply {
                    put("status", "error")
                    put("message", "文件中未找到歌词元数据")
                    put("file", filePath)
                    put("format", extension)
                })
            }
        } catch (e: Exception) {
            conn.sendJson(500, JSONObject().apply {
                put("status", "error")
                put("message", "提取文件歌词失败: ${e.message}")
            })
            Log.e(e)
        }
    }

    // ---- .lrc 文件定位与读取 ----

    private fun findLrcFile(mediaPath: String): File? {
        val mediaFile = File(mediaPath)
        if (!mediaFile.exists()) {
            return null
        }
        val lrcFile = File(generateLrcFilePath(mediaPath))
        if (lrcFile.exists() && lrcFile.isFile) {
            return lrcFile
        }
        return findAlternativeLrcFiles(mediaFile)
    }

    private fun generateLrcFilePath(mediaPath: String): String {
        val mediaFile = File(mediaPath)
        val parentDir = mediaFile.parent
        val fileNameWithoutExt = mediaFile.nameWithoutExtension
        return "$parentDir${File.separator}$fileNameWithoutExt.lrc"
    }

    private fun findAlternativeLrcFiles(mediaFile: File): File? {
        val parentDir = mediaFile.parent
        val baseName = mediaFile.nameWithoutExtension

        val possibleNames = listOf(
            "$baseName.lrc",
            "${baseName.replace(" - ", ".")}.lrc",
            "${baseName.replace(" ", "_")}.lrc",
            "${baseName.replace(" ", "")}.lrc",
            "$baseName.lyric",
            "$baseName.txt"
        )

        for (fileName in possibleNames) {
            val lrcFile = File(parentDir, fileName)
            if (lrcFile.exists() && lrcFile.isFile) {
                return lrcFile
            }
        }
        return null
    }

    private fun readLrcFile(lrcFile: File): String {
        return try {
            lrcFile.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            val encodings = listOf("GBK", "GB2312", "ISO-8859-1", "Windows-1252")
            for (encoding in encodings) {
                try {
                    return lrcFile.readText(Charset.forName(encoding))
                } catch (e: Exception) {
                }
            }
            ""
        }
    }

    // ---- 音频元数据歌词提取（jaudiotagger） ----

    private fun extractLyricsFromFile(file: File): String {
        try {
            val audioFile = AudioFileIO.read(file)
            val tag: Tag? = audioFile.tag
            if (tag == null) {
                return ""
            }
            val usltLyrics = extractUSLTFrameDirectly(tag)
            if (usltLyrics.isNotBlank()) {
                return usltLyrics
            }
            return findLyricsInStandardFields(tag, file.extension)
        } catch (e: Exception) {
            return ""
        }
    }

    private fun extractUSLTFrameDirectly(tag: Tag): String {
        return try {
            if (tag.toString().contains("ID3v2")) {
                extractFromID3v2Tag(tag)
            } else {
                extractUSLTByFieldIteration(tag)
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractFromID3v2Tag(tag: Tag): String {
        try {
            val tagClass = tag.javaClass
            val getFirstFieldMethod = tagClass.methods.find {
                it.name == "getFirstField" && it.parameterCount == 1
            }
            if (getFirstFieldMethod != null) {
                val usltIdentifiers = listOf("USLT", "UNSYNCED LYRICS", "UNSYNCED_LYRICS", "ULT")
                for (identifier in usltIdentifiers) {
                    try {
                        val field = getFirstFieldMethod.invoke(tag, identifier)
                        if (field != null) {
                            val content = extractLyricContentFromField(field)
                            if (content.isNotBlank()) {
                                return content
                            }
                            return field.toString()
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            val getFieldsMethod = tagClass.methods.find {
                it.name == "getFields" && it.parameterCount == 1
            }
            if (getFieldsMethod != null) {
                val fields = getFieldsMethod.invoke(tag, "USLT") as? List<*>
                if (fields != null && fields.isNotEmpty()) {
                    val lyricsBuilder = StringBuilder()
                    for (field in fields) {
                        if (field != null) {
                            val content = extractLyricContentFromField(field)
                            if (content.isNotBlank()) {
                                lyricsBuilder.append(content).append("\n")
                            } else {
                                lyricsBuilder.append(field.toString()).append("\n")
                            }
                        }
                    }
                    val result = lyricsBuilder.toString().trim()
                    if (result.isNotBlank()) {
                        return result
                    }
                }
            }
        } catch (_: Exception) {
        }
        return ""
    }

    private fun extractLyricContentFromField(field: Any): String {
        try {
            val getContentMethod = field.javaClass.methods.find { it.name == "getContent" }
            if (getContentMethod != null) {
                val content = getContentMethod.invoke(field) as? String
                if (!content.isNullOrBlank()) {
                    return content
                }
            }
            return cleanUSLTContent(field.toString())
        } catch (_: Exception) {
            return ""
        }
    }

    private fun cleanUSLTContent(rawContent: String): String {
        var content = rawContent
        val prefixes = listOf(
            "USLT:",
            "USLT=",
            "Unsynchronised lyric:",
            "Unsynchronized lyric:",
            "Unsynchronized lyric/text transcription:"
        )
        prefixes.forEach { prefix ->
            if (content.startsWith(prefix, ignoreCase = true)) {
                content = content.substring(prefix.length).trim()
            }
        }
        if (content.length >= 5 && content.substring(3, 5) == "\u0000") {
            content = content.substring(5)
        }
        return content.trim()
    }

    private fun extractUSLTByFieldIteration(tag: Tag): String {
        val lyricsBuilder = StringBuilder()
        try {
            val fieldsMethod = tag.javaClass.methods.find { it.name == "getFields" }
            if (fieldsMethod != null) {
                val fields = fieldsMethod.invoke(tag) as? List<*>
                fields?.forEach { field ->
                    if (field != null) {
                        val fieldString = field.toString()
                        if (fieldString.contains("USLT", ignoreCase = true) ||
                            fieldString.contains("UNSYNCED", ignoreCase = true)
                        ) {
                            val content = extractLyricContentFromField(field)
                            if (content.isNotBlank()) {
                                lyricsBuilder.append(content).append("\n")
                            } else {
                                lyricsBuilder.append(fieldString).append("\n")
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return lyricsBuilder.toString().trim()
    }

    private fun findLyricsInStandardFields(tag: Tag, fileExtension: String): String {
        val lyricFields = listOf(
            FieldKey.LYRICS,
            FieldKey.LYRICIST,
            FieldKey.COMMENT
        )
        for (field in lyricFields) {
            try {
                if (tag.hasField(field)) {
                    val value = tag.getFirst(field)
                    if (value.isNotBlank()) {
                        return value
                    }
                }
            } catch (_: Exception) {
            }
        }
        return ""
    }

    // ---- SSE 长连接：按变化推送播放状态 / 配置 ----

    private fun handleStream(conn: HttpConnection) {
        val writer = conn.openSseStream()
        val session = SseSession(writer) { sseSessions.remove(it) }
        sseSessions.add(session)
        session.push(stateJson(true))
        session.startTicker()
    }

    /**
     * 组装一次 state 推送。仅在 [trackChanged]（切歌首帧 / 首次连接）时附带歌词文本，
     * 让 Rust 客户端无需再走一次 `/api/lyric` 的 HTTP 往返，避免切歌时仍显示上一首歌词开头。
     * 歌词来源优先级：音频元数据内嵌歌词（/api/lyric）→ SPW 歌词（/api/lyricspw）。
     */
    private fun stateJson(trackChanged: Boolean): String {
        val media = PlaybackStateHolder.currentMedia
        return JSONObject().apply {
            put("type", "state")
            put("position", PlaybackStateHolder.currentPosition)
            put("isPlaying", PlaybackStateHolder.isPlaying)
            put("playbackFactor", PlaybackStateHolder.playbackFactor.toDouble())
            put("title", media?.title ?: "")
            put("artist", media?.artist ?: "")
            put("trackChanged", trackChanged)
            if (trackChanged) {
                val lyric = currentTrackLyric()
                if (lyric.isNotEmpty()) put("lyric", lyric)
            }
        }.toString()
    }

    private fun currentTrackLyric(): String {
        val media = PlaybackStateHolder.currentMedia ?: return ""
        val path = media.path
        if (path.isNullOrBlank()) return ""

        // 优先元数据内嵌歌词 / 回退使用 SPW 已采集歌词。
        val file = File(path)
        if (file.exists() && file.isFile) {
            try {
                val meta = extractLyricsFromFile(file)
                if (meta.isNotBlank()) return meta
            } catch (_: Exception) {
            }
        }
        return PlaybackStateHolder.spwLyricsLrc()
    }

    private fun broadcastConfig() {
        val config = JSONObject(ConfigManager.getAllConfig())
        lastConfigJson = config.toString()
        val json = JSONObject().apply {
            put("type", "config")
            put("config", config)
        }.toString()
        sseSessions.forEach { it.push(json) }
    }

    /** 每 5 秒对账：静默重载配置，若与上次推送有差异则沿 /api/stream 补推，兜底丢失的变更。 */
    private fun reconcileConfig() {
        try {
            ConfigManager.reloadSilently()
            val config = JSONObject(ConfigManager.getAllConfig())
            val snapshot = config.toString()
            if (snapshot != lastConfigJson) {
                lastConfigJson = snapshot
                val json = JSONObject().apply {
                    put("type", "config")
                    put("config", config)
                }.toString()
                sseSessions.forEach { it.push(json) }
            }
        } catch (e: Exception) {
            Log.e(e)
        }
    }

    /**
     * 单个 SSE 连接：独立计时线程，仅在播放状态 / 曲目变化时推送。
     */
    private inner class SseSession(
        private val writer: ChunkedWriter,
        private val onClosed: (SseSession) -> Unit
    ) {
        private val writeLock = Object()
        @Volatile private var closed = false
        private var lastTitle: String? = null
        private var lastPos = Long.MIN_VALUE
        private var lastPlaying: Boolean? = null
        private var timer: Timer? = null

        fun push(data: String) {
            if (closed) return
            try {
                synchronized(writeLock) {
                    if (closed) return
                    writer.write("data: $data\n\n".toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                close()
            }
        }

        fun startTicker() {
            timer = Timer("taskbar-lyrics-sse", true)
            timer?.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (closed) {
                        cancel()
                        return
                    }
                    val media = PlaybackStateHolder.currentMedia
                    val title = media?.title ?: ""
                    val pos = PlaybackStateHolder.currentPosition
                    val playing = PlaybackStateHolder.isPlaying

                    val trackChanged = title != lastTitle
                    val posChanged = pos != lastPos
                    val playingChanged = playing != lastPlaying
                    lastTitle = title
                    lastPos = pos
                    lastPlaying = playing

                    if (trackChanged || posChanged || playingChanged) {
                        push(stateJson(trackChanged))
                    }
                }
            }, 0, 250)
        }

        fun close() {
            if (closed) return
            closed = true
            timer?.cancel()
            timer = null
            synchronized(writeLock) {
                try { writer.close() } catch (_: Exception) {}
            }
            onClosed(this)
        }
    }
}
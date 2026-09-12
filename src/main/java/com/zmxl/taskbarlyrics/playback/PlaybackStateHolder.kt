package com.zmxl.taskbarlyrics.playback
import com.zmxl.taskbarlyrics.Log
import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.CopyOnWriteArrayList

object PlaybackStateHolder {
    @Volatile
    var currentMedia: PlaybackExtensionPoint.MediaItem? = null
    @Volatile
    var isPlaying: Boolean = false
    @Volatile
    var currentState: PlaybackExtensionPoint.State = PlaybackExtensionPoint.State.Idle

    @Volatile
    var currentPosition: Long = 0L
    private var positionUpdateTimer: Timer? = null
    private var lastPositionUpdateTime: Long = 0

    /**
     * 播放速度系数（1.0=原速）。由官方回调 [PlaybackExtensionPoint.onPositionUpdated] 的真实位置
     * 差分推出，用于让任务栏歌词的外推进度在 0.75x/1.5x 变速下也能对齐真实播放。
     */
    @Volatile
    var playbackFactor: Float = 1.0f
    private var lastRealPosition: Long = 0
    private var lastRealTime: Long = 0

    /**
     * 用官方每秒一次的真实播放位置，重新锚定进度并推算速度系数。
     * [realPosition] 为真实进度，[now] 为当前墙钟毫秒。
     */
    fun updatePlaybackFactor(realPosition: Long, now: Long) {
        if (lastRealTime > 0) {
            val dt = now - lastRealTime
            if (dt in 100..3000) {
                val dp = realPosition - lastRealPosition
                if (dp >= 0) {
                    val factor = dp.toFloat() / dt.toFloat()
                    if (factor in 0.5f..2.0f) {
                        playbackFactor = factor
                    }
                }
            }
        }
        lastRealTime = now
        lastRealPosition = realPosition
        currentPosition = realPosition
        lastPositionUpdateTime = now
    }

    fun startPositionUpdate() {
        stopPositionUpdate()

        positionUpdateTimer = Timer(true)
        lastPositionUpdateTime = System.currentTimeMillis()

        positionUpdateTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (isPlaying) {
                    val now = System.currentTimeMillis()
                    val elapsed = now - lastPositionUpdateTime
                    currentPosition += (elapsed * playbackFactor).toLong()
                    lastPositionUpdateTime = now
                }
            }
        }, 0, 100)
    }

    fun stopPositionUpdate() {
        positionUpdateTimer?.cancel()
        positionUpdateTimer = null
    }

    fun setPosition(position: Long) {
        currentPosition = position
        lastPositionUpdateTime = System.currentTimeMillis()
    }

    fun resetPosition() {
        currentPosition = 0L
        lastPositionUpdateTime = System.currentTimeMillis()
    }

    // ---- SPW 歌词采集（/api/lyricspw 来源）：随 onLyricsLineUpdated 累积 ----

    data class SpwLyricLine(val time: Long, val mainText: String, val subText: String)

    private val spwLines = CopyOnWriteArrayList<SpwLyricLine>()

    fun clearSpwLyrics() {
        spwLines.clear()
    }

    fun addSpwLyricLine(time: Long, mainText: String, subText: String) {
        val idx = spwLines.indexOfFirst { it.time == time }
        if (idx >= 0) {
            spwLines[idx] = SpwLyricLine(time, mainText, subText)
        } else {
            spwLines.add(SpwLyricLine(time, mainText, subText))
            spwLines.sortBy { it.time }
        }
    }

    /** 把已采集的 SPW 歌词拼成 LRC 文本（翻译行与主行同时间戳）。 */
    fun spwLyricsLrc(): String {
        if (spwLines.isEmpty()) return ""
        val sb = StringBuilder()
        for (l in spwLines) {
            sb.append(formatTimeTag(l.time)).append(l.mainText).append('\n')
            if (l.subText.isNotEmpty()) {
                sb.append(formatTimeTag(l.time)).append(l.subText).append('\n')
            }
        }
        return sb.toString().trim()
    }

    private fun formatTimeTag(ms: Long): String {
        val minutes = ms / 60000
        val seconds = (ms % 60000) / 1000
        val millis = ms % 1000
        return String.format("[%02d:%02d.%03d]", minutes, seconds, millis)
    }
}
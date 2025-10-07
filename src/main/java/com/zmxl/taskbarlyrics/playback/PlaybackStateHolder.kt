package com.zmxl.taskbarlyrics.playback
import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint
import java.util.*
import java.util.concurrent.ConcurrentHashMap
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
    private val lyricsCache = ConcurrentHashMap<String, MutableList<LyricLine>>()
    @Volatile
    private var currentSongId: String? = null
    private val lyricsLock = Any()
    fun startPositionUpdate() {
        stopPositionUpdate()
        positionUpdateTimer = Timer(true)
        lastPositionUpdateTime = System.currentTimeMillis()
        positionUpdateTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (isPlaying) {
                    val now = System.currentTimeMillis()
                    val elapsed = now - lastPositionUpdateTime
                    currentPosition += elapsed
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
    fun setCurrentSongId(songId: String) {
        currentSongId = songId
        lyricsCache.putIfAbsent(songId, mutableListOf())
    }
    fun addLyricLine(line: LyricLine) {
        currentSongId?.let { songId ->
            synchronized(lyricsLock) {
                val lines = lyricsCache.getOrPut(songId) { mutableListOf() }
                val existingIndex = lines.indexOfFirst { it.time == line.time }
                if (existingIndex >= 0) {
                    lines[existingIndex] = line
                } else {
                    lines.add(line)
                    val sortedLines = lines.sortedBy { it.time }.toMutableList()
                    lines.clear()
                    lines.addAll(sortedLines)
                }
            }
        }
    }
    fun getCurrentAndNextLyrics(currentPosition: Long): Pair<LyricLine?, LyricLine?> {
        currentSongId?.let { songId ->
            synchronized(lyricsLock) {
                val lines = lyricsCache[songId] ?: return Pair(null, null)
                var currentLine: LyricLine? = null
                var nextLine: LyricLine? = null
                for (i in lines.indices) {
                    if (lines[i].time > currentPosition) {
                        nextLine = lines[i]
                        if (i > 0) {
                            currentLine = lines[i - 1]
                        }
                        break
                    }
                    if (i == lines.size - 1 && lines[i].time <= currentPosition) {
                        currentLine = lines[i]
                    }
                }
                return Pair(currentLine, nextLine)
            }
        }
        return Pair(null, null)
    }
    fun clearCurrentLyrics() {
        currentSongId?.let { songId ->
            synchronized(lyricsLock) {
                lyricsCache.remove(songId)
            }
        }
    }
    data class LyricLine(val time: Long, val text: String)
}
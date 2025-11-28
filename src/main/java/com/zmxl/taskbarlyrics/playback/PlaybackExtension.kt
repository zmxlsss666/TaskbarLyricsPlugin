package com.zmxl.taskbarlyrics.playback

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint
import com.xuncorp.spw.workshop.api.WorkshopApi
import org.json.JSONArray
import org.json.JSONObject
import org.pf4j.Extension
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.thread

@Extension
class SpwPlaybackExtension : PlaybackExtensionPoint {
    private val workshopApi: WorkshopApi
        get() = WorkshopApi.instance

    private val workshopApiAvailable by lazy {
        try {
            WorkshopApi.instance.playback
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onStateChanged(state: PlaybackExtensionPoint.State) {
        PlaybackStateHolder.currentState = state
        
        println("播放状态变化: ${state.name}")
        
        when (state) {
            PlaybackExtensionPoint.State.Ready -> {
                if (PlaybackStateHolder.isPlaying) {
                    PlaybackStateHolder.startPositionUpdate()
                }
            }
            PlaybackExtensionPoint.State.Ended -> {
                PlaybackStateHolder.stopPositionUpdate()
            }
            else -> {}
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        PlaybackStateHolder.isPlaying = isPlaying
        
        if (isPlaying) {
            PlaybackStateHolder.startPositionUpdate()
        } else {
            PlaybackStateHolder.stopPositionUpdate()
        }
    }

    override fun onSeekTo(position: Long) {
        PlaybackStateHolder.setPosition(position)
    }

    override fun updateLyrics(mediaItem: PlaybackExtensionPoint.MediaItem): String? {
        return onBeforeLoadLyrics(mediaItem)
    }

override fun onBeforeLoadLyrics(mediaItem: PlaybackExtensionPoint.MediaItem): String? {
    PlaybackStateHolder.currentMedia = mediaItem
    
    val songId = "${mediaItem.title}-${mediaItem.artist}-${mediaItem.album}"
    PlaybackStateHolder.setCurrentSongId(songId)
    
    PlaybackStateHolder.clearCurrentLyrics()
    
    PlaybackStateHolder.resetPosition()
    
    thread {
        try {
            val searchQuery = "${mediaItem.title}-${mediaItem.artist}"
            val encodedQuery = URLEncoder.encode(searchQuery, "UTF-8")
            val searchUrl = "https://music.163.com/api/search/get?type=1&offset=0&limit=1&s=$encodedQuery"
            
            val searchResult = getUrlContent(searchUrl)
            val searchJson = JSONObject(searchResult)
            
            if (searchJson.has("result") && !searchJson.isNull("result")) {
                val result = searchJson.getJSONObject("result")
                if (result.has("songs") && !result.isNull("songs")) {
                    val songs = result.getJSONArray("songs")
                    
                    if (songs.length() > 0) {
                        val songId = songs.getJSONObject(0).getInt("id")
                        
                        val songInfoUrl = "https://api.injahow.cn/meting/?type=song&id=$songId"
                        val songInfoResult = getUrlContent(songInfoUrl)
                        val songInfoArray = JSONArray(songInfoResult)
                        
                        if (songInfoArray.length() > 0) {
                            val songInfo = songInfoArray.getJSONObject(0)
                            PlaybackStateHolder.coverUrl = songInfo.getString("pic")
                            println("获取封面成功: coverUrl=${PlaybackStateHolder.coverUrl}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("获取封面失败: ${e.message}")
        }
    }
    
    return null
}

    override fun onLyricsLineUpdated(lyricsLine: PlaybackExtensionPoint.LyricsLine?) {
        lyricsLine?.let { line ->
            println("歌词行更新: ${line.pureMainText} (${line.startTime}-${line.endTime})")
            
            val pureSubText = line.pureSubText
            val combinedText = if (pureSubText != null && pureSubText.isNotEmpty()) {
                "${line.pureMainText}\n${pureSubText}"
            } else {
                line.pureMainText
            }
            
            val lyricLine = PlaybackStateHolder.LyricLine(
                line.startTime,
                combinedText
            )
            
            PlaybackStateHolder.addLyricLine(lyricLine)
        }
    }
    
    private fun getUrlContent(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    fun setVolume(level: Int) {
        if (level in 0..100) {
            PlaybackStateHolder.volume = level
        }
    }

    fun seekTo(position: Long) {
        PlaybackStateHolder.setPosition(position)
    }

    fun togglePlayback() {
        try {
            if (workshopApiAvailable) {
                if (PlaybackStateHolder.isPlaying) {
                    WorkshopApi.instance.playback.pause()
                } else {
                    WorkshopApi.instance.playback.play()
                }
            } else {
                val newState = !PlaybackStateHolder.isPlaying
                PlaybackStateHolder.isPlaying = newState
            }
        } catch (e: Exception) {
            println("播放/暂停操作失败: ${e.message}")
            e.printStackTrace()
        }
    }

    fun next() {
        try {
            println("执行下一曲操作")
            if (workshopApiAvailable) {
                WorkshopApi.instance.playback.next()
            } else {
                println("下一曲操作（旧方式）")
            }
        } catch (e: Exception) {
            println("下一曲操作失败: ${e.message}")
            e.printStackTrace()
        }
    }

    fun previous() {
        try {
            println("执行上一曲操作")
            if (workshopApiAvailable) {
                WorkshopApi.instance.playback.previous()
            } else {
                println("上一曲操作（旧方式）")
            }
        } catch (e: Exception) {
            println("上一曲操作失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    fun setExclusiveAudio(exclusive: Boolean) {
        try {
            workshopApi.playback.changeExclusive(exclusive)
            println("设置独占音频: $exclusive")
        } catch (e: Exception) {
            println("设置独占音频失败: ${e.message}")
        }
    }
    
    fun showToast(message: String, type: WorkshopApi.Ui.ToastType = WorkshopApi.Ui.ToastType.Success) {
        try {
            workshopApi.ui.toast(message, type)
            println("显示提示: $message")
        } catch (e: Exception) {
            println("显示提示失败: ${e.message}")
        }
    }
}
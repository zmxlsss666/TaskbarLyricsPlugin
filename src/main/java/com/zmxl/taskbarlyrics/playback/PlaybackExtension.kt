package com.zmxl.taskbarlyrics.playback
import com.zmxl.taskbarlyrics.Log
import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint
import org.pf4j.Extension

@Extension
class SpwPlaybackExtension : PlaybackExtensionPoint {

    override fun onStateChanged(state: PlaybackExtensionPoint.State) {
        PlaybackStateHolder.currentState = state

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
            // 暂停时把速度系数归零：恢复播放后在下一次真实位置回调校准前保持不动，
            // 避免用旧的倍速在外推间隙把进度瞬间顶到前面造成跳变。
            PlaybackStateHolder.playbackFactor = 0f
        }
    }

    override fun onSeekTo(position: Long) {
        PlaybackStateHolder.setPosition(position)
    }

    /**
     * 官方每秒一次的真实播放位置：用它重新锚定进度并推算播放速度系数，
     * 从而让 0.75x/1.5x 变速下的歌词动画对齐真实播放。
     */
    override fun onPositionUpdated(position: Long) {
        PlaybackStateHolder.updatePlaybackFactor(position, System.currentTimeMillis())
    }

    override fun updateLyrics(mediaItem: PlaybackExtensionPoint.MediaItem): String? {
        return onBeforeLoadLyrics(mediaItem)
    }

    override fun onBeforeLoadLyrics(mediaItem: PlaybackExtensionPoint.MediaItem): String? {
        PlaybackStateHolder.currentMedia = mediaItem
        PlaybackStateHolder.clearSpwLyrics()
        PlaybackStateHolder.resetPosition()
        return null
    }

    override fun onLyricsLineUpdated(lyricsLine: PlaybackExtensionPoint.LyricsLine?) {
        lyricsLine?.let { line ->
            PlaybackStateHolder.addSpwLyricLine(
                line.startTime,
                line.pureMainText,
                line.pureSubText ?: ""
            )
        }
    }
}
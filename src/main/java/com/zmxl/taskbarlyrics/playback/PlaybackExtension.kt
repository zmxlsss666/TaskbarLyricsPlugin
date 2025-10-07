package com.zmxl.taskbarlyrics.playback
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
        return null
    }
    override fun onLyricsLineUpdated(lyricsLine: PlaybackExtensionPoint.LyricsLine?) {
        lyricsLine?.let { line ->
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
}
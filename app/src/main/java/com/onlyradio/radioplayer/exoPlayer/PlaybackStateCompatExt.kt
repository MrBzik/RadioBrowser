package com.onlyradio.radioplayer.exoPlayer

import android.os.SystemClock
import android.support.v4.media.session.PlaybackStateCompat


//enum class PlaybackState {
//    IS_PLAYING, IS_PLAY_ENABLED
//}


inline val PlaybackStateCompat.isPrepared
    get() = state == PlaybackStateCompat.STATE_BUFFERING ||
            state == PlaybackStateCompat.STATE_PLAYING ||
            state == PlaybackStateCompat.STATE_PAUSED

inline val PlaybackStateCompat.isPlaying
    get() = state == PlaybackStateCompat.STATE_BUFFERING ||
            state == PlaybackStateCompat.STATE_PLAYING

inline val PlaybackStateCompat.isPlayEnabled
    get() = actions and PlaybackStateCompat.ACTION_PLAY != 0L ||
            (actions and PlaybackStateCompat.ACTION_PLAY_PAUSE != 0L &&
                    state == PlaybackStateCompat.STATE_PAUSED)


inline val PlaybackStateCompat.currentPlaybackPosition : Long
    get() = if(state == PlaybackStateCompat.STATE_PLAYING) {
        val timeDifference = SystemClock.elapsedRealtime() - lastPositionUpdateTime
        (position + (timeDifference * playbackSpeed)).toLong()
    } else position

package com.hangfolyam.app

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

class PlayerViewModel : ViewModel() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var player: MediaController? = null
        private set

    // Ez kapcsolódik rá a háttérben futó MusicService-re
    fun initPlayer(context: Context) {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        
        controllerFuture?.addListener(
            { player = controllerFuture?.get() },
            ContextCompat.getMainExecutor(context)
        )
    }

    // EZ INDÍTJA EL A LEJÁTSZÁST!
    fun playAudio(url: String) {
        player?.let {
            val mediaItem = MediaItem.fromUri(url)
            it.setMediaItem(mediaItem)
            it.prepare()
            it.play() // Lejátszás indítása
        }
    }

    fun pauseAudio() {
        player?.pause()
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}

package com.ebookfrenzy.dawaibuddy.models

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ebookfrenzy.dawaibuddy.objects.MeditationTrack
import com.ebookfrenzy.dawaibuddy.NowPlayingFragment
import com.ebookfrenzy.dawaibuddy.services.PlaybackService
import com.google.common.util.concurrent.ListenableFuture

// This ViewModel is shared across all your fragments.
// It connects them all to the background service simultaneously.
class SharedAudioViewModel : ViewModel() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var player: MediaController? = null

    private val _currentTrack = MutableLiveData<MeditationTrack?>()
    val currentTrack: LiveData<MeditationTrack?> = _currentTrack

    private val _isPlaying = MutableLiveData<Boolean>(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _artworkBitmap = MutableLiveData<Bitmap?>()
    val artworkBitmap: LiveData<Bitmap?> = _artworkBitmap

    fun initializeController(context: Context) {
        if (controllerFuture != null) return
        val sessionToken =
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context.applicationContext, sessionToken).buildAsync()

        controllerFuture?.addListener({
            player = controllerFuture?.get()
            // Force the queue to loop infinitely so the "Next" button NEVER disappears!
            player?.repeatMode = Player.REPEAT_MODE_ALL
            setupPlayerListener()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupPlayerListener() {
        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.postValue(isPlaying)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val data = mediaMetadata.artworkData
                if (data != null) {
                    _artworkBitmap.postValue(BitmapFactory.decodeByteArray(data, 0, data.size))
                } else {
                    _artworkBitmap.postValue(null)
                }
            }

            // 🔥 Crucial: Update the app's UI when the user skips tracks via the Android Notification Panel
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) {
                    val title = mediaItem.mediaMetadata.title?.toString()

                    // Match the background player's track to our local playlist to update LiveData
                    val matchedTrack = NowPlayingFragment.Companion.currentTrackList.find { it.title == title }
                    if (matchedTrack != null) {
                        _currentTrack.postValue(matchedTrack)
                    }
                }
            }
        })
    }

    fun playTrack(track: MeditationTrack) {
        _currentTrack.postValue(track)

        val playlist = NowPlayingFragment.Companion.currentTrackList

        if (playlist.isNotEmpty()) {
            // 1. Convert the ENTIRE playlist to MediaItems so the Notification Panel knows what's next
            val mediaItems = playlist.map { t ->
                val metadata = MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .build()

                MediaItem.Builder()
                    .setUri(t.audioUrl)
                    .setMediaMetadata(metadata)
                    .build()
            }

            // 2. Find exactly where the clicked track is inside the active queue
            val startIndex = playlist.indexOfFirst { it.title == track.title }.coerceAtLeast(0)

            // 3. Set the whole queue into ExoPlayer and jump straight to the clicked song
            player?.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
        } else {
            // Fallback: Just play the single song if the playlist hasn't been loaded
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(track.audioUrl)
                .setMediaMetadata(metadata)
                .build()

            player?.setMediaItem(mediaItem)
        }

        player?.prepare()
        player?.play()
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
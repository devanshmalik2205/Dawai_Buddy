package com.ebookfrenzy.dawaibuddy.services

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                super.onMediaMetadataChanged(mediaMetadata)
                val currentItem = player.currentMediaItem ?: return

                val itemMetadata = currentItem.mediaMetadata

                // Check if the current item is missing artwork or title
                val missingArtwork = itemMetadata.artworkData == null && itemMetadata.artworkUri == null
                val missingTitle = itemMetadata.title == null

                // Check if the player just extracted artwork or title from the MP3 file
                val foundArtwork = mediaMetadata.artworkData != null || mediaMetadata.artworkUri != null
                val foundTitle = mediaMetadata.title != null

                // If ExoPlayer found the MP3's embedded cover or title, update the active item!
                if ((missingArtwork && foundArtwork) || (missingTitle && foundTitle)) {
                    val updatedItem = currentItem.buildUpon()
                        .setMediaMetadata(
                            itemMetadata.buildUpon()
                                .populate(mediaMetadata) // Injects the MP3's ID3 cover image
                                .build()
                        )
                        .build()

                    // Seamlessly update the playlist item so the Lock Screen updates instantly
                    player.replaceMediaItem(player.currentMediaItemIndex, updatedItem)
                }
            }
        })

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.isPlaying) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        player.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
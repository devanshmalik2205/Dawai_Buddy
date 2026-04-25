package com.ebookfrenzy.dawaibuddy.adapters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.ebookfrenzy.dawaibuddy.objects.MeditationTrack
import com.ebookfrenzy.dawaibuddy.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistAdapter(
    private var tracks: List<MeditationTrack>,
    private val onTrackClicked: (MeditationTrack) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    // --- NEW: In-Memory Cache ---
    // Stores the extracted images so they load instantly when scrolling
    companion object {
        // Use 1/8th of the available memory for this memory cache.
        private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        private val cacheSize = maxMemory / 8

        private val imageCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                // The cache size will be measured in kilobytes rather than number of items
                return bitmap.byteCount / 1024
            }
        }
    }

    class PlaylistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardIcon: CardView = view.findViewById(R.id.cardIcon)
        val ivCategoryIcon: ImageView = view.findViewById(R.id.ivCategoryIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvPlaylistTitle)
        val tvSubtitle: TextView = view.findViewById(R.id.tvPlaylistSubtitle)
        val btnPlaySmall: TextView = view.findViewById(R.id.btnPlaySmall)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meditation_playlist, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val track = tracks[position]

        holder.tvTitle.text = track.title
        holder.tvSubtitle.text = track.artist

        // Rotating background colors while the image loads
        val defaultColors = listOf("#9DABF5", "#81C784", "#F48FB1", "#FFCC80", "#CE93D8")
        val color = Color.parseColor(defaultColors[position % defaultColors.size])
        holder.cardIcon.setCardBackgroundColor(color)

        // Clear previous image when scrolling to avoid mismatched covers
        holder.ivCategoryIcon.setImageBitmap(null)
        holder.ivCategoryIcon.alpha = 1f // Reset alpha

        if (track.audioUrl.isNotEmpty()) {
            // 1. Check if the image is already downloaded and cached
            val cachedBitmap = imageCache.get(track.audioUrl)

            if (cachedBitmap != null) {
                // BOOM! Instant load from memory
                holder.ivCategoryIcon.setImageBitmap(cachedBitmap)
            } else {
                // 2. Not cached? Fetch it in the background
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(track.audioUrl, HashMap<String, String>())
                        val art = retriever.embeddedPicture

                        if (art != null) {
                            val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)

                            // Save to cache for next time!
                            imageCache.put(track.audioUrl, bitmap)

                            withContext(Dispatchers.Main) {
                                // Ensure the view hasn't been recycled for a different track while we were downloading
                                if (holder.tvTitle.text == track.title) {
                                    // Smooth fade-in animation
                                    holder.ivCategoryIcon.alpha = 0f
                                    holder.ivCategoryIcon.setImageBitmap(bitmap)
                                    holder.ivCategoryIcon.animate().alpha(1f).setDuration(300).start()
                                }
                            }
                        }
                        retriever.release()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // Handle Play button click
        holder.btnPlaySmall.setOnClickListener {
            onTrackClicked(track)
        }
    }

    override fun getItemCount(): Int = tracks.size

    fun updateData(newTracks: List<MeditationTrack>) {
        tracks = newTracks
        notifyDataSetChanged()
    }
}
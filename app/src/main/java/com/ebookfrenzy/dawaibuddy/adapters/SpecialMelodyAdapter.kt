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

class SpecialMelodyAdapter(
    private var tracks: List<MeditationTrack>,
    private val onTrackClicked: (MeditationTrack) -> Unit
) : RecyclerView.Adapter<SpecialMelodyAdapter.MelodyViewHolder>() {

    // Isolated memory cache for rapid swiping
    companion object {
        private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        private val cacheSize = maxMemory / 8
        private val imageCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount / 1024
        }
    }

    class MelodyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivBackground: ImageView = view.findViewById(R.id.ivBackground)
        val tvTitle: TextView = view.findViewById(R.id.tvMainTitle)
        val tvSubtitle: TextView = view.findViewById(R.id.tvMainSubtitle)
        val cvPlay: CardView = view.findViewById(R.id.cvPlay)
        val cvContainer: CardView = view.findViewById(R.id.cvContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MelodyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_special_melody, parent, false)
        return MelodyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MelodyViewHolder, position: Int) {
        val track = tracks[position]

        holder.tvTitle.text = track.title
        holder.tvSubtitle.text = track.artist

        // Give the cards gorgeous rotating gradient fallbacks
        val defaultColors = listOf("#F48FB1", "#BA68C8", "#90CAF9", "#81C784")
        holder.cvContainer.setCardBackgroundColor(Color.parseColor(defaultColors[position % defaultColors.size]))

        holder.ivBackground.setImageBitmap(null)
        holder.ivBackground.alpha = 1f

        if (track.audioUrl.isNotEmpty()) {
            val cachedBitmap = imageCache.get(track.audioUrl)
            if (cachedBitmap != null) {
                holder.ivBackground.setImageBitmap(cachedBitmap)
            } else {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(track.audioUrl, HashMap<String, String>())
                        val art = retriever.embeddedPicture
                        if (art != null) {
                            val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                            imageCache.put(track.audioUrl, bitmap)
                            withContext(Dispatchers.Main) {
                                if (holder.tvTitle.text == track.title) {
                                    holder.ivBackground.alpha = 0f
                                    holder.ivBackground.setImageBitmap(bitmap)
                                    holder.ivBackground.animate().alpha(1f).setDuration(300).start()
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

        // Click actions trigger the audioViewModel!
        holder.itemView.setOnClickListener { onTrackClicked(track) }
        holder.cvPlay.setOnClickListener { onTrackClicked(track) }
    }

    override fun getItemCount(): Int = tracks.size

    fun updateData(newTracks: List<MeditationTrack>) {
        tracks = newTracks
        notifyDataSetChanged()
    }
}
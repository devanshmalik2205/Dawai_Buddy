package com.ebookfrenzy.dawaibuddy.home

import com.ebookfrenzy.dawaibuddy.R
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ebookfrenzy.dawaibuddy.objects.MeditationTrack
import com.ebookfrenzy.dawaibuddy.NowPlayingFragment
import com.ebookfrenzy.dawaibuddy.models.SharedAudioViewModel
import com.ebookfrenzy.dawaibuddy.adapters.PlaylistAdapter
import com.ebookfrenzy.dawaibuddy.adapters.SpecialMelodyAdapter
import com.ebookfrenzy.dawaibuddy.databinding.FragmentMeditateBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController

class MeditateFragment : Fragment() {

    // 🔥 GLOBAL RAM CACHE: Survives Fragment destruction.
    // This makes navigating between bottom tabs load in 0.0ms.
    companion object {
        private val globalTracksCache: MutableMap<String, List<MeditationTrack>> = mutableMapOf()
        private var isFullyCached = false
    }

    private var _binding: FragmentMeditateBinding? = null
    private val binding get() = _binding!!

    private val audioViewModel: SharedAudioViewModel by activityViewModels()

    private lateinit var categoryAdapter: MeditateCategoryAdapter
    private lateinit var adapter: PlaylistAdapter
    private lateinit var specialAdapter: SpecialMelodyAdapter
    private val db = FirebaseFirestore.getInstance()

    // Keep track of the current list for Next/Previous logic
    private var currentTrackList: List<MeditationTrack> = emptyList()

    private var allTracksMap: MutableMap<String, List<MeditationTrack>> = mutableMapOf()
    private var currentCategoryId: String? = null

    // Keep track of all active listeners to prevent memory leaks
    private val tracksListeners = mutableListOf<ListenerRegistration>()

    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressAction = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMeditateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        audioViewModel.initializeController(requireContext())

        setupRecyclerView()

        // Navigate to the unified search fragment
        binding.btnSearch.setOnClickListener {
            findNavController().navigate(R.id.action_nav_meditate_to_searchFragment)
        }

        // Start the high-speed cached fetching
        loadDataInstantly()

        binding.miniPlayerContainer.setOnClickListener {
            NowPlayingFragment.currentTrackList = currentTrackList
            val bottomSheet = NowPlayingFragment()
            bottomSheet.show(parentFragmentManager, "NowPlaying")
        }

        audioViewModel.currentTrack.observe(viewLifecycleOwner) { track ->
            if (track != null) {
                binding.miniPlayerTitle.text = track.title
                binding.miniPlayerArtist.text = track.artist
                binding.miniPlayerContainer.visibility = View.VISIBLE
            }
        }

        audioViewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            if (isPlaying) {
                binding.miniPlayerPlay.setImageResource(android.R.drawable.ic_media_pause)
                handler.post(updateProgressAction)
            } else {
                binding.miniPlayerPlay.setImageResource(android.R.drawable.ic_media_play)
                handler.removeCallbacks(updateProgressAction)
            }
        }

        audioViewModel.artworkBitmap.observe(viewLifecycleOwner) { bitmap ->
            if (bitmap != null) {
                binding.miniPlayerImage.setImageBitmap(bitmap)
                Palette.from(bitmap).generate { palette ->
                    val dominantColor = palette?.getDominantColor(Color.parseColor("#3F3140"))
                        ?: Color.parseColor("#3F3140")
                    binding.miniPlayerContainer.setCardBackgroundColor(dominantColor)
                }
            } else {
                binding.miniPlayerImage.setBackgroundColor(Color.DKGRAY)
                binding.miniPlayerContainer.setCardBackgroundColor(Color.parseColor("#3F3140"))
            }
        }

        binding.miniPlayerPlay.setOnClickListener {
            audioViewModel.player?.let { player ->
                if (player.isPlaying) player.pause() else player.play()
            }
        }

        binding.miniPlayerNext.setOnClickListener {
            val currentTrack = audioViewModel.currentTrack.value
            if (currentTrack != null && currentTrackList.isNotEmpty()) {
                val currentIndex = currentTrackList.indexOfFirst { it.title == currentTrack.title }

                if (currentIndex != -1) {
                    val nextIndex = if (currentIndex + 1 < currentTrackList.size) currentIndex + 1 else 0
                    audioViewModel.playTrack(currentTrackList[nextIndex])
                    return@setOnClickListener
                }
            }
            audioViewModel.player?.seekToNextMediaItem()
        }

        binding.miniPlayerPrev.setOnClickListener {
            val currentTrack = audioViewModel.currentTrack.value
            if (currentTrack != null && currentTrackList.isNotEmpty()) {
                val currentIndex = currentTrackList.indexOfFirst { it.title == currentTrack.title }

                if (currentIndex != -1) {
                    val prevIndex = if (currentIndex - 1 >= 0) currentIndex - 1 else currentTrackList.size - 1
                    audioViewModel.playTrack(currentTrackList[prevIndex])
                    return@setOnClickListener
                }
            }
            audioViewModel.player?.seekToPreviousMediaItem()
        }
    }

    private fun setupRecyclerView() {
        // Setup Categories Adapter
        categoryAdapter = MeditateCategoryAdapter(emptyList()) { category ->
            displayTracksForCategory(category.id)
        }

        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
            setHasFixedSize(true) // UI Optimization
        }

        // Setup All Melodies (Grid)
        adapter = PlaylistAdapter(emptyList()) { track ->
            NowPlayingFragment.currentTrackList = currentTrackList
            audioViewModel.playTrack(track)
        }

        binding.rvPlaylists.apply {
            adapter = this@MeditateFragment.adapter
            setHasFixedSize(true) // UI Optimization
            setItemViewCacheSize(20) // Keeps items in memory for butter-smooth scrolling
        }

        // Setup Special Melodies
        specialAdapter = SpecialMelodyAdapter(emptyList()) { track ->
            NowPlayingFragment.currentTrackList = currentTrackList
            audioViewModel.playTrack(track)
            val bottomSheet = NowPlayingFragment()
            bottomSheet.show(parentFragmentManager, "NowPlaying")
        }
        binding.vpSpecialMelodies.adapter = specialAdapter
        binding.vpSpecialMelodies.offscreenPageLimit = 3
    }

    // 🔥 HIGH SPEED RAM-CACHED LOADING
    private fun loadDataInstantly() {
        // 1. INSTANT CHIPS
        val defaultCategories = listOf(
            Category(id = "deep_sleep", name = "Deep Sleep"),
            Category(id = "deep_focus", name = "Deep Focus"),
            Category(id = "stress_relief", name = "Stress Relief")
        )
        categoryAdapter.updateData(defaultCategories)

        if (currentCategoryId == null) {
            currentCategoryId = defaultCategories[0].id
        }

        // 2. CHECK RAM CACHE: If already loaded during this app session, use it instantly and skip Firestore!
        if (isFullyCached && globalTracksCache.isNotEmpty()) {
            allTracksMap = globalTracksCache.toMutableMap()
            currentTrackList = allTracksMap.values.flatten().shuffled()
            currentCategoryId?.let { displayTracksForCategory(it) }
            return // <-- Crucial: Exits the function early so we don't attach redundant slow listeners
        }

        // 3. FIRST TIME LOAD: Fire lightweight background queries and save to RAM Cache
        var completedCategories = 0
        defaultCategories.forEach { category ->
            val listener = db.collection("meditation_tracks")
                .document(category.id)
                .collection("tracks")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener

                    if (!snapshot.isEmpty) {
                        val tracks = snapshot.documents.mapNotNull { it.toObject(MeditationTrack::class.java) }

                        // Save to both Local and Global RAM Cache
                        allTracksMap[category.id] = tracks
                        globalTracksCache[category.id] = tracks

                        currentTrackList = allTracksMap.values.flatten().shuffled()

                        // Immediately refresh the UI if this is the active tab
                        if (currentCategoryId == category.id) {
                            displayTracksForCategory(category.id)
                        }

                        completedCategories++
                        if (completedCategories == defaultCategories.size) {
                            isFullyCached = true // Mark as fully loaded for future tab switches
                        }
                    }
                }
            tracksListeners.add(listener)
        }
    }

    // Filters the locally cached tracks in 0.0 seconds
    private fun displayTracksForCategory(categoryId: String) {
        currentCategoryId = categoryId
        val tracks = allTracksMap[categoryId] ?: emptyList()

        adapter.updateData(tracks)

        if (tracks.isNotEmpty()) {
            specialAdapter.updateData(tracks.shuffled().take(3))
        } else {
            specialAdapter.updateData(emptyList())
        }
    }

    private fun updateProgress() {
        audioViewModel.player?.let { player ->
            if (player.isPlaying) {
                val duration = player.duration
                if (duration > 0) {
                    binding.miniPlayerProgress.max = duration.toInt()
                    binding.miniPlayerProgress.progress = player.currentPosition.toInt()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up all active listeners to free up phone resources
        tracksListeners.forEach { it.remove() }
        tracksListeners.clear()

        handler.removeCallbacks(updateProgressAction)
        _binding = null
    }
}

// --- Dynamic Category Data Models & Adapter ---

data class Category(
    val id: String = "",
    val name: String = ""
)

class MeditateCategoryAdapter(
    private var categories: List<Category>,
    private val onCategorySelected: (Category) -> Unit
) : RecyclerView.Adapter<MeditateCategoryAdapter.CategoryViewHolder>() {

    private var selectedPosition = 0

    inner class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(com.ebookfrenzy.dawaibuddy.R.id.tvCategoryName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(com.ebookfrenzy.dawaibuddy.R.layout.item_category_meditate, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.tvName.text = category.name

        if (position == selectedPosition) {
            val blue = ContextCompat.getColor(holder.itemView.context, R.color.green_dark_light)
            holder.tvName.backgroundTintList = ColorStateList.valueOf(blue)
            holder.tvName.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.white))
        } else {
            val white = ContextCompat.getColor(holder.itemView.context, R.color.white)
            val gray = ContextCompat.getColor(holder.itemView.context, R.color.green_dark_light)

            holder.tvName.backgroundTintList = ColorStateList.valueOf(white)
            holder.tvName.setTextColor(gray)
        }

        // Handle Chip Selection
        holder.itemView.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = holder.adapterPosition

            // Notify UI of the change to redraw the selected/unselected backgrounds
            notifyItemChanged(previousPosition)
            notifyItemChanged(selectedPosition)

            // Fetch tracks for the new category instantly from RAM cache
            onCategorySelected(category)
        }
    }

    override fun getItemCount() = categories.size

    fun updateData(newCategories: List<Category>) {
        categories = newCategories
        notifyDataSetChanged()
    }
}
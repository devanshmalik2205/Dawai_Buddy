package com.ebookfrenzy.dawaibuddy

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.ebookfrenzy.dawaibuddy.adapters.CategoryAdapter
import com.ebookfrenzy.dawaibuddy.adapters.FlashDealAdapter
import com.ebookfrenzy.dawaibuddy.databinding.FragmentMainPageBinding
import com.ebookfrenzy.dawaibuddy.models.SharedAudioViewModel
import com.ebookfrenzy.dawaibuddy.models.SharedCartViewModel
import com.ebookfrenzy.dawaibuddy.objects.Category
import com.ebookfrenzy.dawaibuddy.objects.FlashDeal
import com.ebookfrenzy.dawaibuddy.objects.MeditationTrack
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class MainPageFragment : Fragment() {

    private var _binding: FragmentMainPageBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val db = FirebaseFirestore.getInstance()

    // CONNECT TO SHARED CART VIEWMODEL
    private val cartViewModel: SharedCartViewModel by activityViewModels()

    // CONNECT TO GLOBAL AUDIO
    private val audioViewModel: SharedAudioViewModel by activityViewModels()

    // List to keep track of loaded songs for next/prev functionality
    private var currentTrackList: List<MeditationTrack> = emptyList()

    private var progressHandler: Handler? = null
    private var progressRunnable: Runnable? = null

    private val sliderHandler = Handler(Looper.getMainLooper())
    private val sliderRunnable = Runnable {
        val adapter = binding.vpFlashDeals.adapter
        if (adapter != null && adapter.itemCount > 0) {
            val nextItem = (binding.vpFlashDeals.currentItem + 1) % adapter.itemCount
            binding.vpFlashDeals.setCurrentItem(nextItem, true)
        }
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                    permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                fetchCurrentLocation()
            }
            else -> {
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
                binding.tvLocation.text = "Location Denied"
            }
        }
    }

    private val startAutocomplete = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data
            if (intent != null) {
                val place = Autocomplete.getPlaceFromIntent(intent)
                binding.tvLocation.text = "${place.name}"
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        audioViewModel.initializeController(requireContext())
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        if (!Places.isInitialized()) {
            Places.initialize(requireContext(), BuildConfig.MAPS_API_KEY)
        }

        binding.tvLocation.setOnClickListener { openLocationPicker() }

        binding.etSearch.isFocusable = false
        binding.etSearch.isClickable = true
        binding.etSearch.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home_to_searchFragment)
        }

        setupRecyclerView()
        fetchCategories()
        fetchFlashDeals()
        fetchAllTracks()
        checkPermissionsAndFetchLocation()
        setupCartObserver()

        // --- NAVIGATION LINKS ---
        binding.root.findViewById<View>(R.id.llHealthQuickLink)?.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home_to_nav_wellness)
        }

        binding.root.findViewById<View>(R.id.llMoodQuickLink)?.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home_to_moodTrackerFragment)
        }

        binding.root.findViewById<View>(R.id.llWaterQuickLink)?.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home_to_waterLoggerFragment)
        }

        binding.root.findViewById<View>(R.id.llMeditateQuickLink)?.setOnClickListener {
            playRandomTrack()
            if (currentTrackList.isNotEmpty()) {
                NowPlayingFragment.currentTrackList = currentTrackList
                val bottomSheet = NowPlayingFragment()
                bottomSheet.show(parentFragmentManager, "NowPlaying")
            }
        }

        // Navigation to checkout when floating cart is clicked
        binding.cvFloatingCart.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home_to_checkoutFragment)
        }

        // --- BACKGROUND AUDIO SYNC & PROGRESS BAR ---
        progressHandler = Handler(Looper.getMainLooper())
        progressRunnable = Runnable {
            audioViewModel.player?.let { player ->
                if (player.isPlaying) {
                    val duration = player.duration
                    val current = player.currentPosition
                    if (duration > 0) {
                        val pb = binding.root.findViewById<android.widget.ProgressBar>(R.id.pbMindfulnessProgress)
                        pb?.max = duration.toInt()
                        pb?.progress = current.toInt()
                    }
                }
            }
            progressHandler?.postDelayed(progressRunnable!!, 1000)
        }

        audioViewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            val ivPlay = binding.root.findViewById<ImageView>(R.id.ivMindfulnessPlay)
            if (isPlaying) {
                ivPlay?.setImageResource(android.R.drawable.ic_media_pause)
                progressRunnable?.let { progressHandler?.post(it) }
            } else {
                ivPlay?.setImageResource(android.R.drawable.ic_media_play)
                progressRunnable?.let { progressHandler?.removeCallbacks(it) }
            }
        }

        audioViewModel.currentTrack.observe(viewLifecycleOwner) { track ->
            val tvTitle = binding.root.findViewById<TextView>(R.id.tvMindfulnessTitle)
            val tvSubtitle = binding.root.findViewById<TextView>(R.id.tvMindfulnessSubtitle)
            val pb = binding.root.findViewById<android.widget.ProgressBar>(R.id.pbMindfulnessProgress)

            if (track != null) {
                tvTitle?.text = track.title
                tvSubtitle?.text = track.artist
            } else {
                tvTitle?.text = "Tap to play music"
                tvSubtitle?.text = "Start a random meditation"
                pb?.progress = 0
            }
        }

        audioViewModel.artworkBitmap.observe(viewLifecycleOwner) { bitmap ->
            val ivArt = binding.root.findViewById<ImageView>(R.id.ivMindfulnessArt)
            if (bitmap != null) {
                ivArt?.setImageBitmap(bitmap)
            } else {
                ivArt?.setBackgroundColor(Color.parseColor("#E0E0E0"))
                ivArt?.setImageResource(0)
            }
        }

        // --- INLINE MEDIA CONTROLS ---
        val cvPlay = binding.root.findViewById<View>(R.id.cvMindfulnessPlay)
        val ivPrev = binding.root.findViewById<View>(R.id.ivMindfulnessPrev)
        val ivNext = binding.root.findViewById<View>(R.id.ivMindfulnessNext)

        cvPlay?.setOnClickListener {
            if (audioViewModel.currentTrack.value != null) {
                audioViewModel.player?.let { player ->
                    if (player.isPlaying) player.pause() else player.play()
                }
            } else {
                playRandomTrack()
            }
        }

        ivNext?.setOnClickListener {
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

        ivPrev?.setOnClickListener {
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

        // Click the whole Card -> Opens NowPlaying
        binding.root.findViewById<View>(R.id.cvMindfulness)?.setOnClickListener {
            if (audioViewModel.currentTrack.value != null) {
                val bottomSheet = NowPlayingFragment()
                bottomSheet.show(parentFragmentManager, "NowPlaying")
            } else {
                playRandomTrack()
                if (currentTrackList.isNotEmpty()) {
                    NowPlayingFragment.currentTrackList = currentTrackList
                    val bottomSheet = NowPlayingFragment()
                    bottomSheet.show(parentFragmentManager, "NowPlaying")
                }
            }
        }
    }

    private fun setupCartObserver() {
        cartViewModel.cartItems.observe(viewLifecycleOwner) { items ->
            if (items.isEmpty()) {
                binding.cvFloatingCart.visibility = View.GONE
            } else {
                binding.cvFloatingCart.visibility = View.VISIBLE

                val totalItems = items.values.sumOf { it.quantity }
                val uniqueItems = items.values.toList()

                binding.tvFloatingCartItems.text = "$totalItems item${if(totalItems > 1) "s" else ""}"

                // Manage stacked images visually based on cart variety
                binding.ivCartImg1.visibility = if (uniqueItems.isNotEmpty()) View.VISIBLE else View.GONE
                binding.ivCartImg2.visibility = if (uniqueItems.size > 1) View.VISIBLE else View.GONE
                binding.ivCartImg3.visibility = if (uniqueItems.size > 2) View.VISIBLE else View.GONE

                // (Optional) You can load actual product thumbnails into ivCartImg using Glide/Picasso here.
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sliderHandler.postDelayed(sliderRunnable, 3000)
    }

    override fun onPause() {
        super.onPause()
        sliderHandler.removeCallbacks(sliderRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressRunnable?.let { progressHandler?.removeCallbacks(it) }
        progressHandler = null
        _binding = null
    }

    private fun fetchAllTracks() {
        db.collectionGroup("tracks")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val allTracks = snapshot.documents.mapNotNull {
                    it.toObject(MeditationTrack::class.java)
                }

                if (allTracks.isNotEmpty()) {
                    currentTrackList = allTracks.shuffled()
                }
            }
    }

    private fun playRandomTrack() {
        if (currentTrackList.isNotEmpty()) {
            val randomTrack = currentTrackList.random()
            audioViewModel.playTrack(randomTrack)
            Toast.makeText(requireContext(), "Playing ${randomTrack.title}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Loading tracks, please wait...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        binding.rvCategories.layoutManager = GridLayoutManager(requireContext(), 2)
    }

    private fun fetchCategories() {
        db.collection("categories").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || snapshot.isEmpty) return@addSnapshotListener
            val categoryList = mutableListOf<Category>()
            for (document in snapshot) categoryList.add(document.toObject(Category::class.java))
            binding.rvCategories.adapter = CategoryAdapter(categoryList)
        }
    }

    private fun fetchFlashDeals() {
        db.collection("promotions").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || snapshot.isEmpty) return@addSnapshotListener
            val flashDeals = mutableListOf<FlashDeal>()
            for (document in snapshot) flashDeals.add(document.toObject(FlashDeal::class.java))
            setupViewPager(flashDeals)
        }
    }

    private fun setupViewPager(flashDeals: List<FlashDeal>) {
        val adapter = FlashDealAdapter(flashDeals)
        binding.vpFlashDeals.adapter = adapter
        binding.vpFlashDeals.clipToPadding = false
        binding.vpFlashDeals.clipChildren = false
        binding.vpFlashDeals.offscreenPageLimit = 3
        binding.vpFlashDeals.getChildAt(0).overScrollMode = RecyclerView.OVER_SCROLL_NEVER

        binding.vpFlashDeals.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                sliderHandler.removeCallbacks(sliderRunnable)
                sliderHandler.postDelayed(sliderRunnable, 4000)
            }
        })
    }

    private fun checkPermissionsAndFetchLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchCurrentLocation()
        } else {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation() {
        binding.tvLocation.text = "Fetching location..."
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) getAddressFromLocation(location.latitude, location.longitude)
            else binding.tvLocation.text = "Select Location"
        }.addOnFailureListener { binding.tvLocation.text = "Failed to get location" }
    }

    private fun getAddressFromLocation(latitude: Double, longitude: Double) {
        Thread {
            try {
                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val subLocality = address.subLocality ?: address.featureName ?: ""
                    val locality = address.locality ?: address.adminArea ?: "Unknown"
                    val displayText = if (subLocality.isNotEmpty() && subLocality != locality) "$subLocality, $locality" else "$locality"
                    requireActivity().runOnUiThread { binding.tvLocation.text = "$displayText" }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread { binding.tvLocation.text = "Lat: ${"%.4f".format(latitude)}, Lng: ${"%.4f".format(longitude)} ▼" }
            }
        }.start()
    }

    private fun openLocationPicker() {
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields).build(requireContext())
        startAutocomplete.launch(intent)
    }
}
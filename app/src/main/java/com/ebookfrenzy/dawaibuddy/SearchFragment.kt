package com.ebookfrenzy.dawaibuddy

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.location.Geocoder
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ebookfrenzy.dawaibuddy.databinding.FragmentSearchBinding
import com.ebookfrenzy.dawaibuddy.models.SharedAudioViewModel
import com.ebookfrenzy.dawaibuddy.objects.MeditationTrack
import com.ebookfrenzy.dawaibuddy.objects.Product
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val db = FirebaseFirestore.getInstance()

    // Shared Audio Player to trigger tracks from the Search page
    private val audioViewModel: SharedAudioViewModel by activityViewModels()

    // Unified Data Source
    private var masterSearchList: MutableList<UnifiedSearchItem> = mutableListOf()
    private lateinit var searchAdapter: UnifiedSearchAdapter

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
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide bottom navigation immediately when view is created to prevent flickering
        setBottomNavigationVisibility(View.GONE)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        if (!Places.isInitialized()) {
            Places.initialize(requireContext(), BuildConfig.MAPS_API_KEY)
        }

        setupRecyclerView()
        setupListeners()

        checkPermissionsAndFetchLocation()
        fetchAllSearchableData()

        // --- Auto-show Keyboard Logic ---
        binding.etSearch.requestFocus()
        binding.etSearch.postDelayed({
            if (isAdded) {
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
            }
        }, 200)
    }

    private fun setBottomNavigationVisibility(visibility: Int) {
        val activity = requireActivity()
        var navFound = false

        // 1. Try finding by common IDs first
        val bottomNavIds = listOf("nav_view", "bottomNavigationView", "bottom_navigation", "bottom_nav")
        for (idName in bottomNavIds) {
            val resId = resources.getIdentifier(idName, "id", activity.packageName)
            if (resId != 0) {
                val view = activity.findViewById<View>(resId)
                if (view != null) {
                    view.visibility = visibility
                    navFound = true
                }
            }
        }

        // 2. Fallback: Search the entire view hierarchy
        if (!navFound) {
            val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
            findAndHideBottomNav(rootView, visibility)
        }
    }

    private fun findAndHideBottomNav(viewGroup: ViewGroup, visibility: Int) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child.javaClass.simpleName.contains("BottomNavigationView")) {
                child.visibility = visibility
                return
            } else if (child is ViewGroup) {
                findAndHideBottomNav(child, visibility)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setBottomNavigationVisibility(View.GONE)
    }

    override fun onPause() {
        super.onPause()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        setBottomNavigationVisibility(View.VISIBLE)
        _binding = null
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.tvLocation.setOnClickListener { openLocationPicker() }
        binding.tvLocationArrow.setOnClickListener { openLocationPicker() }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterResults(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupRecyclerView() {
        searchAdapter = UnifiedSearchAdapter(emptyList()) { item ->
            when (item.type) {
                SearchItemType.PRODUCT -> {
                    // FIXED: Navigate using findNavController instead of Intent
                    val bundle = Bundle().apply {
                        putString("PRODUCT_ID", item.id)
                    }
                    findNavController().navigate(R.id.productDetailFragment, bundle)
                }
                SearchItemType.MUSIC -> {
                    item.originalTrack?.let { track ->
                        val visibleTracks = searchAdapter.getItems()
                            .filter { it.type == SearchItemType.MUSIC }
                            .mapNotNull { it.originalTrack }

                        NowPlayingFragment.currentTrackList = visibleTracks
                        audioViewModel.playTrack(track)

                        val bottomSheet = NowPlayingFragment()
                        bottomSheet.show(parentFragmentManager, "NowPlaying")
                    } ?: Toast.makeText(requireContext(), "Error loading track", Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.rvSearchResults.adapter = searchAdapter
    }

    private fun fetchAllSearchableData() {
        masterSearchList.clear()

        // 1. Fetch Products
        db.collectionGroup("products").get().addOnSuccessListener { snapshot ->
            for (document in snapshot) {
                val product = document.toObject(Product::class.java)
                masterSearchList.add(
                    UnifiedSearchItem(
                        id = product.id.ifEmpty { document.id },
                        title = product.name,
                        subtitle = "Product • ${product.categoryName}",
                        type = SearchItemType.PRODUCT,
                        imageUrl = product.imageUrl
                    )
                )
            }
            if (_binding != null) filterResults(binding.etSearch.text.toString())
        }

        // 2. Fetch Meditation Tracks
        db.collectionGroup("tracks").get().addOnSuccessListener { snapshot ->
            for (document in snapshot) {
                val track = document.toObject(MeditationTrack::class.java)
                masterSearchList.add(
                    UnifiedSearchItem(
                        id = track.id.ifEmpty { document.id },
                        title = track.title,
                        subtitle = "Meditation • ${track.artist}",
                        type = SearchItemType.MUSIC,
                        imageUrl = "",
                        originalTrack = track
                    )
                )
            }
            if (_binding != null) filterResults(binding.etSearch.text.toString())
        }
    }

    private fun filterResults(query: String) {
        if (query.trim().isEmpty()) {
            binding.rvSearchResults.visibility = View.GONE
            binding.llEmptyState.visibility = View.VISIBLE
            binding.tvEmptyStateText.text = "Type to start searching"
            searchAdapter.updateData(emptyList())
            return
        }

        binding.rvSearchResults.visibility = View.VISIBLE
        binding.llEmptyState.visibility = View.GONE

        val lowerQuery = query.lowercase().trim()

        val exactMatches = masterSearchList.filter { it.title.lowercase() == lowerQuery }
        val containsMatches = masterSearchList.filter {
            it.title.lowercase().contains(lowerQuery) && it.title.lowercase() != lowerQuery
        }
        val fuzzyMatches = masterSearchList.filter { item ->
            val words = item.title.lowercase().split(" ")
            words.any { it.startsWith(lowerQuery) } &&
                    !exactMatches.contains(item) &&
                    !containsMatches.contains(item)
        }

        val combinedResults = (exactMatches + containsMatches + fuzzyMatches).distinct()

        if (combinedResults.isEmpty()) {
            binding.rvSearchResults.visibility = View.GONE
            binding.llEmptyState.visibility = View.VISIBLE
            binding.tvEmptyStateText.text = "No results found for '$query'"
        } else {
            searchAdapter.updateData(combinedResults)
        }
    }

    // --- LOCATION LOGIC ---
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
                    val displayText = if (subLocality.isNotEmpty() && subLocality != locality) "$subLocality, $locality" else locality
                    requireActivity().runOnUiThread { if (_binding != null) binding.tvLocation.text = displayText }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread { if (_binding != null) binding.tvLocation.text = "Lat: ${"%.4f".format(latitude)}, Lng: ${"%.4f".format(longitude)} ▼" }
            }
        }.start()
    }

    private fun openLocationPicker() {
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields).build(requireContext())
        startAutocomplete.launch(intent)
    }
}

// --- UNIFIED SEARCH DATA MODELS & ADAPTER ---

enum class SearchItemType { PRODUCT, MUSIC }

data class UnifiedSearchItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: SearchItemType,
    val imageUrl: String = "",
    val originalTrack: MeditationTrack? = null
)

class UnifiedSearchAdapter(
    private var items: List<UnifiedSearchItem>,
    private val onItemClick: (UnifiedSearchItem) -> Unit
) : RecyclerView.Adapter<UnifiedSearchAdapter.SearchViewHolder>() {

    fun getItems(): List<UnifiedSearchItem> = items

    inner class SearchViewHolder(
        val rootView: LinearLayout,
        val tvTitle: TextView,
        val tvSubtitle: TextView,
        val icon: ImageView
    ) : RecyclerView.ViewHolder(rootView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val context = parent.context
        val dpToPx = { dp: Int -> (dp * context.resources.displayMetrics.density).toInt() }

        val isNightMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val primaryTextColor = if (isNightMode) Color.WHITE else Color.BLACK
        val secondaryTextColor = if (isNightMode) Color.parseColor("#B3B3B3") else Color.GRAY
        val cardBgColor = if (isNightMode) Color.parseColor("#2C2C2C") else Color.parseColor("#F5F5F5")

        val root = LinearLayout(context).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            gravity = Gravity.CENTER_VERTICAL

            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            isFocusable = true
        }

        val card = CardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))
            radius = dpToPx(8).toFloat()
            setCardBackgroundColor(cardBgColor)
            cardElevation = 0f
        }

        val image = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        card.addView(image)

        val textContainer = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dpToPx(16)
            }
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(context).apply {
            textSize = 16f
            setTextColor(primaryTextColor)
            setTypeface(null, Typeface.BOLD)
        }

        val subtitle = TextView(context).apply {
            textSize = 12f
            setTextColor(secondaryTextColor)
            setPadding(0, dpToPx(2), 0, 0)
        }

        textContainer.addView(title)
        textContainer.addView(subtitle)

        root.addView(card)
        root.addView(textContainer)

        return SearchViewHolder(root, title, subtitle, image)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        holder.tvSubtitle.text = item.subtitle

        if (item.type == SearchItemType.PRODUCT) {
            Glide.with(holder.itemView.context)
                .load(item.imageUrl.takeIf { it.isNotBlank() } ?: R.drawable.health)
                .into(holder.icon)
        } else {
            Glide.with(holder.itemView.context)
                .load(R.drawable.meditation)
                .into(holder.icon)
        }

        holder.rootView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<UnifiedSearchItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
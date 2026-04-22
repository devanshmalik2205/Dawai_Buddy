package com.ebookfrenzy.dawaibuddy

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.ebookfrenzy.dawaibuddy.databinding.FragmentMainPageBinding
import com.ebookfrenzy.dawaibuddy.objects.Category
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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        if (!Places.isInitialized()) {
            Places.initialize(requireContext(), BuildConfig.MAPS_API_KEY)
        }

        binding.tvLocation.setOnClickListener {
            openLocationPicker()
        }

        setupRecyclerView()
        fetchCategories()
        fetchFlashDeals()
        fetchDailyMindfulness()

        checkPermissionsAndFetchLocation()
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
        _binding = null
    }

    private fun setupRecyclerView() {
        binding.rvCategories.layoutManager = GridLayoutManager(requireContext(), 2)
    }

    private fun fetchCategories() {
        db.collection("categories")
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) return@addOnSuccessListener

                val categoryList = mutableListOf<Category>()
                for (document in result) {
                    val category = document.toObject(Category::class.java)
                    categoryList.add(category)
                }
                binding.rvCategories.adapter = CategoryAdapter(categoryList)
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error fetching categories", e)
            }
    }

    private fun fetchFlashDeals() {
        db.collection("promotions")
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) return@addOnSuccessListener

                val flashDeals = mutableListOf<FlashDeal>()
                for (document in result) {
                    val deal = document.toObject(FlashDeal::class.java)
                    flashDeals.add(deal)
                }

                setupViewPager(flashDeals)
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error fetching flash deals", e)
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

    private fun fetchDailyMindfulness() {
        db.collection("daily_mindfulness").document("today_feature")
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val title = document.getString("title") ?: ""
                    val duration = document.getString("duration") ?: ""
                    val subtitle = document.getString("subtitle") ?: ""
                    val backgroundUrl = document.getString("backgroundUrl") ?: ""

                    binding.tvZenTitle.text = title
                    binding.tvZenSubtitle.text = "$duration • $subtitle"

                    if (backgroundUrl.isNotEmpty()) {
                        Glide.with(requireContext()).load(backgroundUrl).into(binding.ivMindfulnessBg)
                    }
                }
            }
    }

    private fun checkPermissionsAndFetchLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchCurrentLocation()
        } else {
            locationPermissionRequest.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation() {
        binding.tvLocation.text = "Fetching location..."

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    getAddressFromLocation(location.latitude, location.longitude)
                } else {
                    binding.tvLocation.text = "Select Location"
                }
            }
            .addOnFailureListener {
                binding.tvLocation.text = "Failed to get location"
            }
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

                    val displayText = if (subLocality.isNotEmpty() && subLocality != locality) {
                        "$subLocality, $locality"
                    } else {
                        "$locality"
                    }

                    requireActivity().runOnUiThread {
                        binding.tvLocation.text = "$displayText"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    binding.tvLocation.text = "Lat: ${"%.4f".format(latitude)}, Lng: ${"%.4f".format(longitude)} ▼"
                }
            }
        }.start()
    }

    private fun openLocationPicker() {
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .build(requireContext())
        startAutocomplete.launch(intent)
    }

}
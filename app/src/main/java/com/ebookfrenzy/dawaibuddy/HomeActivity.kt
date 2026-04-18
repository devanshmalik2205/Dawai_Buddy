package com.ebookfrenzy.dawaibuddy

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ebookfrenzy.dawaibuddy.databinding.ActivityHomeBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Launcher for requesting Location Permissions
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                    permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Permission granted, fetch the location
                fetchCurrentLocation()
            }
            else -> {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
                binding.tvLocation.text = "Location Denied ▼"
            }
        }
    }

    // Launcher for handling the Google Places Autocomplete Result
    private val startAutocomplete = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data
            if (intent != null) {
                val place = Autocomplete.getPlaceFromIntent(intent)
                // Update the TextView with the selected place from the map
                binding.tvLocation.text = "${place.name} ▼"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // IMPORTANT: Initialize Google Places (Requires a Google Maps API Key from Google Cloud Console)
        // If you don't have one yet, the app will compile, but the location picker won't open properly.
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, "AIzaSyCyQghioHuvWzXnO5j745IHItXslY-7fH0")
        }

        // When user clicks the location text, open the map search/picker
        binding.tvLocation.setOnClickListener {
            openLocationPicker()
        }

        // Check permissions and get current location on startup
        checkPermissionsAndFetchLocation()
    }

    private fun checkPermissionsAndFetchLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchCurrentLocation()
        } else {
            // Request permissions
            locationPermissionRequest.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation() {
        binding.tvLocation.text = "Fetching location... ▼"

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    // Convert latitude and longitude into an address string
                    getAddressFromLocation(location.latitude, location.longitude)
                } else {
                    binding.tvLocation.text = "Select Location ▼"
                }
            }
            .addOnFailureListener {
                binding.tvLocation.text = "Failed to get location ▼"
            }
    }

    private fun getAddressFromLocation(latitude: Double, longitude: Double) {
        // Run in a background thread to prevent UI freezing
        Thread {
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)

                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    // Format the display text (e.g., "Vigyan Vihar, Delhi")
                    val subLocality = address.subLocality ?: address.featureName ?: ""
                    val locality = address.locality ?: address.adminArea ?: "Unknown"

                    val displayText = if (subLocality.isNotEmpty() && subLocality != locality) {
                        "$subLocality, $locality"
                    } else {
                        "$locality"
                    }

                    // Update UI on the main thread
                    runOnUiThread {
                        binding.tvLocation.text = displayText
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to coordinates if Geocoder fails
                runOnUiThread {
                    binding.tvLocation.text = "Lat: ${"%.4f".format(latitude)}, Lng: ${"%.4f".format(longitude)}"
                }
            }
        }.start()
    }

    private fun openLocationPicker() {
        // Specify which details you want the Map Picker to return
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG)

        // Launch the Google Places Autocomplete intent (Overlay mode acts like a popup)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .build(this)

        startAutocomplete.launch(intent)
    }
}
package com.ebookfrenzy.dawaibuddy.host_activities

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.ebookfrenzy.dawaibuddy.R
import com.ebookfrenzy.dawaibuddy.databinding.ActivityHomeBinding
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    // 1. Define the Google Health permissions your app needs
    private val healthPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )

    // 2. Setup the permission launcher that shows the Google Health Auth UI
    private val requestPermissionsLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        if (grantedPermissions.containsAll(healthPermissions)) {
            Toast.makeText(this, "Health Connect permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Health permissions denied. Wellness features may be limited.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Find the NavHostFragment. We use supportFragmentManager instead of findNavController
        // directly from the Activity to avoid crashes with FragmentContainerView
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        val navController = navHostFragment.navController

        // This ONE line magically connects your bottom navigation view to your home_graph.xml.
        binding.bottomNavigation.setupWithNavController(navController)

        // --- DYNAMIC BOTTOM NAV COLORS ---
        // Change colors dynamically based on the current active fragment
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val activeColor = when (destination.id) {
                R.id.nav_home -> Color.parseColor("#4CAF50") // Green for Home
                R.id.nav_wellness -> Color.parseColor("#6B4EE6") // Purple for Wellness
                R.id.nav_meditate -> Color.parseColor("#FF9800") // Orange for Meditate
                R.id.nav_profile -> Color.parseColor("#2196F3") // Blue for Profile
                else -> Color.parseColor("#4CAF50") // Default fallback
            }

            // The color used for all unselected tabs
            val inactiveColor = Color.parseColor("#888888")

            // Create a dynamic ColorStateList mapping checked state to the activeColor
            val dynamicColorStateList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked), // Selected state
                    intArrayOf(-android.R.attr.state_checked) // Unselected state
                ),
                intArrayOf(
                    activeColor,
                    inactiveColor
                )
            )

            // Apply it to both icons and text
            binding.bottomNavigation.itemIconTintList = dynamicColorStateList
            binding.bottomNavigation.itemTextColor = dynamicColorStateList

            // --- NEW: Dynamic Active Indicator (Background Pill) Color ---
            // Apply 20% opacity (approx 51 out of 255) to the active color for the background pill
            val indicatorColor = Color.argb(
                51,
                Color.red(activeColor),
                Color.green(activeColor),
                Color.blue(activeColor)
            )

            val indicatorColorStateList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(indicatorColor, Color.TRANSPARENT)
            )

            binding.bottomNavigation.itemActiveIndicatorColor = indicatorColorStateList
        }

        // 3. Trigger the Google Health Connect authentication/permission prompt
        checkAndRequestHealthConnectPermissions()
    }

    private fun checkAndRequestHealthConnectPermissions() {
        val availabilityStatus = HealthConnectClient.getSdkStatus(this, "com.google.android.apps.healthdata")

        if (availabilityStatus == HealthConnectClient.SDK_AVAILABLE) {
            val healthConnectClient = HealthConnectClient.getOrCreate(this)

            lifecycleScope.launch {
                val granted = healthConnectClient.permissionController.getGrantedPermissions()
                // If permissions aren't granted yet, pop up the Health Connect consent screen
                if (!granted.containsAll(healthPermissions)) {
                    requestPermissionsLauncher.launch(healthPermissions)
                }
            }
        } else {
            Toast.makeText(this, "Google Health Connect is not installed on this device.", Toast.LENGTH_LONG).show()
        }
    }
}
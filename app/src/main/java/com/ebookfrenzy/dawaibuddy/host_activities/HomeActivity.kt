package com.ebookfrenzy.dawaibuddy.host_activities

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
        // Clicking a tab (e.g., ID nav_wellness) will automatically route to the fragment
        // with the matching ID in home_graph.xml. It also handles the back-stack and
        // highlighting the correct tab icon perfectly!
        binding.bottomNavigation.setupWithNavController(navController)

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
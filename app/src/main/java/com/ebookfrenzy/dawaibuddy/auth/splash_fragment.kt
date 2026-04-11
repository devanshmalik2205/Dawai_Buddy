package com.ebookfrenzy.dawaibuddy.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ebookfrenzy.dawaibuddy.HomeActivity
import com.ebookfrenzy.dawaibuddy.R
import com.ebookfrenzy.dawaibuddy.databinding.FragmentSplashFragmentBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class splash_fragment : Fragment() {
    private var _binding: FragmentSplashFragmentBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Handler(Looper.getMainLooper()).postDelayed({
            if (isAdded) {
                val auth = FirebaseAuth.getInstance()
                val currentUser = auth.currentUser

                if (currentUser != null) {
                    // 1. User is authenticated with Phone Auth.
                    // Let's verify if they also completed their profile in Firestore.
                    val db = FirebaseFirestore.getInstance()
                    db.collection("users").document(currentUser.uid).get()
                        .addOnSuccessListener { document ->
                            if (!isAdded) return@addOnSuccessListener

                            if (document.exists()) {
                                // Profile exists! Navigate to Home.
                                launchHomeActivity()
                            } else {
                                // Logged in, but profile is missing (e.g., they closed the app on the TUAY screen).
                                // Sign them out and send back to login to restart the flow safely.
                                auth.signOut()
                                findNavController().navigate(R.id.action_splash_fragment_to_login_fragment)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("SplashFragment", "Firestore check failed", e)
                            if (isAdded) findNavController().navigate(R.id.action_splash_fragment_to_login_fragment)
                        }
                } else {
                    // 2. No user logged in, proceed to the Login screen
                    findNavController().navigate(R.id.action_splash_fragment_to_login_fragment)
                }
            }
        }, 2000)
    }

    private fun launchHomeActivity() {
        try {
            // Launch HomeActivity directly from the Fragment
            val intent = Intent(requireContext(), HomeActivity::class.java)
            // Clear the backstack so the user can't navigate back to auth
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)

            // Finish the hosting AuthActivity
            requireActivity().finish()
        } catch (e: Exception) {
            // Catch errors (like missing AndroidManifest declaration)
            Log.e("SplashFragment", "Failed to launch HomeActivity", e)
            Toast.makeText(requireContext(), "Error: Check if HomeActivity is in AndroidManifest.xml!", Toast.LENGTH_LONG).show()

            // Fallback to login so the app doesn't freeze
            findNavController().navigate(R.id.action_splash_fragment_to_login_fragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.ebookfrenzy.dawaibuddy.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ebookfrenzy.dawaibuddy.host_activities.AuthActivity
import com.ebookfrenzy.dawaibuddy.R
import com.ebookfrenzy.dawaibuddy.databinding.FragmentProfileBinding
import com.ebookfrenzy.dawaibuddy.objects.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvGreeting.text = "Hi..."
        binding.tvProfileName.text = "Loading..."

        // Initial Data Fetch
        fetchUserData()
        fetchOrderCount()

        binding.ivBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // Logic to navigate to Order History
        val navigateToHistory = View.OnClickListener {
            findNavController().navigate(R.id.action_nav_profile_to_orderHistoryFragment)
        }

        binding.cvOrdersStat.setOnClickListener(navigateToHistory)
        binding.llPrescriptionHistory.setOnClickListener(navigateToHistory)

        binding.llLogOut.setOnClickListener {
            auth.signOut()
            Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()
            val intent = Intent(requireContext(), AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }

        binding.llMyAddresses.setOnClickListener {
            Toast.makeText(requireContext(), "Addresses feature coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.llPaymentMethods.setOnClickListener {
            Toast.makeText(requireContext(), "Payments feature coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchOrderCount() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("orders")
                .whereEqualTo("userId", currentUser.uid)
                .get()
                .addOnSuccessListener { result ->
                    val count = result.size()
                    binding.tvOrderCount.text = count.toString()
                }
                .addOnFailureListener { e ->
                    Log.e("ProfileFragment", "Error fetching order count", e)
                    binding.tvOrderCount.text = "0"
                }
        }
    }

    private fun fetchUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val user = document.toObject(User::class.java)
                        if (user != null) {
                            updateUIWithUserData(user)
                        }
                    } else {
                        binding.tvGreeting.text = "Hi there"
                        binding.tvProfileName.text = "User"
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("ProfileFragment", "Error fetching user data", exception)
                    binding.tvGreeting.text = "Hi"
                    binding.tvProfileName.text = "Error"
                }
        } else {
            val intent = Intent(requireContext(), AuthActivity::class.java)
            startActivity(intent)
            requireActivity().finish()
        }
    }

    private fun updateUIWithUserData(user: User) {
        val fullName = user.name.ifEmpty { "User" }
        val firstName = fullName.split(" ").firstOrNull() ?: "User"
        binding.tvGreeting.text = "Hi $firstName"
        binding.tvProfileName.text = fullName
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
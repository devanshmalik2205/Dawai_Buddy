package com.ebookfrenzy.dawaibuddy

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
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

        fetchUserData()

        binding.ivBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.llLogOut.setOnClickListener {
            auth.signOut()
            Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()

            val intent = Intent(requireContext(), AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }

        binding.llMyAddresses.setOnClickListener {
            Toast.makeText(requireContext(), "Addresses Clicked", Toast.LENGTH_SHORT).show()
        }

        binding.llPaymentMethods.setOnClickListener {
            Toast.makeText(requireContext(), "Payments Clicked", Toast.LENGTH_SHORT).show()
        }

        binding.llPrescriptionHistory.setOnClickListener {
            Toast.makeText(requireContext(), "History Clicked", Toast.LENGTH_SHORT).show()
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
                        Log.d("ProfileActivity", "No such document exists")
                        binding.tvGreeting.text = "Hi there"
                        binding.tvProfileName.text = "New User"
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("ProfileActivity", "Error fetching user data", exception)
                    Toast.makeText(requireContext(), "Failed to load profile data", Toast.LENGTH_SHORT).show()
                    binding.tvGreeting.text = "Hi"
                    binding.tvProfileName.text = "Error loading name"
                }
        } else {
            Toast.makeText(requireContext(), "Please log in again", Toast.LENGTH_SHORT).show()
            startActivity(Intent(requireContext(), AuthActivity::class.java))
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
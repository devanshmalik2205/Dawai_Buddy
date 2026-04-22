package com.ebookfrenzy.dawaibuddy.auth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ebookfrenzy.dawaibuddy.AuthActivity
import com.ebookfrenzy.dawaibuddy.objects.User
import com.ebookfrenzy.dawaibuddy.databinding.FragmentTuayFragmentBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class tuay_fragment : Fragment() {

    private var _binding: FragmentTuayFragmentBinding? = null
    private val binding get() = _binding!!

    // Firebase instances
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTuayFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.btnSaveProfile.setOnClickListener {
            // Check if user is properly authenticated first
            val firebaseUser = auth.currentUser
            if (firebaseUser == null) {
                Toast.makeText(requireContext(), "Authentication Error. Please try logging in again.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Gather input data safely
            val nameStr = binding.etName.text.toString().trim()
            val ageStr = binding.etAge.text.toString().trim()
            val weightStr = binding.etWeight.text.toString().trim()
            val heightStr = binding.etHeight.text.toString().trim()
            val conditions = binding.etConditions.text.toString().trim()
            val medicines = binding.etMedicines.text.toString().trim()

            // Find selected gender safely using binding.root
            val selectedGenderId = binding.rgGender.checkedRadioButtonId
            val gender = if (selectedGenderId != -1) {
                binding.root.findViewById<RadioButton>(selectedGenderId)?.text?.toString() ?: ""
            } else {
                ""
            }

            // Simple validation to ensure Name, Age and Gender are provided at minimum
            if (nameStr.isEmpty() || ageStr.isEmpty() || gender.isEmpty()) {
                Toast.makeText(requireContext(), "Please provide at least Name, Age and Gender", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show a loading/saving message
            Toast.makeText(requireContext(), "Saving profile...", Toast.LENGTH_SHORT).show()
            binding.btnSaveProfile.isEnabled = false // Disable button to prevent double-clicks

            // Construct the User object including Firebase UID and Phone Number
            val currentUser = User(
                uid = firebaseUser.uid,
                phoneNumber = firebaseUser.phoneNumber ?: "",
                name = nameStr,
                age = ageStr.toIntOrNull(),
                gender = gender,
                weight = weightStr.toFloatOrNull(),
                height = heightStr.toFloatOrNull(),
                medicalConditions = conditions,
                medicinesTaken = medicines,
                isNewUser = false // Mark as completed
            )

            // Save directly to Firestore under the "users" collection, using their Auth UID as the document ID
            db.collection("users").document(firebaseUser.uid)
                .set(currentUser)
                .addOnSuccessListener {
                    Log.d("TuayFragment", "Successfully wrote User data to Firestore")
                    Toast.makeText(requireContext(), "Profile Saved!", Toast.LENGTH_SHORT).show()

                    // Navigate directly to Home only after a confirmed save
                    (requireActivity() as AuthActivity).navigateToHome()
                }
                .addOnFailureListener { e ->
                    Log.e("TuayFragment", "FAILED to write to Firestore", e)
                    Toast.makeText(requireContext(), "Error saving profile: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.btnSaveProfile.isEnabled = true // Re-enable button so they can try again
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
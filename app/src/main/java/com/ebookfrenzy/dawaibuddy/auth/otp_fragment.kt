package com.ebookfrenzy.dawaibuddy.auth

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.ebookfrenzy.dawaibuddy.host_activities.HomeActivity
import com.ebookfrenzy.dawaibuddy.R
import com.ebookfrenzy.dawaibuddy.databinding.FragmentOtpFragmentBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class otp_fragment : Fragment() {
    private var _binding: FragmentOtpFragmentBinding? = null
    private val binding get() = _binding!!

    // Firebase Auth instance
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOtpFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // Get both the phone number for display and the verificationId from Firebase
        val phoneNumber = arguments?.getString("PHONE_NUMBER") ?: ""
        val verificationId = arguments?.getString("verificationId")

        // Directly accessing tvSubtitle and other views via binding
        binding.tvSubtitle.text = "Enter the 6-digit code sent to +91 $phoneNumber"

        binding.btnVerify.setOnClickListener {
            val otp = binding.etOtp.text.toString().trim()

            // Note: Firebase uses 6-digit OTPs!
            if (otp.length == 6) {
                if (verificationId != null) {
                    Toast.makeText(requireContext(), "Verifying...", Toast.LENGTH_SHORT).show()
                    verifyOtpWithFirebase(verificationId, otp)
                } else {
                    Toast.makeText(requireContext(), "Error: Missing Verification ID", Toast.LENGTH_SHORT).show()
                }
            } else {
                binding.etOtp.error = "Please enter the 6-digit OTP"
            }
        }
    }

    private fun verifyOtpWithFirebase(verificationId: String, code: String) {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)

        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    val user = task.result?.user
                    checkIfUserExistsInFirestore(user?.uid)
                } else {
                    // Sign in failed
                    Toast.makeText(requireContext(), "Invalid OTP. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun checkIfUserExistsInFirestore(uid: String?) {
        if (uid == null) return

        val db = FirebaseFirestore.getInstance()

        // Check the "users" collection to see if this user profile already exists
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // User exists, they have already filled out the TUAY form. Go to Home!
                    Toast.makeText(requireContext(), "Welcome back!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(requireContext(), HomeActivity::class.java))
                    requireActivity().finish()
                } else {
                    // New user, go to Tell Us About Yourself Fragment
                    Toast.makeText(requireContext(), "Phone Verified! Let's setup your profile.", Toast.LENGTH_LONG).show()
                    findNavController().navigate(R.id.action_otp_fragment_to_tuay_fragment)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error connecting to database: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
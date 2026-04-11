package com.ebookfrenzy.dawaibuddy.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.ebookfrenzy.dawaibuddy.R
import com.ebookfrenzy.dawaibuddy.databinding.FragmentLoginFragmentBinding
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class login_fragment : Fragment() {
    private var _binding: FragmentLoginFragmentBinding? = null
    private val binding get() = _binding!!

    // 1. Added Firebase variables
    private lateinit var auth: FirebaseAuth
    private var verificationId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 2. Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Load and animate the GIF using Glide
        Glide.with(this)
            .asGif()
            .load(R.drawable.bg_login)
            .into(binding.ivLoginGif)

        binding.btnContinue.setOnClickListener {
            val phone = binding.etPhoneNumber.text.toString().trim()

            if (phone.length == 10) {
                // 3. Append country code (Assuming +91 for India)
                val phoneNumberWithCode = "+91$phone"

                Toast.makeText(requireContext(), "Sending OTP...", Toast.LENGTH_SHORT).show()

                // 4. Trigger Firebase OTP instead of navigating immediately
                sendVerificationCode(phoneNumberWithCode)
            } else {
                binding.etPhoneNumber.error = "Please enter a valid 10-digit number"
            }
        }
    }

    // 5. Function to start the Firebase phone verification process
    private fun sendVerificationCode(number: String) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(number) // Phone number to verify
            .setTimeout(60L, TimeUnit.SECONDS) // Timeout and unit
            .setActivity(requireActivity()) // Activity (for callback binding)
            .setCallbacks(callbacks) // OnVerificationStateChangedCallbacks
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    // 6. Callbacks to handle Firebase responses
    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Auto-retrieval succeeded (we will handle the actual sign-in in the OTP fragment)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            // Handle error (e.g., invalid phone number, quota exceeded)
            Toast.makeText(requireContext(), "Verification failed: ${e.message}", Toast.LENGTH_LONG).show()
        }

        override fun onCodeSent(
            verId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            // 7. Save verification ID and navigate ONLY when the code is successfully sent
            verificationId = verId

            val bundle = Bundle().apply {
                putString("PHONE_NUMBER", binding.etPhoneNumber.text.toString().trim())
                putString("verificationId", verificationId) // Pass to OTP fragment
            }
            findNavController().navigate(R.id.action_login_fragment_to_otp_fragment, bundle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
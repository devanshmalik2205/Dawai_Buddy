package com.ebookfrenzy.dawaibuddy

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Toast.makeText
import androidx.navigation.fragment.findNavController
import com.ebookfrenzy.dawaibuddy.databinding.FragmentOtpFragmentBinding

class otp_fragment : Fragment() {
    private var _binding: FragmentOtpFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOtpFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val phoneNumber = arguments?.getString("PHONE_NUMBER") ?: ""

        // Directly accessing tvSubtitle and other views via binding
        binding.tvSubtitle.text = "Enter the 4-digit code sent to +91 $phoneNumber"

        binding.btnVerify.setOnClickListener {
            val otp = binding.etOtp.text.toString().trim()

            if (otp.length == 4) {
                makeText(requireContext(), "Login Successful! Welcome to Dawai Buddy", Toast.LENGTH_LONG).show()

                // Simulate fetching the User from your database/backend using the phone number
                // For now, we will create a User object and assume it's a new user to test the flow
                val currentUser = User(phoneNumber = phoneNumber, isNewUser = true)

                if (currentUser.isNewUser) {
                    // Navigate to Tell Us About Yourself Fragment
                    // Note: Ensure action_otp_fragment_to_tuay_fragment is added to your auth.xml graph
                    findNavController().navigate(R.id.action_otp_fragment_to_tuay_fragment)
                } else {
                    // Skip TUAY and go straight to Home
                    // findNavController().navigate(R.id.action_otp_fragment_to_home_fragment)
                    makeText(requireContext(), "Welcome back!", Toast.LENGTH_SHORT).show()
                }

            } else {
                binding.etOtp.error = "Please enter the 4-digit OTP"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
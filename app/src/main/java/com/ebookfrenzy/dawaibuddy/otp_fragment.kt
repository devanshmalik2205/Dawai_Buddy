package com.ebookfrenzy.dawaibuddy

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Toast.makeText
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
                // Navigate to MainActivity here
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
package com.ebookfrenzy.dawaibuddy

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.ebookfrenzy.dawaibuddy.databinding.FragmentLoginFragmentBinding

class login_fragment : Fragment() {
    private var _binding: FragmentLoginFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load and animate the GIF using Glide
        // Replace 'your_gif_file' with the actual name of your GIF in res/drawable
        Glide.with(this)
            .asGif()
            .load(R.drawable.bg_login)
            .into(binding.ivLoginGif)

        // Using binding to access the views directly!
        binding.btnContinue.setOnClickListener {
            val phone = binding.etPhoneNumber.text.toString().trim()

            if (phone.length == 10) {
                val bundle = Bundle().apply {
                    putString("PHONE_NUMBER", phone)
                }
                findNavController().navigate(R.id.action_login_fragment_to_otp_fragment, bundle)
            } else {
                binding.etPhoneNumber.error = "Please enter a valid 10-digit number"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.ebookfrenzy.dawaibuddy

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import com.ebookfrenzy.dawaibuddy.databinding.FragmentTuayFragmentBinding

class tuay_fragment : Fragment() {
    private var _binding: FragmentTuayFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTuayFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSaveProfile.setOnClickListener {
            // Gather input data safely
            val ageStr = binding.etAge.text.toString().trim()
            val weightStr = binding.etWeight.text.toString().trim()
            val heightStr = binding.etHeight.text.toString().trim()
            val conditions = binding.etConditions.text.toString().trim()
            val medicines = binding.etMedicines.text.toString().trim()

            // Find selected gender
            val selectedGenderId = binding.rgGender.checkedRadioButtonId
            val gender = if (selectedGenderId != -1) {
                view.findViewById<RadioButton>(selectedGenderId).text.toString()
            } else {
                ""
            }

            // Simple validation to ensure Age is provided at minimum
            if (ageStr.isEmpty() || gender.isEmpty()) {
                Toast.makeText(requireContext(), "Please provide at least Age and Gender", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Construct the User object
            val currentUser = User(
                age = ageStr.toIntOrNull(),
                gender = gender,
                weight = weightStr.toFloatOrNull(),
                height = heightStr.toFloatOrNull(),
                medicalConditions = conditions,
                medicinesTaken = medicines,
                isNewUser = false // Mark as completed!
            )

            // TODO: Save 'currentUser' to SharedPreferences, Room Database, or Firebase here

            Toast.makeText(requireContext(), "Profile Saved!", Toast.LENGTH_SHORT).show()

            // Navigate to Home Fragment (We'll add this action to the nav graph later)
            // findNavController().navigate(R.id.action_tuay_fragment_to_home_fragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
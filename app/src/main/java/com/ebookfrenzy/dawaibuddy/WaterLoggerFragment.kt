package com.ebookfrenzy.dawaibuddy

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.ebookfrenzy.dawaibuddy.databinding.FragmentWaterLoggerBinding
import com.ebookfrenzy.dawaibuddy.objects.WellnessData
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WaterLoggerFragment : Fragment() {

    private var _binding: FragmentWaterLoggerBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Daily Goal Configuration
    private val dailyGoalMl = 2100f // 2.1 Liters

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWaterLoggerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // HIDE THE BOTTOM NAVIGATION MENU
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav?.visibility = View.GONE

        binding.ivBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.cvAddWater.setOnClickListener {
            showAddDrinkBottomSheet()
        }

        binding.cvHistory.setOnClickListener {
            showHistoryBottomSheet()
        }

        fetchTodayWaterData()
    }

    private fun fetchTodayWaterData() {
        val user = auth.currentUser ?: return
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        db.collection("users").document(user.uid).collection("wellness_data")
            .whereEqualTo("date", todayStr)
            .whereEqualTo("type", "water") // Fetching from the unified data collection
            .addSnapshotListener { snapshot, error ->
                if (_binding == null) return@addSnapshotListener // SAFETY CHECK (Prevents NPE Crash)

                if (error != null) {
                    Toast.makeText(context, "Failed to load water data.", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                var totalMl = 0
                snapshot?.documents?.forEach { doc ->
                    val entry = doc.toObject(WellnessData::class.java)
                    if (entry != null) totalMl += entry.amountMl
                }

                updateUI(totalMl)
            }
    }

    private fun updateUI(totalMl: Int) {
        val percentage = ((totalMl / dailyGoalMl) * 100).toInt()
        val liters = totalMl / 1000f
        val goalLiters = dailyGoalMl / 1000f

        binding.tvPercentage.text = percentage.toString()
        binding.tvVolume.text = String.format(Locale.getDefault(), "%.1f of %.1f l", liters, goalLiters)
    }

    private fun showAddDrinkBottomSheet() {
        val bottomSheet = BottomSheetDialog(requireContext())

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#4A72FF"))
            setPadding(0, 40, 0, 40)
        }

        val title = TextView(requireContext()).apply {
            text = "Add drink"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(40, 20, 40, 40)
        }
        container.addView(title)

        val outValue = TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)

        val options = listOf(50, 100, 250, 300, 500)
        options.forEach { amount ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(40, 30, 40, 30)
                gravity = android.view.Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(requireContext(), outValue.resourceId)
                isClickable = true
                setOnClickListener {
                    logWaterEntry(amount)
                    bottomSheet.dismiss()
                }
            }

            val icon = TextView(requireContext()).apply {
                text = "\uD83C\uDF76"
                textSize = 24f
                setPadding(0, 0, 40, 0)
            }

            val text = TextView(requireContext()).apply {
                text = "$amount ml"
                textSize = 16f
                setTextColor(Color.WHITE)
            }

            row.addView(icon)
            row.addView(text)
            container.addView(row)
        }

        bottomSheet.setContentView(container)
        bottomSheet.show()
    }

    private fun showHistoryBottomSheet() {
        val user = auth.currentUser ?: return
        val bottomSheet = BottomSheetDialog(requireContext())

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(40, 40, 40, 40)
        }

        val title = TextView(requireContext()).apply {
            text = "History"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 40)
        }
        container.addView(title)

        db.collection("users").document(user.uid).collection("wellness_data")
            .whereEqualTo("type", "water")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    val emptyState = TextView(requireContext()).apply {
                        text = "No history yet."
                        setTextColor(Color.LTGRAY)
                    }
                    container.addView(emptyState)
                } else {
                    // Sort locally to avoid needing a custom composite index in Firestore
                    val entries = snapshot.documents
                        .mapNotNull { it.toObject(WellnessData::class.java) }
                        .sortedByDescending { it.timestamp }
                        .take(20)

                    entries.forEach { entry ->
                        val rowText = TextView(requireContext()).apply {
                            text = "${entry.date}  •  ${entry.amountMl} ml"
                            textSize = 16f
                            setTextColor(Color.WHITE)
                            setPadding(0, 20, 0, 20)
                        }
                        container.addView(rowText)
                    }
                }
                bottomSheet.setContentView(container)
                bottomSheet.show()
            }
    }

    private fun logWaterEntry(amountMl: Int) {
        val user = auth.currentUser ?: return
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val newDoc = db.collection("users").document(user.uid)
            .collection("wellness_data").document()

        val entry = WellnessData(
            id = newDoc.id,
            userId = user.uid,
            type = "water", // Unified data type
            amountMl = amountMl,
            timestamp = System.currentTimeMillis(),
            date = todayStr
        )

        newDoc.set(entry).addOnFailureListener {
            Toast.makeText(context, "Failed to log water", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav?.visibility = View.VISIBLE
        _binding = null
    }
}
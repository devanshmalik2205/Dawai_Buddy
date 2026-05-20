package com.ebookfrenzy.dawaibuddy

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.ebookfrenzy.dawaibuddy.databinding.FragmentMoodTrackerBinding
import com.ebookfrenzy.dawaibuddy.objects.WellnessData
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MoodTrackerFragment : Fragment() {

    private var _binding: FragmentMoodTrackerBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private var selectedMood: String = ""

    // Data class to map the UI components to their respective Lottie Resources
    private data class MoodOption(
        val card: MaterialCardView,
        val moodName: String,
        val rawRes: Int
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMoodTrackerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // HIDE THE BOTTOM NAVIGATION MENU SAFELY
        activity?.findViewById<BottomNavigationView>(R.id.bottomNavigation)?.visibility = View.GONE

        binding.ivBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        setupMoodSelection()

        binding.btnSaveNote.setOnClickListener {
            saveMoodEntry()
        }

        binding.tvViewHistory.setOnClickListener {
            showHistoryBottomSheet()
        }
    }

    private fun setupMoodSelection() {
        val moodOptions = listOf(
            MoodOption(binding.cvMoodHappy, "Happy", R.raw.smiley_emoji),
            MoodOption(binding.cvMoodNeutral, "Neutral", R.raw.diagonal_mouth_emoji),
            MoodOption(binding.cvMoodSad, "Sad", R.raw.yawn_emoji),
            MoodOption(binding.cvMoodStressed, "Stressed", R.raw.angry_emoji)
        )

        moodOptions.forEach { option ->
            option.card.setOnClickListener {
                // Reset strokes for all cards
                moodOptions.forEach { it.card.strokeWidth = 0 }

                // Highlight the selected card
                option.card.strokeWidth = 4
                selectedMood = option.moodName

                // Show and animate the central Lottie view once
                binding.lottieMainSelected.visibility = View.VISIBLE
                binding.lottieMainSelected.setAnimation(option.rawRes)
                binding.lottieMainSelected.playAnimation()
            }
        }
    }

    private fun saveMoodEntry() {
        if (selectedMood.isEmpty()) {
            Toast.makeText(context, "Please select a mood first", Toast.LENGTH_SHORT).show()
            return
        }

        val user = auth.currentUser ?: return
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val note = binding.etJournal.text.toString().trim()

        val newDoc = db.collection("users").document(user.uid)
            .collection("wellness_data").document()

        // Create the wellness object, specifically marked as "mood" type
        val entry = WellnessData(
            id = newDoc.id,
            userId = user.uid,
            type = "mood",
            mood = selectedMood,
            journalNote = note,
            timestamp = System.currentTimeMillis(),
            date = todayStr
        )

        newDoc.set(entry).addOnSuccessListener {
            if (_binding != null && isAdded) {
                Toast.makeText(context, "Mood saved successfully!", Toast.LENGTH_SHORT).show()
                _binding?.etJournal?.text?.clear()
            }
        }.addOnFailureListener {
            if (_binding != null && isAdded) {
                Toast.makeText(context, "Failed to save mood", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showHistoryBottomSheet() {
        val user = auth.currentUser ?: return

        // Context safety check to prevent crashes if user quickly navigates back
        if (_binding == null || !isAdded) return

        val bottomSheet = BottomSheetDialog(requireContext())

        // Scrollable UI Container for History (DARK MODE)
        val scrollView = NestedScrollView(requireContext())
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212")) // Dark background
            setPadding(50, 50, 50, 50)
        }
        scrollView.addView(container)

        val title = TextView(requireContext()).apply {
            text = "Mood & Journal History"
            textSize = 20f
            setTextColor(Color.WHITE) // White title
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 40)
        }
        container.addView(title)

        // Fetch recent history where type == "mood"
        db.collection("users").document(user.uid).collection("wellness_data")
            .whereEqualTo("type", "mood")
            .get()
            .addOnSuccessListener { snapshot ->
                // Ensure fragment is still attached before creating views
                if (_binding == null || !isAdded) return@addOnSuccessListener

                if (snapshot.isEmpty) {
                    val emptyState = TextView(requireContext()).apply {
                        text = "No journal entries yet."
                        setTextColor(Color.parseColor("#9E9E9E"))
                        textSize = 16f
                    }
                    container.addView(emptyState)
                } else {
                    val entries = snapshot.documents
                        .mapNotNull { it.toObject(WellnessData::class.java) }
                        .sortedByDescending { it.timestamp }
                        .take(30)

                    entries.forEach { entry ->
                        val entryCard = MaterialCardView(requireContext()).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 0, 0, 32)
                            }
                            setCardBackgroundColor(Color.parseColor("#1E1E1E")) // Dark card surface
                            radius = 24f
                            cardElevation = 0f
                            strokeWidth = 2
                            strokeColor = Color.parseColor("#333333") // Subtle dark outline
                        }

                        val cardInner = LinearLayout(requireContext()).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(40, 40, 40, 40)
                        }

                        // Header Row (Emoji + Date/Mood)
                        val headerRow = LinearLayout(requireContext()).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                        }

                        // Updated to use actual Lottie animations (static frames) in the history
                        val lottieRes = when(entry.mood) {
                            "Happy" -> R.raw.smiley_emoji
                            "Neutral" -> R.raw.diagonal_mouth_emoji
                            "Sad" -> R.raw.yawn_emoji
                            "Stressed" -> R.raw.angry_emoji
                            else -> R.raw.smiley_emoji // Fallback
                        }

                        val emojiView = LottieAnimationView(requireContext()).apply {
                            setAnimation(lottieRes)
                            progress = 1.0f // Lock to the final frame to act as a still image

                            val sizePx = (32 * resources.displayMetrics.density).toInt()
                            val marginEndPx = (16 * resources.displayMetrics.density).toInt()

                            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                                setMargins(0, 0, marginEndPx, 0)
                            }
                        }

                        val headerTextContainer = LinearLayout(requireContext()).apply {
                            orientation = LinearLayout.VERTICAL
                        }

                        val dateText = TextView(requireContext()).apply {
                            text = entry.date
                            textSize = 12f
                            setTextColor(Color.parseColor("#9E9E9E")) // Dimmed text for date
                        }

                        val moodText = TextView(requireContext()).apply {
                            text = entry.mood
                            textSize = 16f
                            setTextColor(Color.WHITE) // White text for mood
                            setTypeface(null, android.graphics.Typeface.BOLD)
                        }

                        headerTextContainer.addView(dateText)
                        headerTextContainer.addView(moodText)

                        headerRow.addView(emojiView)
                        headerRow.addView(headerTextContainer)
                        cardInner.addView(headerRow)

                        // Journal Note (if it exists)
                        if (entry.journalNote.isNotEmpty()) {
                            val divider = View(requireContext()).apply {
                                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply {
                                    setMargins(0, 24, 0, 24)
                                }
                                setBackgroundColor(Color.parseColor("#333333")) // Dark divider
                            }
                            cardInner.addView(divider)

                            val noteText = TextView(requireContext()).apply {
                                text = entry.journalNote
                                textSize = 15f
                                setTextColor(Color.parseColor("#E0E0E0")) // Light grey text for readability
                                setLineSpacing(4f, 1f)
                            }
                            cardInner.addView(noteText)
                        }

                        entryCard.addView(cardInner)
                        container.addView(entryCard)
                    }
                }
                bottomSheet.setContentView(scrollView)
                bottomSheet.show()
            }
            .addOnFailureListener {
                if (_binding != null && isAdded) {
                    Toast.makeText(context, "Failed to load history", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // SAFELY RESTORE THE BOTTOM NAVIGATION MENU WHEN LEAVING
        activity?.findViewById<BottomNavigationView>(R.id.bottomNavigation)?.visibility = View.VISIBLE
        _binding = null
    }
}
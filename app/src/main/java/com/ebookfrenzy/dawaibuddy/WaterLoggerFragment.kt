package com.ebookfrenzy.dawaibuddy

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ebookfrenzy.dawaibuddy.databinding.FragmentWaterLoggerBinding
import com.ebookfrenzy.dawaibuddy.objects.WellnessData
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WaterLoggerFragment : Fragment() {

    private var _binding: FragmentWaterLoggerBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var cycleSnapshotListener: ListenerRegistration? = null

    // Daily Goal Configuration
    private val dailyGoalMl = 2500
    private val sipOptions = listOf(100, 250, 300, 500)
    private var currentSipIndex = 1 // Starts at 250mL

    // Animation & Cycle trackers
    private var currentWaterMl = 0
    private var currentLottieProgress = 0f
    private var currentCycle = 1

    private var textAnimator: ValueAnimator? = null
    private var lottieAnimator: ValueAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWaterLoggerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide the bottom navigation menu to make it immersive
        activity?.findViewById<BottomNavigationView>(R.id.bottomNavigation)?.visibility = View.GONE

        setupUI()
        setupListeners()
        fetchCurrentCycleAndData()
    }

    private fun setupUI() {
        binding.tvGoalWater.text = "/$dailyGoalMl mL"
        updateSipAmountUI()
        binding.clWinnerState.visibility = View.GONE
        binding.lottieWaterBg.progress = 0f
    }

    private fun updateSipAmountUI() {
        binding.tvSipAmount.text = "${sipOptions[currentSipIndex]}mL"
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.cvChangeSip.setOnClickListener {
            currentSipIndex = (currentSipIndex + 1) % sipOptions.size
            updateSipAmountUI()
        }

        binding.cvAddWater.setOnClickListener {
            val amount = sipOptions[currentSipIndex]
            logWaterEntry(amount)
        }

        // Restart cycle button inside the Winner State
        binding.btnDrinkMore.setOnClickListener {
            startNextCycle()
        }
    }

    private fun fetchCurrentCycleAndData() {
        val user = auth.currentUser ?: return
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val dayDocRef = db.collection("users").document(user.uid)
            .collection("water_logs").document(todayStr)

        dayDocRef.get().addOnSuccessListener { doc ->
            if (doc.exists() && doc.contains("currentCycle")) {
                currentCycle = doc.getLong("currentCycle")?.toInt() ?: 1
            } else {
                currentCycle = 1
                dayDocRef.set(mapOf("currentCycle" to currentCycle), SetOptions.merge())
            }
            listenToCurrentCycleLogs()
        }.addOnFailureListener {
            listenToCurrentCycleLogs() // Fallback to cycle 1
        }
    }

    private fun listenToCurrentCycleLogs() {
        val user = auth.currentUser ?: return
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Remove old listener if it exists before attaching to the new cycle
        cycleSnapshotListener?.remove()

        cycleSnapshotListener = db.collection("users").document(user.uid)
            .collection("water_logs").document(todayStr)
            .collection("log_$currentCycle") // Using dynamic log_1, log_2 path
            .addSnapshotListener { snapshot, error ->
                if (_binding == null) return@addSnapshotListener

                if (error != null) {
                    Toast.makeText(context, "Failed to load water data.", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                var totalMl = 0
                snapshot?.documents?.forEach { doc ->
                    val entry = doc.toObject(WellnessData::class.java)
                    if (entry != null) totalMl += entry.amountMl
                }

                animateWaterProgress(totalMl)
            }
    }

    private fun logWaterEntry(amountMl: Int) {
        val user = auth.currentUser ?: return
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val newDoc = db.collection("users").document(user.uid)
            .collection("water_logs").document(todayStr)
            .collection("log_$currentCycle").document()

        val entry = WellnessData(
            id = newDoc.id,
            userId = user.uid,
            type = "water",
            amountMl = amountMl,
            timestamp = System.currentTimeMillis(),
            date = todayStr,
            cycle = currentCycle // Extra tracker attached to the object
        )

        newDoc.set(entry).addOnFailureListener {
            Toast.makeText(context, "Failed to log water", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startNextCycle() {
        val user = auth.currentUser ?: return
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        currentCycle++

        // 1. Update cycle in Firestore Document
        db.collection("users").document(user.uid)
            .collection("water_logs").document(todayStr)
            .set(mapOf("currentCycle" to currentCycle), SetOptions.merge())

        // 2. Hard Reset UI Values
        currentWaterMl = 0
        currentLottieProgress = 0f
        _binding?.tvCurrentWater?.text = "0"
        _binding?.lottieWaterBg?.progress = 0f

        // 3. Reset and Hide Winner View smoothly
        _binding?.clWinnerState?.animate()?.alpha(0f)?.setDuration(300)?.withEndAction {
            if (_binding != null) {
                _binding?.clWinnerState?.visibility = View.GONE
                _binding?.clWinnerState?.alpha = 1f // reset for next time
            }
        }?.start()

        // 4. Start listening to the brand-new cycle
        listenToCurrentCycleLogs()
    }

    private fun animateWaterProgress(newTotalMl: Int) {
        val previousWaterMl = currentWaterMl
        currentWaterMl = newTotalMl.coerceAtMost(dailyGoalMl)

        // 1. Animate the Counter Text Smoothly
        textAnimator?.cancel()
        textAnimator = ValueAnimator.ofInt(previousWaterMl, currentWaterMl)
        textAnimator?.duration = 1500
        textAnimator?.interpolator = DecelerateInterpolator()
        textAnimator?.addUpdateListener { animator ->
            _binding?.tvCurrentWater?.text = animator.animatedValue.toString()
        }
        textAnimator?.start()

        // 2. Animate Lottie Fill Background applying specific WearOS Match Math
        val waterFraction = (currentWaterMl.toFloat() / dailyGoalMl.toFloat()).coerceIn(0f, 1f)

        val targetFrame = 111f
        val totalFrames = _binding?.lottieWaterBg?.composition?.durationFrames ?: 111f
        val maxProgressFraction = if (totalFrames > 0f) targetFrame / totalFrames else 1f
        val targetProgress = waterFraction * maxProgressFraction

        lottieAnimator?.cancel()
        lottieAnimator = ValueAnimator.ofFloat(currentLottieProgress, targetProgress)
        lottieAnimator?.duration = 1500
        lottieAnimator?.interpolator = DecelerateInterpolator()
        lottieAnimator?.addUpdateListener { animator ->
            val progress = animator.animatedValue as Float
            currentLottieProgress = progress
            _binding?.lottieWaterBg?.progress = progress
        }
        lottieAnimator?.start()

        // 3. Trigger Winner State if Goal is Met
        if (currentWaterMl >= dailyGoalMl && _binding?.clWinnerState?.visibility == View.GONE) {
            showWinnerState()
        }
    }

    private fun showWinnerState() {
        _binding?.clWinnerState?.visibility = View.VISIBLE
        _binding?.clWinnerState?.alpha = 0f

        _binding?.clWinnerState?.animate()
            ?.alpha(1f)
            ?.setDuration(500)
            ?.start()

        _binding?.lottieWinner?.playAnimation()

        // Ensure starting state is 100% standard
        _binding?.lottieWinner?.scaleX = 1f
        _binding?.lottieWinner?.scaleY = 1f
        _binding?.lottieWinner?.translationY = 0f
        _binding?.llWinnerText?.alpha = 0f
        _binding?.llWinnerText?.translationY = 0f

        _binding?.clWinnerState?.post {
            if (_binding == null) return@post
            val translateUpwardsTarget = - (_binding!!.clWinnerState.height * 0.25f)

            // Zoom out & move Lottie UP
            _binding?.lottieWinner?.animate()
                ?.scaleX(0.7f) // Keep slightly larger when scaling down
                ?.scaleY(0.7f)
                ?.translationY(translateUpwardsTarget)
                ?.setDuration(1200)
                ?.setInterpolator(DecelerateInterpolator())
                ?.setStartDelay(1000)
                ?.start()

            // Move Text upward as well and fade it in
            _binding?.llWinnerText?.animate()
                ?.translationY(translateUpwardsTarget)
                ?.alpha(1f)
                ?.setDuration(800)
                ?.setStartDelay(1400)
                ?.start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Prevent memory leaks by removing the Firestore listener and safely stopping animators
        cycleSnapshotListener?.remove()
        textAnimator?.cancel()
        lottieAnimator?.cancel()

        activity?.findViewById<BottomNavigationView>(R.id.bottomNavigation)?.visibility = View.VISIBLE
        _binding = null
    }
}
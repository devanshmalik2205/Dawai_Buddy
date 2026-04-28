package com.ebookfrenzy.dawaibuddy.home

import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ebookfrenzy.dawaibuddy.objects.MeditationTrack
import com.ebookfrenzy.dawaibuddy.NowPlayingFragment
import com.ebookfrenzy.dawaibuddy.R
import com.ebookfrenzy.dawaibuddy.models.SharedAudioViewModel
import com.ebookfrenzy.dawaibuddy.databinding.FragmentWellnessBinding
import com.ebookfrenzy.dawaibuddy.objects.User
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WellnessFragment : Fragment() {

    private var _binding: FragmentWellnessBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // CONNECT TO GLOBAL AUDIO
    private val audioViewModel: SharedAudioViewModel by activityViewModels()

    // List to keep track of loaded songs for next/prev functionality
    private var currentTrackList: List<MeditationTrack> = emptyList()

    private var progressHandler: Handler? = null
    private var progressRunnable: Runnable? = null

    private val healthPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )

    private val permissionsLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(healthPermissions)) {
            binding.cvConnectHealth.visibility = View.GONE
            fetchHealthConnectData()
        } else {
            Toast.makeText(requireContext(), "Permissions denied. Unable to sync vitals.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWellnessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        audioViewModel.initializeController(requireContext())

        setDynamicGreeting()
        fetchUserData()
        fetchAllTracks()
        fetchTodayWaterTotal()
        fetchTodayMood()

        checkHealthConnectStatus()

        // --- STREAK LOGIC ---
        // Fetch the user's streak from your database/preferences here.
        // Replace this hardcoded '1' with your dynamic variable from Firebase/ViewModel.
        val userStreakDays = 1
        updateStreakInProfile(userStreakDays)

        binding.cvConnectHealth.setOnClickListener { launchHealthConnectPermissions() }
        binding.cvStepsCard.setOnClickListener { showTrendDialog() }

        // --- NAVIGATION LINKS ---
        binding.root.findViewById<View>(R.id.cvWaterCard)?.setOnClickListener {
            findNavController().navigate(R.id.action_nav_wellness_to_waterLoggerFragment)
        }
        binding.root.findViewById<View>(R.id.cvMoodCard)?.setOnClickListener {
            findNavController().navigate(R.id.action_nav_wellness_to_moodTrackerFragment)
        }

        // --- BACKGROUND AUDIO SYNC & PROGRESS BAR ---
        progressHandler = Handler(Looper.getMainLooper())
        progressRunnable = Runnable {
            audioViewModel.player?.let { player ->
                if (player.isPlaying) {
                    val duration = player.duration
                    val current = player.currentPosition
                    if (duration > 0) {
                        val pb = _binding?.root?.findViewById<android.widget.ProgressBar>(R.id.pbMindfulnessProgress)
                        pb?.max = duration.toInt()
                        pb?.progress = current.toInt()
                    }
                }
            }
            progressHandler?.postDelayed(progressRunnable!!, 1000)
        }

        audioViewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            if (_binding == null) return@observe
            val ivPlay = binding.root.findViewById<ImageView>(R.id.ivMindfulnessPlay)
            if (isPlaying) {
                ivPlay?.setImageResource(android.R.drawable.ic_media_pause)
                progressRunnable?.let { progressHandler?.post(it) }
            } else {
                ivPlay?.setImageResource(android.R.drawable.ic_media_play)
                progressRunnable?.let { progressHandler?.removeCallbacks(it) }
            }
        }

        audioViewModel.currentTrack.observe(viewLifecycleOwner) { track ->
            if (_binding == null) return@observe
            val tvTitle = binding.root.findViewById<TextView>(R.id.tvMindfulnessTitle)
            val tvSubtitle = binding.root.findViewById<TextView>(R.id.tvMindfulnessSubtitle)
            val pb = binding.root.findViewById<android.widget.ProgressBar>(R.id.pbMindfulnessProgress)

            if (track != null) {
                tvTitle?.text = track.title
                tvSubtitle?.text = track.artist
            } else {
                tvTitle?.text = "Tap to play music"
                tvSubtitle?.text = "Start a random meditation"
                pb?.progress = 0
            }
        }

        audioViewModel.artworkBitmap.observe(viewLifecycleOwner) { bitmap ->
            if (_binding == null) return@observe
            val ivArt = binding.root.findViewById<ImageView>(R.id.ivMindfulnessArt)
            if (bitmap != null) {
                ivArt?.setImageBitmap(bitmap)
            } else {
                ivArt?.setBackgroundColor(Color.parseColor("#E0E0E0"))
                ivArt?.setImageResource(0)
            }
        }

        // --- INLINE MEDIA CONTROLS ---
        val cvPlay = binding.root.findViewById<View>(R.id.cvMindfulnessPlay)
        val ivPrev = binding.root.findViewById<View>(R.id.ivMindfulnessPrev)
        val ivNext = binding.root.findViewById<View>(R.id.ivMindfulnessNext)

        cvPlay?.setOnClickListener {
            if (audioViewModel.currentTrack.value != null) {
                audioViewModel.player?.let { player ->
                    if (player.isPlaying) player.pause() else player.play()
                }
            } else {
                playRandomTrack()
            }
        }

        ivNext?.setOnClickListener {
            val currentTrack = audioViewModel.currentTrack.value
            if (currentTrack != null && currentTrackList.isNotEmpty()) {
                val currentIndex = currentTrackList.indexOfFirst { it.title == currentTrack.title }
                if (currentIndex != -1) {
                    val nextIndex = if (currentIndex + 1 < currentTrackList.size) currentIndex + 1 else 0
                    audioViewModel.playTrack(currentTrackList[nextIndex])
                    return@setOnClickListener
                }
            }
            audioViewModel.player?.seekToNextMediaItem()
        }

        ivPrev?.setOnClickListener {
            val currentTrack = audioViewModel.currentTrack.value
            if (currentTrack != null && currentTrackList.isNotEmpty()) {
                val currentIndex = currentTrackList.indexOfFirst { it.title == currentTrack.title }
                if (currentIndex != -1) {
                    val prevIndex = if (currentIndex - 1 >= 0) currentIndex - 1 else currentTrackList.size - 1
                    audioViewModel.playTrack(currentTrackList[prevIndex])
                    return@setOnClickListener
                }
            }
            audioViewModel.player?.seekToPreviousMediaItem()
        }

        binding.cvMindfulness.setOnClickListener {
            if (audioViewModel.currentTrack.value != null) {
                val bottomSheet = NowPlayingFragment()
                bottomSheet.show(parentFragmentManager, "NowPlaying")
            } else {
                playRandomTrack()
            }
        }
    }

    private fun setDynamicGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greetingText = when (hour) {
            in 0..11 -> "Good Morning,"
            in 12..16 -> "Good Afternoon,"
            in 17..20 -> "Good Evening,"
            else -> "Good Night,"
        }
        binding.tvGreeting.text = greetingText
    }

    private fun updateStreakInProfile(streakDays: Int) {
        // Formulate the streak text - Just the number as requested
        val streakText = streakDays.toString()

        // Reference the cvProfile view from your binding
        val cvProfile = binding.cvProfile

        // Check if cvProfile is a ViewGroup (like a CardView or ConstraintLayout)
        if (cvProfile is ViewGroup) {
            // Look for an existing streak label to avoid duplicates when fragment resumes
            var tvStreak = cvProfile.findViewWithTag<TextView>("streakLabel")

            if (tvStreak == null) {
                // Create the Bold, Big, Green TextView programmatically
                tvStreak = TextView(requireContext()).apply {
                    tag = "streakLabel"
                    setTextColor(Color.parseColor("#4CAF50")) // Bold Green Color
                    setTypeface(null, Typeface.BOLD)
                    textSize = 32f // Big letters
                    gravity = Gravity.CENTER
                }

                // Add it to the bottom-center of the cvProfile CardView
                val params = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = (16 * resources.displayMetrics.density).toInt() // converting to pixels for better rendering
                }

                cvProfile.addView(tvStreak, params)
            }

            // Set the streak text
            tvStreak.text = streakText

        } else if (cvProfile is TextView) {
            // Just in case cvProfile is actually a TextView itself
            cvProfile.text = streakText
            cvProfile.setTextColor(Color.parseColor("#4CAF50")) // Green
            cvProfile.setTypeface(null, Typeface.BOLD)
            cvProfile.textSize = 32f // Big letters
        }
    }

    private fun fetchAllTracks() {
        db.collectionGroup("tracks")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val allTracks = snapshot.documents.mapNotNull { it.toObject(MeditationTrack::class.java) }
                if (allTracks.isNotEmpty()) currentTrackList = allTracks.shuffled()
            }
    }

    private fun playRandomTrack() {
        if (currentTrackList.isNotEmpty()) {
            val randomTrack = currentTrackList.random()
            audioViewModel.playTrack(randomTrack)
            Toast.makeText(requireContext(), "Playing ${randomTrack.title}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Loading tracks, please wait...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchTodayWaterTotal() {
        val user = auth.currentUser ?: return
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        db.collection("users").document(user.uid).collection("wellness_data")
            .whereEqualTo("date", todayStr)
            .whereEqualTo("type", "water")
            .addSnapshotListener { snapshot, _ ->
                if (_binding == null) return@addSnapshotListener
                var totalMl = 0
                snapshot?.documents?.forEach { totalMl += it.getLong("amountMl")?.toInt() ?: 0 }
                binding.tvWater.text = String.format(Locale.getDefault(), "%.1f", totalMl / 1000f)
            }
    }

    private fun fetchTodayMood() {
        val user = auth.currentUser ?: return
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        db.collection("users").document(user.uid).collection("wellness_data")
            .whereEqualTo("date", todayStr)
            .whereEqualTo("type", "mood")
            .addSnapshotListener { snapshot, _ ->
                if (_binding == null) return@addSnapshotListener
                if (snapshot != null && !snapshot.isEmpty) {
                    val latestEntry = snapshot.documents.maxByOrNull { it.getLong("timestamp") ?: 0L }
                    val mood = latestEntry?.getString("mood") ?: "-"
                    val emoji = when(mood) {
                        "Excited" -> "🤩"
                        "Happy" -> "😊"
                        "Calm" -> "😌"
                        "Tired" -> "😴"
                        "Sad" -> "😔"
                        "Angry" -> "😠"
                        else -> "-"
                    }
                    binding.tvMood.text = emoji
                }
            }
    }

    private fun fetchUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .addSnapshotListener { document, error ->
                    if (_binding == null) return@addSnapshotListener
                    if (error != null || document == null || !document.exists()) {
                        binding.tvGreetingName.text = "New User"
                        return@addSnapshotListener
                    }
                    val user = document.toObject(User::class.java)
                    val firstName = user?.name?.split(" ")?.firstOrNull() ?: "User"
                    binding.tvGreetingName.text = firstName
                }
        } else {
            if (_binding != null) binding.tvGreetingName.text = "Guest"
        }
    }

    private fun checkHealthConnectStatus() {
        val availabilityStatus = HealthConnectClient.getSdkStatus(requireContext(), "com.google.android.apps.healthdata")
        if (availabilityStatus == HealthConnectClient.SDK_AVAILABLE) {
            val client = HealthConnectClient.getOrCreate(requireContext())
            viewLifecycleOwner.lifecycleScope.launch {
                val granted = client.permissionController.getGrantedPermissions()
                if (_binding == null) return@launch
                if (granted.containsAll(healthPermissions)) {
                    binding.cvConnectHealth.visibility = View.GONE
                    fetchHealthConnectData()
                } else binding.cvConnectHealth.visibility = View.VISIBLE
            }
        } else {
            if (_binding != null) binding.cvConnectHealth.visibility = View.VISIBLE
        }
    }

    private fun launchHealthConnectPermissions() {
        val availabilityStatus = HealthConnectClient.getSdkStatus(requireContext(), "com.google.android.apps.healthdata")
        if (availabilityStatus == HealthConnectClient.SDK_AVAILABLE) permissionsLauncher.launch(healthPermissions)
        else Toast.makeText(requireContext(), "Health Connect is not installed on this device.", Toast.LENGTH_LONG).show()
    }

    private fun fetchHealthConnectData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(requireContext())
                val now = LocalDateTime.now()
                val startOfToday = now.truncatedTo(ChronoUnit.DAYS)

                val response = client.aggregate(AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = TimeRangeFilter.between(startOfToday, now)))
                val todaySteps = response[StepsRecord.COUNT_TOTAL] ?: 0L

                val hrResponse = client.aggregate(AggregateRequest(metrics = setOf(HeartRateRecord.BPM_AVG), timeRangeFilter = TimeRangeFilter.between(startOfToday, now)))
                val avgHr = hrResponse[HeartRateRecord.BPM_AVG]?.toLong() ?: 0L

                val progress = ((todaySteps.toFloat() / 10000f) * 100).toInt()
                val distanceKm = (todaySteps * 0.000762)
                val caloriesKcal = (todaySteps * 0.04)
                val activeMins = (todaySteps / 100).toInt()

                val currentActivity = activity ?: return@launch
                currentActivity.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    binding.tvStepsCount.text = String.format("%,d", todaySteps)
                    binding.pbSteps.progress = progress.coerceAtMost(100)
                    binding.tvDistance.text = String.format(Locale.US, "%.1f", distanceKm)
                    binding.tvCalories.text = String.format(Locale.US, "%.1f", caloriesKcal)
                    binding.tvActiveMins.text = activeMins.toString()
                    binding.tvHeartRate.text = if (avgHr > 0L) avgHr.toString() else "--"
                }
            } catch (e: Exception) { Log.e("WellnessFragment", "Error reading Health Data", e) }
        }
    }

    private fun showTrendDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_step_trends)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.decorView?.setPadding(0, 0, 0, 0)

        val params = dialog.window?.attributes
        params?.width = WindowManager.LayoutParams.MATCH_PARENT
        params?.height = WindowManager.LayoutParams.WRAP_CONTENT
        params?.gravity = Gravity.BOTTOM
        dialog.window?.attributes = params
        dialog.window?.setWindowAnimations(android.R.style.Animation_Dialog)

        // Dark/Light Mode Adaptability
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bgColor = if (isNightMode) Color.parseColor("#121212") else Color.WHITE
        val textColor = if (isNightMode) Color.WHITE else Color.parseColor("#212121")
        val subTextColor = if (isNightMode) Color.parseColor("#9E9E9E") else Color.parseColor("#757575")
        val primaryGreen = Color.parseColor("#4CAF50")
        val highlightColor = if (isNightMode) Color.parseColor("#81C784") else Color.parseColor("#2E7D32")

        val tvDateRange = dialog.findViewById<TextView>(R.id.tvDateRange)
        val tvTotalSteps = dialog.findViewById<TextView>(R.id.tvTotalSteps)
        val btnPrev = dialog.findViewById<ImageView>(R.id.btnPrev)
        val btnNext = dialog.findViewById<ImageView>(R.id.btnNext)

        val tabDay = dialog.findViewById<LinearLayout>(R.id.tabDay)
        val tabWeek = dialog.findViewById<LinearLayout>(R.id.tabWeek)
        val tabMonth = dialog.findViewById<LinearLayout>(R.id.tabMonth)

        val tvTabDay = dialog.findViewById<TextView>(R.id.tvTabDay)
        val tvTabWeek = dialog.findViewById<TextView>(R.id.tvTabWeek)
        val tvTabMonth = dialog.findViewById<TextView>(R.id.tvTabMonth)

        val viewTabDayLine = dialog.findViewById<View>(R.id.viewTabDayLine)
        val viewTabWeekLine = dialog.findViewById<View>(R.id.viewTabWeekLine)
        val viewTabMonthLine = dialog.findViewById<View>(R.id.viewTabMonthLine)

        val layoutDayChart = dialog.findViewById<LinearLayout>(R.id.layoutDayChart)
        val layoutWeekChart = dialog.findViewById<LinearLayout>(R.id.layoutWeekChart)
        val layoutMonthChart = dialog.findViewById<LinearLayout>(R.id.layoutMonthChart)

        val dayBarsContainer = dialog.findViewById<LinearLayout>(R.id.dayBarsContainer)
        val monthGridContainer = dialog.findViewById<GridLayout>(R.id.monthGridContainer)

        // Set Dynamic Dialog Background
        val rootGroup = dialog.findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0)
        rootGroup?.background = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadii = floatArrayOf(60f, 60f, 60f, 60f, 0f, 0f, 0f, 0f)
        }

        tvDateRange?.setTextColor(textColor)
        tvTotalSteps?.setTextColor(textColor)
        btnPrev?.setColorFilter(textColor)
        btnNext?.setColorFilter(textColor)

        viewTabDayLine?.setBackgroundColor(primaryGreen)
        viewTabWeekLine?.setBackgroundColor(primaryGreen)
        viewTabMonthLine?.setBackgroundColor(primaryGreen)

        val bars = arrayOf<View?>(
            dialog.findViewById(R.id.dlgBar1), dialog.findViewById(R.id.dlgBar2), dialog.findViewById(R.id.dlgBar3),
            dialog.findViewById(R.id.dlgBar4), dialog.findViewById(R.id.dlgBar5), dialog.findViewById(R.id.dlgBar6), dialog.findViewById(R.id.dlgBar7)
        )
        val spaces = arrayOf<View?>(
            dialog.findViewById(R.id.dlgSpace1), dialog.findViewById(R.id.dlgSpace2), dialog.findViewById(R.id.dlgSpace3),
            dialog.findViewById(R.id.dlgSpace4), dialog.findViewById(R.id.dlgSpace5), dialog.findViewById(R.id.dlgSpace6), dialog.findViewById(R.id.dlgSpace7)
        )
        val labels = arrayOf<TextView?>(
            dialog.findViewById(R.id.dlgTvDay1), dialog.findViewById(R.id.dlgTvDay2), dialog.findViewById(R.id.dlgTvDay3),
            dialog.findViewById(R.id.dlgTvDay4), dialog.findViewById(R.id.dlgTvDay5), dialog.findViewById(R.id.dlgTvDay6), dialog.findViewById(R.id.dlgTvDay7)
        )

        var currentMode = 1 // 0 = Day, 1 = Week, 2 = Month
        var dateOffset = 0

        fun updateTabUI() {
            tvTabDay?.setTextColor(subTextColor)
            tvTabDay?.typeface = android.graphics.Typeface.DEFAULT
            tvTabWeek?.setTextColor(subTextColor)
            tvTabWeek?.typeface = android.graphics.Typeface.DEFAULT
            tvTabMonth?.setTextColor(subTextColor)
            tvTabMonth?.typeface = android.graphics.Typeface.DEFAULT

            viewTabDayLine?.visibility = View.INVISIBLE
            viewTabWeekLine?.visibility = View.INVISIBLE
            viewTabMonthLine?.visibility = View.INVISIBLE

            layoutDayChart?.visibility = View.GONE
            layoutWeekChart?.visibility = View.GONE
            layoutMonthChart?.visibility = View.GONE

            when (currentMode) {
                0 -> { tvTabDay?.setTextColor(textColor); tvTabDay?.typeface = android.graphics.Typeface.DEFAULT_BOLD; viewTabDayLine?.visibility = View.VISIBLE; layoutDayChart?.visibility = View.VISIBLE }
                1 -> { tvTabWeek?.setTextColor(textColor); tvTabWeek?.typeface = android.graphics.Typeface.DEFAULT_BOLD; viewTabWeekLine?.visibility = View.VISIBLE; layoutWeekChart?.visibility = View.VISIBLE }
                2 -> { tvTabMonth?.setTextColor(textColor); tvTabMonth?.typeface = android.graphics.Typeface.DEFAULT_BOLD; viewTabMonthLine?.visibility = View.VISIBLE; layoutMonthChart?.visibility = View.VISIBLE }
            }
        }

        fun loadDayData() {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val client = HealthConnectClient.getOrCreate(requireContext())
                    val targetDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(dateOffset.toLong())

                    val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")
                    val dateText = targetDay.format(formatter)

                    var totalDailySteps = 0L
                    val hourlySteps = mutableListOf<Long>()

                    for (i in 0..23) {
                        val currentHour = targetDay.plusHours(i.toLong())
                        val nextHour = currentHour.plusHours(1)

                        val response = client.aggregate(
                            AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = TimeRangeFilter.between(currentHour, nextHour))
                        )
                        val steps = response[StepsRecord.COUNT_TOTAL] ?: 0L
                        hourlySteps.add(steps)
                        totalDailySteps += steps
                    }

                    val currentActivity = activity ?: return@launch
                    currentActivity.runOnUiThread {
                        tvDateRange?.text = dateText
                        tvTotalSteps?.text = String.format("%,d steps", totalDailySteps)

                        dayBarsContainer?.removeAllViews()

                        val maxSteps = hourlySteps.maxOrNull()?.coerceAtLeast(10L) ?: 10L
                        val isToday = targetDay.toLocalDate() == LocalDateTime.now().toLocalDate()
                        val currentHourIndex = LocalDateTime.now().hour

                        for (i in 0..23) {
                            val stepCount = hourlySteps[i]
                            val weightBar = (stepCount.toFloat() / maxSteps.toFloat() * 100f).coerceIn(0f, 100f)
                            val weightSpace = 100f - weightBar

                            val container = LinearLayout(requireContext()).apply {
                                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                                orientation = LinearLayout.VERTICAL
                            }

                            val space = View(requireContext()).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, weightSpace.coerceAtLeast(0.01f)) }

                            val readingText = TextView(requireContext()).apply {
                                text = if (stepCount > 0) stepCount.toString() else ""
                                textSize = 6f
                                setTextColor(subTextColor)
                                gravity = Gravity.CENTER
                                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            }

                            val bar = MaterialCardView(requireContext()).apply {
                                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, weightBar.coerceAtLeast(0.01f))
                                lp.setMargins(2, 0, 2, 0)
                                layoutParams = lp
                                setCardBackgroundColor(primaryGreen)
                                radius = 4f
                                cardElevation = 0f

                                // Highlight Current Hour if Today
                                if (isToday && i == currentHourIndex) {
                                    strokeWidth = 4
                                    strokeColor = highlightColor
                                } else {
                                    strokeWidth = 0
                                }
                            }

                            container.addView(space)
                            container.addView(readingText)
                            container.addView(bar)
                            dayBarsContainer?.addView(container)
                        }
                    }
                } catch (e: Exception) { Log.e("WellnessFragment", "Error loading Day Data", e) }
            }
        }

        fun loadWeekData() {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val client = HealthConnectClient.getOrCreate(requireContext())
                    val now = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)

                    // Fixed Calendar Week Logic: Explicitly map Mon-Sun
                    val targetWeekDate = now.minusWeeks(dateOffset.toLong())
                    val startDay = targetWeekDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val endDay = startDay.plusDays(6)

                    val formatter = DateTimeFormatter.ofPattern("d MMM")
                    val dateText = "${startDay.format(formatter)} – ${endDay.format(formatter)}"

                    var totalWeeklySteps = 0L
                    val dailySteps = mutableListOf<Long>()

                    for (i in 0..6) {
                        val currentDay = startDay.plusDays(i.toLong())
                        val nextDay = currentDay.plusDays(1)

                        val response = client.aggregate(
                            AggregateRequest(
                                metrics = setOf(StepsRecord.COUNT_TOTAL),
                                timeRangeFilter = TimeRangeFilter.between(currentDay, nextDay)
                            )
                        )
                        val steps = response[StepsRecord.COUNT_TOTAL] ?: 0L
                        dailySteps.add(steps)
                        totalWeeklySteps += steps
                    }

                    val currentActivity = activity ?: return@launch
                    currentActivity.runOnUiThread {
                        tvDateRange?.text = dateText
                        tvTotalSteps?.text = String.format("%,d steps", totalWeeklySteps)

                        val maxSteps = dailySteps.maxOrNull()?.coerceAtLeast(10L) ?: 10L

                        for (i in 0..6) {
                            val stepCount = dailySteps[i]
                            val weightBar = (stepCount.toFloat() / maxSteps.toFloat() * 100f).coerceIn(0f, 100f)
                            val weightSpace = 100f - weightBar

                            val spaceParams = spaces[i]?.layoutParams as? LinearLayout.LayoutParams
                            if (spaceParams != null) {
                                spaceParams.weight = weightSpace.coerceAtLeast(0.01f)
                                spaces[i]?.layoutParams = spaceParams
                            }

                            val barParams = bars[i]?.layoutParams as? LinearLayout.LayoutParams
                            if (barParams != null) {
                                barParams.weight = weightBar.coerceAtLeast(0.01f)
                                bars[i]?.layoutParams = barParams
                            }

                            val currentDay = startDay.plusDays(i.toLong())
                            val isToday = currentDay.toLocalDate() == LocalDateTime.now().toLocalDate()

                            val barCard = bars[i] as? MaterialCardView
                            barCard?.setCardBackgroundColor(primaryGreen)

                            // True Today Highlighting Logic
                            if (isToday) {
                                barCard?.strokeWidth = 4
                                barCard?.strokeColor = highlightColor
                            } else {
                                barCard?.strokeWidth = 0
                            }

                            val dayName = currentDay.dayOfWeek.name.take(3)
                            val readingStr = if (stepCount >= 1000) String.format(Locale.getDefault(), "%.1fk", stepCount / 1000f) else if (stepCount > 0) stepCount.toString() else ""

                            labels[i]?.text = "$dayName\n$readingStr"
                            labels[i]?.textSize = 10f
                            labels[i]?.gravity = Gravity.CENTER
                            labels[i]?.setTextColor(if (isToday) highlightColor else subTextColor)
                            labels[i]?.typeface = if (isToday) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WellnessFragment", "Error loading Week Data", e)
                }
            }
        }

        fun loadMonthData() {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val client = HealthConnectClient.getOrCreate(requireContext())
                    val targetMonthStart = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1).minusMonths(dateOffset.toLong())
                    val targetMonthEnd = targetMonthStart.plusMonths(1).minusDays(1)

                    val formatter = DateTimeFormatter.ofPattern("MMMM yyyy")
                    val dateText = targetMonthStart.format(formatter)

                    var totalMonthSteps = 0L
                    val dailyStepsMap = mutableMapOf<Int, Long>()

                    for (i in 0 until targetMonthStart.toLocalDate().lengthOfMonth()) {
                        val currentDay = targetMonthStart.plusDays(i.toLong())
                        val nextDay = currentDay.plusDays(1)

                        val response = client.aggregate(
                            AggregateRequest(
                                metrics = setOf(StepsRecord.COUNT_TOTAL),
                                timeRangeFilter = TimeRangeFilter.between(currentDay, nextDay)
                            )
                        )
                        val steps = response[StepsRecord.COUNT_TOTAL] ?: 0L
                        dailyStepsMap[i + 1] = steps
                        totalMonthSteps += steps
                    }

                    val currentActivity = activity ?: return@launch
                    currentActivity.runOnUiThread {
                        tvDateRange?.text = dateText
                        tvTotalSteps?.text = String.format("%,d steps", totalMonthSteps)

                        monthGridContainer?.removeAllViews()

                        val maxSteps = dailyStepsMap.values.maxOrNull()?.coerceAtLeast(10L) ?: 10L

                        val density = resources.displayMetrics.density
                        val maxBubbleSize = 55 * density
                        val minBubbleSize = 25 * density

                        for (day in 1..targetMonthStart.toLocalDate().lengthOfMonth()) {
                            val steps = dailyStepsMap[day] ?: 0L
                            val ratio = (steps.toFloat() / maxSteps.toFloat()).coerceIn(0f, 1f)
                            val bubbleSize = (minBubbleSize + (maxBubbleSize - minBubbleSize) * ratio).toInt()

                            val currentDay = targetMonthStart.plusDays((day - 1).toLong())
                            val isToday = currentDay.toLocalDate() == LocalDateTime.now().toLocalDate()

                            val stepStr = if (steps >= 1000) "${steps/1000}k" else if (steps > 0) steps.toString() else ""

                            val bubbleView = TextView(requireContext()).apply {
                                text = if (steps > 0) "$day\n$stepStr" else day.toString()
                                gravity = Gravity.CENTER
                                setTextColor(if (isToday) highlightColor else (if (isNightMode) Color.WHITE else Color.WHITE))
                                typeface = if (isToday) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                                textSize = if (steps > 0) 10f else 12f

                                val lp = GridLayout.LayoutParams()
                                lp.width = bubbleSize
                                lp.height = bubbleSize
                                lp.setMargins(-10, -10, -10, -10)
                                lp.setGravity(Gravity.CENTER)
                                layoutParams = lp

                                val bgShape = GradientDrawable()
                                bgShape.shape = GradientDrawable.OVAL
                                bgShape.setColor(if (isNightMode) Color.parseColor("#4D4CAF50") else Color.parseColor("#B34CAF50"))

                                // True Today Highlighting Logic
                                if (isToday) {
                                    bgShape.setStroke(4, highlightColor)
                                }
                                background = bgShape
                            }
                            monthGridContainer?.addView(bubbleView)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WellnessFragment", "Error loading Month Data", e)
                }
            }
        }

        fun loadDataForCurrentMode() {
            when (currentMode) {
                0 -> loadDayData()
                1 -> loadWeekData()
                2 -> loadMonthData()
            }
        }

        tabDay?.setOnClickListener { if (currentMode != 0) { currentMode = 0; dateOffset = 0; updateTabUI(); loadDataForCurrentMode() } }
        tabWeek?.setOnClickListener { if (currentMode != 1) { currentMode = 1; dateOffset = 0; updateTabUI(); loadDataForCurrentMode() } }
        tabMonth?.setOnClickListener { if (currentMode != 2) { currentMode = 2; dateOffset = 0; updateTabUI(); loadDataForCurrentMode() } }
        btnPrev?.setOnClickListener { dateOffset++; loadDataForCurrentMode() }
        btnNext?.setOnClickListener { if (dateOffset > 0) { dateOffset--; loadDataForCurrentMode() } }

        updateTabUI()
        loadDataForCurrentMode()
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressRunnable?.let { progressHandler?.removeCallbacks(it) }
        progressHandler = null
        _binding = null
    }
}
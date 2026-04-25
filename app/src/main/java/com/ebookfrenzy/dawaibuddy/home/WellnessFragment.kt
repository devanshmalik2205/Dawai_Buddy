package com.ebookfrenzy.dawaibuddy.home

import android.app.Dialog
import android.graphics.Color
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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

        // 🔥 INSTANT FETCHING WITH SNAPSHOTS
        fetchUserData()
        fetchAllTracks()

        checkHealthConnectStatus()

        binding.cvConnectHealth.setOnClickListener { launchHealthConnectPermissions() }
        binding.cvStepsCard.setOnClickListener { showTrendDialog() }
        binding.tvViewHistory.setOnClickListener { showTrendDialog() }

        // --- BACKGROUND AUDIO SYNC & PROGRESS BAR ---
        progressHandler = Handler(Looper.getMainLooper())
        progressRunnable = Runnable {
            audioViewModel.player?.let { player ->
                if (player.isPlaying) {
                    val duration = player.duration
                    val current = player.currentPosition
                    if (duration > 0) {
                        val pb = binding.root.findViewById<android.widget.ProgressBar>(R.id.pbMindfulnessProgress)
                        pb?.max = duration.toInt()
                        pb?.progress = current.toInt()
                    }
                }
            }
            progressHandler?.postDelayed(progressRunnable!!, 1000)
        }

        audioViewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
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

        // Click the whole Card -> Opens NowPlaying
        binding.cvMindfulness.setOnClickListener {
            if (audioViewModel.currentTrack.value != null) {
                val bottomSheet = NowPlayingFragment()
                bottomSheet.show(parentFragmentManager, "NowPlaying")
            } else {
                playRandomTrack()
            }
        }
    }

    // 🔥 FASTER FETCHING: Collection Group Query grabs all tracks instantly
    private fun fetchAllTracks() {
        db.collectionGroup("tracks")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val allTracks = snapshot.documents.mapNotNull {
                    it.toObject(MeditationTrack::class.java)
                }

                if (allTracks.isNotEmpty()) {
                    currentTrackList = allTracks.shuffled() // Shuffle for randomized playlist
                }
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

    // 🔥 INSTANT USER DATA via addSnapshotListener
    private fun fetchUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .addSnapshotListener { document, error ->
                    if (error != null || document == null || !document.exists()) {
                        binding.tvGreetingName.text = "New User"
                        return@addSnapshotListener
                    }
                    val user = document.toObject(User::class.java)
                    val firstName = user?.name?.split(" ")?.firstOrNull() ?: "User"
                    binding.tvGreetingName.text = firstName
                }
        } else {
            binding.tvGreetingName.text = "Guest"
        }
    }

    private fun checkHealthConnectStatus() {
        val availabilityStatus = HealthConnectClient.getSdkStatus(requireContext(), "com.google.android.apps.healthdata")
        if (availabilityStatus == HealthConnectClient.SDK_AVAILABLE) {
            val client = HealthConnectClient.getOrCreate(requireContext())
            viewLifecycleOwner.lifecycleScope.launch {
                val granted = client.permissionController.getGrantedPermissions()
                if (granted.containsAll(healthPermissions)) {
                    binding.cvConnectHealth.visibility = View.GONE
                    fetchHealthConnectData()
                } else binding.cvConnectHealth.visibility = View.VISIBLE
            }
        } else binding.cvConnectHealth.visibility = View.VISIBLE
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

                requireActivity().runOnUiThread {
                    binding.tvStepsCount.text = String.format("%,d", todaySteps)
                    binding.pbSteps.progress = progress.coerceAtMost(100)
                    binding.tvDistance.text = String.format("%.1f", distanceKm)
                    binding.tvCalories.text = String.format("%.1f", caloriesKcal)
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

        // EXPLICITLY specify the array type as <View?> and <TextView?>
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
            tvTabDay.setTextColor(Color.parseColor("#888888")); tvTabDay.typeface = android.graphics.Typeface.DEFAULT
            tvTabWeek.setTextColor(Color.parseColor("#888888")); tvTabWeek.typeface = android.graphics.Typeface.DEFAULT
            tvTabMonth.setTextColor(Color.parseColor("#888888")); tvTabMonth.typeface = android.graphics.Typeface.DEFAULT

            viewTabDayLine.visibility = View.INVISIBLE
            viewTabWeekLine.visibility = View.INVISIBLE
            viewTabMonthLine.visibility = View.INVISIBLE

            layoutDayChart.visibility = View.GONE
            layoutWeekChart.visibility = View.GONE
            layoutMonthChart.visibility = View.GONE

            when (currentMode) {
                0 -> { tvTabDay.setTextColor(Color.WHITE); tvTabDay.typeface = android.graphics.Typeface.DEFAULT_BOLD; viewTabDayLine.visibility = View.VISIBLE; layoutDayChart.visibility = View.VISIBLE }
                1 -> { tvTabWeek.setTextColor(Color.WHITE); tvTabWeek.typeface = android.graphics.Typeface.DEFAULT_BOLD; viewTabWeekLine.visibility = View.VISIBLE; layoutWeekChart.visibility = View.VISIBLE }
                2 -> { tvTabMonth.setTextColor(Color.WHITE); tvTabMonth.typeface = android.graphics.Typeface.DEFAULT_BOLD; viewTabMonthLine.visibility = View.VISIBLE; layoutMonthChart.visibility = View.VISIBLE }
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

                    requireActivity().runOnUiThread {
                        tvDateRange.text = dateText
                        tvTotalSteps.text = String.format("%,d steps", totalDailySteps)

                        dayBarsContainer.removeAllViews()
                        val maxSteps = hourlySteps.maxOrNull()?.coerceAtLeast(500L) ?: 1000L

                        for (i in 0..23) {
                            val stepCount = hourlySteps[i]
                            val weightBar = ((stepCount.toFloat() / maxSteps.toFloat()) * 100f).coerceIn(2f, 100f)
                            val weightSpace = 100f - weightBar

                            val container = LinearLayout(requireContext()).apply {
                                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                                orientation = LinearLayout.VERTICAL
                            }

                            val space = View(requireContext()).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, weightSpace) }

                            val bar = MaterialCardView(requireContext()).apply {
                                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, weightBar)
                                lp.setMargins(2, 0, 2, 0)
                                layoutParams = lp
                                setCardBackgroundColor(Color.parseColor("#64B5F6"))
                                radius = 4f
                                cardElevation = 0f
                            }

                            container.addView(space)
                            container.addView(bar)
                            dayBarsContainer.addView(container)
                        }
                    }
                } catch (e: Exception) { Log.e("WellnessFragment", "Error loading Day Data", e) }
            }
        }

        fun loadWeekData() {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val client = HealthConnectClient.getOrCreate(requireContext())
                    val endDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).minusWeeks(dateOffset.toLong())
                    val startDay = endDay.minusDays(6)

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

                        requireActivity().runOnUiThread {
                            // Safe call (?.) required since labels[i] is nullable
                            labels[i]?.text = currentDay.dayOfWeek.name.take(3)
                        }
                    }

                    requireActivity().runOnUiThread {
                        tvDateRange?.text = dateText
                        tvTotalSteps?.text = String.format("%,d steps", totalWeeklySteps)

                        val maxSteps = dailySteps.maxOrNull()?.coerceAtLeast(1000L) ?: 10000L

                        for (i in 0..6) {
                            val stepCount = dailySteps[i]
                            val weightBar = ((stepCount.toFloat() / maxSteps.toFloat()) * 100f).coerceIn(2f, 100f)
                            val weightSpace = 100f - weightBar

                            // Safe layout param extraction and assignment
                            val spaceParams = spaces[i]?.layoutParams as? LinearLayout.LayoutParams
                            if (spaceParams != null) {
                                spaceParams.weight = weightSpace
                                spaces[i]?.layoutParams = spaceParams
                            }

                            val barParams = bars[i]?.layoutParams as? LinearLayout.LayoutParams
                            if (barParams != null) {
                                barParams.weight = weightBar
                                bars[i]?.layoutParams = barParams
                            }
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
                    val targetMonthStart = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).minusMonths(dateOffset.toLong()).withDayOfMonth(1)
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

                    requireActivity().runOnUiThread {
                        tvDateRange.text = dateText
                        tvTotalSteps.text = String.format("%,d steps", totalMonthSteps)

                        monthGridContainer.removeAllViews()
                        val maxSteps = dailyStepsMap.values.maxOrNull()?.coerceAtLeast(1000L) ?: 10000L

                        val density = resources.displayMetrics.density
                        val maxBubbleSize = 55 * density
                        val minBubbleSize = 25 * density

                        for (day in 1..targetMonthStart.toLocalDate().lengthOfMonth()) {
                            val steps = dailyStepsMap[day] ?: 0L
                            val ratio = (steps.toFloat() / maxSteps.toFloat()).coerceIn(0.1f, 1f)
                            val bubbleSize = (minBubbleSize + (maxBubbleSize - minBubbleSize) * ratio).toInt()

                            val bubbleView = TextView(requireContext()).apply {
                                text = day.toString()
                                gravity = Gravity.CENTER
                                setTextColor(Color.WHITE)
                                textSize = 12f

                                val lp = GridLayout.LayoutParams()
                                lp.width = bubbleSize
                                lp.height = bubbleSize
                                lp.setMargins(-10, -10, -10, -10)
                                lp.setGravity(Gravity.CENTER)
                                layoutParams = lp

                                val bgShape = GradientDrawable()
                                bgShape.shape = GradientDrawable.OVAL
                                bgShape.setColor(Color.parseColor("#B364B5F6"))
                                background = bgShape
                            }
                            monthGridContainer.addView(bubbleView)
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

        tabDay.setOnClickListener { if (currentMode != 0) { currentMode = 0; dateOffset = 0; updateTabUI(); loadDataForCurrentMode() } }
        tabWeek.setOnClickListener { if (currentMode != 1) { currentMode = 1; dateOffset = 0; updateTabUI(); loadDataForCurrentMode() } }
        tabMonth.setOnClickListener { if (currentMode != 2) { currentMode = 2; dateOffset = 0; updateTabUI(); loadDataForCurrentMode() } }
        btnPrev.setOnClickListener { dateOffset++; loadDataForCurrentMode() }
        btnNext.setOnClickListener { if (dateOffset > 0) { dateOffset--; loadDataForCurrentMode() } }

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
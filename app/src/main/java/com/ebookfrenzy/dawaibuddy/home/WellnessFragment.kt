package com.ebookfrenzy.dawaibuddy.home

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import com.ebookfrenzy.dawaibuddy.objects.WellnessData
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WellnessFragment : Fragment(), MessageClient.OnMessageReceivedListener {

    private var _binding: FragmentWellnessBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // CONNECT TO GLOBAL AUDIO
    private val audioViewModel: SharedAudioViewModel by activityViewModels()

    private var currentTrackList: List<MeditationTrack> = emptyList()

    private var progressHandler: Handler? = null
    private var progressRunnable: Runnable? = null

    // Handlers
    private val autoSyncHandler = Handler(Looper.getMainLooper())
    private var autoSyncRunnable: Runnable? = null
    private var waterTotalListener: ListenerRegistration? = null
    private var hrDialog: AlertDialog? = null

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
        updateWatchStatusUI(false, "Not Connected")

        val cvWatchStatus = binding.root.findViewById<MaterialCardView>(R.id.cvWatchStatus)
        cvWatchStatus?.isClickable = true
        cvWatchStatus?.isFocusable = true
        cvWatchStatus?.setOnClickListener { handleWatchStatusClick() }

        val cvHeartRateCard = binding.root.findViewById<MaterialCardView>(R.id.cvHeartRateCard)
        cvHeartRateCard?.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("WellnessPrefs", Context.MODE_PRIVATE)
            if (prefs.getString("data_source", "none") == "wear") {
                startWatchHeartRateMeasurement()
            } else {
                Toast.makeText(requireContext(), "Connect a Wear OS watch to take live measurements.", Toast.LENGTH_SHORT).show()
            }
        }

        autoSyncRunnable = Runnable {
            if (_binding != null && isAdded) {
                triggerSilentSync()
                autoSyncHandler.postDelayed(autoSyncRunnable!!, 5000)
            }
        }

        setDynamicGreeting()
        fetchUserData()
        fetchAllTracks()
        fetchTodayWaterTotal()
        fetchTodayMood()
        checkHealthConnectStatus()

        val userStreakDays = 1
        updateStreakInProfile(userStreakDays)

        val prefs = requireContext().getSharedPreferences("WellnessPrefs", Context.MODE_PRIVATE)
        val promptShown = prefs.getBoolean("wear_prompt_shown", false)
        if (!promptShown) showDataSourceDialog(prefs)

        binding.cvConnectHealth.setOnClickListener { handleConnectHealthClick() }
        binding.cvStepsCard.setOnClickListener { showTrendDialog() }

        binding.root.findViewById<View>(R.id.cvWaterCard)?.setOnClickListener {
            findNavController().navigate(R.id.action_nav_wellness_to_waterLoggerFragment)
        }
        binding.root.findViewById<View>(R.id.cvMoodCard)?.setOnClickListener {
            findNavController().navigate(R.id.action_nav_wellness_to_moodTrackerFragment)
        }

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

        val cvPlay = binding.root.findViewById<View>(R.id.cvMindfulnessPlay)
        val ivPrev = binding.root.findViewById<View>(R.id.ivMindfulnessPrev)
        val ivNext = binding.root.findViewById<View>(R.id.ivMindfulnessNext)

        cvPlay?.setOnClickListener {
            if (audioViewModel.currentTrack.value != null) {
                audioViewModel.player?.let { player -> if (player.isPlaying) player.pause() else player.play() }
            } else playRandomTrack()
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
            } else playRandomTrack()
        }
    }

    override fun onResume() {
        super.onResume()
        fetchUserData()
        Wearable.getMessageClient(requireContext()).addListener(this)
        autoSyncRunnable?.let { autoSyncHandler.post(it) }
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(requireContext()).removeListener(this)
        autoSyncRunnable?.let { autoSyncHandler.removeCallbacks(it) }
    }

    // =========================================================================================
    // --- SMART DATA SYNC PIPELINE ---
    // =========================================================================================

    // BEAMS the exact UI values directly to the watch so they always match perfectly!
    private fun beamToWatch(type: String, value: Any) {
        if (!isAdded || context == null) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val capabilityInfo = Wearable.getCapabilityClient(requireContext())
                    .getCapability("com.ebookfrenzy.dawaibuddy", CapabilityClient.FILTER_REACHABLE)
                    .await()
                if (capabilityInfo.nodes.isNotEmpty()) {
                    val targetNodeId = capabilityInfo.nodes.first().id
                    val payload = JSONObject().apply {
                        put("type", type)
                        put("value", value)
                    }.toString()
                    Wearable.getMessageClient(requireContext())
                        .sendMessage(targetNodeId, "/update_watch_data", payload.toByteArray())
                }
            } catch (e: Exception) {
                // Ignore silent background failures
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/hr_measure_result") {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                val json = JSONObject(String(messageEvent.data))
                val heartRate = json.optInt("heartRate", 0)
                if (heartRate > 0) {
                    binding.tvHeartRate.text = heartRate.toString()
                    hrDialog?.dismiss()
                    Toast.makeText(requireContext(), "Live Heart Rate Updated!", Toast.LENGTH_SHORT).show()
                } else {
                    hrDialog?.dismiss()
                    Toast.makeText(requireContext(), "Measurement failed. Try wearing watch tighter.", Toast.LENGTH_LONG).show()
                }
            }
        }

        if (messageEvent.path == "/sync_response") {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                try {
                    val json = JSONObject(String(messageEvent.data))
                    val steps = json.optInt("steps", 0)
                    val heartRate = json.optInt("heartRate", 0)
                    val waterMl = json.optInt("waterMl", 0)
                    val mood = json.optString("mood", "")

                    updateWatchStatusUI(true, "Synced Just Now")

                    if (steps > 0) {
                        requireContext().getSharedPreferences("WellnessPrefs", Context.MODE_PRIVATE)
                            .edit().putInt("watch_today_steps", steps).apply()

                        binding.tvStepsCount.text = String.format("%,d", steps)
                        val progress = ((steps.toFloat() / 10000f) * 100).toInt()
                        binding.pbSteps.progress = progress.coerceAtMost(100)
                        binding.tvDistance.text = String.format(Locale.US, "%.2f", steps * 0.000762)
                        binding.tvCalories.text = String.format(Locale.US, "%.0f", steps * 0.04)
                    }

                    if (heartRate > 0) binding.tvHeartRate.text = heartRate.toString()
                    if (waterMl > 0) saveSyncedWellnessData("water", amountMl = waterMl)
                    if (mood.isNotEmpty() && mood != "NONE") saveSyncedWellnessData("mood", mood = mood)

                } catch (e: Exception) {
                    Log.e("WellnessFragment", "Error parsing sync data", e)
                    updateWatchStatusUI(true, "Sync Error")
                }
            }
        }
    }

    private fun startWatchHeartRateMeasurement() {
        val dialogView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(60, 60, 60, 60)

            val icon = ImageView(requireContext()).apply {
                setImageResource(R.drawable.heart_rate_wellness)
                setColorFilter(Color.parseColor("#E91E63"))
                layoutParams = LinearLayout.LayoutParams(120, 120).apply { bottomMargin = 40 }
            }

            val titleText = TextView(requireContext()).apply {
                this.text = "Measuring on Watch..."
                this.textSize = 18f
                this.setTypeface(null, Typeface.BOLD)
                this.gravity = Gravity.CENTER
                this.setTextColor(Color.BLACK)
            }

            val subText = TextView(requireContext()).apply {
                this.text = "Please keep your wrist still for 30 seconds."
                this.textSize = 14f
                this.gravity = Gravity.CENTER
                this.setTextColor(Color.DKGRAY)
                this.setPadding(0, 20, 0, 0)
            }

            addView(icon)
            addView(titleText)
            addView(subText)
        }

        hrDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .show()

        hrDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val nodes = Wearable.getNodeClient(requireContext()).connectedNodes.await()
                if (nodes.isNotEmpty()) {
                    Wearable.getMessageClient(requireContext())
                        .sendMessage(nodes.first().id, "/measure_hr_request", ByteArray(0)).await()
                }
            } catch(e: Exception) {
                hrDialog?.dismiss()
                Toast.makeText(requireContext(), "Failed to reach watch.", Toast.LENGTH_SHORT).show()
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (hrDialog?.isShowing == true) {
                hrDialog?.dismiss()
                Toast.makeText(requireContext(), "Measurement timed out.", Toast.LENGTH_SHORT).show()
            }
        }, 35000)
    }

    private fun saveSyncedWellnessData(type: String, amountMl: Int = 0, mood: String = "") {
        val user = auth.currentUser ?: return
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (type == "water") {
            db.collection("users").document(user.uid).collection("water_logs").document(todayStr)
                .get().addOnSuccessListener { doc ->
                    val cycle = doc.getLong("currentCycle")?.toInt() ?: 1
                    val newDoc = db.collection("users").document(user.uid)
                        .collection("water_logs").document(todayStr)
                        .collection("log_$cycle").document()

                    val entry = WellnessData(
                        id = newDoc.id, userId = user.uid, type = "water", amountMl = amountMl,
                        timestamp = System.currentTimeMillis(), date = todayStr, cycle = cycle
                    )
                    newDoc.set(entry)
                }
        } else if (type == "mood") {
            val newDoc = db.collection("users").document(user.uid)
                .collection("wellness_data").document()

            val entry = WellnessData(
                id = newDoc.id, userId = user.uid, type = "mood", mood = mood,
                timestamp = System.currentTimeMillis(), date = todayStr
            )
            newDoc.set(entry)
        }
    }

    private fun handleWatchStatusClick() {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
            val hasWatch = doc.getBoolean("hasWatch") ?: false
            if (hasWatch) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val capabilityInfo = Wearable.getCapabilityClient(requireContext())
                        .getCapability("com.ebookfrenzy.dawaibuddy", CapabilityClient.FILTER_REACHABLE)
                        .await()

                    withContext(Dispatchers.Main) {
                        if (capabilityInfo.nodes.isNotEmpty()) {
                            val targetNodeId = capabilityInfo.nodes.first().id
                            val options = arrayOf("Sync Watch Data", "Disconnect Watch")
                            AlertDialog.Builder(requireContext())
                                .setTitle("Watch Options")
                                .setItems(options) { _, which ->
                                    if (which == 0) requestSyncFromWatch(targetNodeId, isManual = true)
                                    else confirmDisconnect(user.uid)
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        } else confirmDisconnect(user.uid)
                    }
                }
            } else handleConnectHealthClick()
        }
    }

    private fun triggerSilentSync() {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
            if (doc.getBoolean("hasWatch") == true) {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val capabilityInfo = Wearable.getCapabilityClient(requireContext())
                            .getCapability("com.ebookfrenzy.dawaibuddy", CapabilityClient.FILTER_REACHABLE)
                            .await()
                        if (capabilityInfo.nodes.isNotEmpty()) {
                            val targetNodeId = capabilityInfo.nodes.first().id
                            requestSyncFromWatch(targetNodeId, isManual = false)
                        }
                    } catch (e: Exception) {}
                }
            }
        }
    }

    private fun requestSyncFromWatch(nodeId: String, isManual: Boolean = true) {
        if (isManual) updateWatchStatusUI(true, "Syncing...")

        Wearable.getMessageClient(requireContext())
            .sendMessage(nodeId, "/sync_request", ByteArray(0))
            .addOnSuccessListener {
                if (isManual) {
                    Toast.makeText(requireContext(), "Requesting data from watch...", Toast.LENGTH_SHORT).show()
                    Handler(Looper.getMainLooper()).postDelayed({
                        val tvWatchStatus = binding.root.findViewById<TextView>(R.id.tvWatchStatus)
                        if (tvWatchStatus?.text == "Syncing...") updateWatchStatusUI(true, "Sync Timeout")
                    }, 8000)
                }
            }
            .addOnFailureListener {
                if (isManual) {
                    updateWatchStatusUI(true, "Sync Failed")
                    Toast.makeText(requireContext(), "Failed to reach watch.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun confirmDisconnect(uid: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Disconnect Watch")
            .setMessage("Do you want to unlink your Wear OS watch? This will log out the watch app from your profile.")
            .setPositiveButton("Disconnect") { _, _ -> disconnectWatch(uid) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun disconnectWatch(uid: String) {
        db.collection("users").document(uid).update("hasWatch", false)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Watch Disconnected", Toast.LENGTH_SHORT).show()
                val prefs = requireContext().getSharedPreferences("WellnessPrefs", Context.MODE_PRIVATE)
                prefs.edit().putString("data_source", "phone").apply()

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val nodes = Wearable.getNodeClient(requireContext()).connectedNodes.await()
                        nodes.forEach { node ->
                            Wearable.getMessageClient(requireContext())
                                .sendMessage(node.id, "/logout_request", ByteArray(0)).await()
                        }
                    } catch (e: Exception) {}
                }

                updateWatchStatusUI(false, "Not Connected")
                checkHealthConnectStatus()
            }
    }

    private fun handleConnectHealthClick() {
        val prefs = requireContext().getSharedPreferences("WellnessPrefs", Context.MODE_PRIVATE)
        showDataSourceDialog(prefs)
    }

    private fun showDataSourceDialog(prefs: SharedPreferences) {
        AlertDialog.Builder(requireContext())
            .setTitle("Connect Health Data")
            .setMessage("How would you like to track your vitals?")
            .setPositiveButton("Connect Wear OS Watch") { _, _ ->
                prefs.edit().putBoolean("wear_prompt_shown", true).apply()
                initiateWearOsConnection(prefs)
            }
            .setNegativeButton("Skip (Phone Sensors)") { _, _ ->
                prefs.edit().putString("data_source", "phone").putBoolean("wear_prompt_shown", true).apply()
                launchHealthConnectPermissions()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun initiateWearOsConnection(prefs: SharedPreferences) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val nodes = Wearable.getNodeClient(requireContext()).connectedNodes.await()
                if (nodes.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("No Watch Detected")
                            .setMessage("Please ensure your Bluetooth is turned on and your Wear OS watch is paired and connected.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    return@launch
                }

                val capabilityInfo = Wearable.getCapabilityClient(requireContext())
                    .getCapability("com.ebookfrenzy.dawaibuddy", CapabilityClient.FILTER_ALL)
                    .await()

                if (capabilityInfo.nodes.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Watch App Status")
                            .setMessage("We couldn't verify if the app is installed on your watch yet.\n\nIf you JUST installed it, Google Play Services takes a minute to sync.")
                            .setPositiveButton("Force Connect") { _, _ -> loginWatch(nodes.first().id, prefs) }
                            .setNeutralButton("Download App") { _, _ ->
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dotted-journey-473912-h4.web.app/")))
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                } else {
                    loginWatch(capabilityInfo.nodes.first().id, prefs)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error connecting to watch.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loginWatch(nodeId: String, prefs: SharedPreferences) {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).update("hasWatch", true)
            .addOnSuccessListener {
                val jsonPayload = JSONObject().apply {
                    put("success", true)
                    put("name", user.displayName ?: "User")
                }.toString()

                Wearable.getMessageClient(requireContext())
                    .sendMessage(nodeId, "/login_response", jsonPayload.toByteArray())
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Watch Linked Successfully!", Toast.LENGTH_LONG).show()
                        prefs.edit().putString("data_source", "wear").putBoolean("wear_prompt_shown", true).apply()
                        binding.cvConnectHealth.visibility = View.GONE
                        verifyActiveWatchConnection()
                    }
            }
    }

    private fun setDynamicGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.tvGreeting.text = when (hour) {
            in 0..11 -> "Good Morning,"
            in 12..16 -> "Good Afternoon,"
            in 17..20 -> "Good Evening,"
            else -> "Good Night,"
        }
    }

    private fun updateStreakInProfile(streakDays: Int) {
        val streakText = streakDays.toString()
        val cvProfile = binding.root.findViewById<MaterialCardView>(R.id.cvProfile) ?: return

        var tvStreak = cvProfile.findViewWithTag<TextView>("streakLabel")
        if (tvStreak == null) {
            tvStreak = TextView(requireContext()).apply {
                tag = "streakLabel"
                setTextColor(Color.parseColor("#4CAF50"))
                setTypeface(null, Typeface.BOLD)
                textSize = 32f
                gravity = Gravity.CENTER
            }
            val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = (16 * resources.displayMetrics.density).toInt()
            }
            cvProfile.addView(tvStreak, params)
        }
        tvStreak.text = streakText
    }

    private fun updateWatchStatusUI(isConnected: Boolean, statusText: String) {
        if (_binding == null) return
        val cvWatchStatus = binding.root.findViewById<MaterialCardView>(R.id.cvWatchStatus)
        val tvWatchStatus = binding.root.findViewById<TextView>(R.id.tvWatchStatus)

        tvWatchStatus?.text = statusText
        if (isConnected) {
            cvWatchStatus?.setCardBackgroundColor(Color.parseColor("#E8F5E9"))
            tvWatchStatus?.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            cvWatchStatus?.setCardBackgroundColor(Color.parseColor("#F5F5F5"))
            tvWatchStatus?.setTextColor(Color.parseColor("#757575"))
        }
    }

    private fun verifyActiveWatchConnection() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val capabilityInfo = Wearable.getCapabilityClient(requireContext())
                    .getCapability("com.ebookfrenzy.dawaibuddy", CapabilityClient.FILTER_REACHABLE)
                    .await()
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    if (capabilityInfo.nodes.isNotEmpty()) {
                        updateWatchStatusUI(true, "Connected • Tap to Sync")
                        binding.cvConnectHealth.visibility = View.GONE
                    } else {
                        updateWatchStatusUI(false, "Watch Offline")
                        binding.cvConnectHealth.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { if (_binding != null) updateWatchStatusUI(false, "Offline") }
            }
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
        }
    }

    private fun fetchTodayWaterTotal() {
        val user = auth.currentUser ?: return
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        db.collection("users").document(user.uid).collection("water_logs").document(todayStr)
            .addSnapshotListener { doc, _ ->
                if (_binding == null) return@addSnapshotListener
                val currentCycle = doc?.getLong("currentCycle")?.toInt() ?: 1

                waterTotalListener?.remove()
                waterTotalListener = db.collection("users").document(user.uid)
                    .collection("water_logs").document(todayStr)
                    .collection("log_$currentCycle")
                    .addSnapshotListener { snapshot, _ ->
                        if (_binding == null) return@addSnapshotListener
                        var totalMl = 0
                        snapshot?.documents?.forEach { totalMl += it.getLong("amountMl")?.toInt() ?: 0 }
                        binding.tvWater.text = String.format(Locale.getDefault(), "%.1f", totalMl / 1000f)

                        // BI-DIRECTIONAL: Beam the latest total straight to the watch
                        beamToWatch("water", totalMl)
                    }
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

                    binding.tvMood.text = when(mood) {
                        "Happy" -> "😊"
                        "Neutral" -> "😐"
                        "Sad" -> "😔"
                        "Stressed" -> "😠"
                        else -> "-"
                    }
                    // BI-DIRECTIONAL: Beam the latest mood straight to the watch
                    beamToWatch("mood", mood)
                } else {
                    binding.tvMood.text = "-"
                    beamToWatch("mood", "NONE")
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
                        updateWatchStatusUI(false, "Not Connected")
                        return@addSnapshotListener
                    }
                    val user = document.toObject(User::class.java)
                    val firstName = user?.name?.split(" ")?.firstOrNull() ?: "User"
                    binding.tvGreetingName.text = firstName

                    if (user?.hasWatch == true) verifyActiveWatchConnection()
                    else updateWatchStatusUI(false, "Not Connected")
                }
        } else {
            if (_binding != null) {
                binding.tvGreetingName.text = "Guest"
                updateWatchStatusUI(false, "Not Connected")
            }
        }
    }

    private fun checkHealthConnectStatus() {
        val user = auth.currentUser
        if (user != null) {
            db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
                if (_binding == null) return@addOnSuccessListener
                if (doc.getBoolean("hasWatch") == true) binding.cvConnectHealth.visibility = View.GONE
                else runHealthConnectCheck()
            }
        } else runHealthConnectCheck()
    }

    private fun runHealthConnectCheck() {
        if (_binding == null) return
        val prefs = requireContext().getSharedPreferences("WellnessPrefs", Context.MODE_PRIVATE)
        if (prefs.getString("data_source", "none") == "wear") {
            binding.cvConnectHealth.visibility = View.GONE
            return
        }

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

                val currentActivity = activity ?: return@launch
                currentActivity.runOnUiThread {
                    if (_binding == null) return@runOnUiThread

                    binding.tvStepsCount.text = String.format("%,d", todaySteps)
                    binding.pbSteps.progress = progress.coerceAtMost(100)
                    binding.tvDistance.text = String.format(Locale.US, "%.2f", distanceKm)
                    binding.tvCalories.text = String.format(Locale.US, "%.0f", caloriesKcal)

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

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bgColor = if (isNightMode) Color.parseColor("#121212") else Color.WHITE
        val textColor = if (isNightMode) Color.WHITE else Color.parseColor("#212121")
        val subTextColor = if (isNightMode) Color.parseColor("#9E9E9E") else Color.parseColor("#757575")

        val neonGreenSteps = Color.parseColor("#00FF7F")
        val neonPinkCalories = Color.parseColor("#FF1493")
        val darkGreenFill = Color.parseColor("#1B4228")

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

        val dayStepsBarsContainer = dialog.findViewById<LinearLayout>(R.id.dayStepsBarsContainer)
        val dayCaloriesBarsContainer = dialog.findViewById<LinearLayout>(R.id.dayCaloriesBarsContainer)
        val weekStepsBarsContainer = dialog.findViewById<LinearLayout>(R.id.weekStepsBarsContainer)
        val weekCaloriesBarsContainer = dialog.findViewById<LinearLayout>(R.id.weekCaloriesBarsContainer)
        val weekXAxis = dialog.findViewById<LinearLayout>(R.id.weekXAxis)
        val weekCaloriesXAxis = dialog.findViewById<LinearLayout>(R.id.weekCaloriesXAxis)
        val monthGridContainer = dialog.findViewById<GridLayout>(R.id.monthGridContainer)

        val tvDayStepsMax = dialog.findViewById<TextView>(R.id.tvDayStepsMax)
        val tvDayStepsMid = dialog.findViewById<TextView>(R.id.tvDayStepsMid)
        val tvDayCalMax = dialog.findViewById<TextView>(R.id.tvDayCalMax)
        val tvDayCalMid = dialog.findViewById<TextView>(R.id.tvDayCalMid)

        val tvWeekStepsMax = dialog.findViewById<TextView>(R.id.tvWeekStepsMax)
        val tvWeekStepsMid = dialog.findViewById<TextView>(R.id.tvWeekStepsMid)
        val tvWeekCalMax = dialog.findViewById<TextView>(R.id.tvWeekCalMax)
        val tvWeekCalMid = dialog.findViewById<TextView>(R.id.tvWeekCalMid)

        val rootGroup = dialog.findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0)
        rootGroup?.background = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadii = floatArrayOf(60f, 60f, 60f, 60f, 0f, 0f, 0f, 0f)
        }

        var currentMode = 1
        var dateOffset = 0

        fun updateTabUI() {
            tvTabDay?.setTextColor(subTextColor); tvTabDay?.typeface = android.graphics.Typeface.DEFAULT
            tvTabWeek?.setTextColor(subTextColor); tvTabWeek?.typeface = android.graphics.Typeface.DEFAULT
            tvTabMonth?.setTextColor(subTextColor); tvTabMonth?.typeface = android.graphics.Typeface.DEFAULT

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

        fun populateGraph(container: LinearLayout?, data: List<Float>, barColor: Int, isToday: Boolean, currentIndexToHighlight: Int?, minMaxAllowed: Float, tvMax: TextView?, tvMid: TextView?) {
            if (container == null) return
            container.removeAllViews()

            val maxValue = data.maxOrNull()?.coerceAtLeast(minMaxAllowed) ?: minMaxAllowed
            if (tvMax != null && tvMid != null) {
                val formatLabel = { v: Float -> if (v >= 1000f) String.format(Locale.US, "%.1fk", v / 1000f) else v.toInt().toString() }
                tvMax.text = formatLabel(maxValue)
                tvMid.text = formatLabel(maxValue / 2f)
            }

            for (i in data.indices) {
                val value = data[i]
                val weightBar = if (value > 0f) (value / maxValue * 100f).coerceIn(1f, 100f) else 0f
                val weightSpace = 100f - weightBar

                val col = LinearLayout(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    orientation = LinearLayout.VERTICAL
                    weightSum = 100f
                    setPadding((resources.displayMetrics.density * 2).toInt(), 0, (resources.displayMetrics.density * 2).toInt(), 0)
                }

                col.addView(View(requireContext()).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, weightSpace) })
                col.addView(MaterialCardView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, weightBar)
                    setCardBackgroundColor(barColor)
                    radius = 50f; cardElevation = 0f
                    if (value <= 0f) visibility = View.INVISIBLE
                    else if (isToday && currentIndexToHighlight == i) { strokeWidth = 3; strokeColor = Color.WHITE }
                    else strokeWidth = 0
                })
                container.addView(col)
            }
        }

        fun populateWeekXAxis(container: LinearLayout?, startDay: LocalDateTime, isTodayCheck: (LocalDateTime) -> Boolean) {
            if (container == null) return
            container.removeAllViews()
            for (i in 0..6) {
                val currentDay = startDay.plusDays(i.toLong())
                val isToday = isTodayCheck(currentDay)
                container.addView(TextView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    text = currentDay.dayOfWeek.name.take(3)
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setTextColor(if (isToday) neonGreenSteps else subTextColor)
                    typeface = if (isToday) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                })
            }
        }

        fun showDayPopup(targetDay: LocalDateTime) {
            val popup = Dialog(requireContext())
            popup.setContentView(R.layout.dialog_step_trends)
            popup.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            popup.window?.decorView?.setPadding(0, 0, 0, 0)
            val popupParams = popup.window?.attributes
            popupParams?.width = WindowManager.LayoutParams.MATCH_PARENT
            popupParams?.height = WindowManager.LayoutParams.WRAP_CONTENT
            popupParams?.gravity = Gravity.BOTTOM
            popup.window?.attributes = popupParams
            popup.window?.setWindowAnimations(android.R.style.Animation_Dialog)

            val pRootGroup = popup.findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0)
            pRootGroup?.background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadii = floatArrayOf(60f, 60f, 60f, 60f, 0f, 0f, 0f, 0f)
            }

            popup.findViewById<LinearLayout>(R.id.tabDay)?.visibility = View.GONE
            popup.findViewById<LinearLayout>(R.id.tabWeek)?.visibility = View.GONE
            popup.findViewById<LinearLayout>(R.id.tabMonth)?.visibility = View.GONE
            (popup.findViewById<LinearLayout>(R.id.tabDay)?.parent as? ViewGroup)?.visibility = View.GONE

            popup.findViewById<ImageView>(R.id.btnPrev)?.visibility = View.INVISIBLE
            popup.findViewById<ImageView>(R.id.btnNext)?.visibility = View.INVISIBLE

            popup.findViewById<LinearLayout>(R.id.layoutWeekChart)?.visibility = View.GONE
            popup.findViewById<LinearLayout>(R.id.layoutMonthChart)?.visibility = View.GONE
            popup.findViewById<LinearLayout>(R.id.layoutDayChart)?.visibility = View.VISIBLE

            val pTvDateRange = popup.findViewById<TextView>(R.id.tvDateRange)
            val pTvTotalSteps = popup.findViewById<TextView>(R.id.tvTotalSteps)

            val pDayStepsBarsContainer = popup.findViewById<LinearLayout>(R.id.dayStepsBarsContainer)
            val pDayCaloriesBarsContainer = popup.findViewById<LinearLayout>(R.id.dayCaloriesBarsContainer)

            val pTvDayStepsMax = popup.findViewById<TextView>(R.id.tvDayStepsMax)
            val pTvDayStepsMid = popup.findViewById<TextView>(R.id.tvDayStepsMid)
            val pTvDayCalMax = popup.findViewById<TextView>(R.id.tvDayCalMax)
            val pTvDayCalMid = popup.findViewById<TextView>(R.id.tvDayCalMid)

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val client = HealthConnectClient.getOrCreate(requireContext())
                    val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")
                    val dateText = targetDay.format(formatter)

                    var totalDailySteps = 0L
                    val hourlySteps = mutableListOf<Float>()
                    val hourlyCalories = mutableListOf<Float>()

                    for (i in 0..23) {
                        val currentHour = targetDay.truncatedTo(ChronoUnit.DAYS).plusHours(i.toLong())
                        val nextHour = currentHour.plusHours(1)

                        val response = client.aggregate(
                            AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = TimeRangeFilter.between(currentHour, nextHour))
                        )
                        val steps = response[StepsRecord.COUNT_TOTAL] ?: 0L
                        hourlySteps.add(steps.toFloat())
                        hourlyCalories.add(steps * 0.04f)
                        totalDailySteps += steps
                    }

                    val currentActivity = activity ?: return@launch
                    currentActivity.runOnUiThread {
                        pTvDateRange?.text = dateText
                        pTvTotalSteps?.text = String.format("👟 %,d steps", totalDailySteps)

                        val isToday = targetDay.toLocalDate() == LocalDateTime.now().toLocalDate()
                        val currentHourIndex = LocalDateTime.now().hour

                        populateGraph(pDayStepsBarsContainer, hourlySteps, neonGreenSteps, isToday, currentHourIndex, 500f, pTvDayStepsMax, pTvDayStepsMid)
                        populateGraph(pDayCaloriesBarsContainer, hourlyCalories, neonPinkCalories, isToday, currentHourIndex, 50f, pTvDayCalMax, pTvDayCalMid)
                    }
                } catch (e: Exception) { Log.e("WellnessFragment", "Error loading Popup Day Data", e) }
            }

            popup.show()
        }

        fun loadDayData() {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val client = HealthConnectClient.getOrCreate(requireContext())
                    val targetDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(dateOffset.toLong())
                    val dateText = targetDay.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
                    val isToday = targetDay.toLocalDate() == LocalDateTime.now().toLocalDate()

                    val prefs = requireContext().getSharedPreferences("WellnessPrefs", Context.MODE_PRIVATE)
                    val isWearLinked = prefs.getString("data_source", "none") == "wear"
                    val watchStepsToday = prefs.getInt("watch_today_steps", 0)

                    val hourlySteps = mutableListOf<Float>()
                    val hourlyCalories = mutableListOf<Float>()
                    var totalDailySteps = 0L

                    if (isToday && isWearLinked && watchStepsToday > 0) {
                        totalDailySteps = watchStepsToday.toLong()
                        val currentHour = LocalDateTime.now().hour
                        val safePastHours = currentHour.coerceAtLeast(1)
                        val fakeHistorical = watchStepsToday * 0.7f
                        for(i in 0 until 24) hourlySteps.add(0f)
                        for(i in 0 until currentHour) hourlySteps[i] = (fakeHistorical / safePastHours) * (0.5f + (i % 3) * 0.3f)
                        hourlySteps[currentHour] = (watchStepsToday * 0.3f) + (watchStepsToday % 50)
                        hourlySteps.forEach { hourlyCalories.add(it * 0.04f) }
                    } else {
                        for (i in 0..23) {
                            val currentHour = targetDay.plusHours(i.toLong())
                            val steps = client.aggregate(AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = TimeRangeFilter.between(currentHour, currentHour.plusHours(1))))[StepsRecord.COUNT_TOTAL] ?: 0L
                            hourlySteps.add(steps.toFloat())
                            hourlyCalories.add(steps * 0.04f)
                            totalDailySteps += steps
                        }
                    }

                    val currentActivity = activity ?: return@launch
                    currentActivity.runOnUiThread {
                        tvDateRange?.text = dateText
                        tvTotalSteps?.text = String.format("👟 %,d steps", totalDailySteps)

                        val currentHourIndex = LocalDateTime.now().hour
                        populateGraph(dayStepsBarsContainer, hourlySteps, neonGreenSteps, isToday, currentHourIndex, 500f, tvDayStepsMax, tvDayStepsMid)
                        populateGraph(dayCaloriesBarsContainer, hourlyCalories, neonPinkCalories, isToday, currentHourIndex, 50f, tvDayCalMax, tvDayCalMid)
                    }
                } catch (e: Exception) { Log.e("WellnessFragment", "Error loading Day Data", e) }
            }
        }

        fun loadWeekData() {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val client = HealthConnectClient.getOrCreate(requireContext())
                    val now = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)
                    val targetWeekDate = now.minusWeeks(dateOffset.toLong())
                    val startDay = targetWeekDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val formatter = DateTimeFormatter.ofPattern("d MMM")
                    val dateText = "${startDay.format(formatter)} – ${startDay.plusDays(6).format(formatter)}"

                    val prefs = requireContext().getSharedPreferences("WellnessPrefs", Context.MODE_PRIVATE)
                    val isWearLinked = prefs.getString("data_source", "none") == "wear"
                    val watchStepsToday = prefs.getInt("watch_today_steps", 0)

                    var totalWeeklySteps = 0L
                    val dailySteps = mutableListOf<Float>()
                    val dailyCalories = mutableListOf<Float>()

                    for (i in 0..6) {
                        val currentDay = startDay.plusDays(i.toLong())
                        val isToday = currentDay.toLocalDate() == LocalDateTime.now().toLocalDate()

                        val finalSteps = if (isToday && isWearLinked && watchStepsToday > 0) {
                            watchStepsToday.toLong()
                        } else {
                            client.aggregate(AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = TimeRangeFilter.between(currentDay, currentDay.plusDays(1))))[StepsRecord.COUNT_TOTAL] ?: 0L
                        }

                        dailySteps.add(finalSteps.toFloat())
                        dailyCalories.add(finalSteps * 0.04f)
                        totalWeeklySteps += finalSteps
                    }

                    val currentActivity = activity ?: return@launch
                    currentActivity.runOnUiThread {
                        tvDateRange?.text = dateText
                        tvTotalSteps?.text = String.format("👟 %,d steps", totalWeeklySteps)
                        var todayIndex: Int? = null
                        for (i in 0..6) { if (startDay.plusDays(i.toLong()).toLocalDate() == LocalDateTime.now().toLocalDate()) todayIndex = i }

                        populateGraph(weekStepsBarsContainer, dailySteps, neonGreenSteps, todayIndex != null, todayIndex, 5000f, tvWeekStepsMax, tvWeekStepsMid)
                        populateGraph(weekCaloriesBarsContainer, dailyCalories, neonPinkCalories, todayIndex != null, todayIndex, 500f, tvWeekCalMax, tvWeekCalMid)
                        populateWeekXAxis(weekXAxis, startDay) { it.toLocalDate() == LocalDateTime.now().toLocalDate() }
                        populateWeekXAxis(weekCaloriesXAxis, startDay) { it.toLocalDate() == LocalDateTime.now().toLocalDate() }
                    }
                } catch (e: Exception) { Log.e("WellnessFragment", "Error loading Week Data", e) }
            }
        }

        fun loadMonthData() {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val client = HealthConnectClient.getOrCreate(requireContext())
                    val targetMonthStart = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1).minusMonths(dateOffset.toLong())
                    val dateText = targetMonthStart.format(DateTimeFormatter.ofPattern("MMMM yyyy"))

                    val prefs = requireContext().getSharedPreferences("WellnessPrefs", Context.MODE_PRIVATE)
                    val isWearLinked = prefs.getString("data_source", "none") == "wear"
                    val watchStepsToday = prefs.getInt("watch_today_steps", 0)

                    var totalMonthSteps = 0L
                    val dailyStepsMap = mutableMapOf<Int, Long>()

                    for (i in 0 until targetMonthStart.toLocalDate().lengthOfMonth()) {
                        val currentDay = targetMonthStart.plusDays(i.toLong())
                        val isToday = currentDay.toLocalDate() == LocalDateTime.now().toLocalDate()

                        val finalSteps = if (isToday && isWearLinked && watchStepsToday > 0) {
                            watchStepsToday.toLong()
                        } else {
                            client.aggregate(AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = TimeRangeFilter.between(currentDay, currentDay.plusDays(1))))[StepsRecord.COUNT_TOTAL] ?: 0L
                        }

                        dailyStepsMap[i + 1] = finalSteps
                        totalMonthSteps += finalSteps
                    }

                    val currentActivity = activity ?: return@launch
                    currentActivity.runOnUiThread {
                        tvDateRange?.text = dateText
                        tvTotalSteps?.text = String.format("👟 %,d steps", totalMonthSteps)
                        monthGridContainer?.removeAllViews()

                        val density = resources.displayMetrics.density
                        val cellWidth = (resources.displayMetrics.widthPixels * 0.9f / 7f).toInt()
                        val marginPx = (2 * density).toInt()
                        val bubbleSize = cellWidth - (marginPx * 2)

                        for (i in 0 until (targetMonthStart.dayOfWeek.value - 1)) {
                            monthGridContainer?.addView(Space(requireContext()).apply { layoutParams = GridLayout.LayoutParams().apply { width = bubbleSize; height = bubbleSize; setMargins(marginPx, marginPx, marginPx, marginPx) } })
                        }

                        for (day in 1..targetMonthStart.toLocalDate().lengthOfMonth()) {
                            val steps = dailyStepsMap[day] ?: 0L
                            val isToday = targetMonthStart.plusDays((day - 1).toLong()).toLocalDate() == LocalDateTime.now().toLocalDate()
                            monthGridContainer?.addView(TextView(requireContext()).apply {
                                text = day.toString()
                                textSize = 14f; gravity = Gravity.CENTER
                                typeface = if (isToday) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                                layoutParams = GridLayout.LayoutParams().apply { width = bubbleSize; height = bubbleSize; setMargins(marginPx, marginPx, marginPx, marginPx); setGravity(Gravity.CENTER) }

                                val bgShape = GradientDrawable().apply { setShape(GradientDrawable.OVAL) }
                                if (isToday) { setTextColor(neonGreenSteps); bgShape.setColor(Color.TRANSPARENT); bgShape.setStroke((2 * density).toInt(), neonGreenSteps); background = bgShape }
                                else if (steps > 0) { setTextColor(Color.WHITE); bgShape.setColor(darkGreenFill); background = bgShape }
                                else { setTextColor(subTextColor); background = null }
                            })
                        }
                    }
                } catch (e: Exception) { Log.e("WellnessFragment", "Error loading Month Data", e) }
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
        waterTotalListener?.remove()
        progressRunnable?.let { progressHandler?.removeCallbacks(it) }
        progressHandler = null
        hrDialog?.dismiss()
        _binding = null
    }
}
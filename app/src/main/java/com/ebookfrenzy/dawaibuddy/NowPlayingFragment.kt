package com.ebookfrenzy.dawaibuddy

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.activityViewModels
import com.ebookfrenzy.dawaibuddy.databinding.FragmentNowPlayingBinding
import com.ebookfrenzy.dawaibuddy.models.SharedAudioViewModel
import com.ebookfrenzy.dawaibuddy.objects.MeditationTrack
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class NowPlayingFragment : BottomSheetDialogFragment() {

    companion object {
        // Allows external fragments to pass the active playlist for Next/Prev logic
        var currentTrackList: List<MeditationTrack> = emptyList()
    }

    private var _binding: FragmentNowPlayingBinding? = null
    private val binding get() = _binding!!

    // Grabs the shared background player automatically instead of taking variables manually!
    private val audioViewModel: SharedAudioViewModel by activityViewModels()

    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressAction = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNowPlayingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Expand bottom sheet to full screen
        val dialog = dialog as BottomSheetDialog
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
        BottomSheetBehavior.from(bottomSheet!!).apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        binding.btnMinimize.setOnClickListener { dismiss() }

        // 1. Observe Data Globally (Syncs Title & Artist instantly)
        audioViewModel.currentTrack.observe(viewLifecycleOwner) { track ->
            if (track != null) {
                binding.tvBigTitle.text = track.title
                binding.tvBigArtist.text = track.artist
            }
        }

        // 2. Sync Play/Pause Icon
        audioViewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            if (isPlaying) {
                binding.ivPlayPauseIcon.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                binding.ivPlayPauseIcon.setImageResource(android.R.drawable.ic_media_play)
            }
        }

        // 3. Sync Album Cover Image
        audioViewModel.artworkBitmap.observe(viewLifecycleOwner) { bitmap ->
            if (bitmap != null) {
                binding.ivBigArt.setImageBitmap(bitmap)
            } else {
                binding.ivBigArt.setBackgroundColor(Color.DKGRAY)
            }
        }

        setupPlayerControl()
    }

    private fun setupPlayerControl() {
        val player = audioViewModel.player ?: return

        // Play/Pause Action
        binding.btnPlayPauseCard.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
        }

        // Seekbar Dragging Action
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    player.seekTo(progress.toLong())
                    binding.tvCurrentTime.text = formatTime(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        handler.post(updateProgressAction)

        binding.btnNextBig.setOnClickListener {
            val currentTrack = audioViewModel.currentTrack.value
            if (currentTrack != null && currentTrackList.isNotEmpty()) {
                val currentIndex = currentTrackList.indexOfFirst { it.title == currentTrack.title }

                if (currentIndex != -1) {
                    val nextIndex = if (currentIndex + 1 < currentTrackList.size) currentIndex + 1 else 0
                    audioViewModel.playTrack(currentTrackList[nextIndex])
                    return@setOnClickListener
                }
            }

            // Fallback
            if (player.hasNextMediaItem()) player.seekToNextMediaItem()
        }

        binding.btnPrevBig.setOnClickListener {
            val currentTrack = audioViewModel.currentTrack.value
            if (currentTrack != null && currentTrackList.isNotEmpty()) {
                val currentIndex = currentTrackList.indexOfFirst { it.title == currentTrack.title }

                if (currentIndex != -1) {
                    val prevIndex = if (currentIndex - 1 >= 0) currentIndex - 1 else currentTrackList.size - 1
                    audioViewModel.playTrack(currentTrackList[prevIndex])
                    return@setOnClickListener
                }
            }

            // Fallback
            if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
        }
    }

    private fun updateProgress() {
        val player = audioViewModel.player ?: return

        // Continously track max duration dynamically in case the user skips tracks
        val duration = player.duration
        if (duration > 0) {
            binding.seekBar.max = duration.toInt()
            binding.tvTotalTime.text = formatTime(duration)
        }

        // Constantly updates tracking visually (even while paused, ensuring track changes refresh layout)
        binding.seekBar.progress = player.currentPosition.toInt()
        binding.tvCurrentTime.text = formatTime(player.currentPosition)
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val min = totalSeconds / 60
        val sec = totalSeconds % 60
        return String.format("%d:%02d", min, sec)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateProgressAction)
        _binding = null
    }
}
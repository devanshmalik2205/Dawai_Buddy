package com.ebookfrenzy.dawaibuddy.host_activities

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.Wearable
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class WatchAuthDialogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. WAKE SCREEN & SHOW OVER LOCKSCREEN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(currentIntent: Intent) {
        val watchNodeId = currentIntent.data?.getQueryParameter("nodeId") ?: currentIntent.getStringExtra("WATCH_NODE_ID")

        if (watchNodeId == null) {
            Log.e("WatchAuth", "No Node ID found in intent!")
            finish()
            return
        }

        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser != null) {
            setupBeautifulUI(watchNodeId, currentUser)
        } else {
            Toast.makeText(this, "Please login to Dawai Buddy first.", Toast.LENGTH_LONG).show()
            val loginIntent = Intent(this, AuthActivity::class.java).apply {
                putExtra("WATCH_NODE_ID", watchNodeId)
            }
            startActivity(loginIntent)
            finish()
        }
    }

    private fun setupBeautifulUI(watchNodeId: String, currentUser: FirebaseUser) {
        // Root container with a SOLID background so Android allows it over the lockscreen
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F4F5F7")) // Solid light grey background
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(60, 60, 60, 60)
        }

        // The beautiful white rounded card
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 48f
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(60, 80, 60, 80)
            elevation = 20f
        }

        val icon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_lock_idle_alarm) // Watch icon
            setColorFilter(Color.parseColor("#6B4EE6")) // Dawai Buddy Purple
            layoutParams = LinearLayout.LayoutParams(150, 150).apply {
                bottomMargin = 40
            }
        }

        val title = TextView(this).apply {
            text = "Link Wear OS Watch"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 20
            }
        }

        val message = TextView(this).apply {
            text = "Do you want to link your watch to Dawai Buddy as ${currentUser.displayName ?: "your account"}?"
            textSize = 15f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 60
            }
        }

        val btnAllow = Button(this).apply {
            text = "ALLOW AND LINK"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#6B4EE6")) // Dawai Buddy Purple
                cornerRadius = 24f
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 140).apply {
                bottomMargin = 30
            }
            setOnClickListener {
                text = "Verifying..."
                isEnabled = false

                // Ask for fingerprint/PIN before linking!
                promptUnlockAndLink(watchNodeId, currentUser)
            }
        }

        val btnDeny = Button(this).apply {
            text = "DENY"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 140)
            setOnClickListener {
                Toast.makeText(this@WatchAuthDialogActivity, "Watch link denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        // Assemble the UI
        cardLayout.addView(icon)
        cardLayout.addView(title)
        cardLayout.addView(message)
        cardLayout.addView(btnAllow)
        cardLayout.addView(btnDeny)
        rootLayout.addView(cardLayout)

        setContentView(rootLayout)
    }

    private fun promptUnlockAndLink(nodeId: String, user: FirebaseUser) {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        if (keyguardManager.isKeyguardLocked) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        super.onDismissSucceeded()
                        linkWatchToProfile(nodeId, user)
                    }
                    override fun onDismissCancelled() {
                        super.onDismissCancelled()
                        Toast.makeText(this@WatchAuthDialogActivity, "Unlock required to link", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    override fun onDismissError() {
                        super.onDismissError()
                        Toast.makeText(this@WatchAuthDialogActivity, "Error unlocking", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                })
            } else {
                @Suppress("DEPRECATION")
                window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                linkWatchToProfile(nodeId, user)
            }
        } else {
            // Screen is not locked, link immediately
            linkWatchToProfile(nodeId, user)
        }
    }

    private fun linkWatchToProfile(nodeId: String, user: FirebaseUser) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(user.uid)
            .update("hasWatch", true)
            .addOnSuccessListener {
                sendSuccessToWatch(nodeId, user)
            }
            .addOnFailureListener { e ->
                Log.e("WatchAuth", "Failed to update Firestore", e)
                Toast.makeText(this, "Network error. Try again.", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun sendSuccessToWatch(nodeId: String, user: FirebaseUser) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userName = user.displayName ?: "User"
                val jsonPayload = JSONObject().apply {
                    put("success", true)
                    put("name", userName)
                }.toString()

                Wearable.getMessageClient(applicationContext)
                    .sendMessage(nodeId, "/login_response", jsonPayload.toByteArray())

                runOnUiThread {
                    Toast.makeText(applicationContext, "Watch Linked Successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e("WatchAuth", "Failed to send data to watch", e)
                runOnUiThread { finish() }
            }
        }
    }
}
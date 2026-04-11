package com.ebookfrenzy.dawaibuddy

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ebookfrenzy.dawaibuddy.databinding.ActivityAuthBinding

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    fun navigateToHome() {
        // Create an Intent to start HomeActivity
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)

        // finish() destroys AuthActivity so the user can't press the back button
        // to return to the login screens once they are logged in.
        finish()
    }
}
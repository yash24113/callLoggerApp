package com.example.calll

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var progressStatus = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        progressBar = findViewById(R.id.progress_bar)
        progressText = findViewById(R.id.progress_text)

        simulateLoading()
    }

    private fun simulateLoading() {
        Thread {
            while (progressStatus < 100) {
                progressStatus += 1
                Thread.sleep(30)
                handler.post {
                    progressBar.progress = progressStatus
                    progressText.text = "$progressStatus%"
                }
            }
            // Loading complete, start MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }.start()
    }
}

package com.example.calll

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

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

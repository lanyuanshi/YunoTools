package com.yuno.tools.ui.tools

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yuno.tools.databinding.ActivityClockBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClockActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var binding: ActivityClockBinding

    private val timeRunnable = object : Runnable {
        override fun run() {
            updateTime()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityClockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enterFullScreen()
    }

    override fun onResume() {
        super.onResume()
        enterFullScreen()
        handler.post(timeRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(timeRunnable)
    }

    private fun enterFullScreen() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun updateTime() {
        binding.tvTime.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }
}

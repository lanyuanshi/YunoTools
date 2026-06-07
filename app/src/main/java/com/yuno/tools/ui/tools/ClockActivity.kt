package com.yuno.tools.ui.tools

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yuno.tools.databinding.ActivityClockBinding
import java.text.SimpleDateFormat
import java.util.*

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
        binding = ActivityClockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Toast.makeText(this, "横屏时钟", Toast.LENGTH_SHORT).show()
    }
    
    override fun onResume() {
        super.onResume()
        handler.post(timeRunnable)
    }
    
    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(timeRunnable)
    }
    
    private fun updateTime() {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        binding.tvTime.text = sdf.format(Date())
    }
}

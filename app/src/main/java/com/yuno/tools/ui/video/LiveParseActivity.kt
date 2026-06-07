package com.yuno.tools.ui.video

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yuno.tools.databinding.ActivityVideoParseBinding

class LiveParseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityVideoParseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }
        Toast.makeText(this, "Live实况解析功能", Toast.LENGTH_SHORT).show()
    }
}

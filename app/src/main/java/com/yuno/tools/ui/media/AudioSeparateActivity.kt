package com.yuno.tools.ui.media

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yuno.tools.databinding.ActivitySimpleBinding

class AudioSeparateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvTitle.text = "音频分离"
        binding.btnBack.setOnClickListener { finish() }
        binding.tvContent.text = "音频分离功能\n\n请选择视频文件提取音频"
        Toast.makeText(this, "音频分离功能", Toast.LENGTH_SHORT).show()
    }
}

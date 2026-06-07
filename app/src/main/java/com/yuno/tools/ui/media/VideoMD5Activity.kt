package com.yuno.tools.ui.media

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yuno.tools.databinding.ActivitySimpleBinding

class VideoMD5Activity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvTitle.text = "改视频MD5"
        binding.btnBack.setOnClickListener { finish() }
        binding.tvContent.text = "修改视频MD5功能\n\n请选择视频文件修改MD5值"
        Toast.makeText(this, "改视频MD5功能", Toast.LENGTH_SHORT).show()
    }
}

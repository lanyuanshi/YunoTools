package com.yuno.tools.ui.image

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yuno.tools.databinding.ActivitySimpleBinding

class ImageWatermarkActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvTitle.text = "图像去水印"
        binding.btnBack.setOnClickListener { finish() }
        binding.tvContent.text = "图像去水印功能\n\n请选择图片进行处理"
        Toast.makeText(this, "图像去水印功能", Toast.LENGTH_SHORT).show()
    }
}

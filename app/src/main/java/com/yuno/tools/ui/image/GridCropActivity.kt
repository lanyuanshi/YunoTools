package com.yuno.tools.ui.image

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yuno.tools.databinding.ActivitySimpleBinding

class GridCropActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvTitle.text = "九宫格切图"
        binding.btnBack.setOnClickListener { finish() }
        binding.tvContent.text = "九宫格切图功能\n\n请选择图片进行切分"
        Toast.makeText(this, "九宫格切图功能", Toast.LENGTH_SHORT).show()
    }
}

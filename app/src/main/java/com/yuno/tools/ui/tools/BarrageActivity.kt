package com.yuno.tools.ui.tools

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yuno.tools.databinding.ActivitySimpleBinding

class BarrageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvTitle.text = "手持弹幕"
        binding.btnBack.setOnClickListener { finish() }
        binding.tvContent.text = "手持弹幕功能\n\n输入文字生成全屏滚动弹幕"
        Toast.makeText(this, "手持弹幕功能", Toast.LENGTH_SHORT).show()
    }
}

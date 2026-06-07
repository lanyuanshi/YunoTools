package com.yuno.tools.ui.tools

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yuno.tools.databinding.ActivitySimpleBinding

class AnswerBookActivity : AppCompatActivity() {
    private val answers = listOf(
        "是的", "不是", "可能吧", "毫无疑问", "别想了",
        "再试一次", "最好再等等", "这很重要", "放手去做", "不值得"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvTitle.text = "答案之书"
        binding.btnBack.setOnClickListener { finish() }
        val randomAnswer = answers.random()
        binding.tvContent.text = "闭上眼睛，心中默念你的问题\n\n然后点击屏幕\n\n答案是：\n\n$randomAnswer"
        binding.tvContent.setOnClickListener {
            binding.tvContent.text = "答案是：\n\n${answers.random()}"
        }
        Toast.makeText(this, "答案之书", Toast.LENGTH_SHORT).show()
    }
}

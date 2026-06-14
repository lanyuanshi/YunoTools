package com.yuno.tools.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yuno.tools.R
import kotlin.math.roundToInt

class Base64ToolActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var output: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Base64转换"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            setBackgroundColor(0xFFF2F2F7.toInt())
        }
        root.addView(header("Base64 转换", "支持文本编码、解码、复制与清空"))
        input = edit("输入文本或 Base64 内容")
        output = edit("转换结果", minLines = 5)
        root.addView(input)
        root.addView(row(
            button("编码") { convertEncode() },
            button("解码") { convertDecode() },
            button("复制结果") { copy(output.text.toString()) },
            button("清空") { input.setText(""); output.setText("") }
        ))
        root.addView(output)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun convertEncode() {
        val text = input.text.toString()
        if (text.isBlank()) return toast("请输入内容")
        output.setText(Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
    }

    private fun convertDecode() {
        val text = input.text.toString().trim()
        if (text.isBlank()) return toast("请输入 Base64 内容")
        runCatching {
            String(Base64.decode(text, Base64.DEFAULT), Charsets.UTF_8)
        }.onSuccess { output.setText(it) }
            .onFailure { toast("解码失败：请检查 Base64 内容") }
    }

    private fun header(title: String, sub: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setBackgroundColor(0xFFFFFFFF.toInt())
        addView(TextView(context).apply { text = title; textSize = 22f; setTextColor(0xFF111827.toInt()) })
        addView(TextView(context).apply { text = sub; textSize = 13f; setTextColor(0xFF8A8F98.toInt()); setPadding(0, dp(4), 0, 0) })
    }

    private fun edit(hintText: String, minLines: Int = 4) = EditText(this).apply {
        hint = hintText
        setTextColor(0xFF111827.toInt())
        setHintTextColor(0xFF9CA3AF.toInt())
        setBackgroundColor(0xFFFFFFFF.toInt())
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setMinLines(minLines)
        gravity = Gravity.TOP
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
    }

    private fun row(vararg views: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        views.forEach { addView(it, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(6) }) }
    }

    private fun button(textValue: String, action: () -> Unit) = Button(this).apply { text = textValue; setOnClickListener { action() } }
    private fun copy(text: String) { if (text.isBlank()) toast("没有可复制内容") else { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Base64结果", text)); toast("已复制") } }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}

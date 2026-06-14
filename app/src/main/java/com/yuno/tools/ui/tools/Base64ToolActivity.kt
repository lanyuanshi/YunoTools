package com.yuno.tools.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import kotlin.math.roundToInt

class Base64ToolActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var output: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
            setBackgroundColor(Color.parseColor("#F2F2F7"))
        }
        root.addView(header())
        root.addView(label("输入内容"))
        input = edit("输入普通文本或 Base64 字符串", 5)
        root.addView(input)
        root.addView(actionRow(
            pill("编码", "#007AFF") { convertEncode() },
            pill("解码", "#34C759") { convertDecode() }
        ))
        root.addView(actionRow(
            pill("复制结果", "#5856D6") { copy(output.text.toString()) },
            pill("交换", "#FF9500") { val a=input.text.toString(); input.setText(output.text.toString()); output.setText(a) },
            pill("清空", "#8E8E93") { input.setText(""); output.setText("") }
        ))
        root.addView(label("转换结果"))
        output = edit("结果会显示在这里", 7)
        output.setTextIsSelectable(true)
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
        runCatching { String(Base64.decode(text, Base64.DEFAULT), Charsets.UTF_8) }
            .onSuccess { output.setText(it) }
            .onFailure { toast("解码失败：请检查 Base64 内容") }
    }

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = bg("#FFFFFF", 22)
        setPadding(dp(18), dp(18), dp(18), dp(18))
        addView(TextView(context).apply { text = "Base64 转换"; textSize = 24f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#111827")) })
        addView(TextView(context).apply { text = "文本编码、解码、复制、交换，一页完成。"; textSize = 13f; setTextColor(Color.parseColor("#8A8F98")); setPadding(0, dp(6), 0, 0) })
    }

    private fun label(t: String) = TextView(this).apply { text = t; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#374151")); setPadding(dp(2), dp(16), 0, dp(8)) }
    private fun edit(h: String, lines: Int) = EditText(this).apply { hint = h; textSize = 15f; setTextColor(Color.parseColor("#111827")); setHintTextColor(Color.parseColor("#9CA3AF")); background = bg("#FFFFFF", 16); setPadding(dp(14), dp(12), dp(14), dp(12)); minLines = lines; gravity = Gravity.TOP }
    private fun actionRow(vararg views: Button) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0); views.forEach { addView(it, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(8) }) } }
    private fun pill(t: String, color: String, action: () -> Unit) = Button(this).apply { text = t; setTextColor(Color.WHITE); textSize = 14f; background = bg(color, 18); setOnClickListener { action() } }
    private fun bg(color: String, radius: Int) = GradientDrawable().apply { setColor(Color.parseColor(color)); cornerRadius = dp(radius).toFloat() }
    private fun copy(text: String) { if (text.isBlank()) toast("没有可复制内容") else { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Base64结果", text)); toast("已复制") } }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
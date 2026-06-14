package com.yuno.tools.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.roundToInt

class ExpressQueryActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var numberInput: EditText
    private lateinit var resultText: TextView
    private var lastResult = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(18), dp(16), dp(18)); setBackgroundColor(0xFFF2F2F7.toInt()) }
        root.addView(TextView(this).apply { text = "快递查询"; textSize = 22f; setTextColor(0xFF111827.toInt()) })
        root.addView(TextView(this).apply { text = "输入快递单号查询物流轨迹，支持复制结果。"; textSize = 13f; setTextColor(0xFF8A8F98.toInt()); setPadding(0, dp(4), 0, dp(12)) })
        numberInput = EditText(this).apply { hint = "请输入快递单号"; setSingleLine(true); setPadding(dp(14), 0, dp(14), 0); setBackgroundColor(0xFFFFFFFF.toInt()); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)) }
        resultText = TextView(this).apply { text = "查询结果会显示在这里"; textSize = 15f; setTextColor(0xFF1F2937.toInt()); setPadding(dp(14), dp(14), dp(14), dp(14)); setBackgroundColor(0xFFFFFFFF.toInt()); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) } }
        root.addView(numberInput)
        root.addView(row(button("查询") { queryExpress() }, button("复制") { copy(lastResult) }, button("清空") { numberInput.setText(""); lastResult=""; resultText.text="查询结果会显示在这里" }))
        root.addView(resultText)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun queryExpress() {
        val no = numberInput.text.toString().trim()
        if (no.isBlank()) return toast("请输入快递单号")
        resultText.text = "正在查询..."
        Thread {
            val text = runCatching { requestExpress(no) }.getOrElse { "查询失败：${it.message ?: "网络异常"}\n可稍后重试，或复制单号到官方快递渠道查询。" }
            runOnUiThread { lastResult = text; resultText.text = text }
        }.start()
    }

    private fun requestExpress(no: String): String {
        val url = "https://www.kuaidi100.com/query?type=auto&postid=${URLEncoder.encode(no, "UTF-8")}&temp=${System.currentTimeMillis()}"
        val req = Request.Builder().url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .addHeader("Referer", "https://www.kuaidi100.com/")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val json = JSONObject(resp.body?.string().orEmpty())
            val message = json.optString("message")
            val company = json.optString("com").ifBlank { "自动识别" }
            val arr = json.optJSONArray("data")
            if (arr == null || arr.length() == 0) return "未查到物流轨迹：${message.ifBlank { "请检查单号是否正确" }}"
            return buildString {
                append("单号：$no\n")
                append("快递：$company\n")
                append("状态：${message.ifBlank { "已返回轨迹" }}\n\n")
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    append(item.optString("time")).append('\n')
                    append(item.optString("context")).append("\n\n")
                }
            }.trim()
        }
    }

    private fun row(vararg views: Button) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }; views.forEach { addView(it, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(6) }) } }
    private fun button(t: String, action: () -> Unit) = Button(this).apply { text = t; setOnClickListener { action() } }
    private fun copy(text: String) { if (text.isBlank()) toast("没有可复制内容") else { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("快递查询结果", text)); toast("已复制") } }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
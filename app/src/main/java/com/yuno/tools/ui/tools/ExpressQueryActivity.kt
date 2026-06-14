package com.yuno.tools.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class ExpressQueryActivity : AppCompatActivity() {
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(18, TimeUnit.SECONDS).build()
    private lateinit var numberInput: EditText
    private lateinit var resultBox: LinearLayout
    private var lastResult = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(18), dp(16), dp(24)); setBackgroundColor(Color.parseColor("#F2F2F7")) }
        root.addView(header())
        numberInput = EditText(this).apply { hint = "输入快递单号"; textSize = 16f; setSingleLine(true); background = bg("#FFFFFF", 18); setPadding(dp(16), 0, dp(16), 0); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(14) } }
        root.addView(numberInput)
        root.addView(actionRow(pill("查询", "#007AFF") { queryExpress() }, pill("复制", "#5856D6") { copy(lastResult) }, pill("清空", "#8E8E93") { clear() }))
        resultBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = bg("#FFFFFF", 18); setPadding(dp(14), dp(14), dp(14), dp(14)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) } }
        showPlaceholder()
        root.addView(resultBox)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun queryExpress() {
        val no = numberInput.text.toString().trim()
        if (no.isBlank()) return toast("请输入快递单号")
        resultBox.removeAllViews(); resultBox.addView(line("正在查询...", "#6B7280", false))
        Thread {
            val result = runCatching { requestExpress(no) }.getOrElse { ExpressResult(no, "未知", "查询失败：${it.message ?: "网络异常"}", emptyList()) }
            runOnUiThread { render(result) }
        }.start()
    }

    private fun requestExpress(no: String): ExpressResult {
        val url = "https://www.kuaidi100.com/query?type=auto&postid=${URLEncoder.encode(no, "UTF-8")}&temp=${System.currentTimeMillis()}"
        val req = Request.Builder().url(url).addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) Chrome/120").addHeader("Referer", "https://www.kuaidi100.com/").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val json = JSONObject(resp.body?.string().orEmpty())
            val arr = json.optJSONArray("data")
            val items = mutableListOf<Pair<String, String>>()
            if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { items += it.optString("time") to it.optString("context") }
            return ExpressResult(no, json.optString("com").ifBlank { "自动识别" }, json.optString("message").ifBlank { if (items.isEmpty()) "未查到轨迹" else "已返回轨迹" }, items)
        }
    }

    private fun render(r: ExpressResult) {
        resultBox.removeAllViews()
        resultBox.addView(line("单号：${r.no}", "#111827", true))
        resultBox.addView(line("快递：${r.company}", "#374151", false))
        resultBox.addView(line("状态：${r.message}", "#007AFF", true))
        if (r.items.isEmpty()) resultBox.addView(line("暂无物流轨迹，请检查单号或稍后再试。", "#6B7280", false))
        r.items.forEachIndexed { index, item ->
            resultBox.addView(line("${index + 1}. ${item.first}", "#8A8F98", false))
            resultBox.addView(line(item.second, "#111827", index == 0))
        }
        lastResult = buildString { append("单号：${r.no}\n快递：${r.company}\n状态：${r.message}\n\n"); r.items.forEach { append(it.first).append('\n').append(it.second).append("\n\n") } }.trim()
    }

    private fun clear() { numberInput.setText(""); lastResult = ""; showPlaceholder() }
    private fun showPlaceholder() { resultBox.removeAllViews(); resultBox.addView(line("查询结果会显示在这里", "#8A8F98", false)); resultBox.addView(line("支持复制完整物流轨迹。", "#C0C4CC", false)) }
    private fun header() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = bg("#FFFFFF", 22); setPadding(dp(18), dp(18), dp(18), dp(18)); addView(line("快递查询", "#111827", true, 24f)); addView(line("输入单号后自动识别快递公司并展示轨迹。", "#8A8F98", false, 13f)) }
    private fun line(t: String, color: String, bold: Boolean, size: Float = 15f) = TextView(this).apply { text = t; textSize = size; setTextColor(Color.parseColor(color)); setPadding(0, dp(4), 0, dp(4)); if (bold) typeface = Typeface.DEFAULT_BOLD; setTextIsSelectable(true) }
    private fun actionRow(vararg views: Button) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0); views.forEach { addView(it, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(8) }) } }
    private fun pill(t: String, color: String, action: () -> Unit) = Button(this).apply { text = t; setTextColor(Color.WHITE); background = bg(color, 18); setOnClickListener { action() } }
    private fun bg(color: String, radius: Int) = GradientDrawable().apply { setColor(Color.parseColor(color)); cornerRadius = dp(radius).toFloat() }
    private fun copy(text: String) { if (text.isBlank()) toast("没有可复制内容") else { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("快递查询结果", text)); toast("已复制") } }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    private data class ExpressResult(val no: String, val company: String, val message: String, val items: List<Pair<String, String>>)
}
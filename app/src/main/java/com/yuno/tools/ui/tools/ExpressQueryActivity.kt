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
import com.yuno.tools.data.AccountStore
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class ExpressQueryActivity : AppCompatActivity() {
    private companion object {
        const val UA = "YunoTools/1.2.03 Android ExpressQuery"
        const val UAPI_TRACKING_URL = "https://uapis.cn/api/v1/misc/tracking/query"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private lateinit var numberInput: EditText
    private lateinit var carrierInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var resultBox: LinearLayout
    private var lastResult = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AccountStore.hasVipAccess(this)) {
            toast("快递查询为会员专区功能，请先开通会员")
            finish()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(24))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#ECFDF5"), Color.parseColor("#F8FAFC")))
        }
        root.addView(header())
        numberInput = input("输入快递单号，必填")
        root.addView(numberInput, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(14) })
        carrierInput = input("快递公司编码，可选，如 yuantong / zhongtong / shunfeng")
        root.addView(carrierInput, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(10) })
        phoneInput = input("收件人手机号后4位，可选；顺丰等可能需要")
        root.addView(phoneInput, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(10) })
        root.addView(actionRow(pill("查询", "#10B981") { queryExpress() }, pill("复制", "#6366F1") { copy(lastResult) }, pill("清空", "#64748B") { clear() }))
        resultBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = bg("#FFFFFF", 22); setPadding(dp(16), dp(16), dp(16), dp(16)) }
        showPlaceholder()
        root.addView(resultBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun queryExpress() {
        val no = numberInput.text.toString().trim()
        val carrier = carrierInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        if (no.isBlank()) return toast("请输入快递单号")
        if (phone.isNotBlank() && !Regex("\\d{4}").matches(phone)) return toast("手机尾号请输入4位数字")
        resultBox.removeAllViews()
        resultBox.addView(line("正在通过 UAPI 查询物流…", "#64748B", false))
        Thread {
            val result = runCatching { requestUapi(no, carrier, phone) }
                .getOrElse { ExpressResult(no, carrier.ifBlank { "auto" }, "自动识别", false, "查询失败：${it.message ?: "网络异常"}", emptyList()) }
            runOnUiThread { render(result) }
        }.start()
    }

    private fun requestUapi(no: String, carrier: String, phone: String): ExpressResult {
        val builder = UAPI_TRACKING_URL.toHttpUrl().newBuilder()
            .addQueryParameter("tracking_number", no)
        if (carrier.isNotBlank()) builder.addQueryParameter("carrier_code", carrier)
        if (phone.isNotBlank()) builder.addQueryParameter("phone", phone)
        val req = Request.Builder()
            .url(builder.build())
            .addHeader("User-Agent", UA)
            .addHeader("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching { JSONObject(body).optString("message").ifBlank { JSONObject(body).optString("error") } }.getOrDefault("")
                error(msg.ifBlank { "HTTP ${resp.code}" })
            }
            val json = JSONObject(body)
            val tracks = mutableListOf<Pair<String, String>>()
            val arr = json.optJSONArray("tracks") ?: json.optJSONArray("data")
            if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { obj ->
                val time = obj.optString("time").ifBlank { obj.optString("ftime") }.ifBlank { obj.optString("created_at") }
                val context = obj.optString("context").ifBlank { obj.optString("desc") }.ifBlank { obj.optString("description") }
                if (time.isNotBlank() || context.isNotBlank()) tracks += time to context
            }
            val code = json.optString("carrier_code").ifBlank { carrier.ifBlank { "auto" } }
            val name = json.optString("carrier_name").ifBlank { code }
            val completed = json.optBoolean("is_completed", false)
            val status = json.optString("status").ifBlank { json.optString("message") }.ifBlank { if (tracks.isEmpty()) "暂无物流信息" else "已返回 ${tracks.size} 条轨迹" }
            return ExpressResult(no, code, name, completed, status, tracks)
        }
    }

    private fun render(r: ExpressResult) {
        resultBox.removeAllViews()
        resultBox.addView(line("单号：${r.no}", "#111827", true, 16f))
        resultBox.addView(line("快递：${r.companyName}（${r.company}）", "#334155", false))
        resultBox.addView(line("状态：${r.status}${if (r.completed) " · 已完成" else ""}", if (r.completed) "#16A34A" else "#0EA5E9", true))
        if (r.items.isEmpty()) {
            resultBox.addView(line("暂无物流轨迹。顺丰、京东等部分快递可能需要填写收件人手机号后4位后再查。", "#64748B", false))
        } else {
            r.items.forEachIndexed { index, item ->
                resultBox.addView(line("${index + 1}. ${item.first}", "#94A3B8", false, 13f))
                resultBox.addView(line(item.second, if (index == 0) "#111827" else "#334155", index == 0, 14.5f))
            }
        }
        lastResult = buildString {
            append("单号：${r.no}\n快递：${r.companyName}（${r.company}）\n状态：${r.status}\n\n")
            r.items.forEach { append(it.first).append('\n').append(it.second).append("\n\n") }
        }.trim()
    }

    private fun clear() { numberInput.setText(""); carrierInput.setText(""); phoneInput.setText(""); lastResult = ""; showPlaceholder() }
    private fun showPlaceholder() { resultBox.removeAllViews(); resultBox.addView(line("查询结果会显示在这里", "#64748B", false)); resultBox.addView(line("UAPI 支持自动识别快递公司；如查不到，尝试填写公司编码或手机尾号。", "#94A3B8", false)) }
    private fun header() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = bg("#FFFFFF", 24); setPadding(dp(18), dp(18), dp(18), dp(18)); addView(line("快递查询", "#064E3B", true, 25f)); addView(line("接入 UAPI：自动识别快递公司，支持手机号尾号验证。", "#64748B", false, 13f)) }
    private fun input(h: String) = EditText(this).apply { hint = h; textSize = 15f; setSingleLine(true); setTextColor(Color.parseColor("#111827")); setHintTextColor(Color.parseColor("#94A3B8")); background = bg("#FFFFFF", 18); setPadding(dp(16), 0, dp(16), 0) }
    private fun line(t: String, color: String, bold: Boolean, size: Float = 15f) = TextView(this).apply { text = t; textSize = size; setTextColor(Color.parseColor(color)); setPadding(0, dp(4), 0, dp(4)); if (bold) typeface = Typeface.DEFAULT_BOLD; setTextIsSelectable(true); setLineSpacing(dp(3).toFloat(), 1.0f) }
    private fun actionRow(vararg views: Button) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0); views.forEachIndexed { i, v -> addView(v, LinearLayout.LayoutParams(0, dp(46), 1f).apply { if (i < views.lastIndex) rightMargin = dp(8) }) } }
    private fun pill(t: String, color: String, action: () -> Unit) = Button(this).apply { text = t; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; background = bg(color, 18); setOnClickListener { action() } }
    private fun bg(color: String, radius: Int) = GradientDrawable().apply { setColor(Color.parseColor(color)); cornerRadius = dp(radius).toFloat() }
    private fun copy(text: String) { if (text.isBlank()) toast("没有可复制内容") else { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("快递查询结果", text)); toast("已复制") } }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    private data class ExpressResult(val no: String, val company: String, val companyName: String, val completed: Boolean, val status: String, val items: List<Pair<String, String>>)
}

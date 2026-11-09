package com.yuno.tools.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.yuno.tools.R
import com.yuno.tools.util.ThemeApplier
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class YiyanActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val apiUrl = "https://v2.xxapi.cn/api/yiyan?type=hitokoto"
    private val imageUrls = listOf(
        "https://picsum.photos/seed/yiyan-dawn/1200/720",
        "https://picsum.photos/seed/yiyan-sky/1200/720",
        "https://picsum.photos/seed/yiyan-rain/1200/720",
        "https://picsum.photos/seed/yiyan-cloud/1200/720"
    )

    private lateinit var quoteText: TextView
    private lateinit var statusText: TextView
    private lateinit var refreshButton: View
    private lateinit var copyButton: View
    private lateinit var coverImage: ImageView
    private var currentQuote: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_yiyan)
        ThemeApplier.apply(this)

        quoteText = findViewById(R.id.tvYiyanQuote)
        statusText = findViewById(R.id.tvYiyanStatus)
        refreshButton = findViewById(R.id.btnRefreshYiyan)
        copyButton = findViewById(R.id.btnCopyYiyan)
        coverImage = findViewById(R.id.ivYiyanCover)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        refreshButton.setOnClickListener { loadYiyan() }
        copyButton.setOnClickListener { copyQuote() }
        loadYiyan()
    }

    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
    }

    private fun loadYiyan() {
        statusText.text = "正在获取一言..."
        quoteText.text = ""
        currentQuote = ""
        refreshButton.isEnabled = false
        copyButton.isEnabled = false
        Glide.with(this).load(imageUrls.random()).centerCrop().into(coverImage)
        executor.execute {
            try {
                val conn = URL(apiUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/json")
                val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(body)
                val quote = json.optString("data").ifBlank { "暂无返回内容" }
                runOnUiThread {
                    currentQuote = quote
                    quoteText.text = quote
                    statusText.text = "获取成功 · 可复制"
                    refreshButton.isEnabled = true
                    copyButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    quoteText.text = ""
                    currentQuote = ""
                    statusText.text = "获取失败：${e.message ?: "网络错误"}"
                    refreshButton.isEnabled = true
                    copyButton.isEnabled = false
                    Toast.makeText(this, "一言获取失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun copyQuote() {
        if (currentQuote.isBlank()) {
            Toast.makeText(this, "当前没有可复制内容", Toast.LENGTH_SHORT).show()
            return
        }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("一言", currentQuote))
        Toast.makeText(this, "已复制一言", Toast.LENGTH_SHORT).show()
    }
}

package com.yuno.tools.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.yuno.tools.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class RandomQuoteActivity : AppCompatActivity() {

    private lateinit var tvQuote: TextView
    private lateinit var tvSource: TextView
    private lateinit var btnRefresh: MaterialButton
    private lateinit var btnCopy: MaterialButton

    private var currentQuote = ""
    private var currentSource = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_random_quote)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        tvQuote = findViewById(R.id.tvQuote)
        tvSource = findViewById(R.id.tvSource)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnCopy = findViewById(R.id.btnCopy)

        btnBack.setOnClickListener { finish() }
        btnRefresh.setOnClickListener { fetchQuote() }
        btnCopy.setOnClickListener { copyQuote() }

        fetchQuote()
    }

    private fun fetchQuote() {
        btnRefresh.isEnabled = false
        btnRefresh.text = "加载中..."

        lifecycleScope.launch(Dispatchers.IO) {
            val result = try {
                val url = URL("https://v1.hitokoto.cn/?encode=text&charset=utf-8")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "YunoTools/1.0")
                val code = conn.responseCode
                if (code == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                    // Also try to get source info
                    val infoUrl = URL("https://v1.hitokoto.cn/?encode=json&charset=utf-8")
                    val infoConn = infoUrl.openConnection() as HttpURLConnection
                    infoConn.requestMethod = "GET"
                    infoConn.connectTimeout = 10000
                    infoConn.readTimeout = 10000
                    infoConn.setRequestProperty("User-Agent", "YunoTools/1.0")
                    val source = if (infoConn.responseCode == 200) {
                        val json = infoConn.inputStream.bufferedReader().use { it.readText() }
                        parseSource(json)
                    } else ""
                    Pair(text, source)
                } else {
                    Pair("获取一言失败", "")
                }
            } catch (e: Exception) {
                Pair("网络错误: ${e.message}", "")
            }

            withContext(Dispatchers.Main) {
                currentQuote = result.first
                currentSource = result.second
                tvQuote.text = currentQuote
                tvSource.text = if (currentSource.isNotEmpty()) "—— $currentSource" else ""
                btnRefresh.isEnabled = true
                btnRefresh.text = "换一句"
            }
        }
    }

    private fun parseSource(json: String): String {
        return try {
            val from = json.substringAfter("\"from\":\"").substringBefore("\"")
            val fromWho = json.substringAfter("\"from_who\":").let {
                if (it.startsWith("\"")) it.substring(1).substringBefore("\"") else ""
            }
            if (fromWho.isNotEmpty() && fromWho != "null") "$fromWho「$from」"
            else from
        } catch (e: Exception) {
            ""
        }
    }

    private fun copyQuote() {
        if (currentQuote.isEmpty()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("一言", currentQuote)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
}
package com.yuno.tools.ui.tools

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yuno.tools.R
import com.yuno.tools.util.ThemeApplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class NovelReaderPageActivity : AppCompatActivity() {
    private val base = "https://novel.juhe.im"
    private var bookId = ""
    private var chapterId = ""
    private var title = ""
    private var chapterIds = mutableListOf<String>()
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_novel_reader_page)
        ThemeApplier.apply(this)
        bookId = intent.getStringExtra("bookId").orEmpty()
        chapterId = intent.getStringExtra("chapterId").orEmpty()
        title = intent.getStringExtra("title").orEmpty()
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnPrev).setOnClickListener { move(-1) }
        findViewById<Button>(R.id.btnNext).setOnClickListener { move(1) }
        findViewById<TextView>(R.id.tvTitle).text = title
        loadChapterListAndBody()
    }

    override fun onResume() { super.onResume(); ThemeApplier.apply(this) }

    private fun loadChapterListAndBody() {
        setLoading(true)
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching {
                val list = requestJson("$base/book/$bookId/chapters")
                val arr = list.optJSONArray("chapters") ?: org.json.JSONArray()
                chapterIds.clear()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    chapterIds.add(o.optString("id"))
                    if (o.optString("id") == chapterId) currentIndex = i
                }
                requestJson("$base/book/$bookId/chapters/$chapterId")
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { chapter ->
                    findViewById<TextView>(R.id.tvTitle).text = chapter.optJSONObject("chapter")?.optString("title") ?: title
                    findViewById<TextView>(R.id.tvBody).text = chapter.optJSONObject("chapter")?.optString("body") ?: "暂无内容"
                }.onFailure { toast("加载章节失败") }
                setLoading(false)
            }
        }
    }

    private fun move(step: Int) {
        if (chapterIds.isEmpty()) return
        val next = currentIndex + step
        if (next !in chapterIds.indices) { toast("没有更多章节了"); return }
        chapterId = chapterIds[next]
        currentIndex = next
        loadChapterBody()
    }

    private fun loadChapterBody() {
        setLoading(true)
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching { requestJson("$base/book/$bookId/chapters/$chapterId") }
            withContext(Dispatchers.Main) {
                result.onSuccess { chapter ->
                    findViewById<TextView>(R.id.tvTitle).text = chapter.optJSONObject("chapter")?.optString("title") ?: title
                    findViewById<TextView>(R.id.tvBody).text = chapter.optJSONObject("chapter")?.optString("body") ?: "暂无内容"
                }.onFailure { toast("切换章节失败") }
                setLoading(false)
            }
        }
    }

    private fun requestJson(url: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 10000; readTimeout = 15000
            setRequestProperty("User-Agent", "Mozilla/5.0 YunoTools")
            setRequestProperty("Accept", "application/json,*/*")
        }
        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        conn.disconnect()
        return JSONObject(body)
    }

    private fun setLoading(loading: Boolean) { findViewById<ProgressBar>(R.id.progress).visibility = if (loading) View.VISIBLE else View.GONE }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

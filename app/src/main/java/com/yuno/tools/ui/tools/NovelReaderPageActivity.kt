package com.yuno.tools.ui.tools

import android.net.Uri
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class NovelReaderPageActivity : AppCompatActivity() {
    private val fanqieApi = "https://api.xcvts.cn/api/xiaoshuo/fanqie"
    private var bookId = ""
    private var chapterId = ""
    private var title = ""
    private var bookTitle = ""
    private var chapterIds = mutableListOf<String>()
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_novel_reader_page)
        ThemeApplier.apply(this)
        bookId = intent.getStringExtra("bookId").orEmpty()
        chapterId = intent.getStringExtra("chapterId").orEmpty()
        title = intent.getStringExtra("title").orEmpty()
        bookTitle = intent.getStringExtra("bookTitle").orEmpty()
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnPrev).setOnClickListener { move(-1) }
        findViewById<Button>(R.id.btnNext).setOnClickListener { move(1) }
        findViewById<TextView>(R.id.tvTitle).text = title.ifBlank { "阅读" }
        loadChapterListAndBody()
    }

    override fun onResume() { super.onResume(); ThemeApplier.apply(this) }

    private fun loadChapterListAndBody() {
        if (bookId.isBlank() || chapterId.isBlank()) { renderBody(title.ifBlank { "阅读" }, "章节信息不完整，无法加载正文。"); return }
        if (bookId.startsWith("local_") || chapterId.startsWith("local_")) { loadLocalChapter(); return }
        setLoading(true)
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching {
                val list = requestJson("mulu=${Uri.encode(bookId)}&pretty=1")
                parseChapterIds(list)
                requestJson("content=${Uri.encode(chapterId)}&pretty=1")
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { renderOnlineChapter(it) }.onFailure {
                    renderBody(title.ifBlank { bookTitle.ifBlank { "备用章节" } }, "在线章节暂时加载失败。\n\n可能原因：番茄正文接口超时、上游返回错误，或该章节暂无可读内容。\n\n你可以返回重试、刷新目录，或稍后再试。")
                    toast("在线章节加载失败")
                }
                setLoading(false)
            }
        }
    }

    private fun parseChapterIds(json: JSONObject) {
        val arr = json.optJSONArray("data") ?: json.optJSONObject("data")?.optJSONArray("list") ?: JSONArray()
        chapterIds.clear()
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val id = firstNotBlank(c.optString("item_id"), c.optString("chapter_id"), c.optString("id"), c.optString("Id"))
            if (id.isNotBlank()) {
                chapterIds.add(id)
                if (id == chapterId) currentIndex = chapterIds.lastIndex
            }
        }
    }

    private fun move(step: Int) {
        if (bookId.startsWith("local_") || chapterId.startsWith("local_")) { toast("示例章节不支持翻页"); return }
        if (chapterIds.isEmpty()) { toast("章节列表未加载"); return }
        val next = currentIndex + step
        if (next !in chapterIds.indices) { toast("没有更多章节了"); return }
        chapterId = chapterIds[next]
        currentIndex = next
        loadChapterBody()
    }

    private fun loadChapterBody() {
        setLoading(true)
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching { requestJson("content=${Uri.encode(chapterId)}&pretty=1") }
            withContext(Dispatchers.Main) {
                result.onSuccess { renderOnlineChapter(it) }.onFailure { toast("切换章节失败") }
                setLoading(false)
            }
        }
    }

    private fun renderOnlineChapter(json: JSONObject) {
        if (json.has("error")) {
            val error = firstNotBlank(json.optString("error"), "正文加载失败")
            val details = firstNotBlank(json.optString("details"), json.optString("response"))
            renderBody(title.ifBlank { bookTitle.ifBlank { "阅读" } }, listOf(error, details).filter { it.isNotBlank() }.joinToString("\n\n"))
            return
        }
        val data = json.optJSONObject("data") ?: json
        val chapter = data.optJSONObject("chapter") ?: data
        val body = firstNotBlank(
            chapter.optString("content"),
            chapter.optString("body"),
            chapter.optString("text"),
            data.optString("content"),
            data.optString("body"),
            data.optString("text"),
            "暂无内容"
        )
        val chapterTitle = firstNotBlank(chapter.optString("title"), chapter.optString("name"), title.ifBlank { bookTitle.ifBlank { "阅读" } })
        renderBody(chapterTitle, normalizeText(body))
    }

    private fun loadLocalChapter() {
        val body = when {
            chapterId.endsWith("1") -> "这是内置示例章节，用来保证番茄接口不可用时页面仍能正常打开，不会闪退。\n\n在线接口恢复后，搜索和章节会优先展示真实番茄内容。"
            chapterId.endsWith("2") -> "阅读页已加入空 ID、防异常响应、防网络失败保护。\n\n上一章/下一章仅对在线章节列表生效，示例章节会提示不支持翻页。"
            else -> "当前版本已改为 xcvts 番茄小说接口。\n\n如果上游正文接口超时，页面会显示错误信息，不会闪退或空白。"
        }
        renderBody(title.ifBlank { "示例章节" }, body)
    }

    private fun renderBody(titleText: String, bodyText: String) {
        findViewById<TextView>(R.id.tvTitle).text = titleText
        findViewById<TextView>(R.id.tvBody).text = bodyText
    }

    private fun requestJson(query: String): JSONObject = JSONObject(readUrl("$fanqieApi?$query"))

    private fun readUrl(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 8000
            readTimeout = 30000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) YunoTools/1.1.81")
            setRequestProperty("Accept", "application/json,text/plain,*/*")
            setRequestProperty("Referer", "https://api.xcvts.cn/")
            setRequestProperty("Connection", "close")
        }
        return try {
            val stream = if (conn.responseCode in 200..399) conn.inputStream else conn.errorStream
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { conn.disconnect() }
    }

    private fun normalizeText(text: String): String = text
        .replace("\r\n　　\r\n", "\n")
        .replace("\r\n", "\n")
        .replace("\\n", "\n")
        .replace("“", "")
        .replace("”", "")
        .trim()

    private fun firstNotBlank(vararg values: String) = values.firstOrNull { it.isNotBlank() }.orEmpty()
    private fun setLoading(loading: Boolean) { findViewById<ProgressBar>(R.id.progress).visibility = if (loading) View.VISIBLE else View.GONE }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

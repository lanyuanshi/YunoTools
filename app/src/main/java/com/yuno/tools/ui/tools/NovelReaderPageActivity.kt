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
    private val primaryDomain = "https://dynic.2otea.com"
    private val fallbackDomain = "https://shuapi.jiaston.com"
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
                val list = requestJson("/book/$bookId/")
                parseChapterIds(list)
                requestJson("/book/$bookId/$chapterId.html")
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { renderOnlineChapter(it) }.onFailure {
                    renderBody(title.ifBlank { bookTitle.ifBlank { "备用章节" } }, "在线章节暂时加载失败。\n\n可能原因：书源接口不可达、网络超时、JS 跳转未解析成功，或该章节不存在。\n\n你可以返回重试、刷新章节列表，或等待接口恢复。")
                    toast("在线章节加载失败")
                }
                setLoading(false)
            }
        }
    }

    private fun parseChapterIds(json: JSONObject) {
        val data = json.optJSONObject("data") ?: json
        val groups = data.optJSONArray("list") ?: JSONArraySafe()
        chapterIds.clear()
        for (i in 0 until groups.length()) {
            val group = groups.optJSONObject(i) ?: continue
            val chapters = group.optJSONArray("list") ?: JSONArraySafe()
            for (j in 0 until chapters.length()) {
                val c = chapters.optJSONObject(j) ?: continue
                val id = firstNotBlank(c.optString("id"), c.optString("Id"))
                if (id.isNotBlank()) {
                    chapterIds.add(id)
                    if (id == chapterId) currentIndex = chapterIds.lastIndex
                }
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
            val result = runCatching { requestJson("/book/$bookId/$chapterId.html") }
            withContext(Dispatchers.Main) {
                result.onSuccess { renderOnlineChapter(it) }.onFailure { toast("切换章节失败") }
                setLoading(false)
            }
        }
    }

    private fun renderOnlineChapter(json: JSONObject) {
        val data = json.optJSONObject("data") ?: json
        val chapter = data.optJSONObject("chapter") ?: data
        val body = firstNotBlank(chapter.optString("content"), chapter.optString("body"), data.optString("content"), "暂无内容")
        val chapterTitle = firstNotBlank(chapter.optString("title"), chapter.optString("name"), title.ifBlank { bookTitle.ifBlank { "阅读" } })
        renderBody(chapterTitle, normalizeText(body))
    }

    private fun loadLocalChapter() {
        val body = when {
            chapterId.endsWith("1") -> "这是内置示例章节，用来保证书源接口不可用时页面仍能正常打开，不会闪退。\n\n在线接口恢复后，搜索和章节会优先展示真实书源内容。"
            chapterId.endsWith("2") -> "阅读页已加入空 ID、防异常响应、防网络失败保护。\n\n上一章/下一章仅对在线章节列表生效，示例章节会提示不支持翻页。"
            else -> "当前版本已改为笔趣阁接口体系，并支持服务端 HTML 跳转解析。\n\n如果设备网络无法访问书源，则会回退到示例章节显示。"
        }
        renderBody(title.ifBlank { "示例章节" }, body)
    }

    private fun renderBody(titleText: String, bodyText: String) {
        findViewById<TextView>(R.id.tvTitle).text = titleText
        findViewById<TextView>(R.id.tvBody).text = bodyText
    }

    private fun requestJson(path: String): JSONObject {
        val normalized = if (path.startsWith("http")) path else primaryDomain + path
        return runCatching { JSONObject(readUrl(normalized)) }.getOrElse {
            val fallback = if (path.startsWith("http")) path.replace(primaryDomain, fallbackDomain) else fallbackDomain + path
            JSONObject(readUrl(fallback))
        }
    }

    private fun readUrl(url: String): String {
        var current = url
        repeat(2) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 8000
                readTimeout = 12000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) YunoTools/1.1.74")
                setRequestProperty("Accept", "application/json,text/html,*/*")
                setRequestProperty("Connection", "close")
            }
            try {
                val stream = if (conn.responseCode in 200..399) conn.inputStream else conn.errorStream
                val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val jump = Regex("window\\.location\\.replace\\('([^']+)'\\)").find(body)?.groupValues?.getOrNull(1)
                if (!jump.isNullOrBlank()) current = jump else return body.replace("},]", "}]")
            } finally { conn.disconnect() }
        }
        throw IllegalStateException("redirect loop")
    }

    private fun normalizeText(text: String): String = text
        .replace("\r\n　　\r\n", "\n")
        .replace("\r\n", "\n")
        .replace("“", "")
        .replace("”", "")
        .trim()

    private fun firstNotBlank(vararg values: String) = values.firstOrNull { it.isNotBlank() }.orEmpty()
    private fun setLoading(loading: Boolean) { findViewById<ProgressBar>(R.id.progress).visibility = if (loading) View.VISIBLE else View.GONE }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private class JSONArraySafe : org.json.JSONArray()

}

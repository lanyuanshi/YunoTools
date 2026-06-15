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
    private val base = "http://api.ixdzs.com"
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
        findViewById<TextView>(R.id.tvTitle).text = title.ifBlank { "阅读" }
        loadChapterListAndBody()
    }

    override fun onResume() { super.onResume(); ThemeApplier.apply(this) }

    private fun loadChapterListAndBody() {
        if (bookId.isBlank() || chapterId.isBlank()) {
            renderBody(title.ifBlank { "阅读" }, "章节信息不完整，无法加载正文。")
            return
        }
        if (bookId.startsWith("local_") || chapterId.startsWith("local_")) {
            loadLocalChapter()
            return
        }
        setLoading(true)
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching {
                val list = requestJson("$base/content-ios/$bookId")
                parseChapterIds(list)
                requestJson("$base/chapter/$bookId/$chapterId")
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { renderOnlineChapter(it) }
                    .onFailure {
                        renderBody(title.ifBlank { "备用章节" }, "在线章节暂时加载失败。\n\n可能原因：书源接口不可达、网络超时或该章节不存在。\n\n你可以返回重试、刷新章节列表，或等待接口恢复。")
                        toast("在线章节加载失败")
                    }
                setLoading(false)
            }
        }
    }

    private fun parseChapterIds(json: JSONObject) {
        val arr = json.optJSONArray("chapters") ?: return
        chapterIds.clear()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = firstNotBlank(o.optString("id"), o.optString("link").split('/').lastOrNull().orEmpty())
            if (id.isNotBlank()) {
                chapterIds.add(id)
                if (id == chapterId) currentIndex = chapterIds.lastIndex
            }
        }
    }

    private fun move(step: Int) {
        if (bookId.startsWith("local_") || chapterId.startsWith("local_")) {
            toast("示例章节不支持翻页")
            return
        }
        if (chapterIds.isEmpty()) {
            toast("章节列表未加载")
            return
        }
        val next = currentIndex + step
        if (next !in chapterIds.indices) {
            toast("没有更多章节了")
            return
        }
        chapterId = chapterIds[next]
        currentIndex = next
        loadChapterBody()
    }

    private fun loadChapterBody() {
        setLoading(true)
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching { requestJson("$base/chapter/$bookId/$chapterId") }
            withContext(Dispatchers.Main) {
                result.onSuccess { renderOnlineChapter(it) }
                    .onFailure { toast("切换章节失败") }
                setLoading(false)
            }
        }
    }

    private fun renderOnlineChapter(json: JSONObject) {
        val chapter = json.optJSONObject("chapter") ?: json
        renderBody(chapter.optString("title").ifBlank { title.ifBlank { "阅读" } }, firstNotBlank(chapter.optString("body"), chapter.optString("content"), "暂无内容"))
    }

    private fun loadLocalChapter() {
        val body = when {
            chapterId.endsWith("1") -> "这是内置示例章节，用来保证书源接口不可用时页面仍能正常打开，不会闪退。\n\n在线接口恢复后，搜索和章节会优先展示真实书源内容。"
            chapterId.endsWith("2") -> "阅读页已加入空 ID、防异常响应、防网络失败保护。\n\n上一章/下一章仅对在线章节列表生效，示例章节会提示不支持翻页。"
            else -> "当前参考项目的外层接口 novel.juhe.im 在测试环境出现 SSL EOF；真实服务层接口 api.ixdzs.com 也可能被网络环境拦截。\n\n本版本已改为真实路径并增加兜底显示。"
        }
        renderBody(title.ifBlank { "示例章节" }, body)
    }

    private fun renderBody(titleText: String, bodyText: String) {
        findViewById<TextView>(R.id.tvTitle).text = titleText
        findViewById<TextView>(R.id.tvBody).text = bodyText
    }

    private fun requestJson(url: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 12000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) YunoTools/1.1.73")
            setRequestProperty("Accept", "application/json,*/*")
            setRequestProperty("Connection", "close")
        }
        return try {
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun firstNotBlank(vararg values: String) = values.firstOrNull { it.isNotBlank() }.orEmpty()
    private fun setLoading(loading: Boolean) { findViewById<ProgressBar>(R.id.progress).visibility = if (loading) View.VISIBLE else View.GONE }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

package com.yuno.tools.ui.tools

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.yuno.tools.R
import com.yuno.tools.util.ThemeApplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class NovelDetailActivity : AppCompatActivity() {
    private val base = "http://api.ixdzs.com"
    private val imageBase = "https://img22.aixdzs.com/"
    private lateinit var adapter: ChapterAdapter
    private var bookId = ""
    private var fallbackTitle = ""
    private var fallbackAuthor = ""
    private var fallbackIntro = ""
    private var fallbackCover = ""
    private var fallbackCat = ""
    private var fallbackStatus = ""

    data class ChapterItem(val title: String, val id: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_novel_detail)
        ThemeApplier.apply(this)
        bookId = intent.getStringExtra("id").orEmpty()
        fallbackTitle = intent.getStringExtra("title").orEmpty()
        fallbackAuthor = intent.getStringExtra("author").orEmpty()
        fallbackIntro = intent.getStringExtra("intro").orEmpty()
        fallbackCover = intent.getStringExtra("cover").orEmpty()
        fallbackCat = intent.getStringExtra("cat").orEmpty()
        fallbackStatus = intent.getStringExtra("status").orEmpty()
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnRefreshChapters).setOnClickListener { loadDetail() }
        val rv = findViewById<RecyclerView>(R.id.rvChapters)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = ChapterAdapter { openReader(it) }
        rv.adapter = adapter
        renderFallbackInfo()
        loadDetail()
    }

    override fun onResume() { super.onResume(); ThemeApplier.apply(this) }

    private fun loadDetail() {
        if (bookId.isBlank()) {
            toast("书籍信息不完整")
            return
        }
        if (bookId.startsWith("local_")) {
            adapter.submit(localChapters(bookId))
            return
        }
        setLoading(true)
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching { requestJson("$base/book/$bookId") to requestJson("$base/content-ios/$bookId") }
            withContext(Dispatchers.Main) {
                result.onSuccess { (book, chaptersJson) ->
                    renderOnlineBook(book)
                    val chapters = parseChapters(chaptersJson)
                    adapter.submit(chapters)
                    if (chapters.isEmpty()) toast("暂无章节")
                }.onFailure {
                    adapter.submit(localChapters(bookId))
                    toast("在线详情不可用，已显示备用章节")
                }
                setLoading(false)
            }
        }
    }

    private fun renderFallbackInfo() {
        findViewById<TextView>(R.id.tvTitle).text = fallbackTitle.ifBlank { "小说详情" }
        findViewById<TextView>(R.id.tvMeta).text = listOf(fallbackAuthor, fallbackCat, fallbackStatus).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "书籍信息" }
        findViewById<TextView>(R.id.tvIntro).text = fallbackIntro.ifBlank { "暂无简介" }
        Glide.with(this).load(fallbackCover.ifBlank { R.drawable.bg_banner_random }).placeholder(R.drawable.bg_banner_random).error(R.drawable.bg_banner_random).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).into(findViewById(R.id.ivCover))
    }

    private fun renderOnlineBook(book: JSONObject) {
        val title = book.optString("title").ifBlank { fallbackTitle.ifBlank { "小说详情" } }
        val author = book.optString("author").ifBlank { fallbackAuthor }
        val cat = book.optString("cat").ifBlank { fallbackCat }
        val status = book.optString("zt").ifBlank { fallbackStatus }
        val wordCount = book.optString("wordCount")
        findViewById<TextView>(R.id.tvTitle).text = title
        findViewById<TextView>(R.id.tvMeta).text = listOf(author, cat, status, if (wordCount.isBlank()) "" else "${wordCount}字").filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "书籍信息" }
        findViewById<TextView>(R.id.tvIntro).text = firstNotBlank(book.optString("longIntro"), book.optString("shortIntro"), fallbackIntro, "暂无简介")
        Glide.with(this).load(normalizeCover(firstNotBlank(book.optString("cover"), fallbackCover))).placeholder(R.drawable.bg_banner_random).error(R.drawable.bg_banner_random).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).into(findViewById(R.id.ivCover))
    }

    private fun parseChapters(json: JSONObject): List<ChapterItem> {
        val arr = json.optJSONArray("chapters") ?: return emptyList()
        val list = mutableListOf<ChapterItem>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val title = item.optString("title").ifBlank { "第${i + 1}章" }
            val id = firstNotBlank(item.optString("id"), item.optString("link").split('/').lastOrNull().orEmpty())
            if (id.isNotBlank()) list.add(ChapterItem(title, id))
        }
        return list
    }

    private fun openReader(item: ChapterItem) {
        if (item.id.isBlank()) {
            toast("章节信息不完整")
            return
        }
        startActivity(Intent(this, NovelReaderPageActivity::class.java).apply {
            putExtra("bookId", bookId)
            putExtra("chapterId", item.id)
            putExtra("title", item.title)
            putExtra("bookTitle", fallbackTitle)
        })
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

    private fun localChapters(id: String): List<ChapterItem> {
        val prefix = if (id.startsWith("local_")) id else "local_online"
        return listOf(
            ChapterItem("第一章 书籍介绍", "${prefix}_chapter_1"),
            ChapterItem("第二章 阅读说明", "${prefix}_chapter_2"),
            ChapterItem("第三章 接口状态", "${prefix}_chapter_3")
        )
    }

    private fun normalizeCover(raw: String): String {
        val value = raw.trim()
        return when {
            value.isBlank() -> ""
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.startsWith("/") -> imageBase + value.removePrefix("/")
            else -> imageBase + value
        }
    }

    private fun firstNotBlank(vararg values: String) = values.firstOrNull { it.isNotBlank() }.orEmpty()
    private fun setLoading(loading: Boolean) { findViewById<ProgressBar>(R.id.progress).visibility = if (loading) View.VISIBLE else View.GONE }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private class ChapterAdapter(private val onClick: (ChapterItem) -> Unit) : RecyclerView.Adapter<ChapterVH>() {
        private var items: List<ChapterItem> = emptyList()
        fun submit(newItems: List<ChapterItem>) { items = newItems; notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ChapterVH {
            val tv = TextView(parent.context).apply {
                setPadding(28, 28, 28, 28)
                textSize = 15f
                setTextColor(0xFF241723.toInt())
                setBackgroundColor(0xFFFFFFFF.toInt())
            }
            return ChapterVH(tv, onClick)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: ChapterVH, position: Int) = holder.bind(items[position])
    }

    private class ChapterVH(itemView: android.view.View, private val onClick: (ChapterItem) -> Unit) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: ChapterItem) {
            (itemView as TextView).text = item.title
            itemView.setOnClickListener { onClick(item) }
        }
    }
}

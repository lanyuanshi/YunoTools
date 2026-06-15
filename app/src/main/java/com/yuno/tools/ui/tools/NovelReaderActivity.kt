package com.yuno.tools.ui.tools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class NovelReaderActivity : AppCompatActivity() {
    private val primaryDomain = "https://dynic.2otea.com"
    private val fallbackDomain = "https://shuapi.jiaston.com"
    private val imageBase = "https://appbdimg.cdn.bcebos.com/BookFiles/BookImages/"
    private lateinit var adapter: BookAdapter
    private var currentType = "hot"
    private var currentGender = "man"

    data class BookItem(
        val id: String,
        val title: String,
        val author: String,
        val intro: String,
        val cover: String,
        val cat: String,
        val zt: String,
        val last: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_novel_reader)
        ThemeApplier.apply(this)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { loadBooks() }
        findViewById<Button>(R.id.btnSearch).setOnClickListener { searchBooks() }
        findViewById<EditText>(R.id.etKeyword).setOnEditorActionListener { _, _, _ -> searchBooks(); true }
        findViewById<RecyclerView>(R.id.rvBooks).apply {
            layoutManager = LinearLayoutManager(this@NovelReaderActivity)
            adapter = BookAdapter { openDetail(it) }.also { this@NovelReaderActivity.adapter = it }
        }
        setupFilters()
        loadBooks()
    }

    override fun onResume() { super.onResume(); ThemeApplier.apply(this) }

    private fun setupFilters() {
        val row = findViewById<android.widget.LinearLayout>(R.id.filterRow)
        row.removeAllViews()
        listOf(
            "man:hot" to "男频最热",
            "lady:hot" to "女频最热",
            "man:new" to "新书",
            "man:over" to "完结",
            "man:vote" to "评分"
        ).forEach { (key, label) ->
            val parts = key.split(':')
            val gender = parts[0]
            val type = parts[1]
            val selected = gender == currentGender && type == currentType
            val button = Button(this).apply {
                text = label
                setTextColor(resources.getColor(android.R.color.white, theme))
                backgroundTintList = android.content.res.ColorStateList.valueOf(if (selected) 0xFF7C3AED.toInt() else 0xFF8E8E93.toInt())
                setOnClickListener {
                    currentGender = gender
                    currentType = type
                    findViewById<EditText>(R.id.etKeyword).setText("")
                    setupFilters()
                    loadBooks()
                }
            }
            row.addView(button, android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)).apply {
                val margin = dp(6)
                setMargins(margin, margin, margin, margin)
            })
        }
    }

    private fun loadBooks() {
        setLoading(true, "加载榜单中...")
        CoroutineScope(Dispatchers.IO).launch {
            val online = runCatching { fetchTopBooks(currentGender, currentType) }.getOrElse { emptyList() }
            val list = online.ifEmpty { fallbackBooks() }
            withContext(Dispatchers.Main) {
                adapter.submit(list)
                findViewById<TextView>(R.id.tvState).text = if (online.isEmpty()) {
                    "笔趣阁接口暂不可用，已显示内置示例书库"
                } else {
                    "榜单共 ${list.size} 本，点击进入详情"
                }
                setLoading(false, "")
            }
        }
    }

    private fun searchBooks() {
        val keyword = findViewById<EditText>(R.id.etKeyword).text.toString().trim()
        if (keyword.isBlank()) { loadBooks(); return }
        setLoading(true, "搜索中...")
        CoroutineScope(Dispatchers.IO).launch {
            val online = runCatching { searchBookList(keyword) }.getOrElse { emptyList() }
            val local = fallbackBooks().filter { it.title.contains(keyword, true) || it.author.contains(keyword, true) }
            val list = online.ifEmpty { local }
            withContext(Dispatchers.Main) {
                adapter.submit(list)
                findViewById<TextView>(R.id.tvState).text = when {
                    online.isNotEmpty() -> "搜索到 ${list.size} 本"
                    local.isNotEmpty() -> "在线搜索不可用，已从内置书库找到 ${local.size} 本"
                    else -> "没有找到相关书籍，笔趣阁接口可能暂不可用"
                }
                setLoading(false, "")
            }
        }
    }

    private fun fetchTopBooks(gender: String, type: String): List<BookItem> {
        val path = "/top/$gender/top/$type/week/1.html"
        val json = requestJson(path)
        val data = json.optJSONObject("data") ?: json
        val arr = data.optJSONArray("BookList") ?: data.optJSONArray("data") ?: JSONArray()
        return parseBooks(arr)
    }

    private fun searchBookList(keyword: String): List<BookItem> {
        val json = requestJson("/search.aspx?key=${Uri.encode(keyword)}&page=1&siteid=app2")
        val arr = json.optJSONArray("data") ?: json.optJSONObject("data")?.optJSONArray("BookList") ?: JSONArray()
        return parseBooks(arr)
    }

    private fun parseBooks(arr: JSONArray): List<BookItem> {
        val list = mutableListOf<BookItem>()
        for (i in 0 until arr.length()) {
            val item = bookFrom(arr.optJSONObject(i))
            if (item != null && item.id.isNotBlank() && item.title.isNotBlank()) list.add(item)
        }
        return list
    }

    private fun bookFrom(o: JSONObject?): BookItem? {
        o ?: return null
        val id = firstNotBlank(o.optString("Id"), o.optString("id"), o.optString("BookId"))
        return BookItem(
            id = id,
            title = firstNotBlank(o.optString("Name"), o.optString("title"), o.optString("name")),
            author = firstNotBlank(o.optString("Author"), o.optString("author"), "佚名"),
            intro = firstNotBlank(o.optString("Desc"), o.optString("desc"), o.optString("intro"), "暂无简介"),
            cover = normalizeCover(firstNotBlank(o.optString("Img"), o.optString("cover"))),
            cat = firstNotBlank(o.optString("CName"), o.optString("cat"), "小说"),
            zt = firstNotBlank(o.optString("BookStatus"), o.optString("status"), "连载/完结"),
            last = firstNotBlank(o.optString("LastChapter"), o.optString("lastChapter"), "点击查看详情")
        )
    }

    private fun openDetail(item: BookItem) {
        if (item.id.isBlank()) { toast("书籍信息不完整"); return }
        startActivity(Intent(this, NovelDetailActivity::class.java).apply {
            putExtra("id", item.id)
            putExtra("title", item.title)
            putExtra("author", item.author)
            putExtra("intro", item.intro)
            putExtra("cover", item.cover)
            putExtra("cat", item.cat)
            putExtra("status", item.zt)
        })
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
                if (!jump.isNullOrBlank()) {
                    current = jump
                } else {
                    return body.replace("},]", "}]")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IllegalStateException("redirect loop")
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

    private fun fallbackBooks() = listOf(
        BookItem("local_1", "三体", "刘慈欣", "文明在宇宙尺度上的碰撞与选择，适合测试阅读链路。", "", "科幻", "完结", "内置示例"),
        BookItem("local_2", "西游记", "吴承恩", "师徒四人西行取经，降妖伏魔，章回体古典名著。", "", "古典", "完结", "内置示例"),
        BookItem("local_3", "红楼梦", "曹雪芹", "以贾府兴衰与人物命运为主线的古典长篇小说。", "", "古典", "完结", "内置示例"),
        BookItem("local_4", "斗破苍穹", "天蚕土豆", "少年成长、热血修炼与冒险升级的网络小说示例。", "", "玄幻", "完结", "内置示例")
    )

    private fun firstNotBlank(vararg values: String) = values.firstOrNull { it.isNotBlank() }.orEmpty()
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun setLoading(loading: Boolean, text: String) {
        findViewById<ProgressBar>(R.id.progress).visibility = if (loading) View.VISIBLE else View.GONE
        if (text.isNotBlank()) findViewById<TextView>(R.id.tvState).text = text
        findViewById<Button>(R.id.btnRefresh).isEnabled = !loading
        findViewById<Button>(R.id.btnSearch).isEnabled = !loading
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private class BookAdapter(private val onClick: (BookItem) -> Unit) : RecyclerView.Adapter<BookVH>() {
        private var items: List<BookItem> = emptyList()
        fun submit(newItems: List<BookItem>) { items = newItems; notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): BookVH {
            val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_novel_book, parent, false)
            return BookVH(view, onClick)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: BookVH, position: Int) = holder.bind(items[position])
    }

    private class BookVH(itemView: android.view.View, private val onClick: (BookItem) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val iv = itemView.findViewById<ImageView>(R.id.ivCover)
        private val title = itemView.findViewById<TextView>(R.id.tvBookTitle)
        private val meta = itemView.findViewById<TextView>(R.id.tvBookMeta)
        private val intro = itemView.findViewById<TextView>(R.id.tvBookIntro)
        fun bind(item: BookItem) {
            title.text = item.title.ifBlank { "未命名书籍" }
            meta.text = listOf(item.author, item.cat, item.zt).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "书籍信息" }
            intro.text = item.intro.ifBlank { item.last.ifBlank { "暂无简介" } }
            Glide.with(iv).load(item.cover.ifBlank { R.drawable.bg_banner_random }).placeholder(R.drawable.bg_banner_random).error(R.drawable.bg_banner_random).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).into(iv)
            itemView.setOnClickListener { onClick(item) }
        }
    }
}

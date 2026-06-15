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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class NovelReaderActivity : AppCompatActivity() {
    private val base = "http://api.ixdzs.com"
    private val imageBase = "https://img22.aixdzs.com/"
    private lateinit var rv: RecyclerView
    private lateinit var adapter: BookAdapter
    private var currentType = "hot"

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
        rv = findViewById(R.id.rvBooks)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = BookAdapter { openDetail(it) }
        rv.adapter = adapter
        setupFilters()
        loadBooks()
    }

    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
    }

    private fun setupFilters() {
        val row = findViewById<android.widget.LinearLayout>(R.id.filterRow)
        row.removeAllViews()
        listOf("hot" to "最热", "new" to "最新", "over" to "完结").forEach { (key, label) ->
            val button = Button(this).apply {
                text = label
                setTextColor(resources.getColor(android.R.color.white, theme))
                backgroundTintList = android.content.res.ColorStateList.valueOf(if (key == currentType) 0xFF7C3AED.toInt() else 0xFF8E8E93.toInt())
                setOnClickListener {
                    currentType = key
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
        setLoading(true, "加载中...")
        CoroutineScope(Dispatchers.IO).launch {
            val online = runCatching { fetchBooks(currentType) }.getOrElse { emptyList() }
            val list = online.ifEmpty { fallbackBooks() }
            withContext(Dispatchers.Main) {
                adapter.submit(list)
                findViewById<TextView>(R.id.tvState).text = if (online.isEmpty()) {
                    "在线接口暂不可用，已显示内置示例书库"
                } else {
                    "共${list.size}本，点击进入详情"
                }
                setLoading(false, "")
            }
        }
    }

    private fun searchBooks() {
        val keyword = findViewById<EditText>(R.id.etKeyword).text.toString().trim()
        if (keyword.isBlank()) {
            loadBooks()
            return
        }
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
                    else -> "没有找到相关书籍，在线接口可能暂不可用"
                }
                setLoading(false, "")
            }
        }
    }

    private fun fetchBooks(type: String): List<BookItem> {
        val json = request("$base/book-sort?start=0&limit=30&type=$type")
        return parseBooks(json)
    }

    private fun searchBookList(keyword: String): List<BookItem> {
        val json = request("$base/book/search?query=${Uri.encode(keyword)}")
        return parseBooks(json)
    }

    private fun parseBooks(json: JSONObject): List<BookItem> {
        val arr = json.optJSONArray("books") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { bookFrom(arr.optJSONObject(it)) }.filter { it.id.isNotBlank() && it.title.isNotBlank() }
    }

    private fun bookFrom(o: JSONObject?): BookItem? {
        o ?: return null
        val id = firstNotBlank(o.optString("_id"), o.optString("id"), o.optString("bookId"))
        val cover = normalizeCover(o.optString("cover"))
        return BookItem(
            id = id,
            title = o.optString("title").ifBlank { o.optString("name") },
            author = o.optString("author").ifBlank { "佚名" },
            intro = firstNotBlank(o.optString("shortIntro"), o.optString("longIntro"), o.optString("intro"), "暂无简介"),
            cover = cover,
            cat = o.optString("cat").ifBlank { o.optString("majorCate") },
            zt = firstNotBlank(o.optString("zt"), o.optString("status"), "连载/完结"),
            last = firstNotBlank(o.optString("lastchapter"), o.optString("lastChapter"), "点击查看详情")
        )
    }

    private fun normalizeCover(raw: String): String {
        val value = raw.trim()
        return when {
            value.isBlank() -> "https://img22.aixdzs.com/nopic2.jpg"
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.startsWith("/") -> imageBase + value.removePrefix("/")
            else -> imageBase + value
        }
    }

    private fun openDetail(item: BookItem) {
        if (item.id.isBlank()) {
            toast("书籍信息不完整")
            return
        }
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

    private fun request(url: String): JSONObject {
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
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(body)
        } finally {
            conn.disconnect()
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

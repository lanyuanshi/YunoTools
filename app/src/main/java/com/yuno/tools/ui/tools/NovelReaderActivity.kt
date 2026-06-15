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
    private val base = "https://novel.juhe.im"
    private lateinit var rv: RecyclerView
    private lateinit var adapter: BookAdapter
    private val books = mutableListOf<BookItem>()
    private var currentType = "hot"
    private var currentStart = 0
    private var currentQuery = ""

    data class BookItem(val id: String, val title: String, val author: String, val intro: String, val cover: String, val cat: String, val zt: String, val last: String)

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
        val filters = listOf("hot" to "最热", "new" to "最新", "over" to "完结")
        filters.forEach { (key, label) ->
            val b = Button(this).apply {
                text = label
                setTextColor(resources.getColor(android.R.color.white, theme))
                backgroundTintList = android.content.res.ColorStateList.valueOf(if (key == currentType) 0xFF7C3AED.toInt() else 0xFF8E8E93.toInt())
                setOnClickListener { currentType = key; currentStart = 0; currentQuery = ""; findViewById<EditText>(R.id.etKeyword).setText(""); loadBooks() }
            }
            row.addView(b, android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, (44 * resources.displayMetrics.density + 0.5f).toInt()).apply { val m = (6 * resources.displayMetrics.density + 0.5f).toInt(); setMargins(m, m, m, m) })
        }
    }

    private fun loadBooks() {
        setLoading(true, "加载中...")
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching { fetchBooks(currentStart, currentType) }
            withContext(Dispatchers.Main) {
                result.onSuccess { list ->
                    books.clear(); books.addAll(list); adapter.submit(list)
                    findViewById<TextView>(R.id.tvState).text = if (list.isEmpty()) "没有更多内容" else "共${list.size}本，点击进入详情"
                }.onFailure {
                    books.clear(); adapter.submit(emptyList())
                    toast("加载失败，请稍后重试")
                    findViewById<TextView>(R.id.tvState).text = "加载失败"
                }
                setLoading(false, "")
            }
        }
    }

    private fun searchBooks() {
        val kw = findViewById<EditText>(R.id.etKeyword).text.toString().trim()
        if (kw.isBlank()) { loadBooks(); return }
        currentQuery = kw
        setLoading(true, "搜索中...")
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching { searchBookList(kw) }
            withContext(Dispatchers.Main) {
                result.onSuccess { list ->
                    books.clear(); books.addAll(list); adapter.submit(list)
                    findViewById<TextView>(R.id.tvState).text = "搜索到 ${list.size} 本"
                }.onFailure {
                    toast("搜索失败")
                    findViewById<TextView>(R.id.tvState).text = "搜索失败"
                }
                setLoading(false, "")
            }
        }
    }

    private fun fetchBooks(start: Int, type: String): List<BookItem> {
        val url = "$base/books?start=$start&limit=20&type=$type"
        val json = request(url)
        val arr = json.optJSONArray("books") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> bookFrom(arr.optJSONObject(i)) }
    }

    private fun searchBookList(keyword: String): List<BookItem> {
        val json = request("$base/search?query=${Uri.encode(keyword)}")
        val arr = json.optJSONArray("books") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> bookFrom(arr.optJSONObject(i)) }
    }

    private fun bookFrom(o: JSONObject?): BookItem? {
        o ?: return null
        return BookItem(
            id = o.optString("_id"),
            title = o.optString("title"),
            author = o.optString("author"),
            intro = o.optString("shortIntro"),
            cover = o.optString("cover"),
            cat = o.optString("cat"),
            zt = o.optString("zt"),
            last = o.optString("lastchapter")
        )
    }

    private fun openDetail(item: BookItem) {
        startActivity(Intent(this, NovelDetailActivity::class.java).apply { putExtra("id", item.id) })
    }

    private fun request(url: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("User-Agent", "Mozilla/5.0 YunoTools")
            setRequestProperty("Accept", "application/json,*/*")
        }
        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        conn.disconnect()
        return JSONObject(body)
    }

    private fun setLoading(loading: Boolean, text: String) {
        findViewById<ProgressBar>(R.id.progress).visibility = if (loading) View.VISIBLE else View.GONE
        if (text.isNotBlank()) findViewById<TextView>(R.id.tvState).text = text
        findViewById<Button>(R.id.btnRefresh).isEnabled = !loading
        findViewById<Button>(R.id.btnSearch).isEnabled = !loading
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private class BookAdapter(val onClick: (BookItem) -> Unit) : RecyclerView.Adapter<BookVH>() {
        private var items: List<BookItem> = emptyList()
        fun submit(newItems: List<BookItem>) { items = newItems; notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): BookVH {
            val v = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_novel_book, parent, false)
            return BookVH(v, onClick)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: BookVH, position: Int) = holder.bind(items[position])
    }

    private class BookVH(itemView: android.view.View, val onClick: (BookItem) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val iv = itemView.findViewById<ImageView>(R.id.ivCover)
        private val title = itemView.findViewById<TextView>(R.id.tvBookTitle)
        private val meta = itemView.findViewById<TextView>(R.id.tvBookMeta)
        private val intro = itemView.findViewById<TextView>(R.id.tvBookIntro)
        fun bind(item: BookItem) {
            title.text = item.title
            meta.text = "${item.author} · ${item.cat} · ${item.zt}"
            intro.text = item.intro.ifBlank { item.last }
            Glide.with(iv).load(item.cover).placeholder(R.drawable.bg_banner_random).error(R.drawable.bg_banner_random).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).into(iv)
            itemView.setOnClickListener { onClick(item) }
        }
    }
}

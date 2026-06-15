package com.yuno.tools.ui.tools

import android.content.Intent
import android.net.Uri
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
    private val base = "https://novel.juhe.im"
    private lateinit var rv: RecyclerView
    private lateinit var adapter: ChapterAdapter
    private var bookId = ""
    private val chapters = mutableListOf<ChapterItem>()
    data class ChapterItem(val title: String, val id: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_novel_detail)
        ThemeApplier.apply(this)
        bookId = intent.getStringExtra("id").orEmpty()
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnRefreshChapters).setOnClickListener { loadDetail() }
        rv = findViewById(R.id.rvChapters)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = ChapterAdapter { openReader(it) }
        rv.adapter = adapter
        loadDetail()
    }

    override fun onResume() { super.onResume(); ThemeApplier.apply(this) }

    private fun loadDetail() {
        setLoading(true)
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching { requestJson("$base/book/$bookId") to requestJson("$base/book/$bookId/chapters") }
            withContext(Dispatchers.Main) {
                result.onSuccess { (book, ch) ->
                    findViewById<TextView>(R.id.tvTitle).text = book.optString("title")
                    findViewById<TextView>(R.id.tvMeta).text = "${book.optString("author")} · ${book.optString("cat")} · ${book.optString("zt")} · ${book.optString("wordCount")}字"
                    findViewById<TextView>(R.id.tvIntro).text = book.optString("longIntro")
                    Glide.with(this@NovelDetailActivity).load(book.optString("cover")).placeholder(R.drawable.bg_banner_random).error(R.drawable.bg_banner_random).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).into(findViewById(R.id.ivCover))
                    val arr = ch.optJSONArray("chapters")
                    chapters.clear()
                    if (arr != null) for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        chapters.add(ChapterItem(o.optString("title"), o.optString("id")))
                    }
                    adapter.submit(chapters)
                }.onFailure { toast("加载详情失败") }
                setLoading(false)
            }
        }
    }

    private fun openReader(item: ChapterItem) {
        startActivity(Intent(this, NovelReaderPageActivity::class.java).apply {
            putExtra("bookId", bookId)
            putExtra("chapterId", item.id)
            putExtra("title", item.title)
        })
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

    private class ChapterAdapter(val onClick: (ChapterItem) -> Unit) : RecyclerView.Adapter<ChapterVH>() {
        private var items: List<ChapterItem> = emptyList()
        fun submit(newItems: List<ChapterItem>) { items = newItems; notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ChapterVH {
            val tv = TextView(parent.context).apply {
                setPadding(24, 28, 24, 28)
                textSize = 15f
                setTextColor(0xFF241723.toInt())
                setBackgroundColor(0xFFFFFFFF.toInt())
            }
            return ChapterVH(tv, onClick)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: ChapterVH, position: Int) = holder.bind(items[position])
    }

    private class ChapterVH(itemView: android.view.View, val onClick: (ChapterItem) -> Unit) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: ChapterItem) {
            (itemView as TextView).text = item.title
            itemView.setOnClickListener { onClick(item) }
        }
    }
}

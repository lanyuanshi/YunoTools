package com.yuno.tools.ui.tools

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.R
import com.yuno.tools.util.ThemeApplier
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.roundToInt

class TinyReaderActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var titleView: TextView
    private lateinit var contentHost: FrameLayout
    private lateinit var navRead: LinearLayout
    private lateinit var navShelf: LinearLayout
    private lateinit var navMine: LinearLayout
    private var currentBook: Book? = null

    private data class Book(val title: String, val url: String, val sourceName: String, val content: String)
    private data class BookSource(val name: String, val baseUrl: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildShell()
        ensureDefaultSource()
        showReadTab()
        ThemeApplier.apply(this)
    }

    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.profile_stay, R.anim.profile_slide_down_out)
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F2F2F7"))
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }
        val status = View(this).apply {
            id = resources.getIdentifier("statusBarPlaceholder", "id", packageName)
            layoutParams = LinearLayout.LayoutParams(-1, dp(24))
            setBackgroundColor(Color.parseColor("#F2F2F7"))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(16), dp(10))
        }
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.parseColor("#1C1C1E"))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            setOnClickListener { finish() }
        }
        titleView = TextView(this).apply {
            text = "小小读书"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1C1C1E"))
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        }
        header.addView(back)
        header.addView(titleView)
        contentHost = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        val bottomWrap = LinearLayout(this).apply {
            id = resources.getIdentifier("bottomNavContainer", "id", packageName)
            setPadding(dp(16), dp(8), dp(16), dp(14))
            setBackgroundColor(Color.parseColor("#F2F2F7"))
        }
        val bottomInner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = roundBg(Color.WHITE, dp(28).toFloat())
            layoutParams = LinearLayout.LayoutParams(-1, dp(64))
        }
        navRead = navItem("读书") { showReadTab() }
        navShelf = navItem("书架") { showShelfTab() }
        navMine = navItem("我的") { showMineTab() }
        bottomInner.addView(navRead)
        bottomInner.addView(navShelf)
        bottomInner.addView(navMine)
        bottomWrap.addView(bottomInner)
        root.addView(status)
        root.addView(header)
        root.addView(contentHost)
        root.addView(bottomWrap)
        setContentView(root)
    }

    private fun navItem(text: String, click: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(3); marginEnd = dp(3) }
        addView(TextView(this@TinyReaderActivity).apply {
            this.text = text
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        setOnClickListener { click() }
    }

    private fun selectTab(tab: String) {
        titleView.text = when (tab) { TAB_SHELF -> "小小书架"; TAB_MINE -> "我的书源"; else -> "小小读书" }
        listOf(TAB_READ to navRead, TAB_SHELF to navShelf, TAB_MINE to navMine).forEach { (name, item) ->
            val selected = name == tab
            item.background = if (selected) roundBg(Color.parseColor("#E8F3FF"), dp(20).toFloat()) else null
            (item.getChildAt(0) as TextView).setTextColor(if (selected) Color.parseColor("#007AFF") else Color.parseColor("#8E8E93"))
        }
    }

    private fun showReadTab() {
        selectTab(TAB_READ)
        contentHost.removeAllViews()
        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(18)) }
        val titleInput = edit("书名（可选）")
        val urlInput = edit("小说链接，或输入关键词后选择书源加载").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
        box.addView(infoCard("读书", "粘贴小说章节/网页链接可直接加载；也可以先在“我的”添加书源，再输入关键词或路径组合加载。加载成功后可收藏到书架。"))
        box.addView(label("书名")); box.addView(titleInput)
        box.addView(label("链接 / 关键词 / 路径")); box.addView(urlInput)
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(primaryButton("加载小说") {
            val raw = urlInput.text.toString().trim()
            if (raw.isBlank()) toast("请输入小说链接或关键词") else loadBook(titleInput.text.toString().trim().ifBlank { "未命名小说" }, normalizeUrl(raw, null), "直接链接")
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) })
        btnRow.addView(outlineButton("收藏到书架") {
            currentBook?.let { saveBook(it); toast("已收藏到书架") } ?: toast("请先加载一本小说")
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(8) })
        box.addView(btnRow)
        box.addView(label("书源加载"))
        getSources().forEach { source ->
            box.addView(sourceCard(source) {
                val raw = urlInput.text.toString().trim()
                if (raw.isBlank()) toast("请输入关键词或路径") else loadBook(titleInput.text.toString().trim().ifBlank { raw }, normalizeUrl(raw, source), source.name)
            })
        }
        box.addView(label("阅读内容"))
        box.addView(readerCard("暂无内容，请先加载小说。"))
        scroll.addView(box)
        contentHost.addView(scroll)
    }

    private fun showShelfTab() {
        selectTab(TAB_SHELF)
        contentHost.removeAllViews()
        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(18)) }
        val books = getShelf()
        if (books.isEmpty()) box.addView(infoCard("书架为空", "在“读书”页加载小说后，点击“收藏到书架”，这里会记录你的收藏书籍。"))
        else {
            books.forEach { box.addView(bookCard(it)) }
            box.addView(outlineButton("清空书架") { prefs().edit().putString(KEY_SHELF, "[]").apply(); toast("书架已清空"); showShelfTab() }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        }
        scroll.addView(box); contentHost.addView(scroll)
    }

    private fun showMineTab() {
        selectTab(TAB_MINE)
        contentHost.removeAllViews()
        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(18)) }
        val nameInput = edit("书源名称，例如：我的书源")
        val urlInput = edit("书源地址，例如：https://example.com/search?q=").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
        box.addView(infoCard("添加书源", "支持保存多个书源。书源地址可以是站点首页、搜索前缀或目录前缀；读书页会把你输入的关键词/路径拼接到书源地址后加载。"))
        box.addView(label("书源名称")); box.addView(nameInput)
        box.addView(label("书源地址")); box.addView(urlInput)
        box.addView(primaryButton("保存书源") {
            val name = nameInput.text.toString().trim(); val url = urlInput.text.toString().trim()
            if (name.isBlank() || url.isBlank()) toast("请填写书源名称和地址") else { saveSource(BookSource(name, url)); toast("书源已保存"); showMineTab() }
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
        box.addView(label("已添加书源"))
        val sources = getSources()
        if (sources.isEmpty()) box.addView(infoCard("暂无书源", "添加后会显示在这里，并可在读书页选择使用。")) else sources.forEach { box.addView(sourceManageCard(it)) }
        scroll.addView(box); contentHost.addView(scroll)
    }

    private fun loadBook(title: String, url: String, sourceName: String) {
        val textView = (((contentHost.getChildAt(0) as? ScrollView)?.getChildAt(0) as? LinearLayout)?.let { it.getChildAt(it.childCount - 1) } as? MaterialCardView)?.let { (it.getChildAt(0) as? LinearLayout)?.getChildAt(0) as? TextView }
        textView?.text = "正在加载：$url"
        Thread {
            try {
                val request = Request.Builder().url(url).addHeader("User-Agent", "YunoTools TinyReader/1.0.55").build()
                val body = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                    response.body?.string().orEmpty()
                }
                val clean = cleanNovelText(body).ifBlank { "已加载，但页面没有提取到可读文本。" }
                val book = Book(title, url, sourceName, clean.take(MAX_SAVE_CONTENT))
                currentBook = book
                runOnUiThread { textView?.text = "《$title》\n来源：$sourceName\n$url\n\n$clean" }
            } catch (e: Exception) {
                runOnUiThread { textView?.text = "加载失败：${e.message}\n\n请检查链接是否可访问，或在“我的”添加可用书源。" }
            }
        }.start()
    }

    private fun cleanNovelText(raw: String): String {
        return raw
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<(br|p|div|li|h[1-6])[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n").take(20000)
    }

    private fun normalizeUrl(raw: String, source: BookSource?): String {
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val base = source?.baseUrl?.trim().orEmpty()
        val encoded = runCatching { URLEncoder.encode(raw, "UTF-8") }.getOrDefault(raw)
        return when {
            base.contains("{keyword}") -> base.replace("{keyword}", encoded)
            base.endsWith("/") || base.endsWith("=") || base.endsWith("?") || base.endsWith("&") -> base + encoded
            raw.startsWith("/") -> base.trimEnd('/') + raw
            else -> base.trimEnd('/') + "/" + raw
        }
    }

    private fun saveBook(book: Book) {
        val arr = JSONArray()
        (listOf(book) + getShelf().filterNot { it.url == book.url }).take(50).forEach { arr.put(bookToJson(it)) }
        prefs().edit().putString(KEY_SHELF, arr.toString()).apply()
    }
    private fun getShelf(): List<Book> = parseArray(KEY_SHELF) { Book(it.optString("title"), it.optString("url"), it.optString("sourceName"), it.optString("content")) }
    private fun saveSource(source: BookSource) {
        val arr = JSONArray()
        (listOf(source) + getSources().filterNot { it.name == source.name || it.baseUrl == source.baseUrl }).take(30).forEach { arr.put(JSONObject().put("name", it.name).put("baseUrl", it.baseUrl)) }
        prefs().edit().putString(KEY_SOURCES, arr.toString()).apply()
    }
    private fun getSources(): List<BookSource> = parseArray(KEY_SOURCES) { BookSource(it.optString("name"), it.optString("baseUrl")) }.filter { it.name.isNotBlank() && it.baseUrl.isNotBlank() }
    private fun ensureDefaultSource() { if (!prefs().contains(KEY_SOURCES)) prefs().edit().putString(KEY_SOURCES, JSONArray().put(JSONObject().put("name", "示例书源").put("baseUrl", "https://www.baidu.com/s?wd=")).toString()).apply() }
    private fun <T> parseArray(key: String, mapper: (JSONObject) -> T): List<T> = runCatching { val arr = JSONArray(prefs().getString(key, "[]").orEmpty()); (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(mapper) } }.getOrDefault(emptyList())
    private fun bookToJson(book: Book): JSONObject = JSONObject().put("title", book.title).put("url", book.url).put("sourceName", book.sourceName).put("content", book.content)

    private fun bookCard(book: Book): MaterialCardView = baseCard().apply {
        val box = LinearLayout(this@TinyReaderActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
        box.addView(TextView(this@TinyReaderActivity).apply { text = book.title.ifBlank { "未命名小说" }; textSize = 17f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#111827")) })
        box.addView(TextView(this@TinyReaderActivity).apply { text = "来源：${book.sourceName}\n${book.url}"; textSize = 13f; setTextColor(Color.parseColor("#8E8E93")) })
        val row = LinearLayout(this@TinyReaderActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        row.addView(primaryButton("继续阅读") { currentBook = book; showReadTab(); val tv = (((contentHost.getChildAt(0) as? ScrollView)?.getChildAt(0) as? LinearLayout)?.let { it.getChildAt(it.childCount - 1) } as? MaterialCardView)?.let { (it.getChildAt(0) as? LinearLayout)?.getChildAt(0) as? TextView }; tv?.text = "《${book.title}》\n来源：${book.sourceName}\n${book.url}\n\n${book.content}" }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
        row.addView(outlineButton("删除") { val arr = JSONArray(); getShelf().filterNot { it.url == book.url }.forEach { arr.put(bookToJson(it)) }; prefs().edit().putString(KEY_SHELF, arr.toString()).apply(); toast("已删除"); showShelfTab() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(6) })
        box.addView(row); addView(box)
    }
    private fun sourceCard(source: BookSource, click: () -> Unit): MaterialCardView = baseCard().apply { setOnClickListener { click() }; addView(simpleBox(source.name, source.baseUrl)) }
    private fun sourceManageCard(source: BookSource): MaterialCardView = baseCard().apply { val box = simpleBox(source.name, source.baseUrl); box.addView(outlineButton("删除书源") { val arr = JSONArray(); getSources().filterNot { it.name == source.name && it.baseUrl == source.baseUrl }.forEach { arr.put(JSONObject().put("name", it.name).put("baseUrl", it.baseUrl)) }; prefs().edit().putString(KEY_SOURCES, arr.toString()).apply(); toast("书源已删除"); showMineTab() }, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(10) }); addView(box) }
    private fun simpleBox(title: String, sub: String): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)); addView(TextView(this@TinyReaderActivity).apply { text = title; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#111827")) }); addView(TextView(this@TinyReaderActivity).apply { text = sub; textSize = 13f; setTextColor(Color.parseColor("#8E8E93")) }) }
    private fun infoCard(title: String, desc: String): MaterialCardView = baseCard().apply { val box = simpleBox(title, desc); addView(box) }
    private fun readerCard(text: String): MaterialCardView = baseCard().apply { val box = LinearLayout(this@TinyReaderActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }; box.addView(TextView(this@TinyReaderActivity).apply { this.text = text; textSize = 16f; setTextColor(Color.parseColor("#1C1C1E")); setLineSpacing(dp(7).toFloat(), 1.08f) }); addView(box) }
    private fun baseCard(): MaterialCardView = MaterialCardView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }; radius = dp(18).toFloat(); cardElevation = 0f; setCardBackgroundColor(Color.WHITE); strokeWidth = 0 }
    private fun label(text: String): TextView = TextView(this).apply { this.text = text; textSize = 15f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.parseColor("#1C1C1E")); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(8) } }
    private fun edit(hintText: String): EditText = EditText(this).apply { hint = hintText; textSize = 15f; setSingleLine(true); setTextColor(Color.parseColor("#1C1C1E")); setHintTextColor(Color.parseColor("#A0A0A5")); background = roundBg(Color.WHITE, dp(16).toFloat()); setPadding(dp(14), 0, dp(14), 0); layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(10) } }
    private fun primaryButton(text: String, click: () -> Unit): Button = Button(this).apply { this.text = text; textSize = 15f; setTextColor(Color.WHITE); background = roundBg(Color.parseColor("#007AFF"), dp(15).toFloat()); setOnClickListener { click() } }
    private fun outlineButton(text: String, click: () -> Unit): Button = Button(this).apply { this.text = text; textSize = 15f; setTextColor(Color.parseColor("#007AFF")); background = roundStrokeBg(Color.TRANSPARENT, Color.parseColor("#007AFF"), dp(15).toFloat()); setOnClickListener { click() } }
    private fun roundBg(color: Int, radius: Float) = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.RECTANGLE; setColor(color); cornerRadius = radius }
    private fun roundStrokeBg(color: Int, stroke: Int, radius: Float) = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.RECTANGLE; setColor(color); setStroke(dp(1), stroke); cornerRadius = radius }
    private fun prefs() = getSharedPreferences("tiny_reader_store", MODE_PRIVATE)
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    companion object { private const val TAB_READ = "read"; private const val TAB_SHELF = "shelf"; private const val TAB_MINE = "mine"; private const val KEY_SHELF = "shelf"; private const val KEY_SOURCES = "sources"; private const val MAX_SAVE_CONTENT = 30000 }
}

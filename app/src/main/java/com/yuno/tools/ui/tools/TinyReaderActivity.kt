package com.yuno.tools.ui.tools

import android.app.AlertDialog
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class TinyReaderActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var titleView: TextView
    private lateinit var contentHost: FrameLayout
    private lateinit var navRead: LinearLayout
    private lateinit var navShelf: LinearLayout
    private lateinit var navMine: LinearLayout
    private var currentBook: Book? = null
    private var readerScroll: ScrollView? = null

    private data class Book(
        val title: String,
        val url: String,
        val sourceName: String,
        val content: String,
        val chapters: List<Chapter> = emptyList(),
        val chapterIndex: Int = 0,
        val progressY: Int = 0,
        val lastReadAt: Long = 0L
    )
    private data class Chapter(val title: String, val content: String)
    private data class BookSource(val name: String, val baseUrl: String)
    private data class ReaderSetting(val fontSize: Float, val lineDp: Int, val darkMode: Boolean)

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
        saveCurrentProgress()
        super.finish()
        overridePendingTransition(R.anim.profile_stay, R.anim.profile_slide_down_out)
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor())
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }
        val status = View(this).apply {
            id = resources.getIdentifier("statusBarPlaceholder", "id", packageName)
            layoutParams = LinearLayout.LayoutParams(-1, dp(24))
            setBackgroundColor(bgColor())
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(16), dp(10))
            setBackgroundColor(bgColor())
        }
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(textColor())
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            setOnClickListener { finish() }
        }
        titleView = TextView(this).apply {
            text = "小小读书"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textColor())
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        }
        header.addView(back)
        header.addView(titleView)
        contentHost = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        val bottomWrap = LinearLayout(this).apply {
            id = resources.getIdentifier("bottomNavContainer", "id", packageName)
            setPadding(dp(16), dp(8), dp(16), dp(14))
            setBackgroundColor(bgColor())
        }
        val bottomInner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = roundBg(cardColor(), dp(28).toFloat())
            layoutParams = LinearLayout.LayoutParams(-1, dp(64))
        }
        navRead = navItem("读书") { saveCurrentProgress(); showReadTab() }
        navShelf = navItem("书架") { saveCurrentProgress(); showShelfTab() }
        navMine = navItem("我的") { saveCurrentProgress(); showMineTab() }
        bottomInner.addView(navRead); bottomInner.addView(navShelf); bottomInner.addView(navMine)
        bottomWrap.addView(bottomInner)
        root.addView(status); root.addView(header); root.addView(contentHost); root.addView(bottomWrap)
        setContentView(root)
    }

    private fun navItem(text: String, click: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(3); marginEnd = dp(3) }
        addView(TextView(this@TinyReaderActivity).apply { this.text = text; textSize = 15f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER })
        setOnClickListener { click() }
    }

    private fun selectTab(tab: String) {
        readerScroll = null
        titleView.text = when (tab) { TAB_SHELF -> "小小书架"; TAB_MINE -> "我的书源"; else -> "小小读书" }
        listOf(TAB_READ to navRead, TAB_SHELF to navShelf, TAB_MINE to navMine).forEach { (name, item) ->
            val selected = name == tab
            item.background = if (selected) roundBg(if (setting().darkMode) Color.parseColor("#1D3557") else Color.parseColor("#E8F3FF"), dp(20).toFloat()) else null
            (item.getChildAt(0) as TextView).setTextColor(if (selected) Color.parseColor("#007AFF") else subTextColor())
        }
    }

    private fun showReadTab() {
        selectTab(TAB_READ)
        contentHost.removeAllViews()
        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER; setBackgroundColor(bgColor()) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(18)) }
        val recent = getShelf().maxByOrNull { it.lastReadAt }
        if (recent != null && recent.lastReadAt > 0) {
            box.addView(infoCard("最近阅读", "《${recent.title}》 · 第 ${recent.chapterIndex + 1}/${chapterList(recent).size} 章"))
            box.addView(primaryButton("继续上次阅读") { currentBook = recent; showReader(recent) }, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(10) })
        }
        val titleInput = edit("书名（可选）")
        val urlInput = edit("小说链接，或输入关键词后选择书源加载").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
        box.addView(infoCard("读书", "支持网页小说加载、章节识别、目录跳转、字体调节、夜间模式和阅读进度记忆。"))
        box.addView(label("书名")); box.addView(titleInput)
        box.addView(label("链接 / 关键词 / 路径")); box.addView(urlInput)
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(primaryButton("加载小说") {
            val raw = urlInput.text.toString().trim()
            if (raw.isBlank()) toast("请输入小说链接或关键词") else loadBook(titleInput.text.toString().trim().ifBlank { "未命名小说" }, normalizeUrl(raw, null), "直接链接")
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) })
        btnRow.addView(outlineButton("打开当前") {
            currentBook?.let { showReader(it) } ?: toast("请先加载一本小说")
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
        box.addView(readerCard("暂无内容，请先加载小说。加载成功后会自动进入专业阅读界面。"))
        scroll.addView(box)
        contentHost.addView(scroll)
    }

    private fun showReader(bookIn: Book) {
        val chapters = chapterList(bookIn)
        val idx = bookIn.chapterIndex.coerceIn(0, chapters.lastIndex)
        val book = bookIn.copy(chapters = chapters, chapterIndex = idx, lastReadAt = System.currentTimeMillis())
        currentBook = book
        saveBook(book)
        titleView.text = book.title.ifBlank { "阅读" }
        contentHost.removeAllViews()
        val s = setting()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(readerBgColor()); setPadding(dp(12), dp(8), dp(12), dp(8)) }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        nav.addView(outlineButton("上一章") { jumpChapter(-1) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(5) })
        nav.addView(primaryButton("目录") { showChapterList() }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(5); marginStart = dp(5) })
        nav.addView(outlineButton("下一章") { jumpChapter(1) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(5) })
        val setRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
        setRow.addView(outlineButton("A-") { updateSetting(s.copy(fontSize = max(14f, s.fontSize - 1f))); showReader(currentBook ?: book) }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(4) })
        setRow.addView(outlineButton("A+") { updateSetting(s.copy(fontSize = min(26f, s.fontSize + 1f))); showReader(currentBook ?: book) }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(4); marginStart = dp(4) })
        setRow.addView(outlineButton(if (s.darkMode) "日间" else "夜间") { updateSetting(s.copy(darkMode = !s.darkMode)); rebuildShellAndReader() }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(4); marginStart = dp(4) })
        setRow.addView(primaryButton("收藏") { saveCurrentProgress(); toast("已保存进度") }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(4) })
        val chapter = chapters[idx]
        val meta = TextView(this).apply {
            text = "第 ${idx + 1}/${chapters.size} 章 · ${book.sourceName}"
            textSize = 13f
            setTextColor(readerSubColor())
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(6))
        }
        readerScroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER; setBackgroundColor(readerBgColor()) }
        val article = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(40)) }
        article.addView(TextView(this).apply { text = chapter.title; textSize = s.fontSize + 3f; setTypeface(typeface, Typeface.BOLD); setTextColor(readerTextColor()); setPadding(0, dp(6), 0, dp(14)) })
        article.addView(TextView(this).apply { text = chapter.content; textSize = s.fontSize; setTextColor(readerTextColor()); setLineSpacing(dp(s.lineDp).toFloat(), 1.08f) })
        readerScroll?.addView(article)
        root.addView(nav); root.addView(setRow); root.addView(meta); root.addView(readerScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        contentHost.addView(root)
        readerScroll?.post { readerScroll?.scrollTo(0, book.progressY) }
    }

    private fun rebuildShellAndReader() {
        val book = currentBook
        buildShell()
        if (book != null) showReader(book) else showReadTab()
    }

    private fun jumpChapter(delta: Int) {
        val b = currentBook ?: return
        val chapters = chapterList(b)
        val next = (b.chapterIndex + delta).coerceIn(0, chapters.lastIndex)
        if (next == b.chapterIndex) { toast(if (delta < 0) "已经是第一章" else "已经是最后一章"); return }
        val updated = b.copy(chapters = chapters, chapterIndex = next, progressY = 0, lastReadAt = System.currentTimeMillis())
        currentBook = updated
        saveBook(updated)
        showReader(updated)
    }

    private fun showChapterList() {
        val b = currentBook ?: return
        val chapters = chapterList(b)
        val items = chapters.mapIndexed { i, c -> if (i == b.chapterIndex) "▶ ${c.title}" else c.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("目录 · ${b.title}")
            .setItems(items) { _, which ->
                val updated = b.copy(chapters = chapters, chapterIndex = which, progressY = 0, lastReadAt = System.currentTimeMillis())
                currentBook = updated
                saveBook(updated)
                showReader(updated)
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun saveCurrentProgress() {
        val b = currentBook ?: return
        val y = readerScroll?.scrollY ?: b.progressY
        val updated = b.copy(progressY = y, lastReadAt = System.currentTimeMillis(), chapters = chapterList(b))
        currentBook = updated
        saveBook(updated)
    }

    private fun showShelfTab() {
        selectTab(TAB_SHELF)
        contentHost.removeAllViews()
        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER; setBackgroundColor(bgColor()) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(18)) }
        val books = getShelf().sortedByDescending { it.lastReadAt }
        if (books.isEmpty()) box.addView(infoCard("书架为空", "在“读书”页加载小说后，点击阅读器里的“收藏”，这里会记录书籍和阅读进度。"))
        else {
            books.forEach { box.addView(bookCard(it)) }
            box.addView(outlineButton("清空书架") { prefs().edit().putString(KEY_SHELF, "[]").apply(); toast("书架已清空"); showShelfTab() }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        }
        scroll.addView(box); contentHost.addView(scroll)
    }

    private fun showMineTab() {
        selectTab(TAB_MINE)
        contentHost.removeAllViews()
        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER; setBackgroundColor(bgColor()) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(18)) }
        val s = setting()
        box.addView(infoCard("阅读设置", "当前字号：${s.fontSize.toInt()}sp · 行距：${s.lineDp}dp · ${if (s.darkMode) "夜间模式" else "日间模式"}"))
        val setRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        setRow.addView(outlineButton("字号-") { updateSetting(s.copy(fontSize = max(14f, s.fontSize - 1f))); showMineTab() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(4) })
        setRow.addView(outlineButton("字号+") { updateSetting(s.copy(fontSize = min(26f, s.fontSize + 1f))); showMineTab() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(4); marginStart = dp(4) })
        setRow.addView(primaryButton(if (s.darkMode) "日间" else "夜间") { updateSetting(s.copy(darkMode = !s.darkMode)); rebuildShellAndReader() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(4) })
        box.addView(setRow)
        val nameInput = edit("书源名称，例如：我的书源")
        val urlInput = edit("书源地址，例如：https://example.com/search?q=").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
        box.addView(infoCard("添加书源", "支持多个书源。地址可包含 {keyword} 占位符，也可作为搜索前缀或目录前缀拼接。"))
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
        val textView = findPreviewText()
        textView?.text = "正在加载：$url"
        Thread {
            try {
                val request = Request.Builder().url(url).addHeader("User-Agent", "YunoTools TinyReader/1.0.56").build()
                val body = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                    response.body?.string().orEmpty()
                }
                val clean = cleanNovelText(body).ifBlank { "已加载，但页面没有提取到可读文本。" }.take(MAX_SAVE_CONTENT)
                val chapters = splitChapters(clean)
                val book = Book(title, url, sourceName, clean, chapters, 0, 0, System.currentTimeMillis())
                currentBook = book
                saveBook(book)
                runOnUiThread { showReader(book) }
            } catch (e: Exception) {
                runOnUiThread { textView?.text = "加载失败：${e.message}\n\n请检查链接是否可访问，或在“我的”添加可用书源。" }
            }
        }.start()
    }

    private fun findPreviewText(): TextView? = (((contentHost.getChildAt(0) as? ScrollView)?.getChildAt(0) as? LinearLayout)?.let { it.getChildAt(it.childCount - 1) } as? MaterialCardView)?.let { (it.getChildAt(0) as? LinearLayout)?.getChildAt(0) as? TextView }

    private fun cleanNovelText(raw: String): String = raw
        .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<(br|p|div|li|h[1-6])[^>]*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n")

    private fun splitChapters(text: String): List<Chapter> {
        val titleRegex = Regex("^(第[0-9零一二三四五六七八九十百千万]+[章节回卷].{0,40}|Chapter\\s*\\d+.{0,40})$", RegexOption.IGNORE_CASE)
        val chapters = mutableListOf<Chapter>()
        var currentTitle = "正文"
        val buffer = StringBuilder()
        text.lines().forEach { line ->
            val t = line.trim()
            if (t.length <= 60 && titleRegex.matches(t)) {
                if (buffer.isNotBlank()) chapters.add(Chapter(currentTitle, buffer.toString().trim()))
                currentTitle = t
                buffer.clear()
            } else {
                buffer.append(line).append('\n')
            }
        }
        if (buffer.isNotBlank()) chapters.add(Chapter(currentTitle, buffer.toString().trim()))
        return if (chapters.size >= 2) chapters.take(MAX_CHAPTERS) else paginateAsChapters(text)
    }

    private fun paginateAsChapters(text: String): List<Chapter> {
        if (text.isBlank()) return listOf(Chapter("正文", "暂无内容"))
        val result = mutableListOf<Chapter>()
        var index = 0
        var part = 1
        while (index < text.length && result.size < MAX_CHAPTERS) {
            val end = min(text.length, index + PAGE_SIZE)
            result.add(Chapter("第${part}节", text.substring(index, end).trim()))
            index = end
            part++
        }
        return result
    }

    private fun chapterList(book: Book): List<Chapter> = if (book.chapters.isNotEmpty()) book.chapters else splitChapters(book.content)

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

    private fun getShelf(): List<Book> = parseArray(KEY_SHELF) { json ->
        val chaptersArr = json.optJSONArray("chapters")
        val chapters = if (chaptersArr != null) (0 until chaptersArr.length()).mapNotNull { i ->
            chaptersArr.optJSONObject(i)?.let { Chapter(it.optString("title"), it.optString("content")) }
        } else emptyList()
        Book(json.optString("title"), json.optString("url"), json.optString("sourceName"), json.optString("content"), chapters, json.optInt("chapterIndex", 0), json.optInt("progressY", 0), json.optLong("lastReadAt", 0L))
    }

    private fun bookToJson(book: Book): JSONObject {
        val chapters = JSONArray()
        chapterList(book).take(MAX_CHAPTERS).forEach { chapters.put(JSONObject().put("title", it.title).put("content", it.content)) }
        return JSONObject().put("title", book.title).put("url", book.url).put("sourceName", book.sourceName).put("content", book.content.take(MAX_SAVE_CONTENT)).put("chapters", chapters).put("chapterIndex", book.chapterIndex).put("progressY", book.progressY).put("lastReadAt", book.lastReadAt)
    }

    private fun saveSource(source: BookSource) {
        val arr = JSONArray()
        (listOf(source) + getSources().filterNot { it.name == source.name || it.baseUrl == source.baseUrl }).take(30).forEach { arr.put(JSONObject().put("name", it.name).put("baseUrl", it.baseUrl)) }
        prefs().edit().putString(KEY_SOURCES, arr.toString()).apply()
    }
    private fun getSources(): List<BookSource> = parseArray(KEY_SOURCES) { BookSource(it.optString("name"), it.optString("baseUrl")) }.filter { it.name.isNotBlank() && it.baseUrl.isNotBlank() }
    private fun ensureDefaultSource() { if (!prefs().contains(KEY_SOURCES)) prefs().edit().putString(KEY_SOURCES, JSONArray().put(JSONObject().put("name", "示例书源").put("baseUrl", "https://www.baidu.com/s?wd=")).toString()).apply() }
    private fun <T> parseArray(key: String, mapper: (JSONObject) -> T): List<T> = runCatching { val arr = JSONArray(prefs().getString(key, "[]").orEmpty()); (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(mapper) } }.getOrDefault(emptyList())

    private fun bookCard(book: Book): MaterialCardView = baseCard().apply {
        val chapters = chapterList(book)
        val box = LinearLayout(this@TinyReaderActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
        box.addView(TextView(this@TinyReaderActivity).apply { text = book.title.ifBlank { "未命名小说" }; textSize = 17f; setTypeface(typeface, Typeface.BOLD); setTextColor(textColor()) })
        box.addView(TextView(this@TinyReaderActivity).apply { text = "来源：${book.sourceName}\n第 ${book.chapterIndex + 1}/${chapters.size} 章 · ${if (book.progressY > 0) "已保存进度" else "未开始"}\n${book.url}"; textSize = 13f; setTextColor(subTextColor()) })
        val row = LinearLayout(this@TinyReaderActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        row.addView(primaryButton("继续阅读") { currentBook = book; showReader(book) }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
        row.addView(outlineButton("删除") { val arr = JSONArray(); getShelf().filterNot { it.url == book.url }.forEach { arr.put(bookToJson(it)) }; prefs().edit().putString(KEY_SHELF, arr.toString()).apply(); toast("已删除"); showShelfTab() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(6) })
        box.addView(row); addView(box)
    }
    private fun sourceCard(source: BookSource, click: () -> Unit): MaterialCardView = baseCard().apply { setOnClickListener { click() }; addView(simpleBox(source.name, source.baseUrl)) }
    private fun sourceManageCard(source: BookSource): MaterialCardView = baseCard().apply { val box = simpleBox(source.name, source.baseUrl); box.addView(outlineButton("删除书源") { val arr = JSONArray(); getSources().filterNot { it.name == source.name && it.baseUrl == source.baseUrl }.forEach { arr.put(JSONObject().put("name", it.name).put("baseUrl", it.baseUrl)) }; prefs().edit().putString(KEY_SOURCES, arr.toString()).apply(); toast("书源已删除"); showMineTab() }, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(10) }); addView(box) }
    private fun simpleBox(title: String, sub: String): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)); addView(TextView(this@TinyReaderActivity).apply { text = title; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(textColor()) }); addView(TextView(this@TinyReaderActivity).apply { text = sub; textSize = 13f; setTextColor(subTextColor()) }) }
    private fun infoCard(title: String, desc: String): MaterialCardView = baseCard().apply { addView(simpleBox(title, desc)) }
    private fun readerCard(text: String): MaterialCardView = baseCard().apply { val box = LinearLayout(this@TinyReaderActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }; box.addView(TextView(this@TinyReaderActivity).apply { this.text = text; textSize = setting().fontSize; setTextColor(textColor()); setLineSpacing(dp(setting().lineDp).toFloat(), 1.08f) }); addView(box) }
    private fun baseCard(): MaterialCardView = MaterialCardView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }; radius = dp(18).toFloat(); cardElevation = 0f; setCardBackgroundColor(cardColor()); strokeWidth = if (setting().darkMode) 1 else 0; strokeColor = Color.parseColor("#2A2A2A") }
    private fun label(text: String): TextView = TextView(this).apply { this.text = text; textSize = 15f; setTypeface(typeface, Typeface.BOLD); setTextColor(textColor()); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(8) } }
    private fun edit(hintText: String): EditText = EditText(this).apply { hint = hintText; textSize = 15f; setSingleLine(true); setTextColor(textColor()); setHintTextColor(subTextColor()); background = roundBg(cardColor(), dp(16).toFloat()); setPadding(dp(14), 0, dp(14), 0); layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(10) } }
    private fun primaryButton(text: String, click: () -> Unit): Button = Button(this).apply { this.text = text; textSize = 15f; setTextColor(Color.WHITE); background = roundBg(Color.parseColor("#007AFF"), dp(15).toFloat()); setOnClickListener { click() } }
    private fun outlineButton(text: String, click: () -> Unit): Button = Button(this).apply { this.text = text; textSize = 15f; setTextColor(Color.parseColor("#007AFF")); background = roundStrokeBg(Color.TRANSPARENT, Color.parseColor("#007AFF"), dp(15).toFloat()); setOnClickListener { click() } }
    private fun roundBg(color: Int, radius: Float) = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.RECTANGLE; setColor(color); cornerRadius = radius }
    private fun roundStrokeBg(color: Int, stroke: Int, radius: Float) = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.RECTANGLE; setColor(color); setStroke(dp(1), stroke); cornerRadius = radius }
    private fun setting(): ReaderSetting = ReaderSetting(prefs().getFloat(KEY_FONT_SIZE, 17f), prefs().getInt(KEY_LINE_DP, 8), prefs().getBoolean(KEY_DARK_MODE, false))
    private fun updateSetting(s: ReaderSetting) = prefs().edit().putFloat(KEY_FONT_SIZE, s.fontSize).putInt(KEY_LINE_DP, s.lineDp).putBoolean(KEY_DARK_MODE, s.darkMode).apply()
    private fun bgColor(): Int = if (setting().darkMode) Color.parseColor("#0F0F0F") else Color.parseColor("#F2F2F7")
    private fun cardColor(): Int = if (setting().darkMode) Color.parseColor("#1C1C1E") else Color.WHITE
    private fun textColor(): Int = if (setting().darkMode) Color.parseColor("#F5F5F7") else Color.parseColor("#1C1C1E")
    private fun subTextColor(): Int = if (setting().darkMode) Color.parseColor("#A1A1AA") else Color.parseColor("#8E8E93")
    private fun readerBgColor(): Int = if (setting().darkMode) Color.parseColor("#111111") else Color.parseColor("#F7F3E8")
    private fun readerTextColor(): Int = if (setting().darkMode) Color.parseColor("#EDEDED") else Color.parseColor("#2B2118")
    private fun readerSubColor(): Int = if (setting().darkMode) Color.parseColor("#9A9A9A") else Color.parseColor("#7B6D5B")
    private fun prefs() = getSharedPreferences("tiny_reader_store", MODE_PRIVATE)
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val TAB_READ = "read"
        private const val TAB_SHELF = "shelf"
        private const val TAB_MINE = "mine"
        private const val KEY_SHELF = "shelf"
        private const val KEY_SOURCES = "sources"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_LINE_DP = "line_dp"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val MAX_SAVE_CONTENT = 120000
        private const val MAX_CHAPTERS = 120
        private const val PAGE_SIZE = 6500
    }
}

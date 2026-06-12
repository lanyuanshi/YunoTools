
package com.yuno.tools.ui.tools

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.R
import com.yuno.tools.util.ThemeApplier
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max

class TinyReaderActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var appBarTitle: TextView
    private lateinit var appBarSub: TextView
    private lateinit var actionHost: LinearLayout
    private lateinit var contentHost: FrameLayout
    private lateinit var navHome: LinearLayout
    private lateinit var navLibrary: LinearLayout
    private lateinit var navHistory: LinearLayout
    private lateinit var navMore: LinearLayout
    private var selectedTab = TAB_HOME
    private var screenMode = TAB_HOME
    private var previousScreen = TAB_HOME
    private var currentManga: Manga? = null
    private var readerScroll: ScrollView? = null
    private var homeScrollY: Int = 0
    private var currentHomeTitle: String = "最新"
    private var currentHomeSources: List<Source> = emptyList()

    private data class Manga(
        val title: String,
        val url: String,
        val sourceName: String,
        val description: String = "",
        val cover: String = "",
        val chapters: List<Chapter> = emptyList(),
        val pages: List<String> = emptyList(),
        val favorite: Boolean = false,
        val lastReadAt: Long = 0L,
        val updatedAt: Long = System.currentTimeMillis(),
        val chapterIndex: Int = 0,
        val progressY: Int = 0
    )
    private data class Chapter(val title: String, val url: String, val pages: List<String> = emptyList())
    private data class Source(val name: String, val searchUrl: String)
    private data class SearchResult(val title: String, val url: String, val sourceName: String, val snippet: String = "", val cover: String = "")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeApplier.apply(this)
        window.statusBarColor = C_BG
        window.navigationBarColor = C_BG
        buildRoot()
        ensureDefaultSources()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBack() }
        })
        showHome()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun buildRoot() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(C_BG) }
        val status = View(this).apply { setBackgroundColor(C_BG) }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(24)))
        val appBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), dp(8), dp(12), dp(8)) }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        appBarTitle = TextView(this).apply { textSize = 24f; setTextColor(C_TEXT); setTypeface(typeface, Typeface.BOLD) }
        appBarSub = TextView(this).apply { textSize = 13f; setTextColor(C_SUB) }
        titles.addView(appBarTitle); titles.addView(appBarSub)
        appBar.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        actionHost = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        appBar.addView(actionHost)
        root.addView(appBar, LinearLayout.LayoutParams(-1, dp(72)))
        contentHost = FrameLayout(this)
        root.addView(contentHost, LinearLayout.LayoutParams(-1, 0, 1f))
        val navShell = FrameLayout(this).apply { setPadding(dp(18), dp(8), dp(18), dp(14)); setBackgroundColor(C_BG) }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; background = round(Color.WHITE, dp(28)); elevation = dp(8).toFloat(); setPadding(dp(6), 0, dp(6), 0) }
        navHome = navItem("首页", "⌂") { showHome() }
        navLibrary = navItem("书架", "▦") { showLibrary() }
        navHistory = navItem("历史", "◴") { showHistory() }
        navMore = navItem("更多", "☰") { showMore() }
        listOf(navHome, navLibrary, navHistory, navMore).forEach { nav.addView(it, LinearLayout.LayoutParams(0, -1, 1f)) }
        navShell.addView(nav, FrameLayout.LayoutParams(-1, dp(70), Gravity.CENTER))
        root.addView(navShell, LinearLayout.LayoutParams(-1, dp(92)))
        setContentView(root)
    }

    private fun handleBack() {
        when (screenMode) {
            SCREEN_READER -> currentManga?.let { saveProgress(); showDetail(it) } ?: showHome()
            SCREEN_DETAIL, SCREEN_SEARCH -> when (previousScreen) { TAB_LIBRARY -> showLibrary(); TAB_HISTORY -> showHistory(); TAB_MORE -> showMore(); else -> showHome() }
            TAB_HOME -> finish()
            else -> showHome()
        }
    }

    private fun setHeader(title: String, sub: String, tab: Int? = null) {
        appBarTitle.text = title; appBarSub.text = sub; actionHost.removeAllViews(); tab?.let { selectedTab = it; screenMode = it }; updateNav()
    }
    private fun updateNav() { listOf(navHome to TAB_HOME, navLibrary to TAB_LIBRARY, navHistory to TAB_HISTORY, navMore to TAB_MORE).forEach { (v,t) -> v.background = if (selectedTab == t) round(C_PRIMARY_LIGHT, dp(22)) else null } }
    private fun actionButton(text: String, click: () -> Unit) { actionHost.addView(Button(this).apply { this.text = text; textSize = 16f; minWidth = dp(44); setTextColor(C_PRIMARY); background = round(Color.TRANSPARENT, dp(22)); setOnClickListener { click() } }, LinearLayout.LayoutParams(dp(48), dp(48))) }

    private fun showHome() { if (currentHomeSources.isEmpty()) showHomeCategory("最新", defaultHomeSources()) else showHomeCategory(currentHomeTitle, currentHomeSources) }
    private fun defaultHomeSources() = listOf(Source("包子漫画", "https://cn.bzmanga.com/list/new"), Source("好多漫", "https://m.haoduoman.com/manhua"))

    private fun showHomeCategory(title: String, sources: List<Source>) {
        saveProgress(); currentManga = null; readerScroll = null
        currentHomeTitle = title; currentHomeSources = sources
        setHeader("首页", "$title · 搜索 / 分类 / 封面宫格", TAB_HOME)
        actionButton("↻") { showHomeCategory(title, sources) }
        val box = scroll()
        homeSearchBar(box)
        homeCategoryBar(box)
        empty(box, "正在获取漫画列表…", "按本地分类映射真实网站分类，解析标题、封面、最新集数后展示。")
        Thread {
            val groups = sources.map { src ->
                val rs = runCatching { parseSourceResults(http(src.searchUrl, src.name), src.name, src.searchUrl) }
                    .getOrElse { listOf(SearchResult("加载失败", src.searchUrl, src.name, it.message ?: "源站拒绝或网络失败")) }
                src.name to rs
            }
            runOnUiThread { renderHome(title, groups) }
        }.start()
    }

    private fun homeSearchBar(box: LinearLayout) {
        section(box, "搜索漫画")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(8)) }
        val input = EditText(this).apply { hint = "输入漫画名"; setSingleLine(true); background = round(Color.WHITE, dp(18)); setPadding(dp(14), 0, dp(14), 0) }
        row.addView(input, LinearLayout.LayoutParams(0, dp(50), 1f))
        row.addView(Button(this).apply { text = "搜索"; setTextColor(Color.WHITE); background = round(C_PRIMARY, dp(18)); setOnClickListener { val q = input.text.toString().trim(); if (q.isNotEmpty()) { homeScrollY = readerScroll?.scrollY ?: 0; searchAllSources(q) } } }, LinearLayout.LayoutParams(dp(86), dp(50)).apply { marginStart = dp(8) })
        box.addView(row)
    }

    private fun homeCategoryBar(box: LinearLayout) {
        section(box, "本地分类")
        fun go(label: String, sources: List<Source>) { homeScrollY = 0; showHomeCategory(label, sources) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(8)) }
        fun chip(parent: LinearLayout, label: String, sources: List<Source>) {
            parent.addView(Button(this).apply { text = label; textSize = 12f; setTextColor(C_PRIMARY); background = round(Color.WHITE, dp(18)); setOnClickListener { go(label, sources) } }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
        }
        chip(row, "最新", defaultHomeSources())
        chip(row, "国漫", listOf(Source("包子漫画", "https://cn.bzmanga.com/classify?type=all&region=cn&state=all&filter=%2a"), Source("好多漫", "https://m.haoduoman.com/manhua/area/guonei")))
        chip(row, "日漫", listOf(Source("包子漫画", "https://cn.bzmanga.com/classify?type=all&region=jp&state=all&filter=%2a"), Source("好多漫", "https://m.haoduoman.com/manhua/area/riben")))
        box.addView(row)
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(8)) }
        chip(row2, "韩漫", listOf(Source("包子漫画", "https://cn.bzmanga.com/classify?type=all&region=kr&state=all&filter=%2a"), Source("好多漫", "https://m.haoduoman.com/manhua/area/hanguo")))
        chip(row2, "完结", listOf(Source("包子漫画", "https://cn.bzmanga.com/classify?type=all&region=all&state=pub&filter=%2a")))
        chip(row2, "热血", listOf(Source("包子漫画", "https://cn.bzmanga.com/classify?type=rexie&region=all&state=all&filter=%2a"), Source("好多漫", "https://m.haoduoman.com/manhua/theme/rexue")))
        box.addView(row2)
    }

    private fun renderHome(title: String, groups: List<Pair<String, List<SearchResult>>>) {
        previousScreen = TAB_HOME
        setHeader("首页", "$title · 三列封面列表", TAB_HOME)
        actionButton("↻") { showHome() }
        val box = scroll()
        homeSearchBar(box)
        homeCategoryBar(box)
        groups.forEach { (name, results) ->
            section(box, "$name · ${results.size} 条")
            if (results.isEmpty()) empty(box, "$name 没有解析到漫画", "可能页面结构变化或源站拒绝访问。") else resultGrid(box, results.take(30))
        }
        if (homeScrollY > 0) readerScroll?.post { readerScroll?.scrollTo(0, homeScrollY) }
    }

    private fun showLibrary() {
        saveProgress(); currentManga = null; readerScroll = null
        setHeader("小小漫画", "书架 / 收藏 / 最近阅读", TAB_LIBRARY)
        actionButton("⇅") { showSortDialog() }; actionButton("＋") { showMore() }
        val box = scroll(); section(box, "我的漫画")
        val favs = sortedManga(loadManga().filter { it.favorite })
        if (favs.isEmpty()) empty(box, "书架还没有漫画", "去“首页”从包子漫画 / 好多漫 搜索或加载漫画，点星标加入书架。") else favs.forEach { box.addView(mangaCard(it, true) { showDetail(it) }) }
    }

    private fun showUpdates() { showMore() }

    private fun showHistory() {
        setHeader("历史", "最近看过的漫画", TAB_HISTORY)
        val box = scroll(); section(box, "阅读历史")
        val history = loadManga().filter { it.lastReadAt > 0 }.sortedByDescending { it.lastReadAt }
        if (history.isEmpty()) empty(box, "暂无历史", "打开漫画章节后会自动记录。") else history.forEach { box.addView(mangaCard(it, false) { showDetail(it) }) }
    }

    private fun showBrowse() { showMore() }

    private fun showMore() {
        currentManga = null; readerScroll = null
        setHeader("更多", "书架数据 / 设置", TAB_MORE)
        val box = scroll()
        section(box, "数据")
        box.addView(menuCard("备份漫画数据", "复制书架、历史、阅读进度 JSON。") { backupData() })
        box.addView(menuCard("恢复备份", "粘贴备份 JSON 恢复本地数据。") { showRestoreDialog() })
        box.addView(menuCard("清空漫画数据", "删除书架和历史") { AlertDialog.Builder(this).setTitle("确认清空？").setMessage("会删除小小漫画的本地书架和历史。").setNegativeButton("取消", null).setPositiveButton("清空") { _, _ -> prefs().edit().remove(KEY_MANGA).apply(); showLibrary() }.show() })
        section(box, "关于"); box.addView(menuCard("小小漫画 v1.1.06", "首页搜索和本地分类；漫画源为包子漫画 / 好多漫。") {})
    }

    private fun showDetail(manga: Manga) {
        saveProgress(); previousScreen = if (screenMode == SCREEN_READER || screenMode == SCREEN_DETAIL) previousScreen else selectedTab; screenMode = SCREEN_DETAIL; currentManga = manga
        setHeader(manga.title, manga.sourceName)
        actionButton("↻") { refreshManga(manga) }; actionButton(if (manga.favorite) "★" else "☆") { toggleFavorite(manga) }; actionButton("⋮") { showMangaMenu(manga) }
        val box = scroll(); val top = baseCard(); val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(14), dp(14), dp(14), dp(14)) }
        val cover = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = round(C_PRIMARY_LIGHT, dp(14)); if (manga.cover.isNotBlank()) Glide.with(this@TinyReaderActivity).load(glideUrl(manga.cover, manga.sourceName)).into(this) }
        row.addView(cover, LinearLayout.LayoutParams(dp(92), dp(132)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        info.addView(TextView(this).apply { text = manga.title; textSize = 20f; setTextColor(C_TEXT); setTypeface(typeface, Typeface.BOLD) })
        info.addView(TextView(this).apply { text = "${manga.sourceName} · ${manga.chapters.size} 章 · ${manga.pages.size} 页"; textSize = 13f; setTextColor(C_SUB); setPadding(0, dp(6), 0, dp(6)) })
        info.addView(TextView(this).apply { text = manga.description.ifBlank { "已解析漫画详情。可以从章节列表开始阅读，也可以刷新重新抓取。" }; textSize = 14f; setTextColor(C_SUB); maxLines = 4 })
        val read = Button(this).apply { text = if (manga.chapters.isNotEmpty()) "继续阅读" else "查看图片"; setTextColor(Color.WHITE); background = round(C_PRIMARY, dp(18)); setOnClickListener { openReader(manga, manga.chapterIndex.coerceAtLeast(0), manga.progressY) } }
        info.addView(read, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f)); top.addView(row); box.addView(top)
        section(box, "章节")
        if (manga.chapters.isEmpty()) empty(box, "没有解析到章节", "可以直接查看页面图片，或换一个漫画详情页。") else manga.chapters.forEachIndexed { i, ch -> box.addView(chapterCard(i, ch, i == manga.chapterIndex) { openReader(manga, i, 0) }) }
    }

    private fun showSearchLoading(keyword: String) { previousScreen = selectedTab; screenMode = SCREEN_SEARCH; setHeader("搜索漫画", keyword); val box=scroll(); empty(box,"正在搜索…","正在从包子漫画 / 好多漫 本地抓取漫画列表。") }
    private fun fixedSources() = listOf(Source("包子漫画", "https://cn.bzmanga.com/search?q=%s"), Source("好多漫", "https://m.haoduoman.com/search?keyword=%s"))
    private fun searchAllSources(keyword: String) {
        showSearchLoading(keyword)
        Thread {
            val results = fixedSources().flatMap { src -> runCatching { parseSourceResults(http(src.searchUrl.replace("%s", URLEncoder.encode(keyword, "UTF-8")), src.name), src.name, src.searchUrl.substringBefore("/search").substringBefore("/cn/comics")) }.getOrDefault(emptyList()) }.distinctBy { it.url }.take(60)
            runOnUiThread { showSearchResults(keyword, results) }
        }.start()
    }
    private fun loadSourceList(src: Source) { previousScreen = TAB_MORE; screenMode = SCREEN_SEARCH; setHeader(src.name, "正在加载漫画列表"); val box=scroll(); empty(box,"正在加载…",src.searchUrl.substringBefore('?')); Thread { val rs=runCatching { val url=when(src.name){"包子漫画"->"https://cn.bzmanga.com/list/new";else->"https://m.haoduoman.com/manhua"}; parseSourceResults(http(url, src.name), src.name, url) }.getOrElse { listOf(SearchResult("加载失败", src.searchUrl.substringBefore('?'), src.name, it.message ?: "源站拒绝或网络失败")) }; runOnUiThread { showSearchResults(src.name, rs) } }.start() }
    private fun showSearchResults(keyword: String, results: List<SearchResult>) {
        previousScreen = selectedTab; screenMode = SCREEN_SEARCH; setHeader("搜索结果", keyword)
        val box = scroll(); section(box, "共 ${results.size} 条")
        if (results.isEmpty()) empty(box, "没搜到结果", "换个关键词或漫画源试试。") else resultGrid(box, results)
    }
    private fun loadMangaFromUrl(title: String, url: String, sourceName: String) {
        val box=scroll(); setHeader("解析漫画", title); empty(box,"正在解析详情…",url)
        Thread { val manga=runCatching { fetchManga(title, url, sourceName) }.getOrElse { Manga(title,url,sourceName,"解析失败：${it.message}") }; saveManga(mergeOld(manga)); runOnUiThread { showDetail(mergeOld(manga)) } }.start()
    }

    private fun fetchManga(titleHint: String, url: String, sourceName: String): Manga {
        val html = http(url, sourceName)
        return if (sourceName == "包子漫画") fetchBzManga(titleHint, url, html) else fetchHaoduomanManga(titleHint, url, html)
    }
    private fun fetchBzManga(titleHint: String, url: String, html: String): Manga {
        val title = clean(Regex("""name=["']og:novel:book_name["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1).orEmpty()).ifBlank { clean(textBetween(html, "<title", "</title>").ifBlank { titleHint }).substringBefore(" - ").removePrefix("🍲").take(80) }
        val cover = Regex("""name=["']og:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1)?.let { absUrl(it.replace("&amp;", "&"), url) } ?: extractImages(html, url).firstOrNull { it.contains("/cover/") }.orEmpty()
        val desc = clean(Regex("""name=["']og:description["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1).orEmpty()).take(180)
        val chapters = extractBzChapters(html, url)
        return Manga(title.ifBlank { titleHint }, url, "包子漫画", desc.ifBlank { "包子漫画 · 本地解析详情、章节和阅读图片。" }, cover, chapters, emptyList(), updatedAt = System.currentTimeMillis())
    }
    private fun fetchHaoduomanManga(titleHint: String, url: String, html: String): Manga {
        val title = clean(textBetween(html, "<title", "</title>").ifBlank { titleHint }).substringBefore(" - ").take(80)
        val cover = extractImages(html, url).firstOrNull { it.contains("img.haoduoman.com/cover") }.orEmpty()
        val desc = clean(Regex("""<meta[^>]+name=["']description["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1).orEmpty()).ifBlank { clean(html).take(160) }
        val chapters = extractHaoduomanChapters(html, url)
        return Manga(title.ifBlank { titleHint }, url, "好多漫", desc, cover, chapters, emptyList(), updatedAt = System.currentTimeMillis())
    }
    private fun fetchChapter(ch: Chapter): Chapter {
        if (ch.pages.isNotEmpty()) return ch
        val html=http(ch.url, if(ch.url.contains("haoduoman")) "好多漫" else "包子漫画")
        val pages = if (ch.url.contains("haoduoman")) extractHaoduomanChapterImages(html, ch.url) else if (ch.url.contains("baozimh") || ch.url.contains("bzmanga")) extractBzChapterImages(html, ch.url) else emptyList()
        return ch.copy(pages=pages)
    }
    private fun openReader(manga: Manga, idx: Int, y: Int) {
        saveProgress(); screenMode = SCREEN_READER; currentManga = manga.copy(chapterIndex = idx); setHeader(manga.title, manga.chapters.getOrNull(idx)?.title ?: "图片阅读")
        actionButton("◁") { if (idx > 0) openReader(manga, idx - 1, 0) }
        actionButton("▷") { if (idx + 1 < manga.chapters.size) openReader(manga, idx + 1, 0) }
        val box = scroll()
        empty(box, "正在加载漫画页…", "图片会按漫画阅读顺序纵向显示。")
        Thread {
            val chapter = manga.chapters.getOrNull(idx)?.let { runCatching { fetchChapter(it) }.getOrDefault(it) }
            val pages = chapter?.pages?.ifEmpty { manga.pages } ?: manga.pages
            val updated = manga.copy(chapterIndex = idx, lastReadAt = System.currentTimeMillis(), chapters = if (chapter != null && manga.chapters.isNotEmpty()) manga.chapters.toMutableList().also { it[idx] = chapter } else manga.chapters)
            saveManga(updated); currentManga = updated
            runOnUiThread { renderPages(updated, idx, pages, y) }
        }.start()
    }
    private fun renderPages(manga: Manga, idx: Int, pages: List<String>, y: Int) {
        val box=scroll(); setHeader(manga.title, manga.chapters.getOrNull(idx)?.title ?: "图片阅读")
        actionButton("◁") { if (idx > 0) openReader(manga, idx - 1, 0) }; actionButton("▷") { if (idx + 1 < manga.chapters.size) openReader(manga, idx + 1, 0) }
        if (pages.isEmpty()) empty(box, "没有解析到真实漫画图片", "已阻止显示站点图标、推荐封面和包子漫画 APP 二维码宣传图；若源站只返回 APP 引导页则不显示错图。") else pages.forEachIndexed { i, u ->
            val card=baseCard(); val lay=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL; setPadding(dp(6),dp(6),dp(6),dp(6))}
            lay.addView(TextView(this).apply{text="${i+1} / ${pages.size}"; textSize=12f; setTextColor(C_SUB); gravity=Gravity.CENTER; setPadding(0,dp(4),0,dp(4))})
            lay.addView(ImageView(this).apply{ adjustViewBounds=true; scaleType=ImageView.ScaleType.FIT_CENTER; setBackgroundColor(Color.WHITE); Glide.with(this@TinyReaderActivity).load(glideUrl(u, manga.sourceName)).placeholder(android.R.color.darker_gray).error(android.R.color.darker_gray).into(this) }, LinearLayout.LayoutParams(-1, -2))
            card.addView(lay); box.addView(card)
        }
        readerScroll?.post { readerScroll?.smoothScrollTo(0, y) }
    }

    private fun glideUrl(url: String, sourceName: String): GlideUrl {
        val referer = if (sourceName == "包子漫画") "https://cn.bzmanga.com/" else "https://m.haoduoman.com/"
        return GlideUrl(url, LazyHeaders.Builder()
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .addHeader("Referer", referer)
            .build())
    }

    private fun refreshLibrary() { val favs=loadManga().filter{it.favorite}; if(favs.isEmpty()){toast("没有收藏需要刷新");return}; toast("开始刷新 ${favs.size} 部漫画"); Thread{ favs.forEach{runCatching{saveManga(mergeOld(fetchManga(it.title,it.url,it.sourceName)))}}; runOnUiThread{showLibrary();toast("刷新完成")} }.start() }
    private fun refreshManga(manga: Manga) { toast("正在刷新"); Thread{ val m=runCatching{mergeOld(fetchManga(manga.title,manga.url,manga.sourceName))}.getOrDefault(manga); saveManga(m); runOnUiThread{showDetail(m);toast("已刷新")} }.start() }
    private fun toggleFavorite(manga: Manga) { val m=manga.copy(favorite=!manga.favorite, updatedAt=System.currentTimeMillis()); saveManga(m); showDetail(m); toast(if(m.favorite)"已加入书架" else "已取消收藏") }
    private fun showMangaMenu(manga: Manga) { val items=arrayOf(if(manga.favorite)"取消收藏" else "加入书架","复制链接","分享漫画","删除记录"); AlertDialog.Builder(this).setTitle(manga.title).setItems(items){_,w-> when(w){0->toggleFavorite(manga);1->{clip(manga.url);toast("已复制")};2->share("${manga.title}\n${manga.url}");3->{deleteManga(manga.url);showLibrary()}}}.show() }

    private fun settingCard(box: LinearLayout) { box.addView(menuCard("阅读模式", "当前为漫画纵向连续阅读，图片自适应宽度。") { toast("已是漫画阅读模式") }) }
    private fun showSortDialog() { val items=arrayOf("最近阅读","标题","最近更新","章节数"); AlertDialog.Builder(this).setTitle("书架排序").setItems(items){_,w->prefs().edit().putInt(KEY_SORT,w).apply();showLibrary()}.show() }
    private fun sortedManga(list: List<Manga>) = when(prefs().getInt(KEY_SORT,0)){1->list.sortedBy{it.title};2->list.sortedByDescending{it.updatedAt};3->list.sortedByDescending{it.chapters.size};else->list.sortedByDescending{max(it.lastReadAt,it.updatedAt)}}

    private fun backupData() { val o=JSONObject().put("manga", JSONArray(prefs().getString(KEY_MANGA,"[]"))).put("sort", prefs().getInt(KEY_SORT,0)).put("version",99); val s=o.toString(2); clip(s); share(s); toast("备份已复制") }
    private fun showRestoreDialog() { val input=EditText(this).apply{hint="粘贴备份 JSON"; minLines=6}; AlertDialog.Builder(this).setTitle("恢复备份").setView(input).setNegativeButton("取消",null).setPositiveButton("恢复"){_,_->restoreData(input.text.toString())}.show() }
    private fun restoreData(json: String) { runCatching{ val o=JSONObject(json); prefs().edit().putString(KEY_MANGA,o.optJSONArray("manga")?.toString()?:"[]").putInt(KEY_SORT,o.optInt("sort",0)).apply(); showLibrary(); toast("恢复完成") }.onFailure{toast("恢复失败：${it.message}")} }

    private fun parseSourceResults(html: String, source: String, base: String): List<SearchResult> {
        if (source == "包子漫画") return parseBzResults(html, base)
        if (source == "好多漫") return parseHaoduomanResults(html, base)
        val re = Regex("""<a[^>]+href=["']([^"'#]+)["'][^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
        return re.findAll(html).mapNotNull { m ->
            val href = m.groupValues[1]
            val body = m.groupValues[2]
            val url = absUrl(href, base).substringBefore("?from=")
            val title = clean(Regex("""<h[1-6][^>]*>([\s\S]*?)</h[1-6]>""", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1) ?: body).replace(Regex("\\s+"), " " ).trim()
            val isManga = when (source) {
                "好多漫" -> url.contains("/manhua/") && !url.endsWith(".html")
                else -> false
            }
            val badTitle = title.contains("更多") || title.contains("分类") || title.contains("排行") || title.contains("更新")
            val badUrl = url.contains("/area/") || url.contains("/category") || url.contains("/rank") || url.contains("page=")
            val cover = extractImages(body, base).firstOrNull().orEmpty()
            if (isManga && !badTitle && !badUrl && title.length in 2..100) SearchResult(title, url, source, ifBlank(cover, url), cover) else null
        }.distinctBy { it.url }.take(60).toList()
    }

    private fun parseBzResults(html: String, base: String): List<SearchResult> {
        // 包子漫画列表中封面和标题在同一个 comics-card 的两个 <a> 内，不能按单个 <a> 解析。
        // 这里以 /cover/{slug}.jpg 为锚点反推漫画详情，保证首页一定有封面。
        val coverRe = Regex("""<amp-img[^>]+alt=["']([^"']+)["'][^>]+src=["'](https?://[^"']*/cover/([^"'/]+)\.(?:jpg|jpeg|png|webp)[^"']*)["']""", RegexOption.IGNORE_CASE)
        return coverRe.findAll(html).mapNotNull { m ->
            val title = clean(m.groupValues[1]).take(100)
            val slug = m.groupValues[3]
            if (title.isBlank() || slug == "default_cover") return@mapNotNull null
            val cover = m.groupValues[2].replace("&amp;", "&")
            val url = "https://cn.bzmanga.com/comic/$slug"
            val pos = m.range.first
            val nearStart = (pos - 1600).coerceAtLeast(0)
            val nearEnd = (pos + 2200).coerceAtMost(html.length)
            val near = html.substring(nearStart, nearEnd)
            val author = clean(Regex("""<small[^>]*class=["'][^"']*tags[^"']*["'][^>]*>([\s\S]*?)</small>""", RegexOption.IGNORE_CASE).find(near)?.groupValues?.getOrNull(1).orEmpty())
            val tag = clean(Regex("""<span[^>]*class=["'][^"']*tab[^"']*["'][^>]*>([\s\S]*?)</span>""", RegexOption.IGNORE_CASE).find(near)?.groupValues?.getOrNull(1).orEmpty())
            SearchResult(title, url, "包子漫画", tag.ifBlank { author.ifBlank { "包子漫画" } }, cover)
        }.distinctBy { it.url }.take(60).toList()
    }

    private fun parseHaoduomanResults(html: String, base: String): List<SearchResult> {
        val itemRe = Regex("""<div[^>]+class=["'][^"']*comic-item[^"']*["'][^>]*>([\s\S]*?)</a>\s*</div>""", RegexOption.IGNORE_CASE)
        return itemRe.findAll(html).mapNotNull { m ->
            val item = m.groupValues[1]
            val href = Regex("""<a[^>]+href=["'](/manhua/\d+)["']""", RegexOption.IGNORE_CASE).find(item)?.groupValues?.getOrNull(1) ?: return@mapNotNull null
            val url = absUrl(href, base)
            val title = clean(Regex("""<h3[^>]+class=["']title["'][^>]*>([\s\S]*?)</h3>""", RegexOption.IGNORE_CASE).find(item)?.groupValues?.getOrNull(1).orEmpty())
            val latest = clean(Regex("""<div[^>]+class=["']txt["'][^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE).find(item)?.groupValues?.getOrNull(1).orEmpty()).replace(Regex("""\s+"""), " ").trim()
            val cover = extractImages(item, base).firstOrNull().orEmpty()
            if (title.length in 1..100) SearchResult(title, url, "好多漫", latest.ifBlank { url }, cover) else null
        }.distinctBy { it.url }.take(60).toList()
    }

    private fun ifBlank(v: String, fallback: String) = if (v.isBlank()) fallback else v

    private fun extractHaoduomanChapters(html: String, base: String): List<Chapter> {
        val id = Regex("""/manhua/(\d+)""").find(base)?.groupValues?.getOrNull(1) ?: return emptyList()
        val re = Regex("""<a[^>]+href=["'](/manhua/""" + id + """/[0-9]+\.html)["'][^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
        return re.findAll(html).mapNotNull {
            val t = clean(it.groupValues[2]).trim()
            val u = absUrl(it.groupValues[1], base)
            if (t.contains("第") || t.contains("话") || t.contains("卷")) Chapter(t.take(80), u) else null
        }.distinctBy { it.url }.toList()
    }
    private fun extractBzChapters(html: String, base: String): List<Chapter> {
        val metaUrl = Regex("""name=["']og:novel:latest_chapter_url["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1).orEmpty().replace("&amp;", "&")
        val metaTitle = clean(Regex("""name=["']og:novel:latest_chapter_name["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1).orEmpty()).ifBlank { "第1话" }
        val list = mutableListOf<Chapter>()
        if (metaUrl.isNotBlank()) list.add(Chapter(metaTitle, metaUrl.replace("https://cn.bzmanga.com", "https://www.baozimh.com")))
        Regex("""href=["'](/user/page_direct\?comic_id=([^"'&]+)&amp;section_slot=(\d+)&amp;chapter_slot=(\d+))['"][^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
            val comic = m.groupValues[2]; val sec = m.groupValues[3]; val ch = m.groupValues[4]
            val title = clean(m.groupValues[5]).ifBlank { "第${ch.toIntOrNull()?.plus(1) ?: 1}话" }
            list.add(Chapter(title.take(80), "https://www.baozimh.com/comic/chapter/$comic/${sec}_${ch}.html"))
        }
        return list.distinctBy { it.url }.take(300)
    }
    private fun extractBzChapterImages(html: String, base: String): List<String> {
        // 包子漫画网页端部分章节会把“请在 APP 内阅读/二维码下载 APP”的宣传图伪装成 scomic 图片。
        // 这类图不是漫画页，必须过滤，不能再当成第 1 页显示。
        val pageText = clean(html).lowercase()
        val appOnly = pageText.contains("app内") || pageText.contains("app內") || pageText.contains("下载app") || pageText.contains("下載app") || pageText.contains("二维码") || pageText.contains("二維碼") || pageText.contains("扫码") || pageText.contains("掃描") || pageText.contains("請在包子") || pageText.contains("请在包子")
        val re = Regex("""<amp-img[^>]+(?:src|data-src)=["'](https?://s\d+\.bzcdn\.net/scomic/[^"']+\.(?:jpg|jpeg|png|webp)(?:\?[^"']*)?)["'][^>]*>""", RegexOption.IGNORE_CASE)
        val items = re.findAll(html).mapNotNull { m ->
            val tag = m.value
            val around = html.substring((m.range.first - 500).coerceAtLeast(0), (m.range.last + 500).coerceAtMost(html.length)).lowercase()
            val alt = Regex("""alt=["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.getOrNull(1).orEmpty().lowercase()
            val url = m.groupValues[1].replace("&amp;", "&")
            val bad = listOf("app", "baozimh.com", "qrcode", "qr", "二维码", "二維碼", "下载", "下載", "扫码", "掃描", "請在", "请在", "android-icon", "ios").any { k ->
                around.contains(k) || alt.contains(k) || url.lowercase().contains(k)
            }
            if (!bad && isComicPageImage(url)) url else null
        }.distinct().take(260).toList()
        // 如果页面明确是 APP 引导页，宁可返回空，让 UI 提示源站限制，也不显示错误二维码图。
        return if (appOnly && items.size <= 6) emptyList() else items
    }

    private fun extractHaoduomanChapterImages(html: String, base: String): List<String> {
        // 好多漫专用解析：章节页 var params 是 AES/CBC/PKCS5Padding 加密 JSON。
        // Base64 解码后前 16 字节是 IV，后续是密文；key 来自源站 CMS.chapter.decrypt。
        val enc = Regex("""var\s+params\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1).orEmpty()
        if (enc.isNotBlank()) {
            runCatching {
                val all = Base64.decode(enc, Base64.DEFAULT)
                if (all.size > 32) {
                    val iv = all.copyOfRange(0, 16)
                    val cipherBytes = all.copyOfRange(16, all.size)
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec("5V&RoR%Jf@pJPydF".toByteArray(Charsets.UTF_8), "AES"), IvParameterSpec(iv))
                    val json = String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
                    val o = JSONObject(json)
                    val status = o.optString("comic_status")
                    if (status.isNotBlank() && status != "normal") return emptyList()
                    val arr = o.optJSONArray("chapter_images") ?: JSONArray()
                    val domain = o.optString("images_domain")
                    val useBase64 = o.optBoolean("images_base64", false)
                    return (0 until arr.length()).mapNotNull { i ->
                        var u = arr.optString(i).trim()
                        if (u.isBlank()) return@mapNotNull null
                        if (domain.isNotBlank() && !u.startsWith("http") && !u.startsWith("//")) {
                            u = domain + if (useBase64) android.util.Base64.encodeToString(u.toByteArray(), android.util.Base64.NO_WRAP) else u
                        }
                        absUrl(u, base)
                    }.filter { isComicPageImage(it) }.distinct().take(260)
                }
                emptyList<String>()
            }.getOrElse { emptyList() }.also { if (it.isNotEmpty()) return it }
        }
        // 兜底：只接受明确非封面的漫画图片域名，避免站点图标/推荐封面。
        return Regex("""https?://(?:s\d+\.bzcdn\.net|img\.haoduoman\.com)/(?!cover/)[^"'<>\s]+\.(?:jpg|jpeg|png|webp)(?:\?[^"'<>\s]*)?""", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.value }.filter { isComicPageImage(it) }.distinct().take(260).toList()
    }
    private fun isComicPageImage(url: String): Boolean {
        val u = url.lowercase()
        return (u.contains("/scomic/") || u.contains("img.haoduoman.com/")) && !u.contains("/cover/") && !u.contains("/assets/") && !u.contains("/static/") && !u.contains(".js") && u.contains(Regex("""\.(jpg|jpeg|png|webp)(\?|$)"""))
    }


    private fun extractChapters(html: String, base: String): List<Chapter> {
        val re=Regex("""<a[^>]+href=["']([^"']+)["'][^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
        return re.findAll(html).mapNotNull{ val t=clean(it.groupValues[2]).trim(); val u=absUrl(it.groupValues[1],base); val hit=t.contains(Regex("第.{1,8}[话回章节卷]|chapter|episode|chap", RegexOption.IGNORE_CASE)); if(hit && u.startsWith("http")) Chapter(t.take(80),u) else null }.distinctBy{it.url}.take(300).toList().let{ if(it.size>1) it else emptyList() }
    }
    private fun extractImages(html: String, base: String): List<String> {
        val re=Regex("""(?:src|data-src|data-original|data-url|data-lazy-src)=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        return re.findAll(html)
            .map { absUrl(it.groupValues[1], base) }
            .filter { it.startsWith("http") && !it.startsWith("data:") && !it.contains(".js") && !it.contains("css99") && !it.contains("/assets/") && !it.contains("/static/") && it.contains(Regex("""\.(jpg|jpeg|png|webp|gif)(\?|$)|/comic|/cover|image""", RegexOption.IGNORE_CASE)) }
            .distinct()
            .take(260)
            .toList()
    }
    private fun textBetween(s:String,a:String,b:String):String{ val i=s.indexOf(a,ignoreCase=true); if(i<0) return ""; val j=s.indexOf('>',i); val k=s.indexOf(b,if(j<0)i else j,ignoreCase=true); return if(j>=0&&k>j)s.substring(j+1,k) else "" }
    private fun clean(s:String)=s.replace(Regex("<script[\\s\\S]*?</script>",RegexOption.IGNORE_CASE),"").replace(Regex("<style[\\s\\S]*?</style>",RegexOption.IGNORE_CASE),"").replace(Regex("<[^>]+>"),"").replace("&nbsp;"," ").replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").trim()
    private fun absUrl(u:String,base:String):String=runCatching{ if(u.startsWith("//")) "https:$u" else if(u.startsWith("http")) u else if(base.isNotBlank()) URI(base).resolve(u).toString() else u }.getOrDefault(u)
    private fun http(url: String, sourceName: String = ""): String {
        val req=Request.Builder().url(url)
            .header("User-Agent","Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .header("Accept","text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language","zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", when(sourceName){"包子漫画"->"https://cn.bzmanga.com/";else->"https://m.haoduoman.com/"})
            .build()
        client.newCall(req).execute().use{ if(!it.isSuccessful) error("HTTP ${it.code}"); return it.body?.string().orEmpty() }
    }

    private fun prefs()=getSharedPreferences("tiny_manga", MODE_PRIVATE)
    private fun ensureDefaultSources(){}
    private fun loadManga():List<Manga>{ val a=JSONArray(prefs().getString(KEY_MANGA,"[]")); return (0 until a.length()).map{jsonToManga(a.getJSONObject(it))} }
    private fun saveManga(m:Manga){ val arr=loadManga().filter{it.url!=m.url}.toMutableList(); arr.add(m); saveAll(arr) }
    private fun saveAll(list:List<Manga>){ val a=JSONArray(); list.forEach{a.put(mangaToJson(it))}; prefs().edit().putString(KEY_MANGA,a.toString()).apply() }
    private fun deleteManga(url:String){ saveAll(loadManga().filter{it.url!=url}) }
    private fun mergeOld(m:Manga):Manga{ val old=loadManga().firstOrNull{it.url==m.url}; return if(old==null)m else m.copy(favorite=old.favorite,lastReadAt=old.lastReadAt,chapterIndex=old.chapterIndex,progressY=old.progressY) }
    private fun mangaToJson(m:Manga)=JSONObject().put("title",m.title).put("url",m.url).put("sourceName",m.sourceName).put("description",m.description).put("cover",m.cover).put("favorite",m.favorite).put("lastReadAt",m.lastReadAt).put("updatedAt",m.updatedAt).put("chapterIndex",m.chapterIndex).put("progressY",m.progressY).put("pages",JSONArray(m.pages)).put("chapters",JSONArray().also{a->m.chapters.forEach{c->a.put(JSONObject().put("title",c.title).put("url",c.url).put("pages",JSONArray(c.pages)))}})
    private fun jsonToManga(o:JSONObject):Manga{ val ch=o.optJSONArray("chapters")?:JSONArray(); val chapters=(0 until ch.length()).map{ val c=ch.getJSONObject(it); val p=c.optJSONArray("pages")?:JSONArray(); Chapter(c.optString("title"),c.optString("url"),(0 until p.length()).map{p.getString(it)})}; val p=o.optJSONArray("pages")?:JSONArray(); return Manga(o.optString("title"),o.optString("url"),o.optString("sourceName"),o.optString("description"),o.optString("cover"),chapters,(0 until p.length()).map{p.getString(it)},o.optBoolean("favorite"),o.optLong("lastReadAt"),o.optLong("updatedAt"),o.optInt("chapterIndex"),o.optInt("progressY")) }

    private fun scroll(): LinearLayout { contentHost.removeAllViews(); val sv=ScrollView(this).apply{overScrollMode=View.OVER_SCROLL_NEVER}; readerScroll=sv; val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(24))}; sv.addView(box); contentHost.addView(sv); return box }
    private fun section(box:LinearLayout,title:String){ box.addView(TextView(this).apply{text=title; textSize=15f; setTextColor(C_SUB); setTypeface(typeface,Typeface.BOLD); setPadding(dp(4),dp(14),0,dp(8))}) }
    private fun empty(box:LinearLayout,title:String,sub:String){ val c=baseCard(); val l=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(dp(18),dp(32),dp(18),dp(32))}; l.addView(TextView(this).apply{text=title;textSize=18f;setTextColor(C_TEXT);setTypeface(typeface,Typeface.BOLD);gravity=Gravity.CENTER}); l.addView(TextView(this).apply{text=sub;textSize=14f;setTextColor(C_SUB);gravity=Gravity.CENTER;setPadding(0,dp(8),0,0)}); c.addView(l); box.addView(c) }
    private fun baseCard()=MaterialCardView(this).apply{ radius=dp(18).toFloat(); cardElevation=0f; strokeColor=C_STROKE; strokeWidth=1; setCardBackgroundColor(Color.WHITE); useCompatPadding=false; layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)} }
    private fun resultGrid(box: LinearLayout, results: List<SearchResult>) {
        val grid = GridLayout(this).apply { columnCount = 3; setPadding(0, 0, 0, dp(8)) }
        results.forEach { r -> grid.addView(resultTile(r), GridLayout.LayoutParams().apply { width = 0; height = GridLayout.LayoutParams.WRAP_CONTENT; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(4), dp(4), dp(4), dp(12)) }) }
        box.addView(grid, LinearLayout.LayoutParams(-1, -2))
    }
    private fun resultTile(r: SearchResult)=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL; setPadding(dp(4),dp(4),dp(4),dp(4)); setOnClickListener{ if (selectedTab == TAB_HOME || screenMode == SCREEN_SEARCH) homeScrollY = readerScroll?.scrollY ?: homeScrollY; loadMangaFromUrl(r.title, r.url, r.sourceName) }
        val img=ImageView(context).apply{ scaleType=ImageView.ScaleType.CENTER_CROP; background=round(C_PRIMARY_LIGHT,dp(8)); if(r.cover.isNotBlank()) Glide.with(this@TinyReaderActivity).load(glideUrl(r.cover, r.sourceName)).placeholder(android.R.color.darker_gray).error(android.R.color.darker_gray).into(this) }
        addView(img, LinearLayout.LayoutParams(-1, dp(150)))
        addView(TextView(context).apply{text=r.title; textSize=15f; setTextColor(C_TEXT); setTypeface(typeface,Typeface.BOLD); maxLines=1; setPadding(0,dp(6),0,0)})
        addView(TextView(context).apply{text=r.snippet.ifBlank{r.sourceName}; textSize=13f; setTextColor(C_SUB); maxLines=1; setPadding(0,dp(2),0,0)})
    }
    private fun mangaCard(m:Manga,showProgress:Boolean,click:()->Unit)=baseCard().apply{ val row=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12))}; val img=ImageView(context).apply{scaleType=ImageView.ScaleType.CENTER_CROP; background=round(C_PRIMARY_LIGHT,dp(12)); if(m.cover.isNotBlank()) Glide.with(this@TinyReaderActivity).load(glideUrl(m.cover, m.sourceName)).into(this)}; row.addView(img,LinearLayout.LayoutParams(dp(64),dp(88))); val col=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL; setPadding(dp(12),0,0,0)}; col.addView(TextView(context).apply{text=m.title;textSize=17f;setTextColor(C_TEXT);setTypeface(typeface,Typeface.BOLD)}); col.addView(TextView(context).apply{text="${m.sourceName} · ${m.chapters.size} 章" + if(showProgress&&m.lastReadAt>0) " · 读到 ${m.chapterIndex+1}" else "";textSize=13f;setTextColor(C_SUB);setPadding(0,dp(5),0,0)}); col.addView(TextView(context).apply{text=m.description.ifBlank{m.url}.take(90);textSize=13f;setTextColor(C_SUB);maxLines=2;setPadding(0,dp(6),0,0)}); row.addView(col,LinearLayout.LayoutParams(0,-2,1f)); addView(row); setOnClickListener{click()} }
    private fun chapterCard(i:Int,ch:Chapter,active:Boolean,click:()->Unit)=baseCard().apply{ val t=TextView(context).apply{text="${i+1}. ${ch.title}";textSize=15f;setTextColor(if(active)C_PRIMARY else C_TEXT);setPadding(dp(16),dp(14),dp(16),dp(14));setTypeface(typeface,if(active)Typeface.BOLD else Typeface.NORMAL)}; addView(t); setOnClickListener{click()} }
    private fun menuCard(title:String,sub:String,click:()->Unit)=baseCard().apply{ val l=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(14))}; l.addView(TextView(context).apply{text=title;textSize=16f;setTextColor(C_TEXT);setTypeface(typeface,Typeface.BOLD)}); l.addView(TextView(context).apply{text=sub;textSize=13f;setTextColor(C_SUB);setPadding(0,dp(4),0,0)}); addView(l); setOnClickListener{click()} }
    private fun navItem(label:String,icon:String,click:()->Unit)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER; val i=TextView(context).apply{text=icon;textSize=20f;setTextColor(C_PRIMARY);gravity=Gravity.CENTER}; val t=TextView(context).apply{text=label;textSize=11f;setTextColor(C_SUB);gravity=Gravity.CENTER}; addView(i); addView(t); setOnClickListener{click()} }
    private fun round(color:Int,r:Int)=GradientDrawable().apply{setColor(color);cornerRadius=r.toFloat()}
    private fun dp(v:Int)=(v*resources.displayMetrics.density+0.5f).toInt()
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
    private fun clip(s:String){ (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("小小漫画",s)) }
    private fun share(s:String){ startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,s),"分享")) }
    private fun saveProgress(){ val m=currentManga ?: return; val y=readerScroll?.scrollY ?: m.progressY; saveManga(m.copy(progressY=y,lastReadAt=if(screenMode==SCREEN_READER)System.currentTimeMillis() else m.lastReadAt)) }

    companion object { private const val TAB_HOME=0; private const val TAB_LIBRARY=1; private const val TAB_HISTORY=2; private const val TAB_MORE=4; private const val SCREEN_DETAIL=10; private const val SCREEN_READER=11; private const val SCREEN_SEARCH=12; private const val KEY_MANGA="manga";  private const val KEY_SORT="sort"; private const val C_BG=0xFFF8F7FC.toInt(); private const val C_TEXT=0xFF1D1B20.toInt(); private const val C_SUB=0xFF6F6A78.toInt(); private const val C_PRIMARY=0xFF6750A4.toInt(); private const val C_PRIMARY_LIGHT=0xFFEADDFF.toInt(); private const val C_STROKE=0xFFE7E0EC.toInt() }
}

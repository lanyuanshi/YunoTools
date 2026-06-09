
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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.R
import com.yuno.tools.util.ThemeApplier
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import kotlin.math.max

class TinyReaderActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var appBarTitle: TextView
    private lateinit var appBarSub: TextView
    private lateinit var actionHost: LinearLayout
    private lateinit var contentHost: FrameLayout
    private lateinit var navLibrary: LinearLayout
    private lateinit var navUpdates: LinearLayout
    private lateinit var navHistory: LinearLayout
    private lateinit var navBrowse: LinearLayout
    private lateinit var navMore: LinearLayout
    private var selectedTab = TAB_LIBRARY
    private var screenMode = TAB_LIBRARY
    private var previousScreen = TAB_LIBRARY
    private var currentManga: Manga? = null
    private var readerScroll: ScrollView? = null

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
    private data class SearchResult(val title: String, val url: String, val sourceName: String, val snippet: String = "")

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
        showLibrary()
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
        navLibrary = navItem("书架", "▦") { showLibrary() }
        navUpdates = navItem("更新", "↻") { showUpdates() }
        navHistory = navItem("历史", "◴") { showHistory() }
        navBrowse = navItem("浏览", "⌕") { showBrowse() }
        navMore = navItem("更多", "☰") { showMore() }
        listOf(navLibrary, navUpdates, navHistory, navBrowse, navMore).forEach { nav.addView(it, LinearLayout.LayoutParams(0, -1, 1f)) }
        navShell.addView(nav, FrameLayout.LayoutParams(-1, dp(70), Gravity.CENTER))
        root.addView(navShell, LinearLayout.LayoutParams(-1, dp(92)))
        setContentView(root)
    }

    private fun handleBack() {
        when (screenMode) {
            SCREEN_READER -> currentManga?.let { saveProgress(); showDetail(it) } ?: showLibrary()
            SCREEN_DETAIL, SCREEN_SEARCH -> when (previousScreen) { TAB_BROWSE -> showBrowse(); TAB_HISTORY -> showHistory(); TAB_UPDATES -> showUpdates(); TAB_MORE -> showMore(); else -> showLibrary() }
            TAB_LIBRARY -> finish()
            else -> showLibrary()
        }
    }

    private fun setHeader(title: String, sub: String, tab: Int? = null) {
        appBarTitle.text = title; appBarSub.text = sub; actionHost.removeAllViews(); tab?.let { selectedTab = it; screenMode = it }; updateNav()
    }
    private fun updateNav() { listOf(navLibrary to TAB_LIBRARY, navUpdates to TAB_UPDATES, navHistory to TAB_HISTORY, navBrowse to TAB_BROWSE, navMore to TAB_MORE).forEach { (v,t) -> v.background = if (selectedTab == t) round(C_PRIMARY_LIGHT, dp(22)) else null } }
    private fun actionButton(text: String, click: () -> Unit) { actionHost.addView(Button(this).apply { this.text = text; textSize = 16f; minWidth = dp(44); setTextColor(C_PRIMARY); background = round(Color.TRANSPARENT, dp(22)); setOnClickListener { click() } }, LinearLayout.LayoutParams(dp(48), dp(48))) }

    private fun showLibrary() {
        saveProgress(); currentManga = null; readerScroll = null
        setHeader("小小漫画", "书架 / 收藏 / 最近阅读", TAB_LIBRARY)
        actionButton("⇅") { showSortDialog() }; actionButton("＋") { showBrowse() }
        val box = scroll(); section(box, "我的漫画")
        val favs = sortedManga(loadManga().filter { it.favorite })
        if (favs.isEmpty()) empty(box, "书架还没有漫画", "去“浏览”搜索漫画，点星标加入书架。") else favs.forEach { box.addView(mangaCard(it, true) { showDetail(it) }) }
    }

    private fun showUpdates() {
        setHeader("更新", "收藏漫画的章节更新", TAB_UPDATES); actionButton("↻") { refreshLibrary() }
        val box = scroll(); section(box, "最近更新")
        val favs = loadManga().filter { it.favorite }.sortedByDescending { it.updatedAt }
        if (favs.isEmpty()) empty(box, "暂无收藏", "收藏漫画后这里会显示更新。") else favs.forEach { box.addView(mangaCard(it, false) { showDetail(it) }) }
    }

    private fun showHistory() {
        setHeader("历史", "最近看过的漫画", TAB_HISTORY)
        val box = scroll(); section(box, "阅读历史")
        val history = loadManga().filter { it.lastReadAt > 0 }.sortedByDescending { it.lastReadAt }
        if (history.isEmpty()) empty(box, "暂无历史", "打开漫画章节后会自动记录。") else history.forEach { box.addView(mangaCard(it, false) { showDetail(it) }) }
    }

    private fun showBrowse() {
        currentManga = null; readerScroll = null
        setHeader("浏览", "搜索漫画源 / 发现漫画", TAB_BROWSE); actionButton("源") { showSourceDialog() }
        val box = scroll(); section(box, "搜索漫画")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), dp(10), dp(16), dp(10)) }
        val input = EditText(this).apply { hint = "输入漫画名或作者"; setSingleLine(true); background = round(Color.WHITE, dp(18)); setPadding(dp(14), 0, dp(14), 0) }
        row.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
        row.addView(Button(this).apply { text = "搜索"; setTextColor(Color.WHITE); background = round(C_PRIMARY, dp(18)); setOnClickListener { val q=input.text.toString().trim(); if(q.isNotEmpty()) searchAllSources(q) } }, LinearLayout.LayoutParams(dp(88), dp(52)).apply{marginStart=dp(10)})
        box.addView(row)
        section(box, "漫画源")
        loadSources().forEach { s -> box.addView(menuCard(s.name, s.searchUrl) { promptSearch(s) }) }
    }

    private fun showMore() {
        currentManga = null; readerScroll = null
        setHeader("更多", "设置 / 数据 / 关于", TAB_MORE)
        val box = scroll(); section(box, "漫画设置"); settingCard(box); section(box, "数据")
        box.addView(menuCard("备份漫画数据", "复制书架、历史、漫画源、阅读设置 JSON。") { backupData() })
        box.addView(menuCard("恢复备份", "粘贴备份 JSON 恢复本地数据。") { showRestoreDialog() })
        box.addView(menuCard("清空漫画数据", "删除书架、历史、源设置") { AlertDialog.Builder(this).setTitle("确认清空？").setMessage("会删除小小漫画的本地书架和历史。").setNegativeButton("取消", null).setPositiveButton("清空") { _, _ -> prefs().edit().remove(KEY_MANGA).remove(KEY_SOURCES).apply(); ensureDefaultSources(); showLibrary() }.show() })
        section(box, "关于"); box.addView(menuCard("小小漫画 v1.0.60", "漫画书架、搜索、详情、章节、图片阅读、收藏、更新、历史、备份恢复。") {})
    }

    private fun showDetail(manga: Manga) {
        saveProgress(); previousScreen = if (screenMode == SCREEN_READER || screenMode == SCREEN_DETAIL) previousScreen else selectedTab; screenMode = SCREEN_DETAIL; currentManga = manga
        setHeader(manga.title, manga.sourceName)
        actionButton("↻") { refreshManga(manga) }; actionButton(if (manga.favorite) "★" else "☆") { toggleFavorite(manga) }; actionButton("⋮") { showMangaMenu(manga) }
        val box = scroll(); val top = baseCard(); val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(14), dp(14), dp(14), dp(14)) }
        val cover = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = round(C_PRIMARY_LIGHT, dp(14)); if (manga.cover.isNotBlank()) Glide.with(this@TinyReaderActivity).load(manga.cover).into(this) }
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

    private fun showSearchLoading(keyword: String) { previousScreen = selectedTab; screenMode = SCREEN_SEARCH; setHeader("搜索漫画", keyword); val box=scroll(); empty(box,"正在搜索…","正在从漫画源抓取结果。") }
    private fun searchAllSources(keyword: String) {
        showSearchLoading(keyword)
        Thread {
            val results = loadSources().flatMap { src -> runCatching { parseSearchResults(http(src.searchUrl.replace("%s", URLEncoder.encode(keyword, "UTF-8"))), src.name) }.getOrDefault(emptyList()) }.distinctBy { it.url }.take(50)
            runOnUiThread { showSearchResults(keyword, results) }
        }.start()
    }
    private fun promptSearch(src: Source) { val input=EditText(this).apply{hint="漫画关键词"}; AlertDialog.Builder(this).setTitle(src.name).setView(input).setNegativeButton("取消",null).setPositiveButton("搜索"){_,_-> val q=input.text.toString().trim(); if(q.isNotEmpty()) searchSource(src,q)}.show() }
    private fun searchSource(src: Source, keyword: String) { showSearchLoading(keyword); Thread { val rs=runCatching { parseSearchResults(http(src.searchUrl.replace("%s", URLEncoder.encode(keyword, "UTF-8"))), src.name) }.getOrDefault(emptyList()); runOnUiThread { showSearchResults(keyword, rs) } }.start() }
    private fun showSearchResults(keyword: String, results: List<SearchResult>) {
        previousScreen = TAB_BROWSE; screenMode = SCREEN_SEARCH; setHeader("搜索结果", keyword)
        val box = scroll(); section(box, "共 ${results.size} 条")
        if (results.isEmpty()) empty(box, "没搜到结果", "换个关键词或漫画源试试。") else results.forEach { r -> box.addView(menuCard(r.title, "${r.sourceName}\n${r.snippet}") { loadMangaFromUrl(r.title, r.url, r.sourceName) }) }
    }
    private fun loadMangaFromUrl(title: String, url: String, sourceName: String) {
        val box=scroll(); setHeader("解析漫画", title); empty(box,"正在解析详情…",url)
        Thread { val manga=runCatching { fetchManga(title, url, sourceName) }.getOrElse { Manga(title,url,sourceName,"解析失败：${it.message}") }; saveManga(mergeOld(manga)); runOnUiThread { showDetail(mergeOld(manga)) } }.start()
    }

    private fun fetchManga(titleHint: String, url: String, sourceName: String): Manga {
        val html = http(url); val title = clean(textBetween(html, "<title", "</title>").ifBlank { titleHint }).take(80)
        val cover = extractImages(html, url).firstOrNull().orEmpty()
        val desc = clean(Regex("""<meta[^>]+name=["']description["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1).orEmpty()).ifBlank { clean(html).take(160) }
        val chapters = extractChapters(html, url).ifEmpty { listOf(Chapter("第 1 话", url, extractImages(html, url))) }
        return Manga(title.ifBlank { titleHint }, url, sourceName, desc, cover, chapters, extractImages(html, url), updatedAt = System.currentTimeMillis())
    }
    private fun fetchChapter(ch: Chapter): Chapter { if (ch.pages.isNotEmpty()) return ch; val html=http(ch.url); return ch.copy(pages=extractImages(html, ch.url)) }
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
        if (pages.isEmpty()) empty(box, "没有解析到图片", "这个页面可能需要专用源适配或登录。") else pages.forEachIndexed { i, u ->
            val card=baseCard(); val lay=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL; setPadding(dp(6),dp(6),dp(6),dp(6))}
            lay.addView(TextView(this).apply{text="${i+1} / ${pages.size}"; textSize=12f; setTextColor(C_SUB); gravity=Gravity.CENTER; setPadding(0,dp(4),0,dp(4))})
            lay.addView(ImageView(this).apply{ adjustViewBounds=true; scaleType=ImageView.ScaleType.FIT_CENTER; setBackgroundColor(Color.WHITE); Glide.with(this@TinyReaderActivity).load(u).placeholder(android.R.color.darker_gray).error(android.R.color.darker_gray).into(this) }, LinearLayout.LayoutParams(-1, -2))
            card.addView(lay); box.addView(card)
        }
        readerScroll?.post { readerScroll?.smoothScrollTo(0, y) }
    }

    private fun refreshLibrary() { val favs=loadManga().filter{it.favorite}; if(favs.isEmpty()){toast("没有收藏需要刷新");return}; toast("开始刷新 ${favs.size} 部漫画"); Thread{ favs.forEach{runCatching{saveManga(mergeOld(fetchManga(it.title,it.url,it.sourceName)))}}; runOnUiThread{showUpdates();toast("刷新完成")} }.start() }
    private fun refreshManga(manga: Manga) { toast("正在刷新"); Thread{ val m=runCatching{mergeOld(fetchManga(manga.title,manga.url,manga.sourceName))}.getOrDefault(manga); saveManga(m); runOnUiThread{showDetail(m);toast("已刷新")} }.start() }
    private fun toggleFavorite(manga: Manga) { val m=manga.copy(favorite=!manga.favorite, updatedAt=System.currentTimeMillis()); saveManga(m); showDetail(m); toast(if(m.favorite)"已加入书架" else "已取消收藏") }
    private fun showMangaMenu(manga: Manga) { val items=arrayOf(if(manga.favorite)"取消收藏" else "加入书架","复制链接","分享漫画","删除记录"); AlertDialog.Builder(this).setTitle(manga.title).setItems(items){_,w-> when(w){0->toggleFavorite(manga);1->{clip(manga.url);toast("已复制")};2->share("${manga.title}\n${manga.url}");3->{deleteManga(manga.url);showLibrary()}}}.show() }

    private fun settingCard(box: LinearLayout) { box.addView(menuCard("阅读模式", "当前为漫画纵向连续阅读，图片自适应宽度。") { toast("已是漫画阅读模式") }) }
    private fun showSortDialog() { val items=arrayOf("最近阅读","标题","最近更新","章节数"); AlertDialog.Builder(this).setTitle("书架排序").setItems(items){_,w->prefs().edit().putInt(KEY_SORT,w).apply();showLibrary()}.show() }
    private fun sortedManga(list: List<Manga>) = when(prefs().getInt(KEY_SORT,0)){1->list.sortedBy{it.title};2->list.sortedByDescending{it.updatedAt};3->list.sortedByDescending{it.chapters.size};else->list.sortedByDescending{max(it.lastReadAt,it.updatedAt)}}
    private fun showSourceDialog() { val input=EditText(this).apply{hint="源名称|搜索URL，用 %s 代表关键词"; setText(loadSources().joinToString("\n"){"${it.name}|${it.searchUrl}"})}; AlertDialog.Builder(this).setTitle("漫画源").setView(input).setNegativeButton("取消",null).setPositiveButton("保存"){_,_-> val arr=input.text.toString().lines().mapNotNull{val p=it.split('|',limit=2); if(p.size==2&&p[1].contains("%s")) Source(p[0].trim(),p[1].trim()) else null}; if(arr.isNotEmpty()){saveSources(arr);toast("已保存")}}.show() }

    private fun backupData() { val o=JSONObject().put("manga", JSONArray(prefs().getString(KEY_MANGA,"[]"))).put("sources", JSONArray(prefs().getString(KEY_SOURCES,"[]"))).put("sort", prefs().getInt(KEY_SORT,0)).put("version",60); val s=o.toString(2); clip(s); share(s); toast("备份已复制") }
    private fun showRestoreDialog() { val input=EditText(this).apply{hint="粘贴备份 JSON"; minLines=6}; AlertDialog.Builder(this).setTitle("恢复备份").setView(input).setNegativeButton("取消",null).setPositiveButton("恢复"){_,_->restoreData(input.text.toString())}.show() }
    private fun restoreData(json: String) { runCatching{ val o=JSONObject(json); prefs().edit().putString(KEY_MANGA,o.optJSONArray("manga")?.toString()?:"[]").putString(KEY_SOURCES,o.optJSONArray("sources")?.toString()?:prefs().getString(KEY_SOURCES,"[]")).putInt(KEY_SORT,o.optInt("sort",0)).apply(); ensureDefaultSources(); showLibrary(); toast("恢复完成") }.onFailure{toast("恢复失败：${it.message}")} }

    private fun parseSearchResults(html: String, source: String): List<SearchResult> {
        val re=Regex("""<a[^>]+href=["']([^"'#]+)["'][^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
        return re.findAll(html).mapNotNull { m -> val u=absUrl(m.groupValues[1], ""); val title=clean(m.groupValues[2]).replace(Regex("""\s+""")," ").trim(); if(u.startsWith("http") && title.length in 2..80) SearchResult(title,u,source,u.take(120)) else null }.distinctBy{it.url}.take(40).toList()
    }
    private fun extractChapters(html: String, base: String): List<Chapter> {
        val re=Regex("""<a[^>]+href=["']([^"']+)["'][^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
        return re.findAll(html).mapNotNull{ val t=clean(it.groupValues[2]).trim(); val u=absUrl(it.groupValues[1],base); val hit=t.contains(Regex("第.{1,8}[话回章节卷]|chapter|episode|chap", RegexOption.IGNORE_CASE)); if(hit && u.startsWith("http")) Chapter(t.take(80),u) else null }.distinctBy{it.url}.take(300).toList().let{ if(it.size>1) it else emptyList() }
    }
    private fun extractImages(html: String, base: String): List<String> {
        val re=Regex("""<(?:img|source)[^>]+(?:src|data-src|data-original|data-url|data-lazy-src)=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        return re.findAll(html).map{absUrl(it.groupValues[1],base)}.filter{ it.startsWith("http") && it.contains(Regex("\\.(jpg|jpeg|png|webp|gif)(\\?|$)|image", RegexOption.IGNORE_CASE)) }.distinct().take(200).toList()
    }
    private fun textBetween(s:String,a:String,b:String):String{ val i=s.indexOf(a,ignoreCase=true); if(i<0) return ""; val j=s.indexOf('>',i); val k=s.indexOf(b,if(j<0)i else j,ignoreCase=true); return if(j>=0&&k>j)s.substring(j+1,k) else "" }
    private fun clean(s:String)=s.replace(Regex("<script[\\s\\S]*?</script>",RegexOption.IGNORE_CASE),"").replace(Regex("<style[\\s\\S]*?</style>",RegexOption.IGNORE_CASE),"").replace(Regex("<[^>]+>"),"").replace("&nbsp;"," ").replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").trim()
    private fun absUrl(u:String,base:String):String=runCatching{ if(u.startsWith("//")) "https:$u" else if(u.startsWith("http")) u else if(base.isNotBlank()) URI(base).resolve(u).toString() else u }.getOrDefault(u)
    private fun http(url: String): String { val req=Request.Builder().url(url).header("User-Agent","Mozilla/5.0 YunoTools Manga Reader").build(); client.newCall(req).execute().use{ if(!it.isSuccessful) error("HTTP ${it.code}"); return it.body?.string().orEmpty() } }

    private fun prefs()=getSharedPreferences("tiny_manga", MODE_PRIVATE)
    private fun ensureDefaultSources(){ if(prefs().getString(KEY_SOURCES,null)==null) saveSources(listOf(Source("Bing 漫画搜索","https://www.bing.com/search?q=%s+漫画"), Source("DuckDuckGo 漫画搜索","https://duckduckgo.com/html/?q=%s+manga"), Source("通用网页搜索","https://www.sogou.com/web?query=%s+漫画"))) }
    private fun loadSources():List<Source>{ val a=JSONArray(prefs().getString(KEY_SOURCES,"[]")); return (0 until a.length()).map{a.getJSONObject(it)}.map{Source(it.optString("name"),it.optString("searchUrl"))} }
    private fun saveSources(list:List<Source>){ val a=JSONArray(); list.forEach{a.put(JSONObject().put("name",it.name).put("searchUrl",it.searchUrl))}; prefs().edit().putString(KEY_SOURCES,a.toString()).apply() }
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
    private fun mangaCard(m:Manga,showProgress:Boolean,click:()->Unit)=baseCard().apply{ val row=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12))}; val img=ImageView(context).apply{scaleType=ImageView.ScaleType.CENTER_CROP; background=round(C_PRIMARY_LIGHT,dp(12)); if(m.cover.isNotBlank()) Glide.with(this@TinyReaderActivity).load(m.cover).into(this)}; row.addView(img,LinearLayout.LayoutParams(dp(64),dp(88))); val col=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL; setPadding(dp(12),0,0,0)}; col.addView(TextView(context).apply{text=m.title;textSize=17f;setTextColor(C_TEXT);setTypeface(typeface,Typeface.BOLD)}); col.addView(TextView(context).apply{text="${m.sourceName} · ${m.chapters.size} 章" + if(showProgress&&m.lastReadAt>0) " · 读到 ${m.chapterIndex+1}" else "";textSize=13f;setTextColor(C_SUB);setPadding(0,dp(5),0,0)}); col.addView(TextView(context).apply{text=m.description.ifBlank{m.url}.take(90);textSize=13f;setTextColor(C_SUB);maxLines=2;setPadding(0,dp(6),0,0)}); row.addView(col,LinearLayout.LayoutParams(0,-2,1f)); addView(row); setOnClickListener{click()} }
    private fun chapterCard(i:Int,ch:Chapter,active:Boolean,click:()->Unit)=baseCard().apply{ val t=TextView(context).apply{text="${i+1}. ${ch.title}";textSize=15f;setTextColor(if(active)C_PRIMARY else C_TEXT);setPadding(dp(16),dp(14),dp(16),dp(14));setTypeface(typeface,if(active)Typeface.BOLD else Typeface.NORMAL)}; addView(t); setOnClickListener{click()} }
    private fun menuCard(title:String,sub:String,click:()->Unit)=baseCard().apply{ val l=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(14))}; l.addView(TextView(context).apply{text=title;textSize=16f;setTextColor(C_TEXT);setTypeface(typeface,Typeface.BOLD)}); l.addView(TextView(context).apply{text=sub;textSize=13f;setTextColor(C_SUB);setPadding(0,dp(4),0,0)}); addView(l); setOnClickListener{click()} }
    private fun navItem(label:String,icon:String,click:()->Unit)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER; val i=TextView(context).apply{text=icon;textSize=20f;setTextColor(C_PRIMARY);gravity=Gravity.CENTER}; val t=TextView(context).apply{text=label;textSize=11f;setTextColor(C_SUB);gravity=Gravity.CENTER}; addView(i); addView(t); setOnClickListener{click()} }
    private fun round(color:Int,r:Int)=GradientDrawable().apply{setColor(color);cornerRadius=r.toFloat()}
    private fun dp(v:Int)=(v*resources.displayMetrics.density+0.5f).toInt()
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
    private fun clip(s:String){ (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("小小漫画",s)) }
    private fun share(s:String){ startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,s),"分享")) }
    private fun saveProgress(){ val m=currentManga ?: return; val y=readerScroll?.scrollY ?: m.progressY; saveManga(m.copy(progressY=y,lastReadAt=if(screenMode==SCREEN_READER)System.currentTimeMillis() else m.lastReadAt)) }

    companion object { private const val TAB_LIBRARY=0; private const val TAB_UPDATES=1; private const val TAB_HISTORY=2; private const val TAB_BROWSE=3; private const val TAB_MORE=4; private const val SCREEN_DETAIL=10; private const val SCREEN_READER=11; private const val SCREEN_SEARCH=12; private const val KEY_MANGA="manga"; private const val KEY_SOURCES="sources"; private const val KEY_SORT="sort"; private const val C_BG=0xFFF8F7FC.toInt(); private const val C_TEXT=0xFF1D1B20.toInt(); private const val C_SUB=0xFF6F6A78.toInt(); private const val C_PRIMARY=0xFF6750A4.toInt(); private const val C_PRIMARY_LIGHT=0xFFEADDFF.toInt(); private const val C_STROKE=0xFFE7E0EC.toInt() }
}

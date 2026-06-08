package com.yuno.tools.ui.tools

import android.app.AlertDialog
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
    private var currentEntry: Entry? = null
    private var readerScroll: ScrollView? = null

    private data class Entry(
        val title: String,
        val url: String,
        val sourceName: String,
        val description: String = "",
        val content: String = "",
        val chapters: List<Chapter> = emptyList(),
        val chapterIndex: Int = 0,
        val progressY: Int = 0,
        val favorite: Boolean = true,
        val unread: Int = 0,
        val lastReadAt: Long = 0L,
        val updatedAt: Long = System.currentTimeMillis()
    )

    private data class Chapter(val title: String, val content: String, val index: Int)
    private data class Source(val name: String, val baseUrl: String, val lang: String = "ZH")
    private data class ReaderSetting(val fontSize: Float, val lineDp: Int, val darkMode: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureDefaultSources()
        buildShell()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBack() }
        })
        showLibrary()
        ThemeApplier.apply(this)
    }

    override fun onResume() { super.onResume(); ThemeApplier.apply(this) }

    override fun finish() { saveProgress(); super.finish(); overridePendingTransition(R.anim.profile_stay, R.anim.profile_slide_down_out) }

    private fun buildShell() {
        window.statusBarColor = bar(); window.navigationBarColor = bar()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg()); layoutParams = LinearLayout.LayoutParams(-1, -1) }
        val appBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(6), dp(10), dp(6)); setBackgroundColor(bar()) }
        appBar.addView(ImageButton(this).apply { setImageResource(android.R.drawable.ic_menu_revert); background = rounded(Color.TRANSPARENT, dp(21)); setColorFilter(text()); setOnClickListener { handleBack() } }, LinearLayout.LayoutParams(dp(44), dp(44)))
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        appBarTitle = TextView(this).apply { textSize = 20f; setTypeface(typeface, Typeface.BOLD); setTextColor(text()); maxLines = 1 }
        appBarSub = TextView(this).apply { textSize = 12f; setTextColor(subText()); maxLines = 1 }
        titleBox.addView(appBarTitle); titleBox.addView(appBarSub)
        appBar.addView(titleBox, LinearLayout.LayoutParams(0, -1, 1f))
        actionHost = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        appBar.addView(actionHost, LinearLayout.LayoutParams(-2, -1))
        root.addView(appBar, LinearLayout.LayoutParams(-1, dp(56)))
        contentHost = FrameLayout(this).apply { setBackgroundColor(bg()) }
        root.addView(contentHost, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(dp(6), dp(4), dp(6), dp(4)); setBackgroundColor(bar()) }
        navLibrary = navItem("书架", "▦") { showLibrary() }
        navUpdates = navItem("更新", "↻") { showUpdates() }
        navHistory = navItem("历史", "◴") { showHistory() }
        navBrowse = navItem("浏览", "⌕") { showBrowse() }
        navMore = navItem("更多", "☰") { showMore() }
        listOf(navLibrary, navUpdates, navHistory, navBrowse, navMore).forEach { bottom.addView(it, LinearLayout.LayoutParams(0, dp(56), 1f)) }
        root.addView(bottom)
        setContentView(root)
    }

    private fun navItem(label: String, icon: String, click: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, dp(2), 0, dp(2)); background = rounded(Color.TRANSPARENT, dp(18))
        addView(TextView(context).apply { text = icon; textSize = 18f; gravity = Gravity.CENTER; setTextColor(subText()) })
        addView(TextView(context).apply { text = label; textSize = 11f; gravity = Gravity.CENTER; setTextColor(subText()) })
        setOnClickListener { saveProgress(); click() }
    }

    private fun setHeader(title: String, sub: String = "", tab: String? = null) {
        appBarTitle.text = title; appBarSub.text = sub; actionHost.removeAllViews(); tab?.let { selectedTab = it; screenMode = it; updateNav() }
    }

    private fun updateNav() {
        fun paint(item: LinearLayout, active: Boolean) {
            item.background = rounded(if (active) activeNav() else Color.TRANSPARENT, dp(18))
            for (i in 0 until item.childCount) (item.getChildAt(i) as? TextView)?.setTextColor(if (active) accent() else subText())
        }
        paint(navLibrary, selectedTab == TAB_LIBRARY); paint(navUpdates, selectedTab == TAB_UPDATES); paint(navHistory, selectedTab == TAB_HISTORY); paint(navBrowse, selectedTab == TAB_BROWSE); paint(navMore, selectedTab == TAB_MORE)
    }

    private fun handleBack() {
        when (screenMode) {
            SCREEN_READER -> currentEntry?.let { showDetail(it) } ?: showLibrary()
            SCREEN_DETAIL -> when (previousScreen) {
                TAB_UPDATES -> showUpdates(); TAB_HISTORY -> showHistory(); TAB_BROWSE -> showBrowse(); TAB_MORE -> showMore(); else -> showLibrary()
            }
            else -> finish()
        }
    }

    private fun scroll(): LinearLayout {
        contentHost.removeAllViews(); val sv = ScrollView(this).apply { setBackgroundColor(bg()) }; val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(22)) }
        sv.addView(box); contentHost.addView(sv); return box
    }

    private fun showLibrary() {
        currentEntry = null; readerScroll = null
        setHeader("书架", "分类 / 继续阅读 / 章节进度", TAB_LIBRARY); actionButton("＋") { showAddDialog() }; actionButton("⌕") { showBrowse() }
        val box = scroll(); val all = loadEntries().filter { it.favorite }.sortedWith(compareByDescending<Entry> { it.lastReadAt }.thenBy { it.title })
        if (all.isEmpty()) { emptyCard(box, "书架为空", "去“浏览”添加一个网页/小说/漫画源。像 Mihon 一样先添加到书架再阅读。") ; quickBrowseCard(box); return }
        all.firstOrNull { it.lastReadAt > 0 }?.let { section(box, "继续阅读"); continueCard(box, it) }
        section(box, "全部收藏 · ${all.size}"); all.forEach { libraryCard(box, it) }
    }

    private fun showUpdates() {
        currentEntry = null; readerScroll = null
        setHeader("更新", "章节更新流", TAB_UPDATES); actionButton("↻") { refreshLibrary() }
        val box = scroll(); val all = loadEntries().filter { it.favorite }.sortedByDescending { it.updatedAt }
        if (all.isEmpty()) { emptyCard(box, "暂无更新", "添加作品后这里会显示章节更新。") ; return }
        section(box, "最近更新"); all.forEach { updateCard(box, it, it.chapters.lastOrNull()?.title ?: "等待章节解析") }
    }

    private fun showHistory() {
        currentEntry = null; readerScroll = null
        setHeader("历史", "阅读记录与续读入口", TAB_HISTORY); val box = scroll(); val history = loadEntries().filter { it.lastReadAt > 0 }.sortedByDescending { it.lastReadAt }
        if (history.isEmpty()) { emptyCard(box, "暂无阅读历史", "打开任意章节后会自动保存历史。") ; return }
        history.forEach { historyCard(box, it) }
    }

    private fun showBrowse() {
        currentEntry = null; readerScroll = null
        setHeader("浏览", "源列表 / 链接导入 / 搜索", TAB_BROWSE); actionButton("＋") { showSourceDialog() }
        val box = scroll(); section(box, "快速导入")
        val input = EditText(this).apply { hint = "粘贴小说/漫画网页链接，或输入关键词"; setSingleLine(true); textSize = 14f; setTextColor(text()); setHintTextColor(subText()); setPadding(dp(12), 0, dp(12), 0); background = rounded(card(), dp(14), divider()) }
        box.addView(input, LinearLayout.LayoutParams(-1, dp(48)))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, dp(6)) }
        row.addView(pill("作为链接打开") { val q = input.text.toString().trim(); if (q.startsWith("http")) loadFromUrl(q, guessTitle(q), "手动导入") else toast("请粘贴 http 链接") }, LinearLayout.LayoutParams(0, dp(42), 1f)); row.addView(space(dp(8), 1))
        row.addView(pill("用默认源搜索") { val q = input.text.toString().trim(); if (q.isNotEmpty()) searchDefault(q) else toast("请输入关键词") }, LinearLayout.LayoutParams(0, dp(42), 1f)); box.addView(row)
        section(box, "已安装源"); loadSources().forEach { sourceCard(box, it) }
    }

    private fun showMore() {
        currentEntry = null; readerScroll = null
        setHeader("更多", "设置 / 数据 / 关于", TAB_MORE); val box = scroll(); section(box, "阅读设置"); settingCard(box); section(box, "数据")
        box.addView(menuCard("备份书架", "当前数据保存在本机 SharedPreferences，文件导出后续接入。") { toast("后续接入文件备份") })
        box.addView(menuCard("清空阅读数据", "删除书架、历史、源设置") { AlertDialog.Builder(this).setTitle("确认清空？").setMessage("会删除小小读书的本地书架和历史。").setNegativeButton("取消", null).setPositiveButton("清空") { _, _ -> prefs().edit().remove(KEY_ENTRIES).remove(KEY_SOURCES).apply(); ensureDefaultSources(); showLibrary() }.show() })
        section(box, "关于"); box.addView(menuCard("小小读书 v1.0.58", "修复返回栈并继续贴近 Mihon 的 Material 界面。") {})
    }

    private fun showDetail(entry: Entry) {
        saveProgress(); previousScreen = if (screenMode == SCREEN_READER || screenMode == SCREEN_DETAIL) previousScreen else selectedTab; screenMode = SCREEN_DETAIL; currentEntry = entry; setHeader(entry.title, entry.sourceName); actionButton(if (entry.favorite) "★" else "☆") { toggleFavorite(entry) }
        val box = scroll(); val top = baseCard(); val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(14), dp(14), dp(14), dp(14)) }
        row.addView(cover(entry.title), LinearLayout.LayoutParams(dp(86), dp(122)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        info.addView(TextView(this).apply { text = entry.title; textSize = 20f; setTypeface(typeface, Typeface.BOLD); setTextColor(text()) })
        info.addView(TextView(this).apply { text = entry.sourceName; textSize = 13f; setTextColor(subText()); setPadding(0, dp(4), 0, dp(4)) })
        info.addView(TextView(this).apply { text = "${entry.chapters.size} 章 · ${if (entry.favorite) "已收藏" else "未收藏"}"; textSize = 13f; setTextColor(subText()) })
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0) }
        btnRow.addView(pill(if (entry.lastReadAt > 0) "继续阅读" else "开始阅读") { openReader(entry, entry.chapterIndex, entry.progressY) }, LinearLayout.LayoutParams(0, dp(40), 1f)); btnRow.addView(space(dp(8), 1)); btnRow.addView(pill("收藏") { toggleFavorite(entry) }, LinearLayout.LayoutParams(0, dp(40), 1f))
        info.addView(btnRow); row.addView(info, LinearLayout.LayoutParams(0, -2, 1f)); top.addView(row); box.addView(top, cardLp())
        if (entry.description.isNotBlank()) box.addView(TextView(this).apply { text = entry.description; textSize = 14f; setTextColor(subText()); setLineSpacing(dp(3).toFloat(), 1f); setPadding(dp(4), dp(4), dp(4), dp(12)) })
        section(box, "章节"); if (entry.chapters.isEmpty()) emptyCard(box, "章节未解析", "可以重新打开链接刷新内容。") else entry.chapters.forEachIndexed { i, ch -> chapterCard(box, entry, ch, i) }
    }

    private fun openReader(entry: Entry, chapter: Int, y: Int = 0) {
        saveProgress(); screenMode = SCREEN_READER; val safeIndex = chapter.coerceIn(0, max(0, entry.chapters.size - 1)); val fixed = entry.copy(chapterIndex = safeIndex, progressY = y, lastReadAt = System.currentTimeMillis()); currentEntry = fixed; upsertEntry(fixed)
        val setting = loadSetting(); val ch = fixed.chapters.getOrNull(safeIndex) ?: Chapter("正文", fixed.content, 0)
        setHeader(fixed.title, "${safeIndex + 1}/${max(1, fixed.chapters.size)} · ${ch.title}"); actionButton("☰") { showChapterSheet(fixed) }; actionButton("Aa") { showReaderSettingDialog { openReader(currentEntry ?: fixed, safeIndex, readerScroll?.scrollY ?: 0) } }
        contentHost.removeAllViews(); val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(readerBg()) }
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(dp(10), dp(8), dp(10), dp(8)); setBackgroundColor(readerBg()) }
        tools.addView(pill("上一章") { if (safeIndex > 0) openReader(fixed, safeIndex - 1, 0) else toast("已经是第一章") }, LinearLayout.LayoutParams(0, dp(40), 1f)); tools.addView(space(dp(8), 1)); tools.addView(pill("目录") { showChapterSheet(fixed) }, LinearLayout.LayoutParams(0, dp(40), 1f)); tools.addView(space(dp(8), 1)); tools.addView(pill("下一章") { if (safeIndex < fixed.chapters.size - 1) openReader(fixed, safeIndex + 1, 0) else toast("已经是最后一章") }, LinearLayout.LayoutParams(0, dp(40), 1f)); outer.addView(tools)
        readerScroll = ScrollView(this).apply { setBackgroundColor(readerBg()) }; val article = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(18), dp(22), dp(40)); setBackgroundColor(readerBg()) }
        article.addView(TextView(this).apply { text = ch.title; textSize = setting.fontSize + 5f; setTypeface(typeface, Typeface.BOLD); setTextColor(readerText()); setPadding(0, 0, 0, dp(18)) })
        article.addView(TextView(this).apply { text = ch.content.ifBlank { fixed.content }; textSize = setting.fontSize; setTextColor(readerText()); setLineSpacing(dp(setting.lineDp).toFloat(), 1.05f) })
        readerScroll!!.addView(article); outer.addView(readerScroll, LinearLayout.LayoutParams(-1, 0, 1f)); contentHost.addView(outer); readerScroll!!.post { readerScroll?.scrollTo(0, y) }
    }

    private fun saveProgress() { val e = currentEntry ?: return; val y = readerScroll?.scrollY ?: e.progressY; upsertEntry(e.copy(progressY = y, lastReadAt = if (y > 0 || e.lastReadAt > 0) System.currentTimeMillis() else e.lastReadAt)) }

    private fun loadFromUrl(url: String, titleHint: String, source: String) {
        setHeader("加载中", titleHint); contentHost.removeAllViews(); contentHost.addView(TextView(this).apply { text = "正在解析页面…"; gravity = Gravity.CENTER; textSize = 16f; setTextColor(subText()) })
        Thread {
            try { val body = client.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 YunoTools Reader").build()).execute().body?.string().orEmpty(); val clean = htmlToText(body); val title = extractTitle(body).ifBlank { titleHint }; val chapters = splitChapters(clean); val entry = Entry(title = title, url = url, sourceName = source, description = clean.take(180), content = clean.take(MAX_SAVE_CONTENT), chapters = chapters, unread = chapters.size, updatedAt = System.currentTimeMillis()); upsertEntry(entry); runOnUiThread { showDetail(entry); toast("已加入书架") } }
            catch (e: Exception) { runOnUiThread { toast("加载失败：${e.message}"); showBrowse() } }
        }.start()
    }

    private fun searchDefault(keyword: String) { val src = loadSources().firstOrNull() ?: Source("必应搜索", "https://www.bing.com/search?q=%s"); val url = if (src.baseUrl.contains("%s")) src.baseUrl.replace("%s", URLEncoder.encode(keyword, "UTF-8")) else src.baseUrl + URLEncoder.encode(keyword, "UTF-8"); loadFromUrl(url, keyword, src.name) }
    private fun refreshLibrary() { saveEntries(loadEntries().map { it.copy(updatedAt = System.currentTimeMillis()) }); toast("已刷新更新列表"); showUpdates() }
    private fun showAddDialog() { val input = EditText(this).apply { hint = "输入网页链接"; setSingleLine(true) }; AlertDialog.Builder(this).setTitle("添加到书架").setView(input).setNegativeButton("取消", null).setPositiveButton("打开") { _, _ -> val url = input.text.toString().trim(); if (url.startsWith("http")) loadFromUrl(url, guessTitle(url), "手动导入") else toast("请输入 http 链接") }.show() }
    private fun showSourceDialog() { val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, dp(18), 0) }; val name = EditText(this).apply { hint = "源名称" }; val url = EditText(this).apply { hint = "搜索地址，关键词用 %s" }; box.addView(name); box.addView(url); AlertDialog.Builder(this).setTitle("添加源").setView(box).setNegativeButton("取消", null).setPositiveButton("保存") { _, _ -> val n = name.text.toString().ifBlank { "自定义源" }; val u = url.text.toString().trim(); if (u.startsWith("http")) { saveSources(loadSources() + Source(n, u)); showBrowse() } else toast("地址无效") }.show() }
    private fun showChapterSheet(entry: Entry) { val titles = entry.chapters.mapIndexed { i, c -> (if (i == entry.chapterIndex) "▶ " else "") + c.title }.toTypedArray(); AlertDialog.Builder(this).setTitle("章节列表").setItems(titles) { _, which -> openReader(entry, which, 0) }.show() }
    private fun showReaderSettingDialog(after: () -> Unit) { val s = loadSetting(); val items = arrayOf("字体减小", "字体增大", "行距减小", "行距增大", if (s.darkMode) "关闭夜间模式" else "开启夜间模式"); AlertDialog.Builder(this).setTitle("阅读设置").setItems(items) { _, which -> val now = loadSetting(); val next = when (which) { 0 -> now.copy(fontSize = max(14f, now.fontSize - 1f)); 1 -> now.copy(fontSize = min(28f, now.fontSize + 1f)); 2 -> now.copy(lineDp = max(2, now.lineDp - 1)); 3 -> now.copy(lineDp = min(14, now.lineDp + 1)); else -> now.copy(darkMode = !now.darkMode) }; saveSetting(next); buildShell(); after() }.show() }
    private fun toggleFavorite(e: Entry) { val next = e.copy(favorite = !e.favorite); upsertEntry(next); currentEntry = next; showDetail(next) }

    private fun quickBrowseCard(box: LinearLayout) { box.addView(menuCard("打开浏览", "像 Mihon 一样先从“浏览”进入源，再添加到书架。") { showBrowse() }) }
    private fun continueCard(box: LinearLayout, e: Entry) { val card = baseCard(); val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(12), dp(12), dp(12)) }; row.addView(cover(e.title), LinearLayout.LayoutParams(dp(62), dp(86))); val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }; info.addView(TextView(this).apply { text = e.title; textSize = 17f; setTypeface(typeface, Typeface.BOLD); setTextColor(text()) }); info.addView(TextView(this).apply { text = "第 ${e.chapterIndex + 1}/${max(1, e.chapters.size)} 章 · 已保存进度"; textSize = 13f; setTextColor(subText()); setPadding(0, dp(4), 0, dp(8)) }); info.addView(pill("继续阅读") { openReader(e, e.chapterIndex, e.progressY) }, LinearLayout.LayoutParams(dp(118), dp(38))); row.addView(info, LinearLayout.LayoutParams(0, -2, 1f)); card.addView(row); box.addView(card, cardLp()) }
    private fun libraryCard(box: LinearLayout, e: Entry) { val card = baseCard(); card.setOnClickListener { showDetail(e) }; val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(12), dp(12), dp(12)); gravity = Gravity.CENTER_VERTICAL }; row.addView(cover(e.title), LinearLayout.LayoutParams(dp(58), dp(80))); val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }; info.addView(TextView(this).apply { text = e.title; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(text()); maxLines = 1 }); info.addView(TextView(this).apply { text = e.sourceName; textSize = 12f; setTextColor(subText()); setPadding(0, dp(3), 0, dp(3)) }); info.addView(TextView(this).apply { text = "${e.chapters.size} 章 · ${if (e.lastReadAt > 0) "读到第 ${e.chapterIndex + 1} 章" else "未读"}"; textSize = 12f; setTextColor(subText()) }); row.addView(info, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(TextView(this).apply { text = "›"; textSize = 28f; setTextColor(subText()); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(24), -1)); card.addView(row); box.addView(card, cardLp()) }
    private fun updateCard(box: LinearLayout, e: Entry, chapter: String) { box.addView(menuCard(e.title, "最新：$chapter · ${e.sourceName}") { showDetail(e) }) }
    private fun historyCard(box: LinearLayout, e: Entry) { box.addView(menuCard(e.title, "读到第 ${e.chapterIndex + 1}/${max(1, e.chapters.size)} 章 · 点击继续") { openReader(e, e.chapterIndex, e.progressY) }) }
    private fun sourceCard(box: LinearLayout, s: Source) { val card = baseCard(); val lay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }; lay.addView(TextView(this).apply { text = s.name; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(text()) }); lay.addView(TextView(this).apply { text = "${s.lang} · ${s.baseUrl}"; textSize = 12f; setTextColor(subText()); maxLines = 2; setPadding(0, dp(4), 0, dp(10)) }); lay.addView(pill("在此源中搜索") { val input = EditText(this).apply { hint = "关键词"; setSingleLine(true) }; AlertDialog.Builder(this).setTitle(s.name).setView(input).setNegativeButton("取消", null).setPositiveButton("搜索") { _, _ -> val q = input.text.toString().trim(); if (q.isNotEmpty()) { val url = if (s.baseUrl.contains("%s")) s.baseUrl.replace("%s", URLEncoder.encode(q, "UTF-8")) else s.baseUrl + URLEncoder.encode(q, "UTF-8"); loadFromUrl(url, q, s.name) } }.show() }, LinearLayout.LayoutParams(-1, dp(38))); card.addView(lay); box.addView(card, cardLp()) }
    private fun settingCard(box: LinearLayout) { val s = loadSetting(); box.addView(menuCard("阅读器", "字体 ${s.fontSize.toInt()}sp · 行距 ${s.lineDp}dp · ${if (s.darkMode) "夜间" else "日间"}") { showReaderSettingDialog { showMore() } }) }
    private fun chapterCard(box: LinearLayout, e: Entry, ch: Chapter, i: Int) { val title = if (i == e.chapterIndex && e.lastReadAt > 0) "▶ ${ch.title}" else ch.title; box.addView(menuCard(title, "${ch.content.length} 字") { openReader(e, i, if (i == e.chapterIndex) e.progressY else 0) }) }
    private fun emptyCard(box: LinearLayout, title: String, sub: String) { box.addView(menuCard(title, sub) {}) }
    private fun menuCard(title: String, sub: String, click: () -> Unit): MaterialCardView { val card = baseCard(); card.setOnClickListener { click() }; val lay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(13), dp(14), dp(13)) }; lay.addView(TextView(this).apply { text = title; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(text()) }); lay.addView(TextView(this).apply { text = sub; textSize = 12f; setTextColor(subText()); setPadding(0, dp(4), 0, 0) }); card.addView(lay); return card }
    private fun baseCard() = MaterialCardView(this).apply { radius = dp(20).toFloat(); cardElevation = 0f; strokeWidth = dp(1); strokeColor = divider(); setCardBackgroundColor(card()) }
    private fun cardLp() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    private fun cover(title: String) = TextView(this).apply { text = title.take(2).ifBlank { "书" }; textSize = 22f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = rounded(colorFrom(title), dp(14)) }
    private fun section(box: LinearLayout, title: String) { box.addView(TextView(this).apply { text = title; textSize = 14f; setTypeface(typeface, Typeface.BOLD); setTextColor(subText()); setPadding(dp(2), dp(8), 0, dp(8)) }) }
    private fun actionButton(label: String, click: () -> Unit) { actionHost.addView(TextView(this).apply { text = label; textSize = 20f; gravity = Gravity.CENTER; setTextColor(text()); setOnClickListener { click() } }, LinearLayout.LayoutParams(dp(42), dp(42))) }
    private fun pill(label: String, click: () -> Unit) = TextView(this).apply { text = label; textSize = 13f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE); background = rounded(accent(), dp(20)); setOnClickListener { click() } }
    private fun space(w: Int, h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(w, h) }

    private fun splitChapters(text: String): List<Chapter> { val clean = text.replace("\r", "").lines().map { it.trim() }.filter { it.isNotBlank() }; val re = Regex("^(第[0-9零一二三四五六七八九十百千万]+[章节回卷集].{0,42}|Chapter\\s*\\d+.{0,42}|CHAPTER\\s*\\d+.{0,42})$"); val result = mutableListOf<Chapter>(); var title = "开始"; val buf = StringBuilder(); fun flush() { if (buf.isNotBlank()) { result.add(Chapter(title, buf.toString().trim(), result.size)); buf.clear() } }; clean.forEach { line -> if (re.matches(line) && buf.length > 500) { flush(); title = line } else buf.append(line).append("\n\n") }; flush(); return if (result.size >= 2) result.take(MAX_CHAPTERS) else paginate(text) }
    private fun paginate(text: String): List<Chapter> { val t = text.ifBlank { "当前页面没有提取到足够正文。可以尝试换一个正文页链接。" }; val list = mutableListOf<Chapter>(); var start = 0; while (start < t.length && list.size < MAX_CHAPTERS) { val end = min(t.length, start + PAGE_SIZE); list.add(Chapter("第 ${list.size + 1} 页", t.substring(start, end), list.size)); start = end }; return list }
    private fun htmlToText(html: String) = html.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ").replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ").replace(Regex("</(p|div|br|h[1-6]|li|tr)>", RegexOption.IGNORE_CASE), "\n").replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim().take(MAX_SAVE_CONTENT)
    private fun extractTitle(html: String) = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
    private fun guessTitle(url: String) = url.substringAfterLast('/').substringBefore('?').ifBlank { "未命名条目" }.take(40)

    private fun loadEntries(): List<Entry> { val arr = JSONArray(prefs().getString(KEY_ENTRIES, "[]")); return (0 until arr.length()).mapNotNull { i -> runCatching { val o = arr.getJSONObject(i); val chArr = o.optJSONArray("chapters") ?: JSONArray(); val chapters = (0 until chArr.length()).map { ci -> val c = chArr.getJSONObject(ci); Chapter(c.optString("title"), c.optString("content"), ci) }; Entry(o.optString("title"), o.optString("url"), o.optString("sourceName"), o.optString("description"), o.optString("content"), chapters, o.optInt("chapterIndex"), o.optInt("progressY"), o.optBoolean("favorite", true), o.optInt("unread"), o.optLong("lastReadAt"), o.optLong("updatedAt")) }.getOrNull() } }
    private fun saveEntries(list: List<Entry>) { val arr = JSONArray(); list.forEach { e -> val o = JSONObject(); o.put("title", e.title); o.put("url", e.url); o.put("sourceName", e.sourceName); o.put("description", e.description); o.put("content", e.content); o.put("chapterIndex", e.chapterIndex); o.put("progressY", e.progressY); o.put("favorite", e.favorite); o.put("unread", e.unread); o.put("lastReadAt", e.lastReadAt); o.put("updatedAt", e.updatedAt); val ca = JSONArray(); e.chapters.forEach { c -> ca.put(JSONObject().put("title", c.title).put("content", c.content.take(PAGE_SIZE + 1000))) }; o.put("chapters", ca); arr.put(o) }; prefs().edit().putString(KEY_ENTRIES, arr.toString()).apply() }
    private fun upsertEntry(e: Entry) { val list = loadEntries().toMutableList(); val idx = list.indexOfFirst { it.url == e.url }; if (idx >= 0) list[idx] = e else list.add(0, e); saveEntries(list.take(80)) }
    private fun loadSources(): List<Source> { val arr = JSONArray(prefs().getString(KEY_SOURCES, "[]")); return (0 until arr.length()).map { i -> val o = arr.getJSONObject(i); Source(o.optString("name"), o.optString("baseUrl"), o.optString("lang", "ZH")) } }
    private fun saveSources(list: List<Source>) { val arr = JSONArray(); list.forEach { arr.put(JSONObject().put("name", it.name).put("baseUrl", it.baseUrl).put("lang", it.lang)) }; prefs().edit().putString(KEY_SOURCES, arr.toString()).apply() }
    private fun ensureDefaultSources() { if (loadSources().isEmpty()) saveSources(listOf(Source("必应搜索", "https://www.bing.com/search?q=%s"), Source("百度搜索", "https://www.baidu.com/s?wd=%s"))) }
    private fun loadSetting() = ReaderSetting(prefs().getFloat(KEY_FONT, 18f), prefs().getInt(KEY_LINE, 7), prefs().getBoolean(KEY_DARK, false))
    private fun saveSetting(s: ReaderSetting) { prefs().edit().putFloat(KEY_FONT, s.fontSize).putInt(KEY_LINE, s.lineDp).putBoolean(KEY_DARK, s.darkMode).apply() }
    private fun prefs() = getSharedPreferences("tiny_reader_mihon_style", MODE_PRIVATE)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun colorFrom(s: String): Int { val colors = intArrayOf(0xFF5E72E4.toInt(), 0xFF2DCE89.toInt(), 0xFFFB6340.toInt(), 0xFF11CDEF.toInt(), 0xFFF5365C.toInt()); return colors[kotlin.math.abs(s.hashCode()) % colors.size] }
    private fun dark() = loadSetting().darkMode
    private fun bg() = if (dark()) Color.rgb(15, 15, 17) else Color.rgb(245, 246, 250)
    private fun bar() = if (dark()) Color.rgb(24, 24, 27) else Color.WHITE
    private fun card() = if (dark()) Color.rgb(30, 30, 34) else Color.WHITE
    private fun text() = if (dark()) Color.rgb(245, 245, 247) else Color.rgb(32, 32, 36)
    private fun subText() = if (dark()) Color.rgb(166, 166, 172) else Color.rgb(112, 112, 122)
    private fun accent() = Color.rgb(103, 80, 164)
    private fun divider() = if (dark()) Color.rgb(54, 54, 60) else Color.rgb(226, 226, 232)
    private fun activeNav() = if (dark()) Color.rgb(48, 40, 62) else Color.rgb(234, 221, 255)
    private fun readerBg() = if (dark()) Color.rgb(17, 17, 17) else Color.rgb(247, 243, 232)
    private fun readerText() = if (dark()) Color.rgb(235, 235, 235) else Color.rgb(43, 33, 24)
    private fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat(); stroke?.let { setStroke(dp(1), it) } }

    companion object { private const val TAB_LIBRARY = "library"; private const val TAB_UPDATES = "updates"; private const val TAB_HISTORY = "history"; private const val TAB_BROWSE = "browse"; private const val TAB_MORE = "more"; private const val SCREEN_DETAIL = "detail"; private const val SCREEN_READER = "reader"; private const val KEY_ENTRIES = "entries"; private const val KEY_SOURCES = "sources"; private const val KEY_FONT = "font"; private const val KEY_LINE = "line"; private const val KEY_DARK = "dark"; private const val MAX_SAVE_CONTENT = 180000; private const val MAX_CHAPTERS = 180; private const val PAGE_SIZE = 6500 }
}

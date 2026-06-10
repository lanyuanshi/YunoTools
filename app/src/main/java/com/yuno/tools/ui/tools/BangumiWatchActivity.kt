package com.yuno.tools.ui.tools

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.Html
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.util.ThemeApplier
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BangumiWatchActivity : AppCompatActivity() {
    private enum class Tab { BANGUMI, MINE }
    private enum class Screen { LIST, DETAIL, SEARCH }

    private data class AnimeItem(
        val title: String,
        val status: String,
        val cover: String,
        val detailUrl: String
    )

    private data class EpisodeItem(
        val name: String,
        val playUrl: String,
        val source: String
    )

    private data class AnimeDetail(
        val item: AnimeItem,
        val intro: String,
        val meta: String,
        val playerPrefix: String,
        val episodes: List<EpisodeItem>
    )

    private val libraryUrl = "https://www.animetranslation.com/library"
    private val baseUrl = "https://www.animetranslation.com"
    private val prefs by lazy { getSharedPreferences("yuno_bangumi_watch", Context.MODE_PRIVATE) }
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(14, TimeUnit.SECONDS)
            .callTimeout(18, TimeUnit.SECONDS)
            .build()
    }

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private var tab = Tab.BANGUMI
    private var screen = Screen.LIST
    private var loading = false
    private var allAnime: List<AnimeItem> = emptyList()
    private var searchText = ""
    private var selectedAnime: AnimeItem? = null
    private var selectedDetail: AnimeDetail? = null
    private var player: ExoPlayer? = null
    private var currentEpisode: EpisodeItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeApplier.apply(this)
        allAnime = loadCachedAnime()
        buildUi()
        if (allAnime.isEmpty()) loadLibrary()
    }

    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FAFAFA"))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(82))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomBar())
        setContentView(root)
        render()
    }

    private fun render() {
        content.removeAllViews()
        if (root.childCount > 1) {
            root.removeViewAt(1)
            root.addView(bottomBar())
        }
        if (tab == Tab.MINE) {
            renderMine()
            return
        }
        when (screen) {
            Screen.LIST -> renderList()
            Screen.SEARCH -> renderSearch()
            Screen.DETAIL -> renderDetail()
        }
    }

    private fun renderList() {
        topBar("首页", showSearch = true, showBack = false)
        if (loading && allAnime.isEmpty()) {
            emptyCard("加载中", "正在加载动漫库列表。")
            return
        }
        if (allAnime.isEmpty()) {
            emptyCard("暂无番剧", "列表还没有加载出来。")
            content.addView(primaryWideButton("重新加载") { loadLibrary() })
            return
        }
        section("最新番剧")
        animeGrid(allAnime)
    }

    private fun renderSearch() {
        topBar("搜索", showSearch = false, showBack = true)
        val input = EditText(this).apply {
            hint = "搜索番剧"
            setText(searchText)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(Color.parseColor("#202124"))
            setHintTextColor(Color.parseColor("#8B9099"))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    searchText = text?.toString()?.trim().orEmpty()
                    render()
                    true
                } else false
            }
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.parseColor("#F0F2F6"))
            addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(primaryButton("搜索") {
                searchText = input.text?.toString()?.trim().orEmpty()
                render()
            })
        }
        content.addView(box, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(10)) })
        val result = if (searchText.isBlank()) emptyList() else allAnime.filter { it.title.contains(searchText, true) || it.status.contains(searchText, true) }
        if (searchText.isBlank()) {
            emptyCard("输入关键词", "右上角搜索会在已加载的番剧列表里筛选。")
        } else if (result.isEmpty()) {
            emptyCard("没有结果", "没有找到“$searchText”。")
        } else {
            section("搜索结果")
            animeGrid(result)
        }
    }

    private fun renderDetail() {
        val item = selectedAnime ?: returnToList()
        topBar(item.title, showSearch = false, showBack = true)
        val detail = selectedDetail
        if (detail == null) {
            content.addView(detailHeader(item, null, "正在加载详情..."))
            if (!loading) loadDetail(item)
            return
        }
        content.addView(detailHeader(detail.item, detail.meta, detail.intro))
        section("播放")
        content.addView(playerBox(detail))
        section("剧集")
        if (detail.episodes.isEmpty()) {
            emptyCard("暂无剧集", "没有解析到剧集列表。")
        } else {
            episodeGrid(detail)
        }
    }

    private fun renderMine() {
        topBar("我的", showSearch = false, showBack = false)
        section("历史记录")
        val history = loadRecordList("history")
        if (history.isEmpty()) emptyCard("暂无历史", "播放过的剧集会显示在这里。") else history.forEach { record ->
            content.addView(recordCard(record, "继续") { openUrl(record.optString("url")) })
        }
        section("下载记录")
        val downloads = loadRecordList("downloads")
        if (downloads.isEmpty()) emptyCard("暂无下载", "在播放页点下载后会记录到这里。") else downloads.forEach { record ->
            content.addView(recordCard(record, "打开") { openUrl(record.optString("url")) })
        }
        section("设置")
        content.addView(settingCard("刷新番剧列表", "重新从动漫库加载番剧列表") { loadLibrary() })
        content.addView(settingCard("清空历史记录", "删除本地播放历史") { clearRecords("history") })
        content.addView(settingCard("清空下载记录", "删除本地下载记录") { clearRecords("downloads") })
    }

    private fun loadLibrary() {
        if (loading) return
        loading = true
        render()
        Thread {
            val result = runCatching {
                val html = fetch(libraryUrl)
                parseLibrary(html)
            }
            runOnUiThread {
                loading = false
                result.onSuccess { list ->
                    allAnime = list
                    saveCachedAnime(list)
                    Toast.makeText(this, "已加载 ${list.size} 部番剧", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(this, "加载失败：${err.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
                render()
            }
        }.start()
    }

    private fun loadDetail(item: AnimeItem) {
        if (loading) return
        loading = true
        Thread {
            val result = runCatching {
                val html = fetch(absUrl(item.detailUrl))
                parseDetail(item, html)
            }
            runOnUiThread {
                loading = false
                result.onSuccess { detail -> selectedDetail = detail }
                    .onFailure { err -> Toast.makeText(this, "详情加载失败：${err.message ?: "未知错误"}", Toast.LENGTH_LONG).show() }
                render()
            }
        }.start()
    }

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .header("Referer", baseUrl)
            .build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string().orEmpty().ifBlank { error("empty body") }
        }
    }

    private fun parseLibrary(html: String): List<AnimeItem> {
        val itemRegex = Regex("""<div class=\"anime-item\">(.*?)</div>\s*</div>""", RegexOption.DOT_MATCHES_ALL)
        val hrefRegex = Regex("""<a href=\"([^\"]+)\" class=\"anime-card\">""")
        val imgRegex = Regex("""<img src=\"([^\"]+)\" alt=\"([^\"]*)\"""")
        val statusRegex = Regex("""<div class=\"status-badge\">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
        return itemRegex.findAll(html).mapNotNull { match ->
            val block = match.value
            val href = hrefRegex.find(block)?.groupValues?.getOrNull(1).orEmpty()
            val img = imgRegex.find(block)
            val cover = img?.groupValues?.getOrNull(1).orEmpty()
            val title = htmlText(img?.groupValues?.getOrNull(2).orEmpty())
            val status = htmlText(statusRegex.find(block)?.groupValues?.getOrNull(1).orEmpty())
            if (title.isBlank() || href.isBlank()) null else AnimeItem(title, status, cover, href)
        }.distinctBy { it.detailUrl }.toList()
    }

    private fun parseDetail(item: AnimeItem, html: String): AnimeDetail {
        val intro = htmlText(Regex("""<div class=\"description\">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.getOrNull(1).orEmpty())
            .ifBlank { "暂无简介" }
        val year = htmlText(Regex("""<strong>年份:</strong>\s*<span>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.getOrNull(1).orEmpty())
        val area = htmlText(Regex("""<strong>地区:</strong>\s*<span>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.getOrNull(1).orEmpty())
        val type = htmlText(Regex("""<strong>类型:</strong>\s*<span>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.getOrNull(1).orEmpty())
        val meta = listOf(year, area, type).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { item.status }
        val prefix = htmlAttr(Regex("""data-player-url=\"([^\"]*)\"""").find(html)?.groupValues?.getOrNull(1).orEmpty())
        val episodes = parseEpisodes(html, prefix)
        return AnimeDetail(item, intro, meta, prefix, episodes)
    }

    private fun parseEpisodes(html: String, prefix: String): List<EpisodeItem> {
        val sourceRegex = Regex("""<div class=\"source-tab[^\"]*\"[^>]*data-source=\"([^\"]+)\"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
        val sourceName = sourceRegex.find(html)?.groupValues?.getOrNull(2)?.let { htmlText(it).replace(Regex("\\s+"), " ").trim() }.orEmpty().ifBlank { "播放源" }
        val episodeRegex = Regex("""<a[^>]+class=\"episode-link[^\"]*\"[^>]*(?:data-url=\"([^\"]*)\")?[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>|<a[^>]+href=\"([^\"]*)\"[^>]*(?:data-url=\"([^\"]*)\")?[^>]*class=\"episode-link[^\"]*\"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val found = episodeRegex.findAll(html).mapNotNull { m ->
            val dataUrl = (m.groups[1]?.value ?: m.groups[5]?.value).orEmpty()
            val href = (m.groups[2]?.value ?: m.groups[4]?.value).orEmpty()
            val name = htmlText((m.groups[3]?.value ?: m.groups[6]?.value).orEmpty()).ifBlank { "播放" }
            val raw = dataUrl.ifBlank { href }
            val play = when {
                raw.startsWith("http") -> raw
                prefix.isNotBlank() && raw.isNotBlank() -> prefix + raw
                raw.isNotBlank() -> absUrl(raw)
                else -> ""
            }
            if (play.isBlank()) null else EpisodeItem(name, play, sourceName)
        }.toList()
        if (found.isNotEmpty()) return found.distinctBy { it.playUrl }
        val urlRegex = Regex("""data-url=\"([^\"]+)\"[^>]*>(.*?)</""", RegexOption.DOT_MATCHES_ALL)
        return urlRegex.findAll(html).map { m ->
            val url = htmlAttr(m.groupValues[1])
            val name = htmlText(m.groupValues[2]).ifBlank { "播放" }
            EpisodeItem(name, if (prefix.isNotBlank()) prefix + url else url, sourceName)
        }.distinctBy { it.playUrl }.toList()
    }

    private fun animeGrid(list: List<AnimeItem>) {
        val grid = GridLayout(this).apply { columnCount = 3 }
        list.forEach { item -> grid.addView(animeCard(item)) }
        content.addView(grid)
    }

    private fun animeCard(item: AnimeItem): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }
        val coverFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150))
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#EDEFF5"))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        Glide.with(this).load(item.cover).centerCrop().into(image)
        coverFrame.addView(image)
        if (item.status.isNotBlank()) {
            coverFrame.addView(TextView(this).apply {
                text = item.status
                textSize = 10f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#CC4F6EF7"))
                setPadding(dp(5), dp(2), dp(5), dp(2))
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START)
            })
        }
        box.addView(coverFrame)
        box.addView(TextView(this).apply {
            text = item.title
            textSize = 12f
            setTextColor(Color.parseColor("#202124"))
            maxLines = 2
            setPadding(0, dp(5), 0, 0)
        })
        box.setOnClickListener {
            selectedAnime = item
            selectedDetail = null
            screen = Screen.DETAIL
            addHistory(item, null)
            render()
        }
        return box.apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
        }
    }

    private fun detailHeader(item: AnimeItem, meta: String?, intro: String) = card().apply {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#EDEFF5"))
            layoutParams = LinearLayout.LayoutParams(dp(104), dp(148))
        }
        Glide.with(this@BangumiWatchActivity).load(item.cover).centerCrop().into(image)
        row.addView(image)
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply { text = item.title; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#202124")); maxLines = 2 })
            addView(TextView(context).apply { text = meta ?: item.status; textSize = 12f; setTextColor(Color.parseColor("#707782")); setPadding(0, dp(6), 0, dp(8)) })
            addView(TextView(context).apply { text = intro; textSize = 12f; setTextColor(Color.parseColor("#4D5562")); maxLines = 5 })
        })
        addView(row)
    }

    private fun playerBox(detail: AnimeDetail) = card().apply {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        box.addView(TextView(context).apply { text = "内置播放器"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#202124")) })
        val active = currentEpisode
        if (active == null) {
            box.addView(TextView(context).apply { text = "选择下方剧集后在这里播放。"; textSize = 12f; setTextColor(Color.parseColor("#707782")); setPadding(0, dp(6), 0, dp(10)) })
        } else {
            box.addView(TextView(context).apply { text = "${active.source} · ${active.name}"; textSize = 12f; setTextColor(Color.parseColor("#707782")); setPadding(0, dp(6), 0, dp(10)) })
            val playerView = PlayerView(context).apply {
                useController = true
                player = ensurePlayer()
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(210))
            }
            box.addView(playerView)
            val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0, dp(10), 0, 0) }
            actions.addView(primaryButton("外部打开") { openUrl(active.playUrl) })
            actions.addView(View(this@BangumiWatchActivity).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) })
            actions.addView(primaryButton("下载记录") { addDownload(detail.item, active) })
            box.addView(actions)
        }
        addView(box)
    }

    private fun episodeGrid(detail: AnimeDetail) {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        detail.episodes.take(80).forEach { ep ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(7), dp(8), dp(7))
                setBackgroundColor(Color.parseColor(if (currentEpisode?.playUrl == ep.playUrl) "#DCE4FF" else "#EEF1F6"))
                layoutParams = LinearLayout.LayoutParams(dp(104), LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, dp(8), dp(8)) }
                setOnClickListener { playEpisode(detail.item, ep) }
            }
            cell.addView(TextView(this).apply {
                text = ep.name
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#202124"))
                maxLines = 1
            })
            cell.addView(TextView(this).apply {
                text = "下载记录"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#4F6EF7"))
                setPadding(0, dp(5), 0, 0)
                setOnClickListener { addDownload(detail.item, ep) }
            })
            row.addView(cell)
        }
        scroll.addView(row)
        content.addView(scroll)
    }

    private fun playEpisode(item: AnimeItem, ep: EpisodeItem) {
        addHistory(item, ep)
        currentEpisode = ep
        ensurePlayer().apply {
            setMediaItem(MediaItem.fromUri(ep.playUrl))
            prepare()
            playWhenReady = true
        }
        Toast.makeText(this, "正在播放 ${ep.name}", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun ensurePlayer(): ExoPlayer {
        val existing = player
        if (existing != null) return existing
        return ExoPlayer.Builder(this).build().also { player = it }
    }

    private fun topBar(title: String, showSearch: Boolean, showBack: Boolean) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(10)) }
        row.addView(TextView(this).apply {
            text = if (showBack) "‹" else ""
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#202124"))
            setOnClickListener { if (showBack) { screen = Screen.LIST; render() } }
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        })
        row.addView(TextView(this).apply {
            text = title
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#202124"))
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (showSearch) {
            row.addView(TextView(this).apply {
                text = "搜索"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#4F6EF7"))
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener { screen = Screen.SEARCH; render() }
            })
        }
        content.addView(row)
    }

    private fun bottomBar(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.WHITE)
        listOf(Tab.BANGUMI to "番剧", Tab.MINE to "我的").forEach { (target, label) ->
            addView(TextView(context).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER
                typeface = if (tab == target) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(Color.parseColor(if (tab == target) "#4F6EF7" else "#6D737D"))
                setOnClickListener {
                    tab = target
                    screen = Screen.LIST
                    render()
                }
            }, LinearLayout.LayoutParams(0, dp(56), 1f))
        }
    }

    private fun settingCard(title: String, sub: String, action: () -> Unit) = card().apply {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply { text = title; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#202124")) })
            addView(TextView(context).apply { text = sub; textSize = 12f; setTextColor(Color.parseColor("#7A7F89")) })
        })
        row.addView(primaryButton("执行", action))
        addView(row)
    }

    private fun recordCard(obj: JSONObject, actionText: String, action: () -> Unit) = card().apply {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply { text = obj.optString("title"); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#202124")); maxLines = 1 })
            addView(TextView(context).apply { text = obj.optString("sub"); textSize = 12f; setTextColor(Color.parseColor("#7A7F89")); maxLines = 1 })
        })
        row.addView(primaryButton(actionText, action))
        addView(row)
    }

    private fun addHistory(item: AnimeItem, ep: EpisodeItem?) {
        val sub = ep?.let { "${it.source} · ${it.name}" } ?: item.status
        val url = ep?.playUrl ?: absUrl(item.detailUrl)
        addRecord("history", item.title, sub, url)
    }

    private fun addDownload(item: AnimeItem, ep: EpisodeItem) {
        addRecord("downloads", item.title, "${ep.source} · ${ep.name}", ep.playUrl)
        Toast.makeText(this, "已加入下载记录", Toast.LENGTH_SHORT).show()
    }

    private fun addRecord(key: String, title: String, sub: String, url: String) {
        val arr = JSONArray()
        arr.put(JSONObject().apply { put("title", title); put("sub", sub); put("url", url) })
        loadRecordList(key).filterNot { it.optString("url") == url }.take(29).forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun loadRecordList(key: String): List<JSONObject> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
        buildList { for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { add(it) } }
    }.getOrDefault(emptyList())

    private fun clearRecords(key: String) {
        prefs.edit().putString(key, "[]").apply()
        Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun saveCachedAnime(list: List<AnimeItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(JSONObject().apply { put("title", item.title); put("status", item.status); put("cover", item.cover); put("detailUrl", item.detailUrl) })
        }
        prefs.edit().putString("library_cache", arr.toString()).apply()
    }

    private fun loadCachedAnime(): List<AnimeItem> = runCatching {
        val arr = JSONArray(prefs.getString("library_cache", "[]") ?: "[]")
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                add(AnimeItem(obj.optString("title"), obj.optString("status"), obj.optString("cover"), obj.optString("detailUrl")))
            }
        }
    }.getOrDefault(emptyList())

    private fun returnToList(): Nothing {
        screen = Screen.LIST
        render()
        throw IllegalStateException("return")
    }

    private fun openUrl(url: String) {
        if (url.isBlank()) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "没有可用应用打开播放地址", Toast.LENGTH_SHORT).show() }
    }

    private fun absUrl(url: String): String = when {
        url.startsWith("http") -> url
        url.startsWith("/") -> baseUrl + url
        else -> "$baseUrl/$url"
    }

    private fun htmlText(raw: String): String = Html.fromHtml(raw.replace(Regex("<script.*?</script>"), ""), Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()
    private fun htmlAttr(raw: String): String = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().trim()

    private fun emptyCard(title: String, msg: String) = content.addView(card().apply {
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(20), dp(34), dp(20), dp(34)) }
        box.addView(TextView(context).apply { text = title; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#202124")); gravity = Gravity.CENTER })
        box.addView(TextView(context).apply { text = msg; textSize = 13f; setTextColor(Color.parseColor("#7A7F89")); setPadding(0, dp(8), 0, 0); gravity = Gravity.CENTER })
        addView(box)
    })

    private fun section(text: String) = content.addView(TextView(this).apply { this.text = text; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#202124")); setPadding(dp(2), dp(12), 0, dp(6)) })

    private fun primaryButton(text: String, action: () -> Unit) = MaterialButton(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 12f
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        setPadding(dp(10), dp(4), dp(10), dp(4))
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#4F6EF7"))
        setOnClickListener { action() }
    }

    private fun primaryWideButton(text: String, action: () -> Unit) = primaryButton(text, action).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(8), 0, dp(8)) }
    }

    private fun card() = MaterialCardView(this).apply {
        radius = dp(4).toFloat()
        cardElevation = 0f
        setCardBackgroundColor(Color.WHITE)
        strokeColor = Color.parseColor("#ECEFF3")
        strokeWidth = dp(1)
        useCompatPadding = true
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(6)) }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}

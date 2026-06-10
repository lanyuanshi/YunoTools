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
    private enum class Screen { LIST, DETAIL, SEARCH, HISTORY, DOWNLOADS }

    private data class WatchSource(
        val name: String,
        val baseUrl: String,
        val libraryUrl: String,
        val guarded: Boolean = false
    )

    private data class AnimeItem(
        val title: String,
        val status: String,
        val cover: String,
        val detailUrl: String,
        val source: String = "AnimeTranslation"
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

    private val sources = listOf(
        WatchSource("AnimeTranslation", "https://www.animetranslation.com", "https://www.animetranslation.com/library"),
        WatchSource("风车动漫", "https://www.fsdm02.com", "https://www.fsdm02.com/", guarded = true)
    )
    private var selectedSource = sources.first()
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
        allAnime = loadCachedAnime(selectedSource.name)
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
            Screen.HISTORY -> renderRecords("历史记录", "history")
            Screen.DOWNLOADS -> renderRecords("下载记录", "downloads")
        }
    }

    private fun renderList() {
        topBar("首页", showSearch = true, showBack = false)
        sourceChooser()
        if (selectedSource.guarded) {
            emptyCard("需要安全验证", "${selectedSource.name} 当前返回滑块验证，应用无法直接解析列表。可切回 AnimeTranslation 播放源。")
            content.addView(primaryWideButton("在浏览器打开验证页") { openUrl(selectedSource.baseUrl) })
            return
        }
        if (loading && allAnime.isEmpty()) {
            emptyCard("加载中", "正在加载${selectedSource.name}番剧列表。")
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
        section("记录")
        content.addView(settingCard("历史记录", "查看播放历史，点击后回到应用内播放页") { screen = Screen.HISTORY; render() })
        content.addView(settingCard("下载记录", "查看已加入下载记录的剧集地址") { screen = Screen.DOWNLOADS; render() })
        section("设置")
        content.addView(settingCard("刷新番剧列表", "重新从当前播放源加载番剧列表") { loadLibrary() })
        content.addView(settingCard("清空历史记录", "删除本地播放历史") { clearRecords("history") })
        content.addView(settingCard("清空下载记录", "删除本地下载记录") { clearRecords("downloads") })
    }

    private fun renderRecords(title: String, key: String) {
        topBar(title, showSearch = false, showBack = true)
        val records = loadRecordList(key)
        if (records.isEmpty()) {
            emptyCard("暂无$title", if (key == "history") "播放过的剧集会显示在这里。" else "加入下载记录的剧集会显示在这里。")
            return
        }
        records.forEach { record ->
            val actionText = if (key == "history") "播放" else "打开"
            content.addView(recordCard(record, actionText) {
                if (key == "history") openRecordInApp(record) else openUrl(record.optString("url"))
            })
        }
    }

    private fun loadLibrary() {
        if (loading) return
        if (selectedSource.guarded) {
            allAnime = emptyList()
            render()
            Toast.makeText(this, "${selectedSource.name} 需要滑块验证，暂不能自动加载", Toast.LENGTH_LONG).show()
            return
        }
        loading = true
        render()
        Thread {
            val result = runCatching {
                val html = fetch(selectedSource.libraryUrl)
                if (html.contains("/_guard/") || html.contains("slider_html") || html.contains("安全验证")) error("需要安全验证")
                parseLibrary(html)
            }
            runOnUiThread {
                loading = false
                result.onSuccess { list ->
                    allAnime = list
                    saveCachedAnime(selectedSource.name, list)
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
                val source = sources.firstOrNull { it.name == item.source } ?: selectedSource
                val html = fetch(absUrl(item.detailUrl, source))
                if (html.contains("/_guard/") || html.contains("slider_html") || html.contains("安全验证")) error("需要安全验证")
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
            .header("Referer", selectedSource.baseUrl)
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
            if (title.isBlank() || href.isBlank()) null else AnimeItem(title, status, cover, href, selectedSource.name)
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
        val sourceLabels = Regex("""<div class=\"source-tab[^\"]*\"[^>]*data-source=\"([^\"]+)\"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .associate { it.groupValues[1] to htmlText(it.groupValues[2]).ifBlank { it.groupValues[1] } }
        val linkRegex = Regex("""<a[^>]*episode-link[^>]*>.*?</a>""", RegexOption.DOT_MATCHES_ALL)
        val hrefRegex = Regex("""href=\"([^\"]*)\"""")
        val dataUrlRegex = Regex("""data-url=\"([^\"]*)\"""")
        val dataSourceRegex = Regex("""data-source=\"([^\"]*)\"""")
        val found = linkRegex.findAll(html).mapNotNull { match ->
            val tag = match.value
            val raw = htmlAttr(dataUrlRegex.find(tag)?.groupValues?.getOrNull(1).orEmpty()).ifBlank {
                htmlAttr(hrefRegex.find(tag)?.groupValues?.getOrNull(1).orEmpty())
            }
            val name = htmlText(tag.replace(Regex("""<[^>]+>"""), " ")).ifBlank { "播放" }
            val sourceKey = dataSourceRegex.find(tag)?.groupValues?.getOrNull(1).orEmpty()
            val sourceName = sourceLabels[sourceKey] ?: sourceKey.ifBlank { "播放源" }
            val play = when {
                raw.startsWith("http") -> raw
                prefix.isNotBlank() && raw.isNotBlank() && !raw.startsWith("/") -> prefix + raw
                raw.isNotBlank() -> absUrl(raw)
                else -> ""
            }
            if (play.isBlank()) null else EpisodeItem(name, play, sourceName)
        }.distinctBy { it.playUrl }.toList()
        if (found.isNotEmpty()) return found
        val urlRegex = Regex("""data-url=\"([^\"]+)\"[^>]*>(.*?)</""", RegexOption.DOT_MATCHES_ALL)
        return urlRegex.findAll(html).map { m ->
            val url = htmlAttr(m.groupValues[1])
            val name = htmlText(m.groupValues[2]).ifBlank { "播放" }
            EpisodeItem(name, if (prefix.isNotBlank()) prefix + url else url, "播放源")
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
            currentEpisode = null
            screen = Screen.DETAIL
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
                text = "播放"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#4F6EF7"))
                setPadding(0, dp(5), 0, 0)
                setOnClickListener { playEpisode(detail.item, ep) }
            })
            row.addView(cell)
        }
        scroll.addView(row)
        content.addView(scroll)
    }

    private fun playEpisode(item: AnimeItem, ep: EpisodeItem) {
        tab = Tab.BANGUMI
        screen = Screen.DETAIL
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

    private fun sourceChooser() {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(8)) }
        sources.forEach { source ->
            row.addView(TextView(this).apply {
                text = source.name
                textSize = 13f
                typeface = if (source.name == selectedSource.name) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(Color.parseColor(if (source.name == selectedSource.name) "#FFFFFF" else "#4D5562"))
                setBackgroundColor(Color.parseColor(if (source.name == selectedSource.name) "#4F6EF7" else "#EEF1F6"))
                setPadding(dp(12), dp(7), dp(12), dp(7))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, dp(8), 0) }
                setOnClickListener {
                    if (selectedSource.name != source.name) {
                        selectedSource = source
                        allAnime = loadCachedAnime(source.name)
                        selectedAnime = null
                        selectedDetail = null
                        currentEpisode = null
                        player?.stop()
                        screen = Screen.LIST
                        render()
                        if (allAnime.isEmpty()) loadLibrary()
                    }
                }
            })
        }
        scroll.addView(row)
        content.addView(scroll)
    }

    private fun topBar(title: String, showSearch: Boolean, showBack: Boolean) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(10)) }
        row.addView(TextView(this).apply {
            text = if (showBack) "‹" else ""
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#202124"))
            setOnClickListener {
                if (showBack) {
                    when (screen) {
                        Screen.HISTORY, Screen.DOWNLOADS -> { tab = Tab.MINE; screen = Screen.LIST }
                        else -> { tab = Tab.BANGUMI; screen = Screen.LIST }
                    }
                    render()
                }
            }
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
        val url = ep?.playUrl.orEmpty()
        addRecord("history", item, sub, url, ep?.name.orEmpty())
    }

    private fun addDownload(item: AnimeItem, ep: EpisodeItem) {
        addRecord("downloads", item, "${ep.source} · ${ep.name}", ep.playUrl, ep.name)
        Toast.makeText(this, "已加入下载记录", Toast.LENGTH_SHORT).show()
    }

    private fun addRecord(key: String, item: AnimeItem, sub: String, url: String, episodeName: String) {
        val arr = JSONArray()
        arr.put(JSONObject().apply {
            put("title", item.title)
            put("sub", sub)
            put("url", url)
            put("episode", episodeName)
            put("status", item.status)
            put("cover", item.cover)
            put("detailUrl", item.detailUrl)
            put("source", item.source)
        })
        val unique = if (url.isNotBlank()) url else item.detailUrl
        loadRecordList(key).filterNot { (if (it.optString("url").isNotBlank()) it.optString("url") else it.optString("detailUrl")) == unique }.take(29).forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun loadRecordList(key: String): List<JSONObject> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
        buildList { for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { add(it) } }
    }.getOrDefault(emptyList())

    private fun openRecordInApp(record: JSONObject) {
        val detailUrl = record.optString("detailUrl")
        val playUrl = record.optString("url")
        val item = AnimeItem(
            record.optString("title"),
            record.optString("status"),
            record.optString("cover"),
            detailUrl,
            record.optString("source").ifBlank { selectedSource.name }
        )
        selectedSource = sources.firstOrNull { it.name == item.source } ?: selectedSource
        tab = Tab.BANGUMI
        selectedAnime = item
        selectedDetail = null
        currentEpisode = null
        screen = Screen.DETAIL
        if (detailUrl.isNotBlank()) {
            render()
        } else if (playUrl.isNotBlank()) {
            val ep = EpisodeItem(record.optString("episode").ifBlank { "历史播放" }, playUrl, record.optString("sub").ifBlank { "播放源" })
            currentEpisode = ep
            selectedDetail = AnimeDetail(item, "历史记录", record.optString("sub"), "", listOf(ep))
            ensurePlayer().apply {
                setMediaItem(MediaItem.fromUri(ep.playUrl))
                prepare()
                playWhenReady = true
            }
            render()
        }
    }

    private fun clearRecords(key: String) {
        prefs.edit().putString(key, "[]").apply()
        Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun saveCachedAnime(sourceName: String, list: List<AnimeItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(JSONObject().apply {
                put("title", item.title)
                put("status", item.status)
                put("cover", item.cover)
                put("detailUrl", item.detailUrl)
                put("source", item.source)
            })
        }
        prefs.edit().putString("library_cache_$sourceName", arr.toString()).apply()
    }

    private fun loadCachedAnime(sourceName: String): List<AnimeItem> = runCatching {
        val arr = JSONArray(prefs.getString("library_cache_$sourceName", "[]") ?: "[]")
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                add(AnimeItem(obj.optString("title"), obj.optString("status"), obj.optString("cover"), obj.optString("detailUrl"), obj.optString("source").ifBlank { sourceName }))
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

    private fun absUrl(url: String, source: WatchSource = selectedSource): String = when {
        url.startsWith("http") -> url
        url.startsWith("/") -> source.baseUrl + url
        else -> "${source.baseUrl}/$url"
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

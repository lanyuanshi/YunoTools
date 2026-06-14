package com.yuno.tools.ui.tools

import android.app.AlertDialog
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.Html
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
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
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.util.ThemeApplier
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class BangumiWatchActivity : AppCompatActivity() {
    private enum class Tab { BANGUMI, MINE }
    private enum class Screen { LIST, DETAIL, SEARCH, HISTORY, DOWNLOADS }

    private enum class SourceKind { AGE, XIFAN, NG3 }

    private data class Category(val name: String, val id: String)

    private data class WatchSource(
        val name: String,
        val baseUrl: String,
        val libraryUrl: String,
        val kind: SourceKind,
        val note: String = "",
        val guarded: Boolean = false,
        val categories: List<Category> = emptyList()
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
        val source: String,
        val referer: String = ""
    )

    private data class AnimeDetail(
        val item: AnimeItem,
        val intro: String,
        val meta: String,
        val playerPrefix: String,
        val episodes: List<EpisodeItem>
    )

    private val sources = listOf(
        WatchSource("AGE动漫", "https://m.agedm.io", "https://api.agedm.io/v2/home-list", SourceKind.AGE, note = "AGE 分类自适应", categories = listOf(Category("更新", "update"), Category("TV", "tv"), Category("剧场", "movie"), Category("OVA", "ova"))),
        WatchSource("稀饭动漫", "https://anime.xifanacg.com", "https://anime.xifanacg.com/", SourceKind.XIFAN, categories = listOf(Category("动漫", "1"), Category("国产", "2"), Category("电影", "3"), Category("剧集", "4"))),
        WatchSource("瓜子影视", "https://ng3.app", "https://ng3.app/home", SourceKind.NG3, note = "接口列表 + 页面播放解析", categories = listOf(Category("推荐", "home"), Category("电影", "1"), Category("电视剧", "2"), Category("综艺", "3"), Category("动漫", "4")))
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
    private var currentPlayDirect = true
    private var currentPlayHeaders: Map<String, String> = emptyMap()
    private var playerHeaders: Map<String, String> = emptyMap()
    private var currentPlayError: String = ""
    private var currentPlayLoading = false
    private var currentOriginalEpisode: EpisodeItem? = null
    private val failedPlaybackSourceUrls = mutableSetOf<String>()
    private var autoFallbackInProgress = false
    private var detailBackTab = Tab.BANGUMI
    private var detailBackScreen = Screen.LIST
    private var page = 1
    private var hasMore = true
    private var searchResults: List<AnimeItem> = emptyList()
    private var searchLoading = false
    private var searchPage = 1
    private var searchHasMore = false
    private var selectedCategory = ""
    private var isFullscreenPlayer = false
    private var touchStartY = 0f
    private var bottomLoadArmed = false
    private var bottomLoadTriggered = false
    private var pendingFullscreenRestore = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeApplier.apply(this)
        allAnime = emptyList()
        if (savedInstanceState != null) {
            pendingFullscreenRestore = false
            isFullscreenPlayer = false
            savedInstanceState.getString("detailUrl")?.takeIf { it.isNotBlank() }?.let { url ->
                selectedAnime = AnimeItem(savedInstanceState.getString("title").orEmpty(), savedInstanceState.getString("status").orEmpty(), savedInstanceState.getString("cover").orEmpty(), url, savedInstanceState.getString("source").orEmpty().ifBlank { selectedSource.name })
                screen = Screen.DETAIL
            }
        }
        buildUi()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateBack()
            }
        })
        if (screen == Screen.DETAIL && selectedAnime != null) {
            loadDetail(selectedAnime!!)
        } else {
            loadLibrary(force = true)
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("fullscreen", isFullscreenPlayer)
        selectedAnime?.let {
            outState.putString("detailUrl", it.detailUrl)
            outState.putString("title", it.title)
            outState.putString("status", it.status)
            outState.putString("cover", it.cover)
            outState.putString("source", it.source)
        }
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
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setOnTouchListener { _, event ->
                val child = getChildAt(0)
                val maxScroll = if (child == null) 0 else (child.measuredHeight - height).coerceAtLeast(0)
                val atBottom = child != null && scrollY >= maxScroll - dp(4)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartY = event.rawY
                        bottomLoadArmed = atBottom
                        bottomLoadTriggered = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // 录屏里的效果：滑到最底时先停住，不立即加载；到底后继续往上拉一段，才加载下一页。
                        if (atBottom && !bottomLoadArmed) {
                            bottomLoadArmed = true
                            touchStartY = event.rawY
                        }
                        val pulledPastBottom = bottomLoadArmed && atBottom && touchStartY - event.rawY > dp(36)
                        if (!bottomLoadTriggered && pulledPastBottom && screen == Screen.LIST && tab == Tab.BANGUMI && hasMore && !loading) {
                            bottomLoadTriggered = true
                            loadMoreLibrary()
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        bottomLoadArmed = false
                        bottomLoadTriggered = false
                    }
                }
                false
            }
        }
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
        content.setPadding(if (isFullscreenPlayer) 0 else dp(10), if (isFullscreenPlayer) 0 else dp(8), if (isFullscreenPlayer) 0 else dp(10), if (isFullscreenPlayer) 0 else dp(82))
        updateBottomBarVisibility()
        when (screen) {
            Screen.HISTORY -> renderRecords("历史记录", "history")
            Screen.DOWNLOADS -> renderRecords("下载记录", "downloads")
            else -> {
                if (tab == Tab.MINE) {
                    renderMine()
                    return
                }
                when (screen) {
                    Screen.LIST -> renderList()
                    Screen.SEARCH -> renderSearch()
                    Screen.DETAIL -> renderDetail()
                    else -> renderList()
                }
            }
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
        if (hasMore) {
            content.addView(TextView(this).apply {
                text = if (loading) "正在加载更多…" else "继续上滑加载更多"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#8B9099"))
                setPadding(0, dp(12), 0, dp(8))
            })
        }
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
                    searchResults = emptyList()
                    searchPage = 1
                    searchHasMore = false
                    searchRemote(searchText, reset = true)
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
                searchResults = emptyList()
                searchPage = 1
                searchHasMore = false
                searchRemote(searchText, reset = true)
                render()
            })
        }
        content.addView(box, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(10)) })
        if (searchText.isBlank()) {
            emptyCard("输入关键词", "输入关键词后会请求当前源的真实搜索接口，不再只筛选已加载列表。")
        } else {
            if (searchResults.isEmpty() && !searchLoading) searchRemote(searchText, reset = true)
            if (searchLoading && searchResults.isEmpty()) {
                emptyCard("搜索中", "正在搜索“$searchText”。")
            } else if (searchResults.isEmpty()) {
                emptyCard("没有结果", "没有找到“$searchText”。")
            } else {
                section("搜索结果")
                animeGrid(searchResults)
                if (searchHasMore) content.addView(TextView(this).apply { text = if (searchLoading) "正在搜索更多…" else "继续下滑会自动加载更多结果"; textSize = 12f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#8B9099")); setPadding(0, dp(12), 0, dp(8)) })
            }
        }
    }

    private fun renderDetail() {
        val item = selectedAnime ?: returnToList()
        val detail = selectedDetail
        if (detail == null) {
            content.addView(playerBox(AnimeDetail(item, "", item.status, "", emptyList())))
            if (!loading) loadDetail(item)
            return
        }
        content.addView(playerBox(detail))
        if (isFullscreenPlayer) return
        content.addView(videoTabs())
        content.addView(detailHeader(detail.item, detail.meta, detail.intro))
        content.addView(watchActions(detail))
        section("选集")
        if (detail.episodes.isNotEmpty()) episodeGrid(detail)
    }

    private fun renderMine() {
        topBar("我的", showSearch = false, showBack = false)
        section("记录")
        content.addView(settingCard("历史记录", "查看播放历史，点击后回到应用内播放页") { screen = Screen.HISTORY; render() })
        content.addView(settingCard("下载记录", "查看已加入下载记录的剧集地址") { screen = Screen.DOWNLOADS; render() })
        section("设置")
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
        records.forEachIndexed { index, record ->
            val actionText = if (key == "history") "播放" else "打开"
            content.addView(recordCard(record, actionText) {
                if (key == "history") openRecordInApp(record) else openUrl(record.optString("url"))
            }.also { card ->
                (card as? MaterialCardView)?.setOnLongClickListener {
                    removeRecordAt(key, index)
                    true
                }
            })
        }
        content.addView(TextView(this).apply {
            text = "长按记录可删除单条"
            textSize = 12f
            setTextColor(Color.parseColor("#7A7F89"))
            setPadding(dp(4), dp(8), 0, 0)
        })
    }

    private fun removeRecordAt(key: String, index: Int) {
        val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
        if (index in 0 until arr.length()) {
            val newArr = JSONArray()
            for (i in 0 until arr.length()) if (i != index) newArr.put(arr.opt(i))
            prefs.edit().putString(key, newArr.toString()).apply()
            render()
        }
    }

    private fun loadLibrary(force: Boolean = false) {
        if (loading) return
        if (force) {
            prefs.edit().remove("library_cache_${selectedSource.name}").apply()
            page = 1
            hasMore = true
            allAnime = emptyList()
        }
        if (selectedSource.guarded) {
            allAnime = emptyList()
            render()
            Toast.makeText(this, "${selectedSource.name} 需要滑块验证，暂不能自动加载", Toast.LENGTH_LONG).show()
            return
        }
        loading = true
        render()
        Thread {
            val result = runCatching { fetchLibraryPage(1) }
            runOnUiThread {
                loading = false
                result.onSuccess { list ->
                    page = 1
                    allAnime = list
                    hasMore = list.isNotEmpty()
                    saveCachedAnime(selectedSource.name, list)
                    Toast.makeText(this, "已加载 ${list.size} 部番剧", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(this, "加载失败：${err.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
                render()
            }
        }.start()
    }

    private fun loadMoreLibrary() {
        if (loading || !hasMore) return
        loading = true
        val next = page + 1
        Thread {
            val result = runCatching { fetchLibraryPage(next) }
            runOnUiThread {
                loading = false
                result.onSuccess { list ->
                    if (list.isEmpty()) {
                        hasMore = false
                        Toast.makeText(this, "没有更多了", Toast.LENGTH_SHORT).show()
                    } else {
                        page = next
                        allAnime = (allAnime + list).distinctBy { it.detailUrl }
                        hasMore = true
                        saveCachedAnime(selectedSource.name, allAnime)
                        Toast.makeText(this, "已加载到 ${allAnime.size} 部", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { err -> Toast.makeText(this, "加载更多失败：${err.message ?: "未知错误"}", Toast.LENGTH_LONG).show() }
                render()
            }
        }.start()
    }

    private fun fetchLibraryPage(targetPage: Int): List<AnimeItem> {
        val category = selectedCategory.ifBlank { selectedSource.categories.firstOrNull()?.id.orEmpty() }
        val url = when (selectedSource.kind) {
            SourceKind.AGE -> if (category == "update" || category.isBlank()) "https://api.agedm.io/v2/update?page=$targetPage&size=30" else "https://api.agedm.io/v2/catalog?label=$category&page=$targetPage&size=30"
            SourceKind.XIFAN -> {
                val id = category.ifBlank { "1" }
                if (targetPage <= 1) "https://anime.xifanacg.com/type/$id.html" else "https://anime.xifanacg.com/type/$id-$targetPage.html"
            }
            SourceKind.NG3 -> "ng3-api-home:$targetPage:${category.ifBlank { "home" }}"
        }
        val body = fetch(url)
        if (body.contains("/_guard/") || body.contains("slider_html") || body.contains("安全验证")) error("需要安全验证")
        return when (selectedSource.kind) {
            SourceKind.AGE -> parseAgePagedList(body)
            SourceKind.XIFAN -> parseXifanLibrary(body)
            SourceKind.NG3 -> parseNg3Library(body)
        }
    }

    private fun searchRemote(keyword: String, reset: Boolean) {
        if (searchLoading || keyword.isBlank()) return
        if (reset) {
            searchPage = 1
            searchResults = emptyList()
            searchHasMore = false
        }
        searchLoading = true
        val targetPage = if (reset) 1 else searchPage + 1
        Thread {
            val result = runCatching { fetchSearchPage(keyword, targetPage) }
            runOnUiThread {
                searchLoading = false
                result.onSuccess { list ->
                    searchPage = targetPage
                    searchResults = if (reset) list else (searchResults + list).distinctBy { it.detailUrl }
                    searchHasMore = list.isNotEmpty() && selectedSource.kind == SourceKind.AGE
                }.onFailure { err -> Toast.makeText(this, "搜索失败：${err.message ?: "未知错误"}", Toast.LENGTH_LONG).show() }
                render()
            }
        }.start()
    }

    private fun fetchSearchPage(keyword: String, targetPage: Int): List<AnimeItem> {
        val q = URLEncoder.encode(keyword, "UTF-8")
        return when (selectedSource.kind) {
            SourceKind.AGE -> parseAgeSearch(fetch("https://api.agedm.io/v2/search?query=$q&page=$targetPage"))
            SourceKind.XIFAN -> parseXifanSuggest(fetch("https://anime.xifanacg.com/index.php/ajax/suggest?mid=1&wd=$q"))
            SourceKind.NG3 -> searchNg3(keyword)
        }
    }

    private fun loadDetail(item: AnimeItem) {
        if (loading) return
        loading = true
        Thread {
            val result = runCatching {
                val source = sources.firstOrNull { it.name == item.source } ?: selectedSource
                val detailUrl = when (source.kind) {
                    SourceKind.AGE -> "https://api.agedm.io/v2/detail/" + item.detailUrl.substringAfterLast("/")
                    SourceKind.XIFAN -> absUrl(item.detailUrl, source)
                    SourceKind.NG3 -> absUrl(item.detailUrl, source)
                }
                val body = fetch(detailUrl)
                if (body.contains("/_guard/") || body.contains("slider_html") || body.contains("安全验证")) error("需要安全验证")
                parseDetail(item, body)
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
        if (url.startsWith("ng3-api-home:")) {
            val parts = url.split(":")
            val pageNo = parts.getOrNull(1)?.toIntOrNull() ?: 1
            val category = parts.getOrNull(2).orEmpty()
            val payload = JSONObject().put("page", pageNo).put("pageSize", 12)
            if (category.isNotBlank() && category != "home") payload.put("type_id", category).put("t_id", category).put("cid", category)
            return if (category.isBlank() || category == "home") ng3ApiPost("/Pc/Resource/IndexShow/ShowOnes", payload) else runCatching { ng3ApiPost("/Pc/Resource/ModuleInfo/ShowOnes", payload) }.getOrElse { ng3ApiPost("/Pc/Resource/IndexShow/ShowOnes", payload) }
        }
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

    private fun parseAgeLibrary(jsonText: String): List<AnimeItem> {
        val root = JSONObject(jsonText)
        val lists = listOf(
            root.optJSONArray("latest"),
            root.optJSONArray("recommend")
        ).filterNotNull()
        val fromArray = lists.flatMap { arr ->
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val title = obj.optString("Title")
                    val status = obj.optString("NewTitle")
                    val cover = obj.optString("PicSmall")
                    val href = obj.optString("Href")
                    if (title.isNotBlank() && href.isNotBlank()) add(AnimeItem(title, status, cover, href, selectedSource.name))
                }
            }
        }.distinctBy { it.detailUrl }
        if (fromArray.isNotEmpty()) return fromArray
        return emptyList()
    }

    private fun parseAgePagedList(jsonText: String): List<AnimeItem> {
        val root = JSONObject(jsonText)
        val arr = root.optJSONArray("videos") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("AID").ifBlank { obj.optString("id") }
                val title = obj.optString("Title").ifBlank { obj.optString("name") }
                val status = obj.optString("NewTitle").ifBlank { obj.optString("uptodate") }
                val cover = obj.optString("PicSmall").ifBlank { obj.optString("cover") }
                val href = obj.optString("Href").ifBlank { if (id.isNotBlank()) "/detail/$id" else "" }
                if (title.isNotBlank() && href.isNotBlank()) add(AnimeItem(title, status, cover, href, selectedSource.name))
            }
        }.distinctBy { it.detailUrl }
    }

    private fun parseAgeSearch(jsonText: String): List<AnimeItem> {
        val root = JSONObject(jsonText)
        val data = root.optJSONObject("data") ?: return parseAgePagedList(jsonText)
        val arr = data.optJSONArray("videos") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id").ifBlank { obj.optString("AID") }
                val title = obj.optString("name").ifBlank { obj.optString("Title") }
                val status = obj.optString("uptodate").ifBlank { obj.optString("NewTitle") }
                val cover = obj.optString("cover").ifBlank { obj.optString("PicSmall") }
                if (id.isNotBlank() && title.isNotBlank()) add(AnimeItem(title, status, cover, "/detail/$id", selectedSource.name))
            }
        }.distinctBy { it.detailUrl }
    }

    private fun parseXifanSuggest(jsonText: String): List<AnimeItem> {
        val root = JSONObject(jsonText)
        val arr = root.optJSONArray("list") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val title = obj.optString("name")
                val cover = obj.optString("pic")
                if (id.isNotBlank() && title.isNotBlank()) add(AnimeItem(title, "搜索结果", cover, "/bangumi/$id.html", selectedSource.name))
            }
        }.distinctBy { it.detailUrl }
    }

    private fun parseNg3Library(text: String): List<AnimeItem> {
        if (text.trimStart().startsWith("{")) return parseNg3ApiItems(text)
        val linkRe = Regex("""<a[^>]+href=["']([^"']*(?:videoDetail|detail|video|play|vod|movie)[^"']*)["'][^>]*>([\s\S]{0,800}?)</a>""", RegexOption.IGNORE_CASE)
        return linkRe.findAll(text).mapNotNull { m ->
            val block = m.groupValues[2]
            val title = htmlText(Regex("""(?:alt|title)=["']([^"']{2,80})["']""", RegexOption.IGNORE_CASE).find(m.value)?.groupValues?.getOrNull(1).orEmpty()).ifBlank { htmlText(block).take(40) }
            val cover = htmlAttr(Regex("""(?:data-src|src)=["']([^"']+\.(?:jpg|jpeg|png|webp)[^"']*)["']""", RegexOption.IGNORE_CASE).find(block)?.groupValues?.getOrNull(1).orEmpty())
            val href = htmlAttr(m.groupValues[1])
            if (title.length in 2..80 && href.isNotBlank()) AnimeItem(title, "瓜子影视", absUrl(cover, sources.first { it.kind == SourceKind.NG3 }), absUrl(href, sources.first { it.kind == SourceKind.NG3 }), selectedSource.name) else null
        }.distinctBy { it.detailUrl }.take(60).toList()
    }

    private fun parseNg3ApiItems(jsonText: String): List<AnimeItem> {
        val root = JSONObject(jsonText)
        val data = root.optJSONObject("data") ?: root
        val arr = data.optJSONArray("list") ?: data.optJSONArray("rows") ?: root.optJSONArray("list") ?: JSONArray()
        val out = mutableListOf<AnimeItem>()
        for (i in 0 until arr.length()) {
            val section = arr.optJSONObject(i) ?: continue
            val nested = section.optJSONArray("list") ?: section.optJSONArray("vod_list") ?: section.optJSONArray("data")
            if (nested != null) {
                for (j in 0 until nested.length()) ng3ItemFromJson(nested.optJSONObject(j), section.optString("nav_name")).let { if (it != null) out.add(it) }
            } else {
                ng3ItemFromJson(section, section.optString("nav_name")).let { if (it != null) out.add(it) }
            }
        }
        return out.distinctBy { it.detailUrl }.take(80)
    }

    private fun ng3ItemFromJson(o: JSONObject?, fallbackStatus: String): AnimeItem? {
        if (o == null) return null
        val id = o.optString("vod_id").ifBlank { o.optString("id") }
        val title = o.optString("vod_name").ifBlank { o.optString("title") }.ifBlank { o.optString("name") }
        val cover = o.optString("vod_picthumb").ifBlank { o.optString("vod_pic") }.ifBlank { o.optString("slide_pic") }.ifBlank { o.optString("c_pic") }
        val status = o.optString("vod_remarks").ifBlank { o.optString("vod_total") }.ifBlank { o.optString("vod_type_name") }.ifBlank { fallbackStatus }.ifBlank { "瓜子影视" }
        if (id.isBlank() || title.isBlank()) return null
        return AnimeItem(title.take(100), status.take(40), cover, "https://ng3.app/videoDetail/$id", selectedSource.name)
    }

    private fun searchNg3(keyword: String): List<AnimeItem> {
        val q = keyword.trim()
        if (q.isBlank()) return emptyList()
        val candidates = listOf(
            "/Pc/Resource/VodSearch/ShowOnes" to JSONObject().put("wd", q).put("page", 1).put("pageSize", 30),
            "/Pc/Resource/VodSearch/ShowOnes" to JSONObject().put("keyword", q).put("page", 1).put("pageSize", 30),
            "/Pc/Resource/IndexShow/ShowOnes" to JSONObject().put("page", 1).put("pageSize", 30).put("keyword", q)
        )
        return candidates.firstNotNullOfOrNull { (path, payload) ->
            runCatching { parseNg3ApiItems(ng3ApiPost(path, payload)).filter { it.title.contains(q, true) || it.status.contains(q, true) }.takeIf { it.isNotEmpty() } }.getOrNull()
        }.orEmpty()
    }

    private fun ng3ApiPost(path: String, payload: JSONObject): String {
        val bodyJson = JSONObject().put("params", aesNg3(payload.toString())).toString()
        val hosts = listOf("https://haiwaiapi.1fc8ab0.com", "https://hyperf.718fd9f.com")
        var lastError: Throwable? = null
        for (host in hosts) {
            runCatching {
                val req = Request.Builder()
                    .url(host + path)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                    .header("Referer", "https://ng3.app/home")
                    .header("Origin", "https://ng3.app")
                    .header("Accept", "application/json, text/plain, */*")
                    .post(bodyJson.toRequestBody("application/json;charset=UTF-8".toMediaType()))
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val raw = resp.body?.string().orEmpty().ifBlank { error("empty body") }
                    val root = JSONObject(raw)
                    val enc = root.optString("data")
                    if (enc.isNotBlank()) return aesNg3Decrypt(enc)
                    if (root.optInt("code") == 400) error(root.optString("msg", "请求格式有误"))
                    return raw
                }
            }.onFailure { lastError = it }
        }
        error(lastError?.message ?: "瓜子影视接口失败")
    }

    private fun aesNg3(plain: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec("181cc88340ae5b2b".toByteArray(), "AES"), IvParameterSpec("4423d1e2773476ce".toByteArray()))
        return cipher.doFinal(plain.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun aesNg3Decrypt(hex: String): String {
        val clean = hex.trim().lowercase(Locale.US)
        val bytes = ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec("181cc88340ae5b2b".toByteArray(), "AES"), IvParameterSpec("4423d1e2773476ce".toByteArray()))
        return String(cipher.doFinal(bytes))
    }

    private fun parseXifanLibrary(html: String): List<AnimeItem> {
        val cardRegex = Regex("""<a[^>]+href="(/bangumi/[^"]+\.html)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val imgTagRegex = Regex("""<img[^>]*>""", RegexOption.DOT_MATCHES_ALL)
        val dataSrcRegex = Regex("""data-src="([^"]+)""")
        val srcRegex = Regex("""src="([^"]+)""")
        val altRegex = Regex("""alt="([^"]*)""")
        val titleFromLinkRegex = Regex("""title="([^"]+)""")
        val statusRegex = Regex("""<span[^>]+class="public-list-prb[^"]*"[^>]*>.*?<i[^>]*>(.*?)</i>.*?</span>""", RegexOption.DOT_MATCHES_ALL)
        return cardRegex.findAll(html).mapNotNull { match ->
            val href = htmlAttr(match.groupValues[1])
            val block = match.groupValues[2]
            val imgTag = imgTagRegex.find(block)?.value.orEmpty()
            val title = htmlText(altRegex.find(imgTag)?.groupValues?.getOrNull(1).orEmpty()).ifBlank { htmlText(titleFromLinkRegex.find(match.value)?.groupValues?.getOrNull(1).orEmpty()) }
            val cover = htmlAttr(dataSrcRegex.find(imgTag)?.groupValues?.getOrNull(1).orEmpty()).ifBlank { htmlAttr(srcRegex.find(imgTag)?.groupValues?.getOrNull(1).orEmpty()) }
            val status = htmlText(statusRegex.find(block)?.groupValues?.getOrNull(1).orEmpty())
            if (title.isBlank() || href.isBlank()) null else AnimeItem(title, status, cover, href, selectedSource.name)
        }.distinctBy { it.detailUrl }.toList()
    }

    private fun parseDetail(item: AnimeItem, body: String): AnimeDetail {
        return when (selectedSource.kind) {
            SourceKind.AGE -> parseAgeDetail(item, body)
            SourceKind.XIFAN -> parseXifanDetail(item, body)
            SourceKind.NG3 -> parseNg3Detail(item, body)
        }
    }

    private fun parseAgeDetail(item: AnimeItem, body: String): AnimeDetail {
        val root = JSONObject(body)
        val video = root.optJSONObject("video") ?: error("AGE 详情解析失败")
        val intro = listOf(
            video.optString("plot"),
            video.optJSONArray("plot_arr")?.let { arr ->
                buildList { for (i in 0 until arr.length()) add(arr.optString(i)) }.joinToString(" / ")
            }
        ).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "暂无简介" }
        val meta = listOf(video.optString("type"), video.optString("company"), video.optString("writer")).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { item.status }
        val playerJx = root.optJSONObject("player_jx") ?: JSONObject()
        val vipKeys = root.optString("player_vip").split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val labels = root.optJSONObject("player_label_arr") ?: JSONObject()
        val playlists = video.optJSONObject("playlists") ?: JSONObject()
        val candidates = mutableListOf<Pair<String, JSONArray>>()
        for (key in playlists.keys()) playlists.optJSONArray(key)?.let { candidates.add(key to it) }
        val preferred = candidates.sortedWith(
            compareByDescending<Pair<String, JSONArray>> { !vipKeys.contains(it.first) }
                .thenByDescending { it.second.length() }
        ).firstOrNull()
        val episodes = mutableListOf<EpisodeItem>()
        if (preferred != null) {
            val key = preferred.first
            val arr = preferred.second
            val prefix = if (vipKeys.contains(key)) playerJx.optString("vip") else playerJx.optString("zj")
            val label = labels.optString(key).ifBlank { key }
            for (i in 0 until arr.length()) {
                val row = arr.optJSONArray(i) ?: continue
                val epName = row.optString(0).ifBlank { "第${i + 1}集" }
                val token = row.optString(1)
                if (token.isNotBlank() && prefix.isNotBlank()) episodes.add(EpisodeItem(epName, prefix + token, label))
            }
        }
        return AnimeDetail(item.copy(title = video.optString("name").ifBlank { item.title }), intro, meta, "", sortEpisodes(episodes.distinctBy { it.playUrl }))
    }

    private fun parseNg3Detail(item: AnimeItem, body: String): AnimeDetail {
        val normalized = normalizeNg3Html(body)
        val directUrls = Regex("""https?://[^"'\s]+?(?:\.m3u8|\.mp4)(?:[^"'\s]*)""", RegexOption.IGNORE_CASE)
            .findAll(normalized)
            .map { it.value }
            .filterNot { it.contains("poster", true) || it.contains(".jpg", true) }
            .distinct()
            .toList()
        val eps = Regex("""<a[^>]+href=["']([^"']*(?:play|watch)[^"']*)["'][^>]*>([\s\S]{0,120}?)</a>""", RegexOption.IGNORE_CASE).findAll(body).mapIndexedNotNull { i, m ->
            val name = htmlText(m.groupValues[2]).ifBlank { "第${i + 1}集" }
            val href = absUrl(htmlAttr(m.groupValues[1]), sources.first { it.kind == SourceKind.NG3 })
            if (href.isNotBlank()) EpisodeItem(name.take(50), href, "瓜子影视", item.detailUrl) else null
        }.toMutableList()
        directUrls.forEachIndexed { i, u -> eps.add(EpisodeItem(if (directUrls.size == 1) "播放" else "第${i + 1}集", u, "瓜子影视", item.detailUrl)) }
        return AnimeDetail(item, "瓜子影视 · 接口列表 + 页面播放解析", item.status.ifBlank { "瓜子影视" }, "", eps.distinctBy { it.playUrl }.ifEmpty { listOf(EpisodeItem("播放", item.detailUrl, "瓜子影视", item.detailUrl)) })
    }

    private fun parseXifanDetail(item: AnimeItem, body: String): AnimeDetail {
        val intro = htmlText(Regex("""<div[^>]*class="videos-description"[^>]*>.*?<span[^>]*class="videos-description-title"[^>]*>.*?</span>(.*?)(?:<div class="more-info"|</div>\s*</div>)""", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.getOrNull(1).orEmpty()).ifBlank { "暂无简介" }
        val title = htmlText(Regex("""<h2[^>]*class="g-box-title[^"]*"[^>]*>(.*?)</h2>""", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.getOrNull(1).orEmpty()).ifBlank { item.title }
        val meta = listOf(item.status).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "稀饭动漫" }
        val lineLabels = Regex("""<a[^>]*class="swiper-slide"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(body)
            .map { htmlText(it.groupValues[1]).replace(Regex("""\s+"""), " " ).trim().ifBlank { "线路" } }
            .toList()
        val lineBoxRegex = Regex("""<div[^>]*class="anthology-list-box[^"]*"[^>]*>(.*?)(?=<div[^>]*class="anthology-list-box|</div>\s*</div>\s*</div>)""", RegexOption.DOT_MATCHES_ALL)
        val watchRegex = Regex("""<a[^>]*href="(/watch/[^"]+\.html)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val detailUrl = item.detailUrl
        val grouped = lineBoxRegex.findAll(body).mapIndexedNotNull { index, box ->
            val lineName = lineLabels.getOrNull(index)?.replace(Regex("""\s*\d+\s*$"""), "")?.ifBlank { "线路${index + 1}" } ?: "线路${index + 1}"
            val episodes = watchRegex.findAll(box.groupValues[1]).mapNotNull { m ->
                val url = m.groupValues[1]
                val text = htmlText(m.groupValues[2])
                val name = text.ifBlank { episodeNameFromUrl(url) }
                if (url.isBlank() || name.isBlank() || text.contains("播放预告")) null else EpisodeItem(name, absUrl(url, sources.first { it.kind == SourceKind.XIFAN }), lineName, detailUrl)
            }.distinctBy { it.playUrl }.toList().let { sortEpisodes(it) }
            episodes.takeIf { it.isNotEmpty() }
        }.toList().flatten()
        val fallback = if (grouped.isEmpty()) watchRegex.findAll(body).mapNotNull { m ->
            val url = m.groupValues[1]
            val text = htmlText(m.groupValues[2])
            val name = text.ifBlank { episodeNameFromUrl(url) }
            if (url.isBlank() || name.isBlank() || text.contains("播放预告")) null else EpisodeItem(name, absUrl(url, sources.first { it.kind == SourceKind.XIFAN }), "线路1", item.detailUrl)
        }.distinctBy { it.playUrl }.toList().let { sortEpisodes(it) } else grouped
        return AnimeDetail(item.copy(title = title), intro, meta, "", fallback)
    }

    private fun parseEpisodes(html: String, prefix: String): List<EpisodeItem> {
        val sourceLabels = Regex("""<div class="source-tab[^"]*"[^>]*data-source="([^"]+)"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .associate { it.groupValues[1] to htmlText(it.groupValues[2]).ifBlank { it.groupValues[1] } }
        val linkRegex = Regex("""<a[^>]*episode-link[^>]*>.*?</a>""", RegexOption.DOT_MATCHES_ALL)
        val hrefRegex = Regex("""href="([^"]*)""" )
        val dataUrlRegex = Regex("""data-url="([^"]*)""" )
        val dataSourceRegex = Regex("""data-source="([^"]*)""" )
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
        val m3u8Fallback = Regex("""href="([^"]+\\.m3u8[^"]*)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .mapIndexedNotNull { index, m ->
                val url = htmlAttr(m.groupValues[1])
                val label = htmlText(m.groupValues[2]).ifBlank { "第${(index + 1).toString().padStart(2, '0')}集" }
                if (url.isBlank()) null else EpisodeItem(label, if (url.startsWith("http")) url else absUrl(url), "播放源")
            }
            .toList()
        if (found.isNotEmpty() || m3u8Fallback.isNotEmpty()) return (found + m3u8Fallback).distinctBy { it.playUrl }
        val urlRegex = Regex("""data-url="([^"]+)"[^>]*>(.*?)</""", RegexOption.DOT_MATCHES_ALL)
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
            detailBackTab = tab
            detailBackScreen = screen
            selectedAnime = item
            selectedDetail = null
            currentEpisode = null
            currentPlayDirect = true
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

    private fun detailHeader(item: AnimeItem, meta: String?, intro: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(8), dp(4), dp(6))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = item.title
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#202124"))
                maxLines = 1
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply { text = "简介 ›"; textSize = 16f; setTextColor(Color.parseColor("#6D737D")) })
        })
        addView(TextView(context).apply {
            text = (meta ?: item.status).ifBlank { item.source }
            textSize = 15f
            setTextColor(Color.parseColor("#6D737D"))
            setPadding(0, dp(8), 0, 0)
            maxLines = 2
        })
    }
    private fun playerBox(detail: AnimeDetail) = FrameLayout(this).apply {
        tag = "playerRoot"
        setBackgroundColor(Color.BLACK)
        val height = if (isFullscreenPlayer) resources.displayMetrics.heightPixels else dp(240)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height).apply {
            if (!isFullscreenPlayer) setMargins(-dp(10), -dp(8), -dp(10), dp(12))
        }
        val active = currentEpisode
        val preparedPlayer = if (active != null && currentPlayDirect) {
            runCatching { ensurePlayer() }
                .onFailure { e ->
                    currentPlayLoading = false
                    currentPlayError = "播放器初始化失败：${e.message ?: "未知错误"}"
                    Log.e("BangumiWatch", currentPlayError, e)
                }
                .getOrNull()
        } else null

        if (preparedPlayer != null) {
            addView(PlayerView(context).apply {
                player = preparedPlayer
                useController = true
                controllerAutoShow = true
                controllerHideOnTouch = true
                controllerShowTimeoutMs = 2500
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setFullscreenButtonClickListener { toggleFullscreenPlayer() }
                setBackgroundColor(Color.BLACK)
            }, FrameLayout.LayoutParams(-1, -1))
        } else {
            val poster = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.BLACK)
                alpha = 0.78f
            }
            Glide.with(this@BangumiWatchActivity).load(detail.item.cover).centerCrop().into(poster)
            addView(poster, FrameLayout.LayoutParams(-1, -1))
            addView(TextView(context).apply {
                text = if (currentPlayLoading) "正在解析…" else "点击播放"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#55000000"))
            }, FrameLayout.LayoutParams(-1, -1))
            setOnClickListener {
                detail.episodes.firstOrNull()?.let { playEpisode(detail.item, it) }
                    ?: Toast.makeText(this@BangumiWatchActivity, "没有可播放剧集", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun videoTabs() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(6), dp(4), dp(12))
        addView(TextView(context).apply { text = "视频"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#202124")) })
        addView(TextView(context).apply { text = "讨论"; textSize = 20f; setTextColor(Color.parseColor("#9AA0A6")); setPadding(dp(34), 0, 0, 0) })
    }

    private fun watchActions(detail: AnimeDetail) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(0, dp(12), 0, dp(18))
        val active = currentEpisode ?: detail.episodes.firstOrNull()
        listOf(
            Triple("⇄", "换源") { if (detail.episodes.isNotEmpty()) playEpisode(detail.item, detail.episodes.first()) },
            Triple("⇩", "下载") { active?.let { addDownload(detail.item, it) } ?: Toast.makeText(this@BangumiWatchActivity, "请先选择剧集", Toast.LENGTH_SHORT).show() },
            Triple("☆", "收藏") { Toast.makeText(this@BangumiWatchActivity, "已收藏", Toast.LENGTH_SHORT).show() }
        ).forEach { (icon, label, action) ->
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setOnClickListener { action() }
                addView(TextView(context).apply { text = icon; textSize = 34f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#202124")) })
                addView(TextView(context).apply { text = label; textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.parseColor("#9AA0A6")) })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun episodeGrid(detail: AnimeDetail) {
        val groups = detail.episodes.groupBy { it.source.ifBlank { "播放源" } }
        groups.forEach { (sourceName, episodes) ->
            content.addView(TextView(this).apply {
                text = "$sourceName（${episodes.size} 集）"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#4D5562"))
                setPadding(dp(2), dp(8), 0, dp(6))
            })
            val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            episodes.take(120).forEach { ep ->
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(8), dp(7), dp(8), dp(7))
                    val isActive = isCurrentEpisode(ep)
                    setBackgroundColor(Color.parseColor(if (isActive) "#4F6EF7" else "#EEF1F6"))
                    layoutParams = LinearLayout.LayoutParams(dp(104), LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, dp(8), dp(8)) }
                    setOnClickListener { playEpisode(detail.item, ep) }
                }
                cell.addView(TextView(this).apply {
                    text = ep.name
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor(if (isCurrentEpisode(ep)) "#FFFFFF" else "#202124"))
                    maxLines = 1
                })
                cell.addView(TextView(this).apply {
                    val isActive = isCurrentEpisode(ep)
                    text = if (isActive) { if (currentPlayLoading) "解析中" else "播放中" } else "播放"
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor(if (isActive) "#FFFFFF" else "#4F6EF7"))
                    setPadding(0, dp(5), 0, 0)
                    setOnClickListener { playEpisode(detail.item, ep) }
                })
                row.addView(cell)
            }
            scroll.addView(row)
            content.addView(scroll)
        }
    }

    private fun playEpisode(item: AnimeItem, ep: EpisodeItem) {
        startPlayEpisode(item, ep, resetFailures = true)
    }

    private fun startPlayEpisode(item: AnimeItem, ep: EpisodeItem, resetFailures: Boolean) {
        tab = Tab.BANGUMI
        screen = Screen.DETAIL
        selectedAnime = item
        currentOriginalEpisode = ep
        if (resetFailures) {
            failedPlaybackSourceUrls.clear()
            autoFallbackInProgress = false
        }
        Toast.makeText(this, "正在解析 ${ep.name}", Toast.LENGTH_SHORT).show()
        Thread {
            val result = runCatching { resolvePlayableUrlWithFallback(ep) }
            runOnUiThread {
                runCatching {
                    result.onSuccess { resolved ->
                        handleResolvedEpisode(item, ep, resolved)
                    }.onFailure { err ->
                        showEpisodeError("播放解析失败：${err.message ?: "未知错误"}", err)
                    }
                }.onFailure { err ->
                    showEpisodeError("播放界面异常：${err.message ?: "未知错误"}", err)
                }
            }
        }.start()
    }

    private fun handleResolvedEpisode(item: AnimeItem, ep: EpisodeItem, resolved: ResolvedPlay) {
        currentPlayError = ""
        currentPlayLoading = resolved.direct
        val playable = ep.copy(playUrl = resolved.url, source = resolved.source)
        currentOriginalEpisode = ep
        currentEpisode = playable
        currentPlayDirect = resolved.direct
        currentPlayHeaders = resolved.headers
        runCatching { addHistory(item, playable) }
            .onFailure { Log.w("BangumiWatch", "addHistory failed", it) }
        runCatching { renderDetailOnly() }
            .onFailure { Log.e("BangumiWatch", "render before play failed", it) }
        if (resolved.direct) {
            runCatching {
                ensurePlayer(resolved.headers).apply {
                    setMediaItem(MediaItem.fromUri(playable.playUrl))
                    prepare()
                    playWhenReady = true
                }
            }.onSuccess {
                autoFallbackInProgress = false
                Toast.makeText(this, "正在播放 ${ep.name}", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                currentPlayLoading = false
                showEpisodeError("播放器启动失败：${err.message ?: "未知错误"}", err)
            }
        } else {
            runCatching { player?.pause() }
            currentPlayLoading = false
            showEpisodeError("当前线路未提取到可内置播放地址，请换一条线路/播放源", null)
        }
    }

    private fun showEpisodeError(message: String, err: Throwable?) {
        err?.let { Log.e("BangumiWatch", message, it) } ?: Log.w("BangumiWatch", message)
        tab = Tab.BANGUMI
        screen = Screen.DETAIL
        currentPlayLoading = false
        currentPlayError = ""
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        runCatching { renderDetailOnly() }
            .onFailure { Log.e("BangumiWatch", "render error state failed", it) }
    }


    private fun tryFallbackAfterPlayerError(error: PlaybackException) {
        if (autoFallbackInProgress) return
        val item = selectedAnime ?: return
        val original = currentOriginalEpisode ?: return
        val detail = selectedDetail ?: return
        failedPlaybackSourceUrls.add(original.playUrl)
        val next = detail.episodes.firstOrNull {
            it.name == original.name && it.playUrl != original.playUrl && !failedPlaybackSourceUrls.contains(it.playUrl)
        } ?: return
        autoFallbackInProgress = true
        Toast.makeText(this, "当前线路播放失败，自动切换下一线路", Toast.LENGTH_SHORT).show()
        startPlayEpisode(item, next, resetFailures = false)
    }

    private data class ResolvedPlay(val url: String, val direct: Boolean, val source: String, val headers: Map<String, String> = emptyMap(), val webFallback: Boolean = false)

    private fun resolvePlayableUrlWithFallback(ep: EpisodeItem): ResolvedPlay {
        val candidates = mutableListOf(ep)
        val sameEpisode = selectedDetail?.episodes.orEmpty().filter {
            it.name == ep.name && it.playUrl != ep.playUrl && !failedPlaybackSourceUrls.contains(it.playUrl)
        }
        candidates.addAll(sameEpisode)
        var lastError: Throwable? = null
        for (candidate in candidates.distinctBy { it.playUrl }) {
            val resolved = runCatching { resolvePlayableUrl(candidate) }
                .onFailure { lastError = it }
                .getOrNull() ?: continue
            if (!resolved.direct) return resolved
            if (!candidate.playUrl.contains("anime.xifanacg.com/watch/") || isPlayableReachable(resolved)) {
                return if (candidate.playUrl != ep.playUrl) resolved.copy(source = "${resolved.source} · 自动换源") else resolved
            }
            lastError = IllegalStateException("${resolved.source} 源不可访问")
        }
        throw lastError ?: IllegalStateException("没有可用播放源")
    }

    private fun isPlayableReachable(resolved: ResolvedPlay): Boolean {
        return runCatching {
            val builder = Request.Builder()
                .url(resolved.url)
                .get()
                .header("Range", "bytes=0-1023")
            resolved.headers.forEach { (k, v) -> builder.header(k, v) }
            http.newCall(builder.build()).execute().use { response ->
                response.isSuccessful || response.code == 206
            }
        }.getOrDefault(false)
    }

    private fun resolvePlayableUrl(ep: EpisodeItem): ResolvedPlay {
        val url = ep.playUrl
        if (url.endsWith(".m3u8", true) || url.contains(".m3u8?", true) || url.endsWith(".mp4", true) || url.contains(".mp4?", true)) {
            return ResolvedPlay(url, true, ep.source)
        }
        return when {
            url.contains("anime.xifanacg.com/watch/") -> resolveXifanWatch(url, ep)
            url.contains("jx.wuzhoupai.com") || url.contains("agedm") -> resolveAgePlayerPage(url, ep.source)
            url.contains("ng3.app") -> resolveNg3Watch(url, ep)
            else -> ResolvedPlay(url, false, ep.source)
        }
    }

    private fun resolveNg3Watch(url: String, ep: EpisodeItem): ResolvedPlay {
        val html = fetch(url)
        val direct = Regex("""https?://[^"']+?(?:\.m3u8|\.mp4)[^"']*""", RegexOption.IGNORE_CASE).findAll(html).map { it.value.replace("\\/", "/") }.firstOrNull()
        val headers = mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36", "Referer" to url, "Origin" to "https://ng3.app")
        return if (!direct.isNullOrBlank()) ResolvedPlay(direct, true, ep.source, headers) else ResolvedPlay(url, false, ep.source)
    }

    private fun resolveXifanWatch(url: String, ep: EpisodeItem): ResolvedPlay {
        val html = fetch(url)
        val block = Regex("""var\s+player_aaaa\s*=\s*(\{.*?\})</script>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.getOrNull(1).orEmpty()
        val play = JSONObject(block).optString("url").replace("\\/", "/")
        if (play.startsWith("http")) {
            val direct = play.contains(".mp4", true) || play.contains(".m3u8", true)
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36",
                "Referer" to url,
                "Origin" to "https://anime.xifanacg.com"
            )
            return ResolvedPlay(play, direct, if (ep.source.isNotBlank()) ep.source else "稀饭直链", headers)
        }
        error("稀饭播放地址为空")
    }

    private fun resolveAgePlayerPage(url: String, source: String): ResolvedPlay {
        val html = fetch(url)
        val direct = Regex("""https?://[^"']+?(?:\.m3u8|\.mp4)[^"']*""", RegexOption.IGNORE_CASE).findAll(html)
            .map { it.value }
            .firstOrNull { !it.contains("adposter", true) }
        if (!direct.isNullOrBlank()) return ResolvedPlay(direct, true, source)
        return ResolvedPlay(url, false, source, webFallback = false)
    }

    private fun renderDetailOnly() {
        if (screen != Screen.DETAIL) return
        content.removeAllViews()
        renderDetail()
    }

    private fun ensurePlayer(headers: Map<String, String> = currentPlayHeaders): ExoPlayer {
        val existing = player
        if (existing != null && headers == playerHeaders) return existing
        existing?.release()
        currentPlayHeaders = headers
        playerHeaders = headers
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setUserAgent(headers["User-Agent"] ?: "Mozilla/5.0")
            .setAllowCrossProtocolRedirects(true)
        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
            .also { exo ->
                exo.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        if (currentPlayLoading) {
                            currentPlayLoading = false
                            runOnUiThread { runCatching { renderDetailOnly() } }
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY && currentPlayLoading) {
                            currentPlayLoading = false
                            runOnUiThread { runCatching { renderDetailOnly() } }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        currentPlayLoading = false
                        currentPlayError = "播放器错误：${error.errorCodeName} ${error.message ?: ""}"
                        runOnUiThread {
                            Toast.makeText(this@BangumiWatchActivity, currentPlayError, Toast.LENGTH_LONG).show()
                            runCatching { renderDetailOnly() }
                            runCatching { tryFallbackAfterPlayerError(error) }
                        }
                    }
                })
                player = exo
            }
    }

    private fun sourceChooser() {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(8)) }
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sources.forEach { source ->
            chips.addView(TextView(this).apply {
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
                        allAnime = emptyList()
                        selectedAnime = null
                        selectedDetail = null
                        currentEpisode = null
                        currentPlayDirect = true
                        page = 1
                        hasMore = true
                        searchText = ""
                        searchResults = emptyList()
                        searchPage = 1
                        searchHasMore = false
                        selectedCategory = source.categories.firstOrNull()?.id.orEmpty()
                        player?.stop()
                        screen = Screen.LIST
                        render()
                        loadLibrary(force = true)
                    }
                }
            })
        }
        row.addView(chips)
        if (selectedSource.categories.isNotEmpty()) {
            val catRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
            val activeCat = selectedCategory.ifBlank { selectedSource.categories.first().id }
            selectedSource.categories.forEach { cat ->
                catRow.addView(TextView(this).apply {
                    text = cat.name
                    textSize = 12f
                    setTextColor(Color.parseColor(if (cat.id == activeCat) "#FFFFFF" else "#5F6673"))
                    setBackgroundColor(Color.parseColor(if (cat.id == activeCat) "#202124" else "#F0F2F6"))
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, dp(8), 0) }
                    setOnClickListener {
                        if (selectedCategory != cat.id) {
                            selectedCategory = cat.id
                            allAnime = emptyList()
                            page = 1
                            hasMore = true
                            render()
                            loadLibrary(force = true)
                        }
                    }
                })
            }
            row.addView(catRow)
        }
        if (selectedSource.note.isNotBlank()) row.addView(TextView(this).apply { text = selectedSource.note; textSize = 11f; setTextColor(Color.parseColor("#7A7F89")); setPadding(0, dp(6), 0, 0) })
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
if (showBack) navigateBack()
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

    private fun updateBottomBarVisibility() {
        val shouldShow = !isFullscreenPlayer && screen == Screen.LIST
        val hasBar = root.childCount > 1
        if (shouldShow && !hasBar) root.addView(bottomBar())
        if (!shouldShow && hasBar) root.removeViewAt(1)
    }

    private fun isCurrentEpisode(ep: EpisodeItem): Boolean {
        val original = currentOriginalEpisode
        if (original != null && original.playUrl.isNotBlank() && ep.playUrl.isNotBlank() && original.playUrl == ep.playUrl) return true
        val active = currentEpisode
        return active != null && active.playUrl.isNotBlank() && ep.playUrl.isNotBlank() && active.playUrl == ep.playUrl
    }

    private fun normalizeNg3Html(text: String): String = text
        .replace("""\/""", "/")
        .replace("\\u002F", "/")
        .replace("\\u002f", "/")

    private fun toggleFullscreenPlayer() {
        if (isFullscreenPlayer) exitFullscreenPlayer() else enterFullscreenPlayer()
    }

    private fun enterFullscreenPlayer() {
        val detail = selectedDetail ?: return
        if (currentEpisode == null) {
            detail.episodes.firstOrNull()?.let { playEpisode(detail.item, it) }
            return
        }
        isFullscreenPlayer = true
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val insetsController = window.insetsController
        if (insetsController != null) {
            insetsController.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
            insetsController.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        renderDetailOnly()
    }

    private fun exitFullscreenPlayer() {
        isFullscreenPlayer = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val insetsController = window.insetsController
        insetsController?.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
        renderDetailOnly()
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
            addView(TextView(context).apply { text = listOf(obj.optString("source"), obj.optString("episodeSource"), obj.optString("episode"), obj.optString("time")).filter { it.isNotBlank() }.joinToString(" · "); textSize = 12f; setTextColor(Color.parseColor("#7A7F89")); maxLines = 2 })
            val sub = obj.optString("sub")
            if (sub.isNotBlank()) addView(TextView(context).apply { text = sub; textSize = 12f; setTextColor(Color.parseColor("#4D5562")); maxLines = 2 })
        })
        row.addView(primaryButton(actionText, action))
        addView(row)
    }

    private fun addHistory(item: AnimeItem, ep: EpisodeItem?) {
        val sub = ep?.let { "${it.source} · ${it.name}" } ?: item.status
        val url = ep?.playUrl.orEmpty()
        addRecord("history", item, sub, url, ep?.name.orEmpty(), ep?.source.orEmpty())
    }

    private fun addDownload(item: AnimeItem, ep: EpisodeItem) {
        addRecord("downloads", item, "${ep.source} · ${ep.name}", ep.playUrl, ep.name, ep.source)
        Toast.makeText(this, "已加入下载记录", Toast.LENGTH_SHORT).show()
    }

    private fun addRecord(key: String, item: AnimeItem, sub: String, url: String, episodeName: String, sourceName: String) {
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
            put("episodeSource", sourceName)
            put("time", nowText())
        })
        val unique = if (url.isNotBlank()) url else item.detailUrl
        loadRecordList(key).filterNot { (if (it.optString("url").isNotBlank()) it.optString("url") else it.optString("detailUrl")) == unique }.take(49).forEach { arr.put(it) }
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
        detailBackTab = Tab.MINE
        detailBackScreen = Screen.HISTORY
        tab = Tab.BANGUMI
        selectedAnime = item
        selectedDetail = null
        currentEpisode = null
        currentPlayDirect = true
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

    private fun nowText(): String = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())

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

    private fun sortEpisodes(list: List<EpisodeItem>): List<EpisodeItem> = list.sortedWith(compareBy<EpisodeItem> { episodeNumber(it.name).let { n -> if (n <= 0) Int.MAX_VALUE else n } }.thenBy { it.name })

    private fun episodeNumber(text: String): Int {
        val n = Regex("""(\d+)""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (n != null) return n
        val cn = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9, '十' to 10)
        val m = Regex("第([一二三四五六七八九十]+)[集话話]").find(text)?.groupValues?.getOrNull(1) ?: return Int.MAX_VALUE
        if (m == "十") return 10
        if (m.startsWith("十")) return 10 + (cn[m.getOrNull(1)] ?: 0)
        if (m.endsWith("十")) return (cn[m.first()] ?: 0) * 10
        if (m.contains("十")) return (cn[m.first()] ?: 0) * 10 + (cn[m.last()] ?: 0)
        return cn[m.first()] ?: Int.MAX_VALUE
    }

    private fun episodeNameFromUrl(url: String): String {
        val n = Regex("""/watch/[^/]+/[^/]+/(\d+)\.html""").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return if (n != null) "第${n}集" else "播放"
    }

    private fun navigateBack() {
        if (isFullscreenPlayer) {
            exitFullscreenPlayer()
            return
        }
        when (screen) {
            Screen.DETAIL -> {
                player?.pause()
                currentEpisode = null
                currentPlayDirect = true
                selectedDetail = null
                selectedAnime = null
                tab = detailBackTab
                screen = detailBackScreen
                render()
            }
            Screen.SEARCH -> { tab = Tab.BANGUMI; screen = Screen.LIST; render() }
            Screen.HISTORY, Screen.DOWNLOADS -> { tab = Tab.MINE; screen = Screen.LIST; render() }
            Screen.LIST -> {
                if (tab == Tab.MINE) { tab = Tab.BANGUMI; render() } else finish()
            }
        }
    }

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
        url.startsWith("//") -> "https:$url"
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

package com.yuno.tools.ui.tools

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.util.ThemeApplier
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BangumiWatchActivity : AppCompatActivity() {
    private enum class Page { HOME, SEARCH, SOURCE, SETTINGS }
    private data class LoadedSource(
        val key: String,
        val label: String,
        val url: String,
        val versionName: String,
        val repo: String
    )

    private val prefs by lazy { getSharedPreferences("yuno_bangumi_watch", Context.MODE_PRIVATE) }
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(16, TimeUnit.SECONDS)
            .build()
    }
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var repoInput: EditText
    private var page = Page.HOME
    private var keyword = ""
    private var loadingRepo: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeApplier.apply(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FAFAFA"))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(84))
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
        when (page) {
            Page.HOME -> renderHome()
            Page.SEARCH -> renderSearch()
            Page.SOURCE -> renderSource()
            Page.SETTINGS -> renderSettings()
        }
    }

    private fun renderHome() {
        topBar("看番", "扩展源", false)
        searchBox()
        val repos = loadRepos()
        val loaded = loadSources()
        if (repos.isEmpty()) {
            sourceEmptyState()
            return
        }
        repoTabs(repos)
        if (loaded.isEmpty()) {
            emptyCard("暂无内容", "源仓库还没有加载出可用源。去“源”页点击加载后，加载出的源会显示在这里。")
            return
        }
        section("已加载源")
        loaded.forEach { source -> content.addView(sourceCard(source)) }
        emptyCard("暂无影视内容", "加载器当前只加载源仓库元数据，不执行第三方脚本、不解析播放内容。后续接入具体源组件后，这里显示番剧列表。")
    }

    private fun renderSearch() {
        topBar("搜索", "源搜索", false)
        searchBox()
        val repos = loadRepos()
        val loaded = loadSources()
        if (repos.isEmpty()) {
            sourceEmptyState()
            return
        }
        if (loaded.isEmpty()) {
            emptyCard("暂无可搜索源", "先到“源”页加载源仓库，加载出源后再搜索。")
            return
        }
        if (keyword.isBlank()) {
            emptyCard("输入关键词", "输入番剧名后，会交给已加载源搜索。")
        } else {
            emptyCard("暂无搜索结果", "关键词：$keyword。当前加载器只加载源元数据，暂未执行源搜索组件。")
        }
    }

    private fun renderSource() {
        topBar("源", "仓库与加载器", false)
        val repos = loadRepos()
        if (repos.isEmpty()) {
            sourceEmptyState()
            return
        }
        section("源仓库")
        repos.forEachIndexed { index, repo ->
            val state = when {
                loadingRepo == repo -> "加载中"
                loadSources(repo).isNotEmpty() -> "已加载 ${loadSources(repo).size} 个源"
                else -> "未加载"
            }
            content.addView(repoCard("源仓库 ${index + 1}", repo, state, "加载") { loadRepo(repo) })
        }
        val loaded = loadSources()
        section("已加载源")
        if (loaded.isEmpty()) {
            emptyCard("暂无已加载源", "点击上面的“加载”会请求仓库 URL，解析 JSONL/JSON 源列表并保存到本地。")
        } else {
            loaded.forEach { source -> content.addView(sourceCard(source)) }
        }
    }

    private fun renderSettings() {
        topBar("设置", "源仓库", false)
        section("添加源仓库")
        content.addView(card().apply {
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }
            repoInput = EditText(context).apply {
                hint = "输入源仓库 URL"
                setSingleLine(true)
                inputType = InputType.TYPE_TEXT_VARIATION_URI
                imeOptions = EditorInfo.IME_ACTION_DONE
                setTextColor(Color.parseColor("#202124"))
                setHintTextColor(Color.parseColor("#8B9099"))
            }
            box.addView(repoInput)
            val actions = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(8), 0, 0)
            }
            actions.addView(smallButton("保存并加载") { saveRepoFromInput(true) })
            actions.addView(space(dp(8), 1))
            actions.addView(grayButton("保存") { saveRepoFromInput(false) })
            box.addView(actions)
            addView(box)
        })
        section("已添加仓库")
        val repos = loadRepos()
        if (repos.isEmpty()) {
            emptyCard("暂无源仓库", "添加源仓库后，源页可以加载仓库里的扩展源列表；没有源时不展示任何作品。")
        } else {
            repos.forEachIndexed { index, repo ->
                content.addView(repoCard("源仓库 ${index + 1}", repo, "已添加", "删除") { removeRepo(repo) })
            }
        }
    }

    private fun loadRepo(repo: String) {
        if (loadingRepo != null) return
        loadingRepo = repo
        render()
        Thread {
            val result = runCatching {
                val request = Request.Builder()
                    .url(repo)
                    .header("User-Agent", "YunoTools/1.0")
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) error("empty body")
                    parseRepo(repo, body)
                }
            }
            runOnUiThread {
                loadingRepo = null
                result.onSuccess { list ->
                    saveLoadedSources(repo, list)
                    Toast.makeText(this, "加载完成：${list.size} 个源", Toast.LENGTH_SHORT).show()
                    render()
                }.onFailure { err ->
                    saveLoadedSources(repo, emptyList())
                    Toast.makeText(this, "加载失败：${err.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                    render()
                }
            }
        }.start()
    }

    private fun parseRepo(repo: String, body: String): List<LoadedSource> {
        val lines = body.lineSequence().map { it.trim() }.filter { it.startsWith("{") }.toList()
        val fromJsonl = lines.mapNotNull { parseSourceObject(repo, runCatching { JSONObject(it) }.getOrNull()) }
        if (fromJsonl.isNotEmpty()) return fromJsonl.distinctBy { it.key.ifBlank { it.url } }

        val trimmed = body.trim()
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            return buildList {
                for (i in 0 until arr.length()) {
                    parseSourceObject(repo, arr.optJSONObject(i))?.let { add(it) }
                }
            }.distinctBy { it.key.ifBlank { it.url } }
        }
        if (trimmed.startsWith("{")) {
            val obj = JSONObject(trimmed)
            val arrays = listOf("sources", "extensions", "items", "data", "list", "repo")
            for (name in arrays) {
                val arr = obj.optJSONArray(name) ?: continue
                val parsed = buildList {
                    for (i in 0 until arr.length()) {
                        parseSourceObject(repo, arr.optJSONObject(i))?.let { add(it) }
                    }
                }
                if (parsed.isNotEmpty()) return parsed.distinctBy { it.key.ifBlank { it.url } }
            }
            parseSourceObject(repo, obj)?.let { return listOf(it) }
        }
        return emptyList()
    }

    private fun parseSourceObject(repo: String, obj: JSONObject?): LoadedSource? {
        obj ?: return null
        val url = firstString(obj, "url", "downloadUrl", "download_url", "file", "src", "path")
        val key = firstString(obj, "key", "id", "pkg", "package", "name").ifBlank { url.substringAfterLast('/').ifBlank { url } }
        val label = firstString(obj, "label", "title", "name", "sourceName").ifBlank { key }
        val version = firstString(obj, "versionName", "version", "ver").ifBlank {
            val code = obj.optInt("versionCode", -1)
            if (code > -1) code.toString() else ""
        }
        if (key.isBlank() && url.isBlank() && label.isBlank()) return null
        return LoadedSource(key = key, label = label, url = url, versionName = version, repo = repo)
    }

    private fun firstString(obj: JSONObject, vararg names: String): String {
        for (name in names) {
            val value = obj.optString(name, "").trim()
            if (value.isNotBlank() && value != "null") return value
        }
        return ""
    }

    private fun topBar(title: String, sub: String, back: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        row.addView(TextView(this).apply {
            text = "‹"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#202124"))
            setOnClickListener { if (back) { page = Page.HOME; render() } else finish() }
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = title
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#202124"))
                maxLines = 1
            })
            addView(TextView(context).apply {
                text = sub
                textSize = 12f
                setTextColor(Color.parseColor("#7A7F89"))
                maxLines = 1
            })
        })
        row.addView(TextView(this).apply {
            text = if (loadSources().isEmpty()) "无源" else "${loadSources().size} 源"
            textSize = 12f
            setTextColor(Color.parseColor("#4F6EF7"))
            setPadding(dp(10), dp(6), dp(10), dp(6))
        })
        content.addView(row)
    }

    private fun searchBox() {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.parseColor("#F0F2F6"))
        }
        searchInput = EditText(this).apply {
            hint = "搜索番剧"
            setText(keyword)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(Color.parseColor("#202124"))
            setHintTextColor(Color.parseColor("#8B9099"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    doSearch()
                    true
                } else false
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        wrap.addView(searchInput)
        wrap.addView(smallButton("搜索") { doSearch() })
        content.addView(wrap, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(8)) })
    }

    private fun repoTabs(repos: List<String>) {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        repos.forEachIndexed { index, repo ->
            val count = loadSources(repo).size
            row.addView(TextView(this).apply {
                text = if (count > 0) "源仓库 ${index + 1} · $count" else "源仓库 ${index + 1}"
                textSize = 13f
                typeface = if (index == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(Color.parseColor(if (index == 0) "#FFFFFF" else "#2B2F36"))
                setBackgroundColor(Color.parseColor(if (index == 0) "#4F6EF7" else "#EEF1F6"))
                setPadding(dp(12), dp(7), dp(12), dp(7))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(4), dp(8), dp(8)) }
            })
        }
        scroll.addView(row)
        content.addView(scroll)
    }

    private fun sourceEmptyState() {
        emptyCard("暂无源", "还没有添加源仓库。")
        content.addView(smallWideButton("去设置添加源仓库") {
            page = Page.SETTINGS
            render()
        })
    }

    private fun repoCard(title: String, url: String, state: String, actionText: String, action: () -> Unit) = card().apply {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        row.addView(TextView(context).apply {
            text = "仓"
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4F6EF7"))
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        })
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = "$title · $state"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#202124"))
            })
            addView(TextView(context).apply {
                text = url
                textSize = 12f
                setTextColor(Color.parseColor("#7A7F89"))
                maxLines = 2
            })
        })
        row.addView(grayButton(actionText, action))
        addView(row)
    }

    private fun sourceCard(source: LoadedSource) = card().apply {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        row.addView(TextView(context).apply {
            text = source.label.take(1).ifBlank { "源" }
            textSize = 16f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#607D8B"))
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        })
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = source.label
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#202124"))
                maxLines = 1
            })
            addView(TextView(context).apply {
                text = listOf(source.key, source.versionName.ifBlank { null }).filterNotNull().joinToString(" · ")
                textSize = 12f
                setTextColor(Color.parseColor("#7A7F89"))
                maxLines = 1
            })
            if (source.url.isNotBlank()) {
                addView(TextView(context).apply {
                    text = source.url
                    textSize = 11f
                    setTextColor(Color.parseColor("#9AA0AA"))
                    maxLines = 1
                })
            }
        })
        addView(row)
    }

    private fun bottomBar(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.WHITE)
        listOf(Page.HOME to "首页", Page.SEARCH to "搜索", Page.SOURCE to "源", Page.SETTINGS to "设置").forEach { (target, label) ->
            addView(TextView(context).apply {
                text = label
                textSize = 13f
                gravity = Gravity.CENTER
                typeface = if (page == target) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(Color.parseColor(if (page == target) "#4F6EF7" else "#6D737D"))
                setOnClickListener { page = target; render() }
            }, LinearLayout.LayoutParams(0, dp(56), 1f))
        }
    }

    private fun doSearch() {
        keyword = searchInput.text?.toString()?.trim().orEmpty()
        if (keyword.isBlank()) {
            Toast.makeText(this, "请输入番剧名", Toast.LENGTH_SHORT).show()
            return
        }
        page = Page.SEARCH
        render()
    }

    private fun saveRepoFromInput(loadNow: Boolean) {
        val url = repoInput.text?.toString()?.trim().orEmpty()
        if (url.isBlank()) {
            Toast.makeText(this, "请输入源仓库 URL", Toast.LENGTH_SHORT).show()
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(this, "源仓库需要以 http:// 或 https:// 开头", Toast.LENGTH_SHORT).show()
            return
        }
        val next = (loadRepos() + url).distinct()
        prefs.edit().putString("repos", JSONArray(next).toString()).apply()
        repoInput.setText("")
        Toast.makeText(this, if (loadNow) "已保存，开始加载" else "已保存源仓库", Toast.LENGTH_SHORT).show()
        if (loadNow) {
            page = Page.SOURCE
            loadRepo(url)
        } else {
            render()
        }
    }

    private fun removeRepo(url: String) {
        val next = loadRepos().filterNot { it == url }
        val sources = loadSources().filterNot { it.repo == url }
        prefs.edit()
            .putString("repos", JSONArray(next).toString())
            .putString("loaded_sources", sourcesToJson(sources).toString())
            .apply()
        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun loadRepos(): List<String> = jsonArray("repos")

    private fun loadSources(repo: String? = null): List<LoadedSource> = runCatching {
        val arr = JSONArray(prefs.getString("loaded_sources", "[]") ?: "[]")
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val item = LoadedSource(
                    key = obj.optString("key"),
                    label = obj.optString("label"),
                    url = obj.optString("url"),
                    versionName = obj.optString("versionName"),
                    repo = obj.optString("repo")
                )
                if (repo == null || item.repo == repo) add(item)
            }
        }
    }.getOrDefault(emptyList())

    private fun saveLoadedSources(repo: String, list: List<LoadedSource>) {
        val merged = loadSources().filterNot { it.repo == repo } + list
        prefs.edit().putString("loaded_sources", sourcesToJson(merged).toString()).apply()
    }

    private fun sourcesToJson(list: List<LoadedSource>): JSONArray {
        val arr = JSONArray()
        list.forEach { source ->
            arr.put(JSONObject().apply {
                put("key", source.key)
                put("label", source.label)
                put("url", source.url)
                put("versionName", source.versionName)
                put("repo", source.repo)
            })
        }
        return arr
    }

    private fun jsonArray(key: String): List<String> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
        buildList { for (i in 0 until arr.length()) add(arr.optString(i)) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun emptyCard(title: String, msg: String) = content.addView(card().apply {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(36), dp(20), dp(36))
        }
        box.addView(TextView(context).apply {
            text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#202124"))
            gravity = Gravity.CENTER
        })
        box.addView(TextView(context).apply {
            text = msg
            textSize = 13f
            setTextColor(Color.parseColor("#7A7F89"))
            setPadding(0, dp(8), 0, 0)
            gravity = Gravity.CENTER
        })
        addView(box)
    })

    private fun section(text: String) = content.addView(TextView(this).apply {
        this.text = text
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#202124"))
        setPadding(dp(2), dp(12), 0, dp(6))
    })

    private fun smallButton(text: String, action: () -> Unit) = MaterialButton(this).apply {
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

    private fun grayButton(text: String, action: () -> Unit) = MaterialButton(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 12f
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        setPadding(dp(10), dp(4), dp(10), dp(4))
        setTextColor(Color.parseColor("#40454F"))
        setBackgroundColor(Color.parseColor("#EEF1F6"))
        setOnClickListener { action() }
    }

    private fun smallWideButton(text: String, action: () -> Unit) = MaterialButton(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#4F6EF7"))
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(8), 0, dp(8)) }
    }

    private fun space(width: Int, height: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(width, height)
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
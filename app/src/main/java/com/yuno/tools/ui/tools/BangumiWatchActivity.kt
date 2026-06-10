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
import org.json.JSONArray

class BangumiWatchActivity : AppCompatActivity() {
    private enum class Page { HOME, SEARCH, SOURCE, SETTINGS }

    private val prefs by lazy { getSharedPreferences("yuno_bangumi_watch", Context.MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var repoInput: EditText
    private var page = Page.HOME
    private var keyword = ""

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
        if (repos.isEmpty()) {
            sourceEmptyState()
            return
        }
        sourceTabs(repos)
        emptyCard("暂无内容", "已添加源仓库，但当前版本还没有解析加载源内容。等源仓库加载成功后，这里显示番剧封面列表。")
    }

    private fun renderSearch() {
        topBar("搜索", "源搜索", false)
        searchBox()
        val repos = loadRepos()
        if (repos.isEmpty()) {
            sourceEmptyState()
            return
        }
        if (keyword.isBlank()) {
            emptyCard("输入关键词", "输入番剧名后，会交给已添加的源仓库搜索。")
        } else {
            emptyCard("暂无搜索结果", "已记录关键词：$keyword。源仓库解析接入后，这里展示搜索结果网格。")
        }
    }

    private fun renderSource() {
        topBar("源", "仓库与扩展", false)
        val repos = loadRepos()
        if (repos.isEmpty()) {
            sourceEmptyState()
            return
        }
        section("源仓库")
        repos.forEachIndexed { index, repo ->
            content.addView(repoCard("源仓库 ${index + 1}", repo, "已添加") {})
        }
        emptyCard("等待加载", "源仓库配置已保存；后续接入加载器后，会在这里展开源列表与分组页。")
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
            actions.addView(smallButton("保存") { saveRepoFromInput() })
            actions.addView(space(dp(8), 1))
            actions.addView(grayButton("清空") { repoInput.setText("") })
            box.addView(actions)
            addView(box)
        })
        section("已添加仓库")
        val repos = loadRepos()
        if (repos.isEmpty()) {
            emptyCard("暂无源仓库", "添加源仓库后，首页和搜索页才会开始显示源相关内容；没有源时不展示任何作品。")
        } else {
            repos.forEachIndexed { index, repo ->
                content.addView(repoCard("源仓库 ${index + 1}", repo, "删除") { removeRepo(repo) })
            }
        }
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
            text = if (loadRepos().isEmpty()) "无源" else "${loadRepos().size} 源"
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

    private fun sourceTabs(repos: List<String>) {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        repos.forEachIndexed { index, _ ->
            row.addView(TextView(this).apply {
                text = "源仓库 ${index + 1}"
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

    private fun repoCard(title: String, url: String, actionText: String, action: () -> Unit) = card().apply {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        row.addView(TextView(context).apply {
            text = "源"
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
                text = title
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

    private fun saveRepoFromInput() {
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
        Toast.makeText(this, "已保存源仓库", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun removeRepo(url: String) {
        val next = loadRepos().filterNot { it == url }
        prefs.edit().putString("repos", JSONArray(next).toString()).apply()
        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun loadRepos(): List<String> = runCatching {
        val arr = JSONArray(prefs.getString("repos", "[]") ?: "[]")
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

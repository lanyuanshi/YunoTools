package com.yuno.tools.ui.tools

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.util.ThemeApplier
import org.json.JSONArray
import java.net.URLEncoder

class BangumiWatchActivity : AppCompatActivity() {
    private data class WatchSource(
        val name: String,
        val desc: String,
        val urlTemplate: String,
        val official: Boolean = true
    )

    private val prefs by lazy { getSharedPreferences("yuno_bangumi_watch", Context.MODE_PRIVATE) }
    private lateinit var searchInput: EditText
    private lateinit var resultList: LinearLayout
    private lateinit var historyList: LinearLayout

    private val sources = listOf(
        WatchSource("Bangumi 番组资料", "查番剧资料、评分、条目与放送信息", "https://bgm.tv/subject_search/%s?cat=2"),
        WatchSource("哔哩哔哩番剧", "打开 B 站官方番剧搜索", "https://search.bilibili.com/bangumi?keyword=%s"),
        WatchSource("腾讯视频动漫", "打开腾讯视频官方搜索", "https://v.qq.com/x/search/?q=%s"),
        WatchSource("爱奇艺动漫", "打开爱奇艺官方搜索", "https://so.iqiyi.com/so/q_%s"),
        WatchSource("优酷动漫", "打开优酷官方搜索", "https://so.youku.com/search_video/q_%s"),
        WatchSource("芒果 TV", "打开芒果 TV 官方搜索", "https://so.mgtv.com/so?k=%s")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeApplier.apply(this)
        buildUi()
        renderSources("")
        renderHistory()
    }

    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F6F8FC"))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(10))
        }
        header.addView(TextView(this).apply {
            text = "‹"
            textSize = 34f
            setTextColor(Color.parseColor("#182033"))
            gravity = Gravity.CENTER
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = "看番"
                textSize = 24f
                setTextColor(Color.parseColor("#182033"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply {
                text = "搜索番剧资料与官方平台入口"
                textSize = 13f
                setTextColor(Color.parseColor("#69758A"))
            })
        })
        root.addView(header)

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, dp(18), dp(28))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        content.addView(card().apply {
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }
            box.addView(TextView(context).apply {
                text = "合规说明"
                textSize = 16f
                setTextColor(Color.parseColor("#182033"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            box.addView(TextView(context).apply {
                text = "本功能参考 EasyBangumi 的番剧聚合思路，提供资料检索、官方平台搜索、历史记录和收藏入口；不内置盗版源，不绕过会员、登录、地区限制或 DRM。"
                textSize = 13f
                setTextColor(Color.parseColor("#69758A"))
                setPadding(0, dp(8), 0, 0)
            })
            addView(box)
        })

        content.addView(card().apply {
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }
            searchInput = EditText(context).apply {
                hint = "输入番剧名，例如 葬送的芙莉莲"
                setSingleLine(true)
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                setTextColor(Color.parseColor("#182033"))
                setHintTextColor(Color.parseColor("#9AA4B2"))
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        doSearch()
                        true
                    } else false
                }
            }
            box.addView(searchInput)
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(10), 0, 0)
            }
            row.addView(MaterialButton(context).apply {
                text = "搜索"
                isAllCaps = false
                setOnClickListener { doSearch() }
            })
            row.addView(MaterialButton(context).apply {
                text = "清空"
                isAllCaps = false
                setOnClickListener {
                    searchInput.setText("")
                    renderSources("")
                }
            })
            box.addView(row)
            addView(box)
        })

        content.addView(sectionTitle("搜索入口"))
        resultList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(resultList)

        content.addView(sectionTitle("最近搜索"))
        historyList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(historyList)
    }

    private fun doSearch() {
        val keyword = searchInput.text?.toString()?.trim().orEmpty()
        if (keyword.isBlank()) {
            Toast.makeText(this, "请输入番剧名", Toast.LENGTH_SHORT).show()
            return
        }
        saveHistory(keyword)
        renderSources(keyword)
        renderHistory()
    }

    private fun renderSources(keyword: String) {
        resultList.removeAllViews()
        sources.forEach { source ->
            resultList.addView(sourceCard(source, keyword))
        }
    }

    private fun sourceCard(source: WatchSource, keyword: String) = card().apply {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        box.addView(TextView(context).apply {
            text = source.name
            textSize = 16f
            setTextColor(Color.parseColor("#182033"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(context).apply {
            text = source.desc
            textSize = 13f
            setTextColor(Color.parseColor("#69758A"))
            setPadding(0, dp(5), 0, dp(10))
        })
        val buttonText = if (keyword.isBlank()) "打开首页" else "搜索“$keyword”"
        box.addView(MaterialButton(context).apply {
            text = buttonText
            isAllCaps = false
            setOnClickListener { openSource(source, keyword) }
        })
        addView(box)
    }

    private fun openSource(source: WatchSource, keyword: String) {
        val encoded = URLEncoder.encode(keyword.ifBlank { "动漫" }, "UTF-8")
        val url = source.urlTemplate.format(encoded)
        if (keyword.isNotBlank()) saveHistory(keyword)
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, "没有可用浏览器打开链接", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderHistory() {
        historyList.removeAllViews()
        val history = loadHistory()
        if (history.isEmpty()) {
            historyList.addView(TextView(this).apply {
                text = "暂无搜索记录"
                textSize = 14f
                setTextColor(Color.parseColor("#8D98AA"))
                gravity = Gravity.CENTER
                setPadding(0, dp(18), 0, dp(18))
            })
            return
        }
        history.forEach { item ->
            historyList.addView(card().apply {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(10), dp(10), dp(10))
                }
                row.addView(TextView(context).apply {
                    text = item
                    textSize = 15f
                    setTextColor(Color.parseColor("#182033"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        searchInput.setText(item)
                        renderSources(item)
                    }
                })
                row.addView(MaterialButton(context).apply {
                    text = "再搜"
                    isAllCaps = false
                    setOnClickListener {
                        searchInput.setText(item)
                        renderSources(item)
                    }
                })
                addView(row)
            })
        }
    }

    private fun saveHistory(keyword: String) {
        val next = (listOf(keyword) + loadHistory().filterNot { it == keyword }).take(12)
        prefs.edit().putString("history", JSONArray(next).toString()).apply()
    }

    private fun loadHistory(): List<String> {
        val raw = prefs.getString("history", "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList { for (i in 0 until arr.length()) add(arr.optString(i)) }
                .filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 17f
        setTextColor(Color.parseColor("#182033"))
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, dp(16), 0, dp(8))
    }

    private fun card() = MaterialCardView(this).apply {
        radius = dp(18).toFloat()
        cardElevation = 0f
        setCardBackgroundColor(Color.WHITE)
        strokeColor = Color.parseColor("#E9EEF7")
        strokeWidth = dp(1)
        useCompatPadding = true
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp(10)) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
package com.yuno.tools.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.DecimalFormat
import java.util.Locale

class CloudDriveActivity : AppCompatActivity() {
    private lateinit var container: LinearLayout
    private lateinit var listBox: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var pathText: TextView
    private lateinit var searchInput: EditText
    private lateinit var progressBar: ProgressBar
    private var currentPath = "cloudreve://root"
    private var activeFilter = FileFilter.ALL
    private val files = mutableListOf<DriveFile>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        seedFiles()
        setContentView(buildContent())
        renderFiles()
    }

    private fun buildContent(): View {
        val root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0F172A"), Color.parseColor("#EFF6FF"), Color.parseColor("#F8FAFC"))
            )
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(26))
        }
        scroll.addView(container)
        root.addView(scroll)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(this).apply {
            text = "Cloudreve 网盘"
            setTextColor(Color.WHITE)
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(pill("返回", "#334155") { finish() }, LinearLayout.LayoutParams(dp(74), dp(42)))
        container.addView(top)

        container.addView(TextView(this).apply {
            text = "文件管理 UI 已预留 Cloudreve 登录、目录、上传和分享接口，当前展示本地模拟数据。"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 13f
            setPadding(0, dp(8), 0, dp(14))
        })

        container.addView(storageCard(), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        container.addView(actionPanel(), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        searchInput = EditText(this).apply {
            hint = "搜索文件、文件夹、标签"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            textSize = 14f
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            background = bg("#FFFFFF", 18)
            setPadding(dp(14), 0, dp(14), 0)
            setOnEditorActionListener { _, _, _ -> renderFiles(); false }
        }
        container.addView(searchInput, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(10) })
        container.addView(filterTabs(), LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(12) })

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(listBox)
        return root
    }

    private fun storageCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg("#FFFFFF", 24)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(TextView(this).apply {
            text = "云盘容量"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
        })
        statusText = TextView(this).apply {
            text = "未连接 Cloudreve · UI 预览模式"
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(4), 0, 0)
        }
        left.addView(statusText)
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply {
            text = "72%"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#2563EB"))
        })
        card.addView(row)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 72
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2563EB"))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#DBEAFE"))
        }
        card.addView(progressBar, LinearLayout.LayoutParams(-1, dp(12)).apply { topMargin = dp(14) })
        card.addView(statGrid(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        return card
    }

    private fun statGrid(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(statRow("已用", "73.8 GB", "总量", "102.4 GB", "文件", files.size.toString()))
        box.addView(statRow("图片", "246", "视频", "38", "文档", "92"))
        return box
    }

    private fun statRow(a: String, av: String, b: String, bv: String, c: String, cv: String): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(statCell(a, av), LinearLayout.LayoutParams(0, dp(58), 1f).apply { rightMargin = dp(8) })
        row.addView(statCell(b, bv), LinearLayout.LayoutParams(0, dp(58), 1f).apply { rightMargin = dp(8) })
        row.addView(statCell(c, cv), LinearLayout.LayoutParams(0, dp(58), 1f))
        return row
    }

    private fun statCell(label: String, value: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = bg("#F8FAFC", 16)
        addView(TextView(context).apply {
            text = value
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#111827"))
        })
        addView(TextView(context).apply {
            text = label
            textSize = 11f
            setTextColor(Color.parseColor("#64748B"))
        })
    }

    private fun actionPanel(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg("#FFFFFF", 22)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        pathText = TextView(this).apply {
            text = "当前位置：$currentPath"
            textSize = 13f
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, 0, 0, dp(12))
        }
        card.addView(pathText)
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(pill("刷新", "#2563EB") { fakeAction("刷新目录") }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
        row1.addView(pill("上传", "#16A34A") { fakeAction("上传文件") }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
        row1.addView(pill("新建", "#7C3AED") { addMockFolder() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        card.addView(row1)
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        row2.addView(pill("分享管理", "#0F766E") { fakeAction("分享管理") }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(8) })
        row2.addView(pill("连接设置", "#475569") { fakeAction("Cloudreve 连接设置") }, LinearLayout.LayoutParams(0, dp(42), 1f))
        card.addView(row2)
        return card
    }

    private fun filterTabs(): View {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        FileFilter.values().forEach { filter ->
            row.addView(pill(filter.label, if (filter == activeFilter) "#111827" else "#64748B") {
                activeFilter = filter
                renderFiles()
            }, LinearLayout.LayoutParams(dp(88), dp(40)).apply { rightMargin = dp(8) })
        }
        scroll.addView(row)
        return scroll
    }

    private fun renderFiles() {
        listBox.removeAllViews()
        listBox.addView(TextView(this).apply {
            text = "文件列表"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(0, dp(4), 0, dp(10))
        })
        val query = searchInput.text?.toString().orEmpty().trim().lowercase(Locale.ROOT)
        val filtered = files.filter { file ->
            val matchType = when (activeFilter) {
                FileFilter.ALL -> true
                FileFilter.FOLDER -> file.type == FileType.FOLDER
                FileFilter.IMAGE -> file.type == FileType.IMAGE
                FileFilter.VIDEO -> file.type == FileType.VIDEO
                FileFilter.DOCUMENT -> file.type == FileType.DOCUMENT
            }
            val matchQuery = query.isBlank() || file.name.lowercase(Locale.ROOT).contains(query) || file.tag.lowercase(Locale.ROOT).contains(query)
            matchType && matchQuery
        }
        if (filtered.isEmpty()) {
            listBox.addView(emptyCard("没有匹配内容，后续接入 Cloudreve 后会显示真实目录。"))
            return
        }
        filtered.forEach { file ->
            listBox.addView(fileCard(file), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
    }

    private fun fileCard(file: DriveFile): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg("#FFFFFF", 20)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener { if (file.type == FileType.FOLDER) openFolder(file) else fakeAction("打开 ${file.name}") }
        }
        card.addView(TextView(this).apply {
            text = file.type.symbol
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(file.type.color)
            background = bg(file.type.bg, 18)
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(12) })
        val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textBox.addView(TextView(this).apply {
            text = file.name
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#111827"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        textBox.addView(TextView(this).apply {
            text = "${file.sizeText()} · ${file.modified} · ${file.tag}"
            textSize = 12.5f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(4), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        card.addView(textBox, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(TextView(this).apply {
            text = "更多"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#2563EB"))
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = bg("#EFF6FF", 14)
            setOnClickListener { copyName(file) }
        })
        return card
    }

    private fun emptyCard(textValue: String): View = TextView(this).apply {
        text = textValue
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#64748B"))
        setPadding(dp(18), dp(24), dp(18), dp(24))
        background = bg("#FFFFFF", 20)
    }

    private fun openFolder(file: DriveFile) {
        currentPath = "cloudreve://root/${file.name}"
        pathText.text = "当前位置：$currentPath"
        statusText.text = "已进入模拟文件夹 · 后续对接 Cloudreve 目录接口"
        fakeAction("进入文件夹：${file.name}")
    }

    private fun addMockFolder() {
        val index = files.count { it.name.startsWith("新建文件夹") } + 1
        files.add(0, DriveFile("新建文件夹 $index", FileType.FOLDER, 0L, "刚刚", "本地占位"))
        renderFiles()
        fakeAction("已创建占位文件夹")
    }

    private fun copyName(file: DriveFile) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("文件名", file.name))
        Toast.makeText(this, "已复制文件名：${file.name}", Toast.LENGTH_SHORT).show()
    }

    private fun fakeAction(name: String) {
        Toast.makeText(this, "$name 已预留，后续接入 Cloudreve API", Toast.LENGTH_SHORT).show()
    }

    private fun seedFiles() {
        files.clear()
        files.addAll(
            listOf(
                DriveFile("应用安装包", FileType.FOLDER, 0L, "今天 14:20", "同步目录"),
                DriveFile("YunoTools-v1.2.39.apk", FileType.OTHER, 42_600_000L, "昨天 19:42", "release"),
                DriveFile("主题背景素材", FileType.FOLDER, 0L, "周一 09:18", "设计"),
                DriveFile("云粉新主题预览.png", FileType.IMAGE, 1_377_658L, "周一 18:46", "图片"),
                DriveFile("用户资料备份.json", FileType.DOCUMENT, 86_200L, "06-28 22:10", "配置"),
                DriveFile("短视频剪辑样片.mp4", FileType.VIDEO, 128_400_000L, "06-24 16:35", "视频"),
                DriveFile("接口对接说明.md", FileType.DOCUMENT, 24_300L, "06-21 11:02", "文档")
            )
        )
    }

    private fun pill(textValue: String, color: String, click: () -> Unit): TextView = TextView(this).apply {
        text = textValue
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = bg(color, 16)
        setOnClickListener { click() }
    }

    private fun bg(color: String, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class DriveFile(
        val name: String,
        val type: FileType,
        val bytes: Long,
        val modified: String,
        val tag: String
    ) {
        fun sizeText(): String = if (type == FileType.FOLDER) "文件夹" else formatBytes(bytes)
    }

    private enum class FileType(val symbol: String, val color: Int, val bg: String) {
        FOLDER("夹", Color.parseColor("#2563EB"), "#DBEAFE"),
        IMAGE("图", Color.parseColor("#16A34A"), "#DCFCE7"),
        VIDEO("视", Color.parseColor("#DC2626"), "#FEE2E2"),
        DOCUMENT("文", Color.parseColor("#7C3AED"), "#F3E8FF"),
        OTHER("云", Color.parseColor("#0F766E"), "#CCFBF1")
    }

    private enum class FileFilter(val label: String) {
        ALL("全部"),
        FOLDER("文件夹"),
        IMAGE("图片"),
        VIDEO("视频"),
        DOCUMENT("文档")
    }

    companion object {
        private val formatter = DecimalFormat("0.#")

        private fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1 -> "${formatter.format(gb)} GB"
                mb >= 1 -> "${formatter.format(mb)} MB"
                kb >= 1 -> "${formatter.format(kb)} KB"
                else -> "$bytes B"
            }
        }
    }
}

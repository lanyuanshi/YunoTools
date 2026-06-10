package com.yuno.tools.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.R
import com.yuno.tools.util.ThemeApplier
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MusicDownloadsActivity : AppCompatActivity() {
    private data class OnlineMusicRecord(
        val title: String,
        val artist: String,
        val sourceLabel: String,
        val pageUrl: String,
        val playUrl: String,
        val localPath: String,
        val savedAt: Long
    )

    companion object {
        private const val MUSIC_PREFS = "yuno_music_records"
        private const val MUSIC_DOWNLOADS_KEY = "online_downloads"
    }

    private lateinit var listContainer: LinearLayout
    private lateinit var emptyState: TextView
    private lateinit var countText: TextView
    private lateinit var pathText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_music_downloads)
        ThemeApplier.apply(this)
        listContainer = findViewById(R.id.downloadListContainer)
        emptyState = findViewById(R.id.tvMusicDownloadsEmpty)
        countText = findViewById(R.id.tvMusicDownloadsCount)
        pathText = findViewById(R.id.tvMusicDownloadsPath)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnRefreshDownloads).setOnClickListener { renderDownloads() }
        renderDownloads()
    }

    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
        renderDownloads()
    }

    private fun renderDownloads() {
        val downloads = loadDownloads()
        val dir = File(getExternalFilesDir(null), "Music")
        pathText.text = "保存目录：${dir.absolutePath}"
        countText.text = "共 ${downloads.size} 首"
        listContainer.removeAllViews()
        emptyState.visibility = if (downloads.isEmpty()) View.VISIBLE else View.GONE
        downloads.forEach { record -> listContainer.addView(createRecordCard(record)) }
    }

    private fun createRecordCard(record: OnlineMusicRecord): View {
        val density = resources.displayMetrics.density
        val card = MaterialCardView(this).apply {
            radius = 18f * density
            cardElevation = 0f
            setCardBackgroundColor(0xFFFFFFFF.toInt())
            strokeColor = 0xFFE9EEF7.toInt()
            strokeWidth = (1f * density).toInt()
            useCompatPadding = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, (12f * density).toInt()) }
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16f * density).toInt(), (14f * density).toInt(), (16f * density).toInt(), (14f * density).toInt())
        }
        val title = TextView(this).apply {
            text = record.title.ifBlank { "未知歌曲" }
            textSize = 16f
            setTextColor(0xFF182033.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val meta = TextView(this).apply {
            text = listOf(record.sourceLabel, record.artist).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "在线音乐" }
            textSize = 13f
            setTextColor(0xFF69758A.toInt())
            setPadding(0, (4f * density).toInt(), 0, 0)
        }
        val time = TextView(this).apply {
            text = "下载时间：${formatTime(record.savedAt)}"
            textSize = 12f
            setTextColor(0xFF8D98AA.toInt())
            setPadding(0, (8f * density).toInt(), 0, 0)
        }
        val path = TextView(this).apply {
            text = record.localPath.ifBlank { "本地路径缺失" }
            textSize = 12f
            setTextColor(0xFF8D98AA.toInt())
            setPadding(0, (4f * density).toInt(), 0, 0)
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, (12f * density).toInt(), 0, 0)
        }
        actions.addView(MaterialButton(this).apply {
            text = "播放"
            isAllCaps = false
            setOnClickListener { openRecord(record, false) }
        })
        actions.addView(MaterialButton(this).apply {
            text = "分享"
            isAllCaps = false
            setPadding((8f * density).toInt(), 0, 0, 0)
            setOnClickListener { openRecord(record, true) }
        })
        box.addView(title)
        box.addView(meta)
        box.addView(time)
        box.addView(path)
        box.addView(actions)
        card.addView(box)
        return card
    }

    private fun openRecord(record: OnlineMusicRecord, share: Boolean) {
        val file = File(record.localPath)
        if (!file.exists() || file.length() <= 0L) {
            Toast.makeText(this, "本地文件不存在", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = if (share) {
            Intent(Intent.ACTION_SEND).apply {
                type = contentResolver.getType(uri) ?: "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, contentResolver.getType(uri) ?: "audio/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        runCatching {
            startActivity(if (share) Intent.createChooser(intent, "分享下载歌曲") else intent)
        }.onFailure {
            Toast.makeText(this, "没有可用应用打开该文件", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDownloads(): List<OnlineMusicRecord> {
        val raw = getSharedPreferences(MUSIC_PREFS, Context.MODE_PRIVATE).getString(MUSIC_DOWNLOADS_KEY, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        OnlineMusicRecord(
                            title = obj.optString("title"),
                            artist = obj.optString("artist"),
                            sourceLabel = obj.optString("sourceLabel"),
                            pageUrl = obj.optString("pageUrl"),
                            playUrl = obj.optString("playUrl"),
                            localPath = obj.optString("localPath"),
                            savedAt = obj.optLong("savedAt", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun formatTime(time: Long): String {
        if (time <= 0L) return "未知"
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
    }
}

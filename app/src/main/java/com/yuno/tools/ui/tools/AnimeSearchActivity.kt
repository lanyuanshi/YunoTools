package com.yuno.tools.ui.tools

import android.content.Intent
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.yuno.tools.R
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class AnimeSearchActivity : AppCompatActivity() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
    private var selectedUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        selectedUri = uri
        findViewById<ImageView>(R.id.ivAnimePreview).apply {
            visibility = View.VISIBLE
            Glide.with(this@AnimeSearchActivity).load(uri).centerCrop().into(this)
        }
        findViewById<TextView>(R.id.tvAnimeStatus).text = "已选择图片，点击开始搜索"
        findViewById<LinearLayout>(R.id.llAnimeResults).removeAllViews()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_anime_search)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnPickAnimeImage).setOnClickListener { pickImage.launch(arrayOf("image/*")) }
        findViewById<Button>(R.id.btnSearchAnime).setOnClickListener { searchAnime() }
    }

    private fun searchAnime() {
        val uri = selectedUri
        if (uri == null) {
            Toast.makeText(this, "请先选择一张动漫截图", Toast.LENGTH_SHORT).show()
            return
        }
        val btn = findViewById<Button>(R.id.btnSearchAnime)
        val status = findViewById<TextView>(R.id.tvAnimeStatus)
        val container = findViewById<LinearLayout>(R.id.llAnimeResults)
        btn.isEnabled = false
        status.text = "正在以图搜番，请稍候..."
        container.removeAllViews()

        Thread {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IOException("无法读取图片")

                val results = requestTraceMoe(bytes, true).ifEmpty { requestTraceMoe(bytes, false) }
                if (results.isEmpty()) throw IOException("没有找到匹配番剧，请换一张更清晰截图")

                runOnUiThread {
                    runCatching {
                        status.text = "搜索完成，共展示 ${results.size} 个可能结果（包含低概率匹配）"
                        renderResults(results)
                    }.onFailure { err ->
                        status.text = "结果渲染失败"
                        container.removeAllViews()
                        addTextOnlyCard(err.message ?: "结果渲染异常，请换图重试")
                    }
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    status.text = "搜索失败"
                    container.removeAllViews()
                    addTextOnlyCard(e.message ?: "请换一张更清晰的动漫截图重试")
                }
            } finally {
                runOnUiThread { btn.isEnabled = true }
            }
        }.start()
    }


    private fun pickChineseTitle(anilist: JSONObject?, title: JSONObject?): String {
        val candidates = mutableListOf<String>()
        title?.optString("chinese")?.takeIf { it.isNotBlank() }?.let { candidates += it }
        anilist?.optJSONArray("synonyms")?.let { arr ->
            for (i in 0 until arr.length()) candidates += arr.optString(i)
        }
        title?.optString("native")?.takeIf { it.isNotBlank() }?.let { candidates += it }
        return candidates.firstOrNull { it.hasChinese() }.orEmpty()
    }

    private fun String.hasChinese(): Boolean = any { it.code in 0x4E00..0x9FFF }

    private fun requestTraceMoe(bytes: ByteArray, cutBorders: Boolean): List<AnimeMatch> {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                "anime_search.jpg",
                bytes.toRequestBody("image/*".toMediaTypeOrNull())
            ).build()

        val url = if (cutBorders) {
            "https://api.trace.moe/search?anilistInfo&cutBorders"
        } else {
            "https://api.trace.moe/search?anilistInfo"
        }
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("User-Agent", "YunoTools/1.1.36")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("搜索失败：HTTP ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            val arr = json.optJSONArray("result") ?: return emptyList()
            val list = mutableListOf<AnimeMatch>()
            val count = minOf(arr.length(), 8)
            for (i in 0 until count) {
                val item = arr.optJSONObject(i) ?: continue
                val anilist = item.optJSONObject("anilist")
                val title = anilist?.optJSONObject("title")
                val name = title?.optString("native").orEmpty().ifBlank {
                    title?.optString("romaji").orEmpty().ifBlank { title?.optString("english").orEmpty() }
                }.ifBlank { "未知番剧" }
                val episodeRaw = item.opt("episode")
                val episode = if (episodeRaw == null || episodeRaw.toString() == "null") "未知" else episodeRaw.toString()
                val totalEpisodes = anilist?.optInt("episodes", 0)?.takeIf { it > 0 }?.toString() ?: "未知"
                val episodeInfo = when {
                    episode != "未知" && totalEpisodes != "未知" -> "第 $episode 集 / 约共 $totalEpisodes 集"
                    episode != "未知" -> "大概第 $episode 集"
                    else -> "未知"
                }
                val cnName = pickChineseTitle(anilist, title)
                list += AnimeMatch(
                    rank = i + 1,
                    title = name,
                    chineseTitle = cnName,
                    type = anilist?.optString("type", "").orEmpty().ifBlank { "未知" },
                    episodeInfo = episodeInfo,
                    timeRange = "${formatTime(item.optDouble("from", 0.0))} - ${formatTime(item.optDouble("to", 0.0))}",
                    similarity = (item.optDouble("similarity", 0.0) * 100).roundToInt().coerceIn(0, 100),
                    imageUrl = item.optString("image", "").orEmpty(),
                    videoUrl = item.optString("video", "").orEmpty()
                )
            }
            return list
        }
    }

    private fun renderResults(results: List<AnimeMatch>) {
        val container = findViewById<LinearLayout>(R.id.llAnimeResults)
        container.removeAllViews()
        results.forEach { match ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                setBackgroundColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(12) }
            }
            if (match.imageUrl.isNotBlank()) {
                val image = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(178)
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(0xFFF2F2F7.toInt())
                }
                card.addView(image)
                runCatching { Glide.with(this).load(match.imageUrl).centerCrop().into(image) }
            }
            val resultText = match.copyText()
            val text = TextView(this).apply {
                text = resultText
                textSize = 16f
                setTextColor(0xFF1C1C1E.toInt())
                setLineSpacing(dp(4).toFloat(), 1.0f)
                typeface = Typeface.DEFAULT
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = if (match.imageUrl.isNotBlank()) dp(12) else 0 }
                setOnLongClickListener {
                    copyResult(resultText)
                    true
                }
            }
            card.addView(text)
            card.addView(Button(this).apply {
                this.text = "复制内容"
                setOnClickListener { copyResult(resultText) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(44)
                ).apply { topMargin = dp(10) }
            })
            container.addView(card)
        }
    }


    private fun AnimeMatch.copyText(): String = buildString {
        append("#${rank}  ${displayTitle()}\n")
        if (chineseTitle.isNotBlank() && chineseTitle != title) append("中文名：${chineseTitle}\n")
        append("类型：${type}\n")
        append("大概集数：${episodeInfo}\n")
        append("片段时间：${timeRange}\n")
        append("匹配概率：${similarity}%")
        if (videoUrl.isNotBlank()) append("\n预览片段：已获取")
    }

    private fun copyResult(text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("以图搜番结果", text))
        Toast.makeText(this, "已复制识别内容", Toast.LENGTH_SHORT).show()
    }

    private fun addTextOnlyCard(message: String) {
        val container = findViewById<LinearLayout>(R.id.llAnimeResults)
        container.removeAllViews()
        val text = TextView(this).apply {
            text = message
            setTextColor(0xFF1C1C1E.toInt())
            textSize = 16f
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        container.addView(text)
    }

    private fun formatTime(seconds: Double): String {
        val total = seconds.toInt().coerceAtLeast(0)
        return "%02d:%02d".format(total / 60, total % 60)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun AnimeMatch.displayTitle(): String = if (chineseTitle.isNotBlank()) chineseTitle else title

    private data class AnimeMatch(
        val rank: Int,
        val title: String,
        val chineseTitle: String,
        val type: String,
        val episodeInfo: String,
        val timeRange: String,
        val similarity: Int,
        val imageUrl: String,
        val videoUrl: String
    )
}

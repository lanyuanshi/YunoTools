package com.yuno.tools.ui.tools

import android.content.Intent
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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
                val rawBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IOException("无法读取图片")
                val variants = buildSearchVariants(rawBytes)
                val results = enrichChineseMetadata(searchTraceMoeAccurate(variants))
                if (results.isEmpty()) throw IOException("没有找到可靠匹配，请换一张原视频截图，尽量避开字幕、弹幕、拼图和二创图")

                runOnUiThread {
                    runCatching {
                        val best = results.maxOfOrNull { it.similarity } ?: 0
                        val cnCount = results.count { it.chineseTitle.isNotBlank() }
                        status.text = "搜索完成 · ${variants.size} 组候选 · 最佳 $best% · 中文名 $cnCount/${results.size}"
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
        return titleAliases(anilist, title).firstOrNull { it.hasChinese() }.orEmpty()
    }

    private fun titleAliases(anilist: JSONObject?, title: JSONObject?): List<String> {
        val candidates = mutableListOf<String>()
        title?.optString("chinese")?.takeIf { it.isNotBlank() }?.let { candidates += it }
        title?.optString("native")?.takeIf { it.isNotBlank() }?.let { candidates += it }
        title?.optString("romaji")?.takeIf { it.isNotBlank() }?.let { candidates += it }
        title?.optString("english")?.takeIf { it.isNotBlank() }?.let { candidates += it }
        anilist?.optJSONArray("synonyms")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { candidates += it }
        }
        return candidates.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    private fun String.hasChinese(): Boolean = any { it.code in 0x4E00..0x9FFF }

    private fun buildSearchVariants(raw: ByteArray): List<Pair<String, ByteArray>> {
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return listOf("原图" to raw)
        val normalized = normalizeBitmap(bitmap)
        val variants = mutableListOf<Pair<String, Bitmap>>()
        variants += "原图" to normalized
        variants += "去字幕主体" to cropPercent(normalized, 0.08f, 0.04f, 0.84f, 0.72f)
        variants += "中心角色" to cropPercent(normalized, 0.24f, 0.02f, 0.56f, 0.76f)
        variants += "去黑边宽屏" to cropPercent(normalized, 0.10f, 0.02f, 0.80f, 0.82f)
        variants += "上半画面" to cropPercent(normalized, 0.12f, 0.00f, 0.76f, 0.62f)
        return variants.distinctBy { "${it.second.width}x${it.second.height}_${it.first}" }
            .map { it.first to jpegBytes(it.second) }
            .filter { it.second.isNotEmpty() }
    }

    private fun normalizeBitmap(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= 1600) return bitmap
        val scale = 1600f / maxSide
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true)
    }

    private fun cropPercent(bitmap: Bitmap, x: Float, y: Float, w: Float, h: Float): Bitmap {
        val left = (bitmap.width * x).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (bitmap.height * y).roundToInt().coerceIn(0, bitmap.height - 1)
        val width = (bitmap.width * w).roundToInt().coerceIn(1, bitmap.width - left)
        val height = (bitmap.height * h).roundToInt().coerceIn(1, bitmap.height - top)
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    private fun jpegBytes(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
        out.toByteArray()
    }

    private fun searchTraceMoeAccurate(variants: List<Pair<String, ByteArray>>): List<AnimeMatch> {
        val merged = mutableMapOf<String, AnimeMatch>()
        variants.forEach { (variantName, bytes) ->
            listOf(false, true).forEach { cutBorders ->
                runCatching { requestTraceMoe(bytes, cutBorders, variantName) }.getOrDefault(emptyList()).forEach { match ->
                    val key = match.matchKey()
                    val old = merged[key]
                    merged[key] = if (old == null) match else old.copy(
                        similarity = maxOf(old.similarity, (match.similarity + old.sources.size * 3 + 5).coerceAtMost(100)),
                        sources = old.sources + match.sources
                    )
                }
            }
        }
        val ranked = merged.values.sortedWith(
            compareByDescending<AnimeMatch> { it.sources.size }
                .thenByDescending { it.similarity }
                .thenBy { it.rank }
        )
        val reliable = ranked.filter { it.similarity >= 70 || it.sources.size >= 2 }
        return (reliable.ifEmpty { ranked.take(5) }).take(8).mapIndexed { index, item -> item.copy(rank = index + 1) }
    }

    private fun requestTraceMoe(bytes: ByteArray, cutBorders: Boolean, variantName: String): List<AnimeMatch> {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                "anime_search.jpg",
                bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            ).build()

        val url = if (cutBorders) {
            "https://api.trace.moe/search?anilistInfo&cutBorders"
        } else {
            "https://api.trace.moe/search?anilistInfo"
        }
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("User-Agent", "YunoTools/1.2.12")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("搜索失败：HTTP ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            val arr = json.optJSONArray("result") ?: return emptyList()
            val list = mutableListOf<AnimeMatch>()
            val count = minOf(arr.length(), 12)
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
                val aliases = titleAliases(anilist, title)
                val cnName = aliases.firstOrNull { it.hasChinese() }.orEmpty()
                val from = item.optDouble("from", 0.0)
                val to = item.optDouble("to", 0.0)
                list += AnimeMatch(
                    rank = i + 1,
                    title = name,
                    chineseTitle = cnName,
                    type = anilist?.optString("type", "").orEmpty().ifBlank { "未知" },
                    episodeInfo = episodeInfo,
                    timeRange = "${formatTime(from)} - ${formatTime(to)}",
                    similarity = (item.optDouble("similarity", 0.0) * 100).roundToInt().coerceIn(0, 100),
                    imageUrl = item.optString("image", "").orEmpty(),
                    videoUrl = item.optString("video", "").orEmpty(),
                    aliases = aliases.filter { it != name && it != cnName }.take(8),
                    anilistId = anilist?.optInt("id", 0) ?: 0,
                    episode = episode,
                    fromSecond = from.roundToInt(),
                    sources = setOf("$variantName/${if (cutBorders) "裁边" else "原图"}")
                )
            }
            return list
        }
    }


    private fun enrichChineseMetadata(matches: List<AnimeMatch>): List<AnimeMatch> {
        return matches.map { match ->
            val bgm = findBangumiChinese(match)
            if (bgm == null) match else match.copy(
                chineseTitle = bgm.chineseTitle.ifBlank { match.chineseTitle },
                aliases = (listOf(match.chineseTitle, bgm.name, bgm.chineseTitle) + match.aliases + bgm.aliases)
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it != match.title }
                    .distinct()
                    .take(12)
            )
        }
    }

    private fun findBangumiChinese(match: AnimeMatch): BangumiTitle? {
        val queries = (listOf(match.title, match.chineseTitle) + match.aliases)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)
        for (query in queries) {
            val found = runCatching { searchBangumiSubject(query) }.getOrNull()
            if (found != null && (found.chineseTitle.isNotBlank() || found.aliases.any { it.hasChinese() })) return found
        }
        return null
    }

    private fun searchBangumiSubject(keyword: String): BangumiTitle? {
        val payload = JSONObject()
            .put("keyword", keyword)
            .put("filter", JSONObject().put("type", JSONArray().put(2)))
        val request = Request.Builder()
            .url("https://api.bgm.tv/v0/search/subjects?limit=5")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .addHeader("User-Agent", "YunoTools/1.2.14 (Android; anime-search)")
            .addHeader("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val root = JSONObject(response.body?.string().orEmpty())
            val data = root.optJSONArray("data") ?: return null
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val title = parseBangumiTitle(item)
                if (title.chineseTitle.isNotBlank() || title.aliases.any { it.hasChinese() }) return title
            }
        }
        return null
    }

    private fun parseBangumiTitle(item: JSONObject): BangumiTitle {
        val aliases = mutableListOf<String>()
        val name = item.optString("name").orEmpty()
        val nameCn = item.optString("name_cn").orEmpty()
        item.optJSONArray("infobox")?.let { info ->
            for (i in 0 until info.length()) {
                val row = info.optJSONObject(i) ?: continue
                val key = row.optString("key")
                if (!key.contains("别名") && !key.contains("中文") && !key.contains("简体")) continue
                val value = row.opt("value")
                when (value) {
                    is String -> aliases += value
                    is JSONArray -> for (j in 0 until value.length()) {
                        val v = value.opt(j)
                        when (v) {
                            is JSONObject -> aliases += v.optString("v")
                            is String -> aliases += v
                        }
                    }
                }
            }
        }
        return BangumiTitle(name, nameCn, aliases.map { it.trim() }.filter { it.isNotBlank() }.distinct())
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
    private fun AnimeMatch.matchKey(): String = listOf(anilistId.takeIf { it > 0 }?.toString() ?: displayTitle(), episode, fromSecond / 3).joinToString("_")
    private fun AnimeMatch.confidenceLabel(): String = when {
        similarity >= 90 -> "很高，基本可信"
        similarity >= 80 -> "较高，建议核对集数时间"
        similarity >= 72 -> "中等，可能受字幕/裁切影响"
        else -> "较低，仅作备选"
    }

    private data class AnimeMatch(
        val rank: Int,
        val title: String,
        val chineseTitle: String,
        val type: String,
        val episodeInfo: String,
        val timeRange: String,
        val similarity: Int,
        val imageUrl: String,
        val videoUrl: String,
        val aliases: List<String>,
        val anilistId: Int,
        val episode: String,
        val fromSecond: Int,
        val sources: Set<String>
    )

    private data class BangumiTitle(val name: String, val chineseTitle: String, val aliases: List<String>)
}

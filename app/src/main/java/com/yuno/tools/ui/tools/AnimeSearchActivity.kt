package com.yuno.tools.ui.tools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
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
import kotlin.math.roundToInt

class AnimeSearchActivity : AppCompatActivity() {
    private val client = OkHttpClient()
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
        findViewById<ImageView>(R.id.ivAnimeMatchedClip).visibility = View.GONE
        findViewById<TextView>(R.id.tvAnimeStatus).text = "已选择图片，点击开始搜索"
        findViewById<TextView>(R.id.tvAnimeResult).text = ""
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
        val result = findViewById<TextView>(R.id.tvAnimeResult)
        val clip = findViewById<ImageView>(R.id.ivAnimeMatchedClip)
        btn.isEnabled = false
        status.text = "正在以图搜番，请稍候..."
        result.text = ""
        clip.visibility = View.GONE

        Thread {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IOException("无法读取图片")
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "image",
                        "anime_search.jpg",
                        bytes.toRequestBody("image/*".toMediaTypeOrNull())
                    ).build()
                val request = Request.Builder()
                    .url("https://api.trace.moe/search?anilistInfo")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("搜索失败：HTTP ${response.code}")
                    val json = JSONObject(response.body?.string().orEmpty())
                    val arr = json.optJSONArray("result")
                    if (arr == null || arr.length() == 0) throw IOException("没有找到匹配番剧，请换一张更清晰截图")

                    val top = arr.getJSONObject(0)
                    val anilist = top.optJSONObject("anilist")
                    val title = anilist?.optJSONObject("title")
                    val name = title?.optString("native").orEmpty().ifBlank { title?.optString("romaji").orEmpty() }
                    val episodeRaw = top.opt("episode")
                    val episode = if (episodeRaw == null || episodeRaw.toString() == "null") "未知" else episodeRaw.toString()
                    val totalEpisodes = anilist?.optInt("episodes", 0)?.takeIf { it > 0 }?.toString() ?: "未知"
                    val episodeInfo = when {
                        episode != "未知" && totalEpisodes != "未知" -> "第 $episode 集 / 约共 $totalEpisodes 集"
                        episode != "未知" -> "大概第 $episode 集"
                        else -> "未知"
                    }
                    val from = formatTime(top.optDouble("from", 0.0))
                    val to = formatTime(top.optDouble("to", 0.0))
                    val similarity = (top.optDouble("similarity", 0.0) * 100).roundToInt().coerceIn(0, 100)
                    val type = anilist?.optString("type", "").orEmpty().ifBlank { "未知" }
                    val imageUrl = top.optString("image", "").orEmpty()
                    val videoUrl = top.optString("video", "").orEmpty()
                    val resultText = "番剧：${name.ifBlank { "未知番剧" }}\n类型：$type\n大概集数：$episodeInfo\n片段时间：$from - $to\n相似度：$similarity%" +
                        if (videoUrl.isNotBlank()) "\n预览片段：已获取" else ""

                    runOnUiThread {
                        status.text = "搜索完成"
                        result.text = resultText
                        if (imageUrl.isNotBlank()) {
                            clip.visibility = View.VISIBLE
                            Glide.with(this@AnimeSearchActivity).load(imageUrl).centerCrop().into(clip)
                        } else {
                            clip.visibility = View.GONE
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "搜索失败"
                    result.text = e.message ?: "请换一张更清晰的动漫截图重试"
                    clip.visibility = View.GONE
                }
            } finally {
                runOnUiThread { btn.isEnabled = true }
            }
        }.start()
    }

    private fun formatTime(seconds: Double): String {
        val total = seconds.toInt().coerceAtLeast(0)
        return "%02d:%02d".format(total / 60, total % 60)
    }
}

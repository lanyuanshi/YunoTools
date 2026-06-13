package com.yuno.tools.ui.video

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import android.widget.ProgressBar
import android.widget.EditText
import com.yuno.tools.R
import com.yuno.tools.data.RetrofitClient
import com.yuno.tools.data.VideoParseResult
import com.yuno.tools.data.ParseHistoryStore
import com.yuno.tools.util.UrlExtractor
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoParseActivity : AppCompatActivity() {

    private lateinit var etUrl: EditText
    private lateinit var btnParse: MaterialButton
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_parse)

        etUrl = findViewById(R.id.etUrl)
        btnParse = findViewById(R.id.btnParse)
        progress = findViewById(R.id.progressBar)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnPaste).setOnClickListener { pasteUrl() }
        findViewById<TextView>(R.id.btnClear).setOnClickListener { etUrl.setText("") }
        btnParse.setOnClickListener { doParse() }
    }

    private fun pasteUrl() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            etUrl.setText(text)
            etUrl.setSelection(text.length)
        }
    }

    private fun doParse() {
        val rawInput = etUrl.text.toString().trim()
        val url = UrlExtractor.extractUrl(rawInput) ?: rawInput
        if (url.isEmpty()) {
            Toast.makeText(this, "请输入视频链接", Toast.LENGTH_SHORT).show()
            return
        }

        progress.visibility = View.VISIBLE
        btnParse.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (isDoubaoThreadUrl(url)) {
                    val result = parseDoubaoThread(url)
                    withContext(Dispatchers.Main) {
                        progress.visibility = View.GONE
                        btnParse.isEnabled = true
                        if (result.images.isNotEmpty()) {
                            ParseHistoryStore.add(this@VideoParseActivity, result, url)
                            showResult(result)
                        } else {
                            Toast.makeText(this@VideoParseActivity, "豆包解析失败：未找到无水印原图", Toast.LENGTH_LONG).show()
                        }
                    }
                    return@launch
                }

                val response = RetrofitClient.apiService.parseVideo(url)
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    btnParse.isEnabled = true

                    if (response.isSuccessful && response.body() != null) {
                        val apiResp = response.body()!!
                        if (apiResp.code == 200 && apiResp.data != null) {
                            val result = convertToResult(apiResp.data)
                            ParseHistoryStore.add(this@VideoParseActivity, result, url)
                            showResult(result)
                        } else {
                            Toast.makeText(this@VideoParseActivity,
                                apiResp.msg ?: "解析失败", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this@VideoParseActivity,
                            "请求失败: ${response.code()}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    btnParse.isEnabled = true
                    Toast.makeText(this@VideoParseActivity,
                        "错误: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    private fun isDoubaoThreadUrl(url: String): Boolean {
        return url.contains("doubao.com/thread/", ignoreCase = true)
    }

    private fun parseDoubaoThread(url: String): VideoParseResult {
        val html = fetchDoubaoHtml(url)
        val images = extractDoubaoRawImages(html)
        val prompt = extractDoubaoPrompt(html)
        return VideoParseResult(
            title = prompt.ifBlank { "豆包无水印图片" },
            videoUrl = "",
            coverUrl = images.firstOrNull().orEmpty(),
            musicUrl = "",
            authorName = "豆包 AI 生成图",
            authorAvatar = "",
            content = prompt.ifBlank { "豆包 AI 生成图" },
            images = images,
            isImageSet = images.isNotEmpty()
        )
    }

    private fun fetchDoubaoHtml(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json,*/*")
            setRequestProperty("Referer", "https://www.doubao.com/")
        }
        return try {
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun normalizeDoubaoHtml(html: String): String {
        var s = html
        repeat(6) {
            s = s.replace("&amp;", "&")
                .replace("&"+"quot;", "\"")
                .replace("&#34;", "\"")
                .replace("\\u002F", "/")
                .replace("\\u002f", "/")
                .replace("\\/", "/")
                .replace("\\\"", "\"")
        }
        return s
    }

    private fun extractDoubaoRawImages(html: String): List<String> {
        val s = normalizeDoubaoHtml(html)
        val urls = linkedSetOf<String>()
        val patterns = listOf(
            Regex("https://[^\\\"\\s<>]+~tplv-[^\\\"\\s<>]*image_raw\\.(?:png|jpg|jpeg|webp|heic)[^\\\"\\s<>]*", RegexOption.IGNORE_CASE),
            Regex("https://[^\\\"\\s<>]+/rc_gen_image/[^\\\"\\s<>]+?\\.(?:png|jpg|jpeg|webp|heic)[^\\\"\\s<>]*image_raw[^\\\"\\s<>]*", RegexOption.IGNORE_CASE)
        )
        patterns.forEach { pattern ->
            pattern.findAll(s).forEach { match ->
                val url = match.value.trim().trimEnd(',', '}', ']', '\\')
                if (url.contains("image_raw", ignoreCase = true) && !url.contains("watermark", ignoreCase = true)) {
                    urls.add(url)
                }
            }
        }
        return urls.toList()
    }

    private fun extractDoubaoPrompt(html: String): String {
        val s = normalizeDoubaoHtml(html)
        val match = Regex("\"prompt\"\\s*:\\s*\"([^\"]{1,120})\"").find(s)
        return match?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }

    private fun convertToResult(data: com.yuno.tools.data.VideoParseData): VideoParseResult {
        val imageUrls = mutableListOf<String>()
        data.images?.let { list ->
            list.forEach { element ->
                if (element.isJsonPrimitive) {
                    element.asString?.let { imageUrls.add(it) }
                } else if (element.isJsonObject) {
                    element.asJsonObject.get("url")?.asString?.let { imageUrls.add(it) }
                    element.asJsonObject.get("img_url")?.asString?.let { imageUrls.add(it) }
                }
            }
        }
        data.imageList?.let { list ->
            list.forEach { item ->
                item.imgUrl?.let { imageUrls.add(it) }
            }
        }
        return VideoParseResult(
            title = data.title ?: "",
            videoUrl = data.videoUrl ?: "",
            coverUrl = data.coverUrl ?: "",
            musicUrl = data.musicUrl ?: "",
            authorName = data.author?.name ?: "",
            authorAvatar = data.author?.avatar ?: "",
            content = data.content ?: "",
            images = imageUrls,
            isImageSet = imageUrls.isNotEmpty()
        )
    }

    private fun showResult(result: VideoParseResult) {
        val intent = if (result.isImageSet) {
            Intent(this, ImageSetResultActivity::class.java)
        } else {
            Intent(this, ParseResultActivity::class.java)
        }
        intent.putExtra("result", result)
        startActivity(intent)
    }
}
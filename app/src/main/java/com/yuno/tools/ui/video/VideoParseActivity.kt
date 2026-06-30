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
import java.io.IOException
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response

class VideoParseActivity : AppCompatActivity() {

    private lateinit var etUrl: EditText
    private lateinit var btnParse: MaterialButton
    private lateinit var progress: ProgressBar
    private lateinit var tvParseState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_parse)

        etUrl = findViewById(R.id.etUrl)
        btnParse = findViewById(R.id.btnParse)
        progress = findViewById(R.id.progressBar)
        tvParseState = findViewById(R.id.tvParseState)

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
        val url = UrlExtractor.extractUrl(rawInput) ?: rawInput.takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty()
        if (url.isEmpty()) {
            Toast.makeText(this, "请粘贴包含 http/https 的有效分享链接", Toast.LENGTH_SHORT).show()
            return
        }

        setParsingState(true, "正在准备解析链接...")

        lifecycleScope.launch(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val outcome = runCatching { parseWithNetworkAdaptation(url) }
            withContext(Dispatchers.Main) {
                setParsingState(false, "")
                outcome.onSuccess { result ->
                    if (result.isImageSet && result.images.isEmpty()) {
                        Toast.makeText(this@VideoParseActivity, "解析失败：未找到无水印图片", Toast.LENGTH_LONG).show()
                        return@onSuccess
                    }
                    ParseHistoryStore.add(this@VideoParseActivity, result, url)
                    Toast.makeText(this@VideoParseActivity, "解析完成，用时 ${(System.currentTimeMillis() - startedAt) / 1000.0}s", Toast.LENGTH_SHORT).show()
                    showResult(result)
                }.onFailure { error ->
                    Toast.makeText(this@VideoParseActivity, friendlyParseError(error), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun parseWithNetworkAdaptation(url: String): VideoParseResult {
        if (isDoubaoThreadUrl(url)) {
            updateState("正在解析豆包无水印原图...")
            return parseDoubaoThread(url).takeIf { it.images.isNotEmpty() } ?: error("豆包解析失败：未找到无水印原图")
        }

        val candidates = buildUrlCandidates(url)
        var lastError: Throwable? = null
        candidates.forEachIndexed { index, candidate ->
            updateState(if (index == 0) "正在请求解析接口..." else "正在尝试展开后的链接...")
            repeat(3) { attempt ->
                try {
                    if (attempt > 0) {
                        updateState("网络不稳定，正在第 ${attempt + 1} 次重试...")
                        delay((450L * attempt).coerceAtMost(1200L))
                    }
                    val response = RetrofitClient.apiService.parseVideo(candidate)
                    val parsed = parseApiResponse(response)
                    if (parsed.hasUsefulContent()) return parsed
                    lastError = IllegalStateException("解析接口返回为空结果")
                } catch (e: Exception) {
                    lastError = e
                    if (!e.isRetryableNetworkError()) return@repeat
                }
            }
        }
        throw lastError ?: IllegalStateException("解析失败，请稍后再试")
    }

    private fun parseApiResponse(response: Response<com.yuno.tools.data.ApiResponse>): VideoParseResult {
        if (!response.isSuccessful) error(parseHttpError(response.code()))
        val apiResp = response.body() ?: error("解析接口返回为空")
        if (apiResp.code != 200 || apiResp.data == null) error(apiResp.msg.ifBlank { "解析失败：接口未返回可用数据" })
        return convertToResult(apiResp.data)
    }

    private fun buildUrlCandidates(url: String): List<String> {
        val result = linkedSetOf(url)
        if (isShortShareUrl(url)) {
            resolveRedirectUrl(url)?.let { result.add(it) }
        }
        return result.toList()
    }

    private fun isShortShareUrl(url: String): Boolean {
        return listOf("v.douyin.com", "v.kuaishou.com", "xhslink.com", "b23.tv", "v.ixigua.com").any { url.contains(it, ignoreCase = true) }
    }

    private fun resolveRedirectUrl(url: String): String? {
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            }
            try {
                conn.responseCode
                conn.getHeaderField("Location")?.takeIf { it.startsWith("http") }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun VideoParseResult.hasUsefulContent(): Boolean {
        return videoUrl.isNotBlank() || images.isNotEmpty() || coverUrl.isNotBlank()
    }

    private suspend fun updateState(text: String) {
        withContext(Dispatchers.Main) { tvParseState.text = text }
    }

    private fun setParsingState(parsing: Boolean, state: String) {
        progress.visibility = if (parsing) View.VISIBLE else View.GONE
        tvParseState.visibility = if (parsing) View.VISIBLE else View.GONE
        tvParseState.text = state
        btnParse.isEnabled = !parsing
        btnParse.text = if (parsing) "解析中..." else "开始解析"
    }

    private fun Throwable.isRetryableNetworkError(): Boolean {
        val message = message.orEmpty()
        return this is IOException || message.contains("timeout", true) || message.contains("timed out", true) || message.contains("reset", true) || message.contains("closed", true)
    }

    private fun friendlyParseError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            error.isRetryableNetworkError() -> "网络不稳定或接口响应慢，已自动重试仍失败，请切换网络后再试"
            message.contains("解析接口") || message.contains("HTTP") -> message
            message.isNotBlank() -> "解析失败：$message"
            else -> "解析失败，请检查链接或稍后再试"
        }
    }


    private fun parseHttpError(code: Int): String {
        return when (code) {
            204 -> "解析接口未返回内容：链接可能失效、接口暂不支持，或服务被限流，请换链接/稍后重试"
            400 -> "解析请求无效：请确认粘贴的是完整分享链接"
            403 -> "解析接口拒绝访问：可能被平台限制或接口限流"
            404 -> "解析接口没有找到内容：链接可能已失效或作品不可访问"
            429 -> "解析接口请求过多：请稍后再试"
            in 500..599 -> "解析接口服务异常：HTTP $code，请稍后重试"
            else -> "请求失败：HTTP $code"
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
            connectTimeout = 8000
            readTimeout = 15000
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
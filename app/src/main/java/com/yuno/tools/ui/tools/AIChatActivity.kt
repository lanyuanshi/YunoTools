package com.yuno.tools.ui.tools

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.yuno.tools.R
import com.yuno.tools.util.ThemeApplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AIChatActivity : AppCompatActivity() {
    private lateinit var messageContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var etInput: EditText
    private lateinit var spinnerModel: Spinner
    private lateinit var tvApiHint: TextView
    private lateinit var tvAttachmentHint: TextView
    private lateinit var btnSend: MaterialButton
    private lateinit var btnAttach: MaterialButton
    private lateinit var btnClear: ImageButton
    private val messages = mutableListOf<ChatMessage>()
    private var pendingAttachment: PendingAttachment? = null

    private val mediaPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let { handlePickedMedia(it) }
    }

    private val modelOptions = listOf(
        "gpt-5.5",
        "gpt-5.4-mini",
        "gpt-5.4",
        "gpt-5.2-chat-latest",
        "gpt-5.2",
        "gpt-4o-realtime-preview",
        "gpt-4o-audio-preview"
    )
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat)
        ThemeApplier.apply(this)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        btnClear = findViewById(R.id.btnClear)
        scrollView = findViewById(R.id.scrollMessages)
        messageContainer = findViewById(R.id.messageContainer)
        etInput = findViewById(R.id.etInput)
        spinnerModel = findViewById(R.id.spinnerModel)
        tvApiHint = findViewById(R.id.tvApiHint)
        tvAttachmentHint = findViewById(R.id.tvAttachmentHint)
        btnSend = findViewById(R.id.btnSend)
        btnAttach = findViewById(R.id.btnAttach)
        bindModelSelector()
        loadHistory()
        renderAll()
        if (messages.isEmpty()) {
            addMessage("assistant", "你好，我是 Yuno AI。API 已移动到设置页，可使用默认配置或自定义配置；这里直接选择模型并发送问题即可。")
        }
        btnAttach.setOnClickListener { pickMedia() }
        tvAttachmentHint.setOnClickListener { pendingAttachment = null; refreshAttachmentHint() }
        btnSend.setOnClickListener { sendCurrentMessage() }
        btnClear.setOnClickListener {
            messages.clear()
            saveHistory()
            renderAll()
            pendingAttachment = null
            refreshAttachmentHint()
            addMessage("assistant", "已清空历史，重新开始聊天。")
        }
    }

    override fun onResume() {
        super.onResume()
        refreshApiHint()
        ThemeApplier.apply(this)
    }

    private fun bindModelSelector() {
        spinnerModel.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modelOptions)
        val sp = getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val savedModel = sp.getString(KEY_MODEL, modelOptions.first()).orEmpty()
        spinnerModel.setSelection(modelOptions.indexOf(savedModel).takeIf { it >= 0 } ?: 0)
        spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                sp.edit().putString(KEY_MODEL, modelOptions[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        refreshApiHint()
    }

    private fun refreshApiHint() {
        val cfg = currentConfig()
        tvApiHint.text = if (cfg.useDefault) {
            "当前使用默认 API · ${cfg.endpointBase}"
        } else {
            val ready = cfg.apiKey.isNotBlank() && cfg.endpointBase.isNotBlank()
            if (ready) "当前使用自定义 API · ${cfg.endpointBase}" else "自定义 API 未完整填写，请到设置页补全或切回默认 API"
        }
    }

    private fun pickMedia() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        }
        mediaPicker.launch(intent)
    }

    private fun handlePickedMedia(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val mime = contentResolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }
                if (!mime.startsWith("image/") && !mime.startsWith("video/")) {
                    withContext(Dispatchers.Main) { Toast.makeText(this@AIChatActivity, "请选择图片或视频", Toast.LENGTH_SHORT).show() }
                    return@launch
                }
                val name = queryDisplayName(uri) ?: if (mime.startsWith("image/")) "image" else "video"
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                if (bytes.isEmpty()) throw IllegalStateException("文件为空或无法读取")
                if (bytes.size > MAX_ATTACHMENT_BYTES) {
                    withContext(Dispatchers.Main) { Toast.makeText(this@AIChatActivity, "附件过大，请选择 15MB 以内的图片/视频", Toast.LENGTH_LONG).show() }
                    return@launch
                }
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                withContext(Dispatchers.Main) {
                    pendingAttachment = PendingAttachment(name, mime, bytes.size, b64)
                    refreshAttachmentHint()
                    Toast.makeText(this@AIChatActivity, "已添加：$name", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@AIChatActivity, "读取附件失败：${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, null, null, null, null)
            val idx = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME) ?: -1
            if (cursor?.moveToFirst() == true && idx >= 0) cursor?.getString(idx) else null
        } finally { cursor?.close() }
    }

    private fun refreshAttachmentHint() {
        val att = pendingAttachment
        if (att == null) {
            tvAttachmentHint.visibility = View.GONE
            tvAttachmentHint.text = ""
        } else {
            tvAttachmentHint.visibility = View.VISIBLE
            tvAttachmentHint.text = "已附加：${att.name} · ${formatSize(att.size)}（点击移除）"
        }
    }

    private fun sendCurrentMessage() {
        val text = etInput.text.toString().trim()
        val attachment = pendingAttachment
        val cfg = currentConfig()
        val model = getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_MODEL, modelOptions.first()).orEmpty().ifBlank { modelOptions.first() }
        if (text.isEmpty() && attachment == null) { Toast.makeText(this, "请输入消息或添加图片/视频", Toast.LENGTH_SHORT).show(); return }
        if (cfg.apiKey.isBlank() || cfg.endpointBase.isBlank()) { Toast.makeText(this, "请先在设置中配置 AI API", Toast.LENGTH_SHORT).show(); return }
        etInput.setText("")
        pendingAttachment = null
        refreshAttachmentHint()
        val displayText = buildDisplayText(text, attachment)
        addMessage("user", displayText)
        val aiView = addMessage("assistant", "正在思考…", persist = false)
        setSending(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val reply = requestAi(cfg.apiKey, normalizeEndpoint(cfg.endpointBase), model, text, attachment)
            withContext(Dispatchers.Main) {
                aiView.text = reply
                messages.add(ChatMessage("assistant", reply))
                trimHistory()
                saveHistory()
                setSending(false)
            }
        }
    }

    private fun buildDisplayText(text: String, attachment: PendingAttachment?): String {
        if (attachment == null) return text
        val prefix = if (attachment.mime.startsWith("image/")) "[图片]" else "[视频]"
        return listOf(text, "$prefix ${attachment.name} · ${formatSize(attachment.size)}").filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun setSending(sending: Boolean) {
        btnSend.isEnabled = !sending
        btnSend.text = if (sending) "…" else "发送"
        btnAttach.isEnabled = !sending
        etInput.isEnabled = !sending
    }

    private fun currentConfig(): AiConfig {
        val sp = getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val useDefault = sp.getBoolean(KEY_USE_DEFAULT, true)
        return if (useDefault) {
            AiConfig(true, DEFAULT_ENDPOINT, DEFAULT_API_KEY)
        } else {
            AiConfig(false, sp.getString(KEY_CUSTOM_ENDPOINT, DEFAULT_ENDPOINT).orEmpty(), sp.getString(KEY_CUSTOM_API_KEY, "").orEmpty())
        }
    }

    private fun normalizeEndpoint(endpoint: String): String {
        val clean = endpoint.trim().trimEnd('/')
        return if (clean.endsWith("/chat/completions")) clean else "$clean/v1/chat/completions"
    }

    private fun requestAi(apiKey: String, endpoint: String, model: String, latestText: String, attachment: PendingAttachment?): String {
        return try {
            val arr = JSONArray()
            arr.put(JSONObject().put("role", "system").put("content", "你是 YunoTools 内置AI助手，回答要简洁、可靠、中文优先。用户可能会附加图片或视频；如果当前模型无法理解视频，请说明限制并基于文件信息给出下一步建议。"))
            messages.takeLast(15).forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }
            arr.put(JSONObject().put("role", "user").put("content", buildRequestContent(latestText, attachment)))
            val bodyJson = JSONObject().put("model", model).put("stream", false).put("temperature", 0.7).put("messages", arr)
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://github.com/lanyuanshi/YunoTools")
                .addHeader("X-Title", "YunoTools")
                .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return "AI请求失败：${resp.code}\n${raw.take(300)}"
                JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "AI没有返回内容").trim().ifEmpty { "AI没有返回内容" }
            }
        } catch (e: Exception) { "网络或解析错误：${e.message ?: "未知错误"}" }
    }

    private fun buildRequestContent(text: String, attachment: PendingAttachment?): Any {
        if (attachment == null) return text
        val prompt = text.ifBlank { "请分析这个附件。" }
        return if (attachment.mime.startsWith("image/")) {
            JSONArray()
                .put(JSONObject().put("type", "text").put("text", prompt))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:${attachment.mime};base64,${attachment.base64}")))
        } else {
            "$prompt\n\n用户附加了一个视频文件：${attachment.name}，类型：${attachment.mime}，大小：${formatSize(attachment.size)}。视频内容以 base64 data URI 提供：data:${attachment.mime};base64,${attachment.base64}\n如果当前模型或接口不支持直接解析视频，请明确说明，并提示用户换用支持视频理解的模型或上传关键帧图片。"
        }
    }

    private fun addMessage(role: String, content: String, persist: Boolean = true): TextView {
        if (persist) { messages.add(ChatMessage(role, content)); trimHistory(); saveHistory() }
        val tv = TextView(this).apply {
            text = content
            textSize = 15f
            setTextColor(if (role == "user") 0xFFFFFFFF.toInt() else 0xFF1C1C1E.toInt())
            setBackgroundResource(if (role == "user") R.drawable.bg_chat_user else R.drawable.bg_chat_ai)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = if (role == "user") Gravity.END else Gravity.START
            setMargins(dp(16), dp(6), dp(16), dp(6))
            width = (resources.displayMetrics.widthPixels * 0.78f).toInt()
        }
        messageContainer.addView(tv, lp)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        return tv
    }

    private fun renderAll() { messageContainer.removeAllViews(); messages.forEach { addMessage(it.role, it.content, persist = false) } }
    private fun loadHistory() { runCatching { val arr = JSONArray(getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_HISTORY, "[]") ?: "[]"); for (i in 0 until arr.length()) { val obj = arr.getJSONObject(i); messages.add(ChatMessage(obj.getString("role"), obj.getString("content"))) } } }
    private fun saveHistory() { val arr = JSONArray(); messages.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }; getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_HISTORY, arr.toString()).apply() }
    private fun trimHistory() { while (messages.size > 40) messages.removeAt(0) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun formatSize(size: Int): String = if (size >= 1024 * 1024) String.format("%.1fMB", size / 1024f / 1024f) else String.format("%.0fKB", size / 1024f)

    data class ChatMessage(val role: String, val content: String)
    data class AiConfig(val useDefault: Boolean, val endpointBase: String, val apiKey: String)
    data class PendingAttachment(val name: String, val mime: String, val size: Int, val base64: String)

    companion object {
        const val PREF = "ai_chat"
        const val KEY_HISTORY = "history"
        const val KEY_MODEL = "model"
        const val KEY_USE_DEFAULT = "use_default_api"
        const val KEY_CUSTOM_API_KEY = "custom_api_key"
        const val KEY_CUSTOM_ENDPOINT = "custom_endpoint"
        const val DEFAULT_ENDPOINT = "https://flxapi.cc"
        const val DEFAULT_API_KEY = "sk-325f3a00dfe2a102b143fc18d60934e591f1fca80eb1e5ecae086d0ee8a7eb0a"
        const val MAX_ATTACHMENT_BYTES = 15 * 1024 * 1024
    }
}

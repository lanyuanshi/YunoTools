package com.yuno.tools.ui.tools

import android.content.Context
import android.os.Bundle
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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.yuno.tools.R
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
    private lateinit var btnSend: MaterialButton
    private lateinit var btnClear: ImageButton

    private val messages = mutableListOf<ChatMessage>()
    private val modelOptions = listOf(
        "deepseek/deepseek-chat-v3-0324",
        "deepseek-chat",
        "gpt-4o-mini",
        "gpt-4o",
        "gpt-4.1-mini",
        "gpt-4.1",
        "gpt-4-turbo",
        "gpt-4",
        "gpt-3.5-turbo",
        "o4-mini",
        "o3-mini",
        "claude-3-5-sonnet",
        "gemini-1.5-flash"
    )
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        btnClear = findViewById(R.id.btnClear)
        scrollView = findViewById(R.id.scrollMessages)
        messageContainer = findViewById(R.id.messageContainer)
        etInput = findViewById(R.id.etInput)
        spinnerModel = findViewById(R.id.spinnerModel)
        tvApiHint = findViewById(R.id.tvApiHint)
        btnSend = findViewById(R.id.btnSend)

        bindModelSelector()
        loadHistory()
        renderAll()
        if (messages.isEmpty()) {
            addMessage("assistant", "你好，我是 Yuno AI。API 已移动到设置页，可使用默认配置或自定义配置；这里直接选择模型并发送问题即可。")
        }
        btnSend.setOnClickListener { sendCurrentMessage() }
        btnClear.setOnClickListener {
            messages.clear()
            saveHistory()
            renderAll()
            addMessage("assistant", "已清空历史，重新开始聊天。")
        }
    }

    override fun onResume() {
        super.onResume()
        refreshApiHint()
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

    private fun sendCurrentMessage() {
        val text = etInput.text.toString().trim()
        val cfg = currentConfig()
        val model = getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_MODEL, modelOptions.first()).orEmpty().ifBlank { modelOptions.first() }
        if (text.isEmpty()) { Toast.makeText(this, "请输入消息", Toast.LENGTH_SHORT).show(); return }
        if (cfg.apiKey.isBlank() || cfg.endpointBase.isBlank()) { Toast.makeText(this, "请先在设置中配置 AI API", Toast.LENGTH_SHORT).show(); return }

        etInput.setText("")
        addMessage("user", text)
        val aiView = addMessage("assistant", "正在思考…", persist = false)
        setSending(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val reply = requestAi(cfg.apiKey, normalizeEndpoint(cfg.endpointBase), model)
            withContext(Dispatchers.Main) {
                aiView.text = reply
                messages.add(ChatMessage("assistant", reply))
                trimHistory()
                saveHistory()
                setSending(false)
            }
        }
    }

    private fun setSending(sending: Boolean) {
        btnSend.isEnabled = !sending
        btnSend.text = if (sending) "…" else "↑"
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

    private fun requestAi(apiKey: String, endpoint: String, model: String): String {
        return try {
            val arr = JSONArray()
            arr.put(JSONObject().put("role", "system").put("content", "你是 YunoTools 内置AI助手，回答要简洁、可靠、中文优先。"))
            messages.takeLast(16).forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }
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

    data class ChatMessage(val role: String, val content: String)
    data class AiConfig(val useDefault: Boolean, val endpointBase: String, val apiKey: String)

    companion object {
        const val PREF = "ai_chat"
        const val KEY_HISTORY = "history"
        const val KEY_MODEL = "model"
        const val KEY_USE_DEFAULT = "use_default_api"
        const val KEY_CUSTOM_API_KEY = "custom_api_key"
        const val KEY_CUSTOM_ENDPOINT = "custom_endpoint"
        const val DEFAULT_ENDPOINT = "https://flxapi.cc"
        const val DEFAULT_API_KEY = "sk-325f3a00dfe2a102b143fc18d60934e591f1fca80eb1e5ecae086d0ee8a7eb0a"
    }
}

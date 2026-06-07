package com.yuno.tools.ui.tools

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
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
    private lateinit var etModel: EditText
    private lateinit var etApiKey: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var btnClear: ImageButton

    private val messages = mutableListOf<ChatMessage>()
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
        etModel = findViewById(R.id.etModel)
        etApiKey = findViewById(R.id.etApiKey)
        btnSend = findViewById(R.id.btnSend)
        val sp = getSharedPreferences("ai_chat", Context.MODE_PRIVATE)
        etModel.setText(sp.getString("model", "deepseek/deepseek-chat-v3-0324"))
        etApiKey.setText(sp.getString("api_key", ""))
        loadHistory()
        renderAll()
        if (messages.isEmpty()) addMessage("assistant", "你好，我是 Yuno AI。你可以问我文案、解析思路、工具用法或日常问题。首次使用请填入 OpenRouter API Key。")
        btnSend.setOnClickListener { sendCurrentMessage() }
        btnClear.setOnClickListener {
            messages.clear()
            saveHistory()
            renderAll()
            addMessage("assistant", "已清空历史，重新开始聊天。")
        }
    }

    private fun sendCurrentMessage() {
        val text = etInput.text.toString().trim()
        val key = etApiKey.text.toString().trim()
        val model = etModel.text.toString().trim().ifEmpty { "deepseek/deepseek-chat-v3-0324" }
        if (text.isEmpty()) { Toast.makeText(this, "请输入消息", Toast.LENGTH_SHORT).show(); return }
        if (key.isEmpty()) { Toast.makeText(this, "请先填写 OpenRouter API Key", Toast.LENGTH_SHORT).show(); return }
        getSharedPreferences("ai_chat", Context.MODE_PRIVATE).edit().putString("api_key", key).putString("model", model).apply()
        etInput.setText("")
        addMessage("user", text)
        val aiView = addMessage("assistant", "正在思考…", persist = false)
        btnSend.isEnabled = false
        btnSend.text = "发送中"
        lifecycleScope.launch(Dispatchers.IO) {
            val reply = requestAi(key, model)
            withContext(Dispatchers.Main) {
                aiView.text = reply
                messages.add(ChatMessage("assistant", reply))
                trimHistory()
                saveHistory()
                btnSend.isEnabled = true
                btnSend.text = "发送"
            }
        }
    }

    private fun requestAi(apiKey: String, model: String): String {
        return try {
            val arr = JSONArray()
            arr.put(JSONObject().put("role", "system").put("content", "你是 YunoTools 内置AI助手，回答要简洁、可靠、中文优先。"))
            messages.takeLast(16).forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }
            val bodyJson = JSONObject().put("model", model).put("stream", false).put("temperature", 0.7).put("messages", arr)
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
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
    private fun loadHistory() { runCatching { val arr = JSONArray(getSharedPreferences("ai_chat", Context.MODE_PRIVATE).getString("history", "[]") ?: "[]"); for (i in 0 until arr.length()) { val obj = arr.getJSONObject(i); messages.add(ChatMessage(obj.getString("role"), obj.getString("content"))) } } }
    private fun saveHistory() { val arr = JSONArray(); messages.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }; getSharedPreferences("ai_chat", Context.MODE_PRIVATE).edit().putString("history", arr.toString()).apply() }
    private fun trimHistory() { while (messages.size > 40) messages.removeAt(0) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    data class ChatMessage(val role: String, val content: String)
}

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
        val url = etUrl.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "请输入视频链接", Toast.LENGTH_SHORT).show()
            return
        }

        progress.visibility = View.VISIBLE
        btnParse.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
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
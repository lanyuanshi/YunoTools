package com.yuno.tools.ui.tools

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.bumptech.glide.signature.ObjectKey
import com.yuno.tools.R
import com.yuno.tools.util.ThemeApplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class HeisiImageActivity : AppCompatActivity() {
    private val apiUrl = "https://v2.xxapi.cn/api/heisi?return=302"
    private var currentImageUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heisi_image)
        ThemeApplier.apply(this)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { loadImage() }
        findViewById<ImageView>(R.id.ivHeisi).setOnClickListener { loadImage() }
        findViewById<ImageView>(R.id.ivHeisi).setOnLongClickListener { showImageActions(); true }
        findViewById<Button>(R.id.btnSave).setOnClickListener { saveCurrentImage() }
        findViewById<Button>(R.id.btnShare).setOnClickListener { shareCurrentImage() }
        loadImage()
    }

    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
    }

    private fun loadImage() {
        setLoading(true, "正在加载图片...")
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching { fetchImageUrl() }
            withContext(Dispatchers.Main) {
                result.onSuccess { url ->
                    if (url.isBlank()) {
                        setLoading(false, "接口没有返回图片地址")
                        toast("接口没有返回图片地址")
                    } else {
                        currentImageUrl = url
                        renderImage(url)
                    }
                }.onFailure { e ->
                    setLoading(false, "加载失败：${e.message ?: "网络异常"}")
                    toast("加载失败，请稍后重试")
                }
            }
        }
    }

    private fun fetchImageUrl(): String {
        val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("User-Agent", "Mozilla/5.0 YunoTools")
            setRequestProperty("Accept", "application/json,image/*,*/*")
        }
        val code = conn.responseCode
        val finalUrl = conn.url?.toString().orEmpty()
        val contentType = conn.contentType.orEmpty().lowercase()
        if (code in 300..399) {
            conn.getHeaderField("Location")?.takeIf { it.isNotBlank() }?.let { location ->
                conn.disconnect()
                return normalizeImageUrl(location)
            }
        }
        if (contentType.startsWith("image/")) {
            conn.disconnect()
            return finalUrl.ifBlank { apiUrl }
        }
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty().trim()
        conn.disconnect()
        if (body.isBlank()) return ""
        if (body.startsWith("http://") || body.startsWith("https://")) return normalizeImageUrl(body)
        runCatching {
            val json = JSONObject(body)
            val keys = listOf("url", "img", "image", "pic", "link", "data")
            for (key in keys) {
                val value = json.optString(key, "").trim()
                if (value.startsWith("http://") || value.startsWith("https://")) return normalizeImageUrl(value)
            }
            val data = json.optJSONObject("data")
            if (data != null) {
                for (key in keys) {
                    val value = data.optString(key, "").trim()
                    if (value.startsWith("http://") || value.startsWith("https://")) return normalizeImageUrl(value)
                }
            }
        }
        return Regex("https?://[^\\s\"']+").find(body)?.value?.let { normalizeImageUrl(it) }.orEmpty()
    }

    private fun normalizeImageUrl(url: String): String = url.trim().trim('\"', '\'', ' ', '\n', '\r', '\t')

    private fun renderImage(url: String) {
        setLoading(true, "图片加载中，点击图片可换一张")
        val iv = findViewById<ImageView>(R.id.ivHeisi)
        Glide.with(iv)
            .load(url)
            .signature(ObjectKey(url + "#heisi#" + System.currentTimeMillis()))
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    setLoading(false, "图片加载失败，点换图重试")
                    return false
                }
                override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                    setLoading(false, "已加载 · 点击图片可继续换图")
                    return false
                }
            })
            .into(iv)
    }

    private fun saveCurrentImage() {
        val url = currentImageUrl
        if (url.isBlank()) { toast("请先加载图片"); return }
        setLoading(true, "正在保存到相册...")
        Glide.with(this).asBitmap().load(url).skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val saved = runCatching { saveBitmapToGallery(resource) }
                        withContext(Dispatchers.Main) {
                            setLoading(false, if (saved.isSuccess) "已保存到相册" else "保存失败")
                            toast(if (saved.isSuccess) "已保存到相册" else "保存失败")
                        }
                    }
                }
                override fun onLoadCleared(placeholder: Drawable?) {}
                override fun onLoadFailed(errorDrawable: Drawable?) { setLoading(false, "保存失败"); toast("保存失败") }
            })
    }

    private fun shareCurrentImage() {
        val url = currentImageUrl
        if (url.isBlank()) { toast("请先加载图片"); return }
        setLoading(true, "正在准备分享...")
        Glide.with(this).asBitmap().load(url).skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val uri = runCatching { saveBitmapToCache(resource) }
                        withContext(Dispatchers.Main) {
                            setLoading(false, "已准备分享")
                            uri.onSuccess { shareUri(it) }.onFailure { toast("分享失败") }
                        }
                    }
                }
                override fun onLoadCleared(placeholder: Drawable?) {}
                override fun onLoadFailed(errorDrawable: Drawable?) { setLoading(false, "分享失败"); toast("分享失败") }
            })
    }

    private fun saveBitmapToGallery(bitmap: Bitmap): Uri {
        val name = "YunoTools_Heisi_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YunoTools")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("创建媒体文件失败")
        contentResolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) } ?: error("写入媒体文件失败")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        }
        return uri
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri {
        val dir = File(cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "heisi_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
    }

    private fun shareUri(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "分享图片"))
    }

    private fun showImageActions() {
        if (currentImageUrl.isBlank()) {
            toast("请先加载图片")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("图片操作")
            .setItems(arrayOf("保存图片", "查看原图")) { _, which ->
                when (which) {
                    0 -> saveCurrentImage()
                    1 -> openOriginalImage()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openOriginalImage() {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(currentImageUrl)
            }
            startActivity(intent)
        }.onFailure {
            toast("无法打开原图")
        }
    }

    private fun setLoading(loading: Boolean, text: String) {
        findViewById<ProgressBar>(R.id.progress).visibility = if (loading) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvStatus).text = text
        findViewById<Button>(R.id.btnRefresh).isEnabled = !loading
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

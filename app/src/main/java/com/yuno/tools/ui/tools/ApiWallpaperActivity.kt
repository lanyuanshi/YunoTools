package com.yuno.tools.ui.tools

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.yuno.tools.R
import com.yuno.tools.util.ThemeApplier
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ApiWallpaperActivity : AppCompatActivity() {
    private val apis = arrayOf(
        "https://v2.xxapi.cn/api/wallpaper",
        "https://v2.xxapi.cn/api/randomAcgPic?type=pc",
        "https://v2.xxapi.cn/api/randomAcgPic?type=wap",
        "https://v2.xxapi.cn/api/jk",
        "https://v2.xxapi.cn/api/random4kPic?type=acg",
        "https://v2.xxapi.cn/api/random4kPic?type=wallpaper"
    )
    private val names = arrayOf("随机壁纸", "电脑横屏二次元", "手机竖屏二次元", "JK 写真", "动漫 4K", "风景 4K")
    private var selected = 0
    private var bitmap: Bitmap? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state); setContentView(R.layout.activity_api_wallpaper); ThemeApplier.apply(this)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { load() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnShare).setOnClickListener { share() }
        findViewById<Button>(R.id.btnSet).setOnClickListener { setWallpaper() }
        val ids = intArrayOf(R.id.cat1, R.id.cat2, R.id.cat3, R.id.cat4, R.id.cat5, R.id.cat6)
        ids.forEachIndexed { i, id -> findViewById<Button>(id).setOnClickListener { selected = i; style(ids); load() } }
        style(ids); load()
    }

    private fun style(ids: IntArray) { ids.forEachIndexed { i, id -> findViewById<Button>(id).apply { backgroundTintList = android.content.res.ColorStateList.valueOf(if (i == selected) 0xFF252A31.toInt() else 0xFFE7EBF0.toInt()); setTextColor(if (i == selected) -1 else 0xFF303742.toInt()) } } }
    private fun load() { status("正在加载${names[selected]}..."); bitmap = null; CoroutineScope(Dispatchers.IO).launch { val result = runCatching { fetch(apis[selected]) }; withContext(Dispatchers.Main) { result.onSuccess { url -> render(url) }.onFailure { status("加载失败，请点击换一张重试"); toast("加载失败") } } } }
    private fun fetch(api: String): String { val c = URL(api).openConnection() as HttpURLConnection; c.connectTimeout = 10000; c.readTimeout = 15000; c.setRequestProperty("User-Agent", "Mozilla/5.0 YunoTools"); val body = c.inputStream.bufferedReader().use { it.readText() }; c.disconnect(); return JSONObject(body).optString("data").trim() }
    private fun render(url: String) { val image = findViewById<ImageView>(R.id.image); status("正在渲染图片..."); Glide.with(this).asBitmap().load(url).skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE).into(object : CustomTarget<Bitmap>() { override fun onResourceReady(b: Bitmap, t: Transition<in Bitmap>?) { bitmap = b; image.setImageBitmap(b); status("${names[selected]} · ${b.width} × ${b.height}") }; override fun onLoadCleared(d: android.graphics.drawable.Drawable?) {}; override fun onLoadFailed(d: android.graphics.drawable.Drawable?) { status("图片加载失败，请点击换一张重试") } }) }
    private fun save() { val b = bitmap ?: return toast("请先加载图片"); CoroutineScope(Dispatchers.IO).launch { val ok = runCatching { val v = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, "YunoTools_Wallpaper_${System.currentTimeMillis()}.jpg"); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg"); if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YunoTools") }; contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)?.let { contentResolver.openOutputStream(it)?.use { o -> b.compress(Bitmap.CompressFormat.JPEG, 95, o) } } }; withContext(Dispatchers.Main) { toast(if (ok.isSuccess) "已保存到相册" else "保存失败") } } }
    private fun share() { val b = bitmap ?: return toast("请先加载图片"); CoroutineScope(Dispatchers.IO).launch { val f = File(cacheDir, "wallpaper.jpg"); f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }; withContext(Dispatchers.Main) { val u = FileProvider.getUriForFile(this@ApiWallpaperActivity, "${packageName}.fileprovider", f); startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "image/jpeg"; putExtra(Intent.EXTRA_STREAM, u); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "分享壁纸")) } } }
    private fun setWallpaper() { bitmap?.let { runCatching { WallpaperManager.getInstance(this).setBitmap(it); toast("已设置为系统壁纸") }.onFailure { toast("设置失败") } } ?: toast("请先加载图片") }
    private fun status(text: String) { findViewById<TextView>(R.id.status).text = text; findViewById<ProgressBar>(R.id.progress).visibility = if (text.startsWith("正在")) View.VISIBLE else View.GONE }
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
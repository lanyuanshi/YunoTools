package com.yuno.tools.ui.tools

import android.app.WallpaperManager
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

class WallpaperToolActivity : AppCompatActivity() {
    private lateinit var preview: ImageView
    private lateinit var info: TextView
    private var currentBitmap: Bitmap? = null
    private var currentName = "wallpaper"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(18), dp(16), dp(18)); setBackgroundColor(Color.parseColor("#F2F2F7")) }
        root.addView(TextView(this).apply { text = "获取当前壁纸"; textSize = 24f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.parseColor("#111827")); setPadding(0, 0, 0, dp(4)) })
        root.addView(TextView(this).apply { text = "可读取桌面壁纸；锁屏壁纸受系统权限限制，支持的设备会显示。"; textSize = 13f; setTextColor(Color.parseColor("#8A8F98")); setPadding(0, dp(4), 0, dp(12)) })
        preview = ImageView(this).apply { background = roundedBg("#E5E7EB", 18); scaleType = ImageView.ScaleType.CENTER_CROP; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(360)) }
        info = TextView(this).apply { textSize = 14f; setTextColor(Color.parseColor("#374151")); setPadding(0, dp(12), 0, dp(8)) }
        root.addView(row(button("桌面壁纸") { loadWallpaper(false) }, button("锁屏壁纸") { loadWallpaper(true) }, button("保存") { saveCurrent() }))
        root.addView(preview)
        root.addView(info)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun loadWallpaper(lock: Boolean) {
        val wm = WallpaperManager.getInstance(this)
        val drawable = runCatching {
            if (lock && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) wm.getDrawable(WallpaperManager.FLAG_LOCK) else wm.drawable
        }.getOrNull()
        val bmp = (drawable as? BitmapDrawable)?.bitmap
        if (bmp == null) {
            currentBitmap = null
            info.text = if (lock) "当前系统不允许读取锁屏壁纸，或未设置独立锁屏壁纸。" else "读取桌面壁纸失败。"
            preview.setImageDrawable(null)
            return
        }
        currentBitmap = bmp
        currentName = if (lock) "lock_wallpaper" else "home_wallpaper"
        preview.setImageBitmap(bmp)
        info.text = "已获取${if (lock) "锁屏" else "桌面"}壁纸：${bmp.width} × ${bmp.height}"
    }

    private fun saveCurrent() {
        val bmp = currentBitmap ?: return toast("请先获取壁纸")
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "YunoTools_${currentName}_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/YunoTools")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return toast("创建图片失败")
        contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        toast("已保存到相册/YunoTools")
    }


    private fun Drawable.toBitmapSafe(): Bitmap? {
        if (this is BitmapDrawable && bitmap != null) return bitmap
        val w = if (intrinsicWidth > 0) intrinsicWidth else resources.displayMetrics.widthPixels
        val h = if (intrinsicHeight > 0) intrinsicHeight else resources.displayMetrics.heightPixels
        return runCatching {
            Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = Canvas(bmp)
                setBounds(0, 0, canvas.width, canvas.height)
                draw(canvas)
            }
        }.getOrNull()
    }

    private fun roundedBg(color: String, radius: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = dp(radius).toFloat()
    }

    private fun row(vararg views: Button) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; views.forEach { addView(it, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(6) }) } }
    private fun button(t: String, action: () -> Unit) = Button(this).apply { text = t; setOnClickListener { action() } }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
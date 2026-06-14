package com.yuno.tools.ui.tools

import android.Manifest
import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class WallpaperToolActivity : AppCompatActivity() {
    private lateinit var preview: ImageView
    private lateinit var info: TextView
    private lateinit var status: TextView
    private var currentBitmap: Bitmap? = null
    private var currentName = "wallpaper"
    private var pendingLockRead = false
    private val requestLegacyPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) loadWallpaperInternal(pendingLockRead) else showPermissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F3F6FF"))
            addView(LinearLayout(this@WallpaperToolActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(18), dp(16), dp(24))
                addView(heroCard())
                addView(actionCard())
                addView(previewCard())
                addView(statusCard())
            })
        })
    }

    private fun heroCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = gradientBg("#667EEA", "#764BA2", 24)
        setPadding(dp(18), dp(18), dp(18), dp(18))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
        addView(TextView(context).apply {
            text = "当前壁纸"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        addView(TextView(context).apply {
            text = "读取桌面/锁屏壁纸，支持保存到相册。若 ROM 限制读取，会显示详细诊断。"
            textSize = 13.5f
            setTextColor(Color.argb(225, 255, 255, 255))
            setPadding(0, dp(8), 0, 0)
            setLineSpacing(dp(2).toFloat(), 1.0f)
        })
    }

    private fun actionCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBg("#FFFFFF", 22)
        elevation = dp(2).toFloat()
        setPadding(dp(14), dp(14), dp(14), dp(14))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
        addView(TextView(context).apply {
            text = "操作"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#111827"))
            setPadding(0, 0, 0, dp(10))
        })
        addView(row(
            pill("桌面壁纸", "#2563EB") { loadWallpaper(false) },
            pill("锁屏壁纸", "#7C3AED") { loadWallpaper(true) }
        ))
        addView(row(
            pill("保存图片", "#10B981") { saveCurrent() },
            pill("权限设置", "#F59E0B") { openWallpaperSettings() }
        ).apply { setPadding(0, dp(10), 0, 0) })
    }

    private fun previewCard() = FrameLayout(this).apply {
        background = roundedBg("#FFFFFF", 24)
        elevation = dp(2).toFloat()
        setPadding(dp(10), dp(10), dp(10), dp(10))
        layoutParams = LinearLayout.LayoutParams(-1, dp(430)).apply { bottomMargin = dp(14) }
        preview = ImageView(context).apply {
            background = roundedBg("#E5E7EB", 20)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageDrawable(null)
        }
        addView(preview, FrameLayout.LayoutParams(-1, -1))
        addView(TextView(context).apply {
            text = "预览区域"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = roundedBg("#66000000", 14)
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { leftMargin = dp(16); topMargin = dp(16) })
    }

    private fun statusCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBg("#FFFFFF", 22)
        elevation = dp(2).toFloat()
        setPadding(dp(14), dp(14), dp(14), dp(14))
        addView(TextView(context).apply {
            text = "状态 / 诊断"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#111827"))
            setPadding(0, 0, 0, dp(8))
        })
        status = TextView(context).apply {
            text = "请选择要读取的壁纸。"
            textSize = 13f
            setTextColor(Color.parseColor("#4B5563"))
            setLineSpacing(dp(3).toFloat(), 1.0f)
        }
        info = status
        addView(status)
    }

    private fun loadWallpaper(lock: Boolean) {
        pendingLockRead = lock
        val label = if (lock) "锁屏" else "桌面"
        status.text = "正在读取${label}壁纸..."
        preview.setImageDrawable(null)
        currentBitmap = null

        val missingPermissions = wallpaperPermissions().filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missingPermissions.isNotEmpty()) {
            status.text = "当前设备需要读取图片/存储权限后才能访问系统壁纸，正在请求权限..."
            requestLegacyPermission.launch(missingPermissions.toTypedArray())
            return
        }

        loadWallpaperInternal(lock)
    }

    private fun loadWallpaperInternal(lock: Boolean) {
        val label = if (lock) "锁屏" else "桌面"
        val report = StringBuilder()
        val wm = WallpaperManager.getInstance(this)
        val result = readWallpaperBitmap(wm, lock, report)
        if (result == null) {
            currentBitmap = null
            status.text = buildString {
                append("读取${label}壁纸失败。\n\n")
                append(report.toString())
                append("\n可能原因：\n")
                append("1. 当前 ROM 禁止普通应用读取壁纸原图；\n")
                append("2. 锁屏壁纸未单独设置，或系统不开放 FLAG_LOCK；\n")
                append("3. 使用动态壁纸/主题壁纸时系统不返回 Bitmap；\n")
                append("4. 如一个木函可读取，可能用了该机型的私有适配或系统级权限。")
            }
            return
        }
        currentBitmap = result
        currentName = if (lock) "lock_wallpaper" else "home_wallpaper"
        preview.setImageBitmap(result)
        status.text = buildString {
            append("已获取${label}壁纸：${result.width} × ${result.height}\n\n")
            append(report.toString())
        }
    }

    private fun wallpaperPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun showPermissionDenied() {
        status.text = "未授予读取图片/存储权限，无法继续读取壁纸。请到系统设置里允许“照片和视频/存储”权限后重试。"
        toast("需要读取存储权限")
    }

    private fun readWallpaperBitmap(wm: WallpaperManager, lock: Boolean, report: StringBuilder): Bitmap? {
        fun log(ok: Boolean, name: String, extra: String = "") {
            report.append(if (ok) "✓ " else "✕ ").append(name)
            if (extra.isNotBlank()) report.append("：").append(extra)
            report.append('\n')
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val flag = if (lock) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
            try {
                val pfd = wm.getWallpaperFile(flag)
                if (pfd == null) {
                    log(false, "getWallpaperFile(${if (lock) "FLAG_LOCK" else "FLAG_SYSTEM"})", "系统返回空")
                } else {
                    pfd.use {
                        val bmp = BitmapFactory.decodeFileDescriptor(it.fileDescriptor)
                        if (bmp != null) {
                            log(true, "getWallpaperFile", "${bmp.width}×${bmp.height}")
                            return bmp
                        } else {
                            log(false, "decodeFileDescriptor", "解码为空")
                        }
                    }
                }
            } catch (t: Throwable) {
                log(false, "getWallpaperFile", t.javaClass.simpleName + ": " + (t.message ?: "无信息"))
            }
        } else {
            log(false, "getWallpaperFile", "Android 7.0 以下不可用")
        }

        if (!lock) {
            try {
                val d = wm.drawable
                val bmp = d?.toBitmapSafe()
                if (bmp != null) {
                    log(true, "WallpaperManager.drawable", "${bmp.width}×${bmp.height}")
                    return bmp
                } else {
                    log(false, "WallpaperManager.drawable", "Drawable 为空或无法绘制")
                }
            } catch (t: Throwable) {
                log(false, "WallpaperManager.drawable", t.javaClass.simpleName + ": " + (t.message ?: "无信息"))
            }

            try {
                val d = wm.fastDrawable
                val bmp = d?.toBitmapSafe()
                if (bmp != null) {
                    log(true, "WallpaperManager.fastDrawable", "${bmp.width}×${bmp.height}")
                    return bmp
                } else {
                    log(false, "WallpaperManager.fastDrawable", "Drawable 为空或无法绘制")
                }
            } catch (t: Throwable) {
                log(false, "WallpaperManager.fastDrawable", t.javaClass.simpleName + ": " + (t.message ?: "无信息"))
            }
        } else {
            log(false, "Drawable fallback", "Android 不提供锁屏壁纸 Drawable fallback")
        }

        return null
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

    private fun openWallpaperSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = android.net.Uri.parse("package:$packageName") }) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun row(vararg views: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEachIndexed { index, v ->
            addView(v, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                if (index != views.lastIndex) rightMargin = dp(10)
            })
        }
    }

    private fun pill(textValue: String, color: String, action: () -> Unit) = Button(this).apply {
        text = textValue
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        background = roundedBg(color, 18)
        stateListAnimator = null
        setOnClickListener { action() }
    }

    private fun roundedBg(color: String, radius: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = dp(radius).toFloat()
    }

    private fun gradientBg(start: String, end: String, radius: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor(start), Color.parseColor(end))
    ).apply { cornerRadius = dp(radius).toFloat() }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}

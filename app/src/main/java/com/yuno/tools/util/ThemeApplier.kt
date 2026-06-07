package com.yuno.tools.util
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.WindowCompat
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.data.UserSettingsStore

data class YunoTheme(val bg:Int, val card:Int, val primary:Int, val text:Int, val sub:Int)

object ThemeApplier {
    private val skipTintIds = setOf("ivAvatar", "ivAvatarPreview", "ivCover", "ivPreview", "ivCompressed", "ivQRCode", "ivGridItem")
    fun current(activity: Activity): YunoTheme = when (UserSettingsStore.getTheme(activity)) {
        UserSettingsStore.THEME_BLACK -> YunoTheme(Color.parseColor("#111114"), Color.parseColor("#1F1F24"), Color.parseColor("#8AB4FF"), Color.WHITE, Color.parseColor("#B8BBC2"))
        UserSettingsStore.THEME_PINK -> YunoTheme(Color.parseColor("#FFF1F7"), Color.WHITE, Color.parseColor("#FF5FA2"), Color.parseColor("#231824"), Color.parseColor("#8B6475"))
        UserSettingsStore.THEME_BLUE -> YunoTheme(Color.parseColor("#EFF6FF"), Color.WHITE, Color.parseColor("#007AFF"), Color.parseColor("#111827"), Color.parseColor("#64748B"))
        else -> YunoTheme(Color.parseColor("#F2F2F7"), Color.WHITE, Color.parseColor("#007AFF"), Color.parseColor("#1C1C1E"), Color.parseColor("#8E8E93"))
    }
    fun apply(activity: Activity) {
        val theme = current(activity)
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        root.setBackgroundColor(theme.bg)
        applyView(root, theme)

        // 系统状态栏 & 导航栏
        val window = activity.window
        window.statusBarColor = theme.bg
        window.navigationBarColor = theme.bg
        val isLightBg = isLight(theme.bg)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = isLightBg
            isAppearanceLightNavigationBars = isLightBg
        }
    }
    private fun applyView(v: View, t: YunoTheme) {
        val idName = runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull().orEmpty()
        if (idName.endsWith("Root") || idName == "mainRoot") v.setBackgroundColor(t.bg)

        // 首页顶部元素
        when (idName) {
            "statusBarPlaceholder" -> v.setBackgroundColor(t.bg)
            "tvMainTitle" -> {
                v.setBackgroundColor(t.bg)
                (v as? TextView)?.setTextColor(t.text)
            }
            "bottomNavContainer" -> v.setBackgroundColor(t.bg)
            "bottomNavInner" -> {
                applyBottomNav(v, t)
                return
            }
        }

        when (v) {
            is MaterialCardView -> v.setCardBackgroundColor(t.card)
            is TextView -> {
                if (idName == "tvMainTitle") { /* 已精确处理 */ }
                else {
                    val text = v.text?.toString() ?: ""
                    val small = v.textSize <= 42f || text.contains("共 ") || text.contains("暂无") || text.contains("点击") || text.contains("条")
                    v.setTextColor(if (small && !v.typeface?.isBold.orFalse()) t.sub else t.text)
                }
            }
            is ImageView -> {
                if (idName in skipTintIds) v.clearColorFilter() else try { v.setColorFilter(t.primary) } catch (_: Exception) {}
            }
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) applyView(v.getChildAt(i), t)
    }

    private fun applyBottomNav(v: View, t: YunoTheme) {
        val density = v.resources.displayMetrics.density
        val isDefault = t.bg == Color.parseColor("#F2F2F7")
        val capsuleColor = t.card
        val selectedBg = if (isDefault) Color.parseColor("#E8F3FF") else blend(t.primary, t.card, 0.18f)
        val selectedColor = if (isDefault) Color.parseColor("#1E88E5") else t.primary
        val unselectedColor = if (isDefault) Color.parseColor("#A0A7B3") else t.sub

        v.background = roundRect(capsuleColor, 28f * density)
        val group = v as? ViewGroup ?: return
        for (i in 0 until group.childCount) {
            val item = group.getChildAt(i)
            val itemId = runCatching { item.resources.getResourceEntryName(item.id) }.getOrNull().orEmpty()
            val selected = itemId == "navChat"
            item.background = if (selected) roundRect(selectedBg, 18f * density) else null
            if (item is ViewGroup) {
                for (j in 0 until item.childCount) {
                    when (val child = item.getChildAt(j)) {
                        is ImageView -> child.setColorFilter(if (selected) selectedColor else unselectedColor)
                        is TextView -> child.setTextColor(if (selected) selectedColor else unselectedColor)
                    }
                }
            }
        }
    }

    private fun roundRect(color: Int, radiusPx: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radiusPx
    }

    private fun blend(fg: Int, bg: Int, alpha: Float): Int {
        val a = alpha.coerceIn(0f, 1f)
        val r = (Color.red(fg) * a + Color.red(bg) * (1f - a)).toInt()
        val g = (Color.green(fg) * a + Color.green(bg) * (1f - a)).toInt()
        val b = (Color.blue(fg) * a + Color.blue(bg) * (1f - a)).toInt()
        return Color.rgb(r, g, b)
    }

    private fun isLight(color: Int): Boolean {
        val yiq = ((Color.red(color) * 299) + (Color.green(color) * 587) + (Color.blue(color) * 114)) / 1000
        return yiq >= 128
    }

    private fun Boolean?.orFalse() = this ?: false
}


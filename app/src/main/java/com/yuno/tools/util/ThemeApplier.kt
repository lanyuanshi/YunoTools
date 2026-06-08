package com.yuno.tools.util

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.WindowCompat
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.R
import com.yuno.tools.data.UserSettingsStore

data class YunoTheme(
    val bg: Int,
    val card: Int,
    val primary: Int,
    val text: Int,
    val sub: Int,
    val imageBg: Boolean = false
)

object ThemeApplier {
    private const val AMIS_BG_TAG = "amis_theme_background"
    private const val YUNO_BG_TAG = "yuno_theme_background"
    private const val FEI_XUE_1_BG_TAG = "fei_xue_1_theme_background"
    private const val FEI_XUE_2_BG_TAG = "fei_xue_2_theme_background"
    private const val FEI_XUE_3_BG_TAG = "fei_xue_3_theme_background"
    private val skipTintIds = setOf("ivAvatar", "ivAvatarPreview", "ivCover", "ivPreview", "ivCompressed", "ivQRCode", "ivGridItem")

    fun current(activity: Activity): YunoTheme = when (UserSettingsStore.getTheme(activity)) {
        UserSettingsStore.THEME_BLACK -> YunoTheme(Color.parseColor("#111114"), Color.parseColor("#1F1F24"), Color.parseColor("#8AB4FF"), Color.WHITE, Color.parseColor("#B8BBC2"))
        UserSettingsStore.THEME_PINK -> YunoTheme(Color.parseColor("#FFF1F7"), Color.WHITE, Color.parseColor("#FF5FA2"), Color.parseColor("#231824"), Color.parseColor("#8B6475"))
        UserSettingsStore.THEME_BLUE -> YunoTheme(Color.parseColor("#EFF6FF"), Color.WHITE, Color.parseColor("#007AFF"), Color.parseColor("#111827"), Color.parseColor("#64748B"))
        UserSettingsStore.THEME_AMIS -> YunoTheme(Color.parseColor("#EAF4FF"), Color.argb(218, 255, 255, 255), Color.parseColor("#FB7DB8"), Color.parseColor("#223044"), Color.parseColor("#6D7890"), true)
        UserSettingsStore.THEME_YUNO -> YunoTheme(Color.parseColor("#FFF0F7"), Color.argb(224, 255, 255, 255), Color.parseColor("#FF6FAE"), Color.parseColor("#231827"), Color.parseColor("#7A5F73"), true)
        UserSettingsStore.THEME_FEI_XUE_1 -> YunoTheme(Color.parseColor("#FFF7FA"), Color.argb(228, 255, 255, 255), Color.parseColor("#F26C9B"), Color.parseColor("#2B1A25"), Color.parseColor("#8B6677"), true)
        UserSettingsStore.THEME_FEI_XUE_2 -> YunoTheme(Color.parseColor("#FFF4F9"), Color.argb(228, 255, 255, 255), Color.parseColor("#E86FA9"), Color.parseColor("#2B1B2D"), Color.parseColor("#86647E"), true)
        UserSettingsStore.THEME_FEI_XUE_3 -> YunoTheme(Color.parseColor("#F2F4FF"), Color.argb(226, 255, 255, 255), Color.parseColor("#8D7BFF"), Color.parseColor("#1D2035"), Color.parseColor("#62677F"), true)
        else -> YunoTheme(Color.parseColor("#F2F2F7"), Color.WHITE, Color.parseColor("#007AFF"), Color.parseColor("#1C1C1E"), Color.parseColor("#8E8E93"))
    }

    fun apply(activity: Activity) {
        val theme = current(activity)
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (theme.imageBg) {
            val (bgRes, bgTag) = when (UserSettingsStore.getTheme(activity)) {
                UserSettingsStore.THEME_YUNO -> R.drawable.theme_yuno_bg to YUNO_BG_TAG
                UserSettingsStore.THEME_FEI_XUE_1 -> R.drawable.theme_fei_xue_1_bg to FEI_XUE_1_BG_TAG
                UserSettingsStore.THEME_FEI_XUE_2 -> R.drawable.theme_fei_xue_2_bg to FEI_XUE_2_BG_TAG
                UserSettingsStore.THEME_FEI_XUE_3 -> R.drawable.theme_fei_xue_3_bg to FEI_XUE_3_BG_TAG
                else -> R.drawable.theme_amis_bg to AMIS_BG_TAG
            }
            applyImageBackground(root, bgRes, bgTag)
        } else clearImageBackground(root)
        root.setBackgroundColor(if (theme.imageBg) Color.TRANSPARENT else theme.bg)
        applyView(root, theme)

        val window = activity.window
        window.statusBarColor = if (theme.imageBg) Color.argb(88, 255, 255, 255) else theme.bg
        window.navigationBarColor = if (theme.imageBg) Color.argb(232, 255, 255, 255) else theme.bg
        val isLightBg = isLight(theme.bg)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = isLightBg
            isAppearanceLightNavigationBars = isLightBg
        }
    }

    private fun isThemeBgTag(tag: Any?): Boolean = tag == AMIS_BG_TAG || tag == YUNO_BG_TAG || tag == FEI_XUE_1_BG_TAG || tag == FEI_XUE_2_BG_TAG || tag == FEI_XUE_3_BG_TAG

    private fun applyImageBackground(root: ViewGroup, resId: Int, tagValue: String) {
        val frame = root as? FrameLayout ?: return
        for (i in frame.childCount - 1 downTo 0) {
            val tag = frame.getChildAt(i).tag
            if (isThemeBgTag(tag) && tag != tagValue) frame.removeViewAt(i)
        }
        val exists = (0 until frame.childCount).map { frame.getChildAt(it) }.firstOrNull { it.tag == tagValue }
        if (exists != null) return
        val bg = ImageView(frame.context).apply {
            tag = tagValue
            setImageResource(resId)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.38f
            clearColorFilter()
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        frame.addView(bg, 0)
    }

    private fun clearImageBackground(root: ViewGroup) {
        val frame = root as? FrameLayout ?: return
        for (i in frame.childCount - 1 downTo 0) {
            val tag = frame.getChildAt(i).tag
            if (isThemeBgTag(tag)) frame.removeViewAt(i)
        }
    }

    private fun applyView(v: View, t: YunoTheme) {
        if (isThemeBgTag(v.tag)) return
        val idName = runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull().orEmpty()
        if (idName.endsWith("Root") || idName == "mainRoot") v.setBackgroundColor(if (t.imageBg) Color.TRANSPARENT else t.bg)

        when (idName) {
            "statusBarPlaceholder" -> v.setBackgroundColor(if (t.imageBg) Color.argb(88, 255, 255, 255) else t.bg)
            "tvMainTitle" -> {
                v.setBackgroundColor(if (t.imageBg) Color.TRANSPARENT else t.bg)
                (v as? TextView)?.setTextColor(t.text)
            }
            "bottomNavContainer" -> v.setBackgroundColor(if (t.imageBg) Color.TRANSPARENT else t.bg)
            "bottomNavInner" -> {
                applyBottomNav(v, t)
                return
            }
        }

        when (v) {
            is MaterialCardView -> {
                v.setCardBackgroundColor(t.card)
                if (t.imageBg) {
                    v.strokeWidth = (0.7f * v.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                    v.strokeColor = blend(t.primary, Color.WHITE, 0.30f)
                }
            }
            is TextView -> {
                if (idName != "tvMainTitle") {
                    val text = v.text?.toString() ?: ""
                    val small = v.textSize <= 42f || text.contains("共 ") || text.contains("暂无") || text.contains("点击") || text.contains("条") || text.startsWith("当前：")
                    v.setTextColor(if (small && !v.typeface?.isBold.orFalse()) t.sub else t.text)
                }
            }
            is ImageView -> {
                if (idName in skipTintIds || isThemeBgTag(v.tag)) v.clearColorFilter() else runCatching { v.setColorFilter(t.primary) }
            }
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) applyView(v.getChildAt(i), t)
    }

    private fun applyBottomNav(v: View, t: YunoTheme) {
        val density = v.resources.displayMetrics.density
        val isDefault = !t.imageBg && t.bg == Color.parseColor("#F2F2F7")
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

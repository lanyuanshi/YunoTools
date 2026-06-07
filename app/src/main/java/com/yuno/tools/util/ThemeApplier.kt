package com.yuno.tools.util
import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.data.UserSettingsStore

data class YunoTheme(val bg:Int, val card:Int, val primary:Int, val text:Int, val sub:Int)

object ThemeApplier {
    private val skipTintIds = setOf("ivAvatar", "ivAvatarPreview", "ivCover")
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
    }
    private fun applyView(v: View, t: YunoTheme) {
        val idName = runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull().orEmpty()
        if (idName.endsWith("Root") || idName == "mainRoot") v.setBackgroundColor(t.bg)
        when (v) {
            is MaterialCardView -> v.setCardBackgroundColor(t.card)
            is TextView -> {
                val text = v.text?.toString() ?: ""
                val small = v.textSize <= 42f || text.contains("共 ") || text.contains("暂无") || text.contains("点击") || text.contains("条")
                v.setTextColor(if (small && !v.typeface?.isBold.orFalse()) t.sub else t.text)
            }
            is ImageView -> {
                if (idName in skipTintIds) v.clearColorFilter() else try { v.setColorFilter(t.primary) } catch (_: Exception) {}
            }
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) applyView(v.getChildAt(i), t)
    }
    private fun Boolean?.orFalse() = this ?: false
}

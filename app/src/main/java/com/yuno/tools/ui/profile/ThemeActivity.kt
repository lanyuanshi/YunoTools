package com.yuno.tools.ui.profile

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.R
import com.yuno.tools.data.UserSettingsStore
import com.yuno.tools.util.ThemeApplier

class ThemeActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout

    private data class ThemeOption(
        val key: String,
        val title: String,
        val desc: String,
        val previewRes: Int,
        val imagePreview: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme)
        ThemeApplier.apply(this)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        list = findViewById(R.id.themeList)
        buildThemeList()
        refreshThemeState()
    }

    override fun onResume() {
        super.onResume()
        ThemeApplier.apply(this)
        refreshThemeState()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.profile_stay, R.anim.profile_slide_down_out)
    }

    private fun buildThemeList() {
        list.removeAllViews()
        options.forEach { option -> list.addView(createThemeCard(option)) }
    }

    private fun createThemeCard(option: ThemeOption): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
            radius = dp(22).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.WHITE)
            foreground = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).use { it.getDrawable(0) }
            setOnClickListener {
                UserSettingsStore.setTheme(this@ThemeActivity, option.key)
                ThemeApplier.apply(this@ThemeActivity)
                refreshThemeState()
                Toast.makeText(this@ThemeActivity, "已切换：${option.title}", Toast.LENGTH_SHORT).show()
            }
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        if (option.imagePreview) {
            val image = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                adjustViewBounds = true
                maxHeight = dp(420)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.parseColor("#F4F5F7"))
                setImageResource(option.previewRes)
                tag = ThemeApplier.THEME_PREVIEW_IMAGE_TAG
                clearColorFilter()
                contentDescription = "${option.title}原图比例预览"
            }
            box.addView(image)
        } else {
            box.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58))
                background = getDrawable(option.previewRes)
            })
        }

        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val textBox = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
        }
        textBox.addView(TextView(this).apply {
            text = option.title
            textSize = 16f
            setTextColor(Color.parseColor("#111827"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        textBox.addView(TextView(this).apply {
            text = option.desc
            textSize = 13f
            setTextColor(Color.parseColor("#8E8E93"))
        })
        row.addView(textBox)
        row.addView(TextView(this).apply {
            tag = "check_${option.key}"
            text = "已选"
            textSize = 13f
            setTextColor(ThemeApplier.current(this@ThemeActivity).primary)
            visibility = View.GONE
        })
        box.addView(row)
        card.addView(box)
        return card
    }

    private fun refreshThemeState() {
        val current = UserSettingsStore.getTheme(this)
        findViewById<TextView>(R.id.tvCurrentTheme).text = "当前：${themeDisplayName(current)}"
        val primary = ThemeApplier.current(this).primary
        for (i in 0 until list.childCount) {
            val card = list.getChildAt(i) as? MaterialCardView ?: continue
            val option = options.getOrNull(i) ?: continue
            val selected = option.key == current
            card.strokeWidth = if (selected) dp(2) else 0
            card.strokeColor = primary
            val check = card.findViewWithTag<TextView>("check_${option.key}")
            check?.visibility = if (selected) View.VISIBLE else View.GONE
            check?.setTextColor(primary)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val options = listOf(
            ThemeOption(UserSettingsStore.THEME_DEFAULT, "默认", "清爽浅色界面", R.drawable.bg_theme_swatch_default, false),
            ThemeOption(UserSettingsStore.THEME_BLACK, "黑色", "深色高对比界面", R.drawable.bg_theme_swatch_black, false),
            ThemeOption(UserSettingsStore.THEME_PINK, "粉色", "柔和粉色界面", R.drawable.bg_theme_swatch_pink, false),
            ThemeOption(UserSettingsStore.THEME_BLUE, "蓝色", "清透蓝色界面", R.drawable.bg_theme_swatch_blue, false),
            ThemeOption(UserSettingsStore.THEME_AMIS, "爱弥斯", "图片背景主题，按原比例预览", R.drawable.theme_amis_bg, true),
            ThemeOption(UserSettingsStore.THEME_YUNO, "尤诺", "图片背景主题，按原比例预览", R.drawable.theme_yuno_bg, true),
            ThemeOption(UserSettingsStore.THEME_FEI_XUE_1, "绯雪1", "图片背景主题，按原比例预览", R.drawable.theme_fei_xue_1_bg, true),
            ThemeOption(UserSettingsStore.THEME_FEI_XUE_2, "绯雪2", "图片背景主题，按原比例预览", R.drawable.theme_fei_xue_2_bg, true),
            ThemeOption(UserSettingsStore.THEME_FEI_XUE_3, "绯雪3", "图片背景主题，按原比例预览", R.drawable.theme_fei_xue_3_bg, true)
        )

        fun themeDisplayName(key: String): String = when (key) {
            UserSettingsStore.THEME_BLACK -> "黑色主题"
            UserSettingsStore.THEME_PINK -> "粉色主题"
            UserSettingsStore.THEME_BLUE -> "蓝色主题"
            UserSettingsStore.THEME_AMIS -> "爱弥斯主题"
            UserSettingsStore.THEME_YUNO -> "尤诺主题"
            UserSettingsStore.THEME_FEI_XUE_1 -> "绯雪1主题"
            UserSettingsStore.THEME_FEI_XUE_2 -> "绯雪2主题"
            UserSettingsStore.THEME_FEI_XUE_3 -> "绯雪3主题"
            else -> "默认主题"
        }
    }
}

private inline fun <T> android.content.res.TypedArray.use(block: (android.content.res.TypedArray) -> T): T {
    return try { block(this) } finally { recycle() }
}

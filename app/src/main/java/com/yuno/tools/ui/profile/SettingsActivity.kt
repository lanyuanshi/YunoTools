package com.yuno.tools.ui.profile
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.R
import com.yuno.tools.data.UserSettingsStore
import com.yuno.tools.util.ThemeApplier

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        ThemeApplier.apply(this)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        bindTheme(R.id.themeDefault, UserSettingsStore.THEME_DEFAULT)
        bindTheme(R.id.themeBlack, UserSettingsStore.THEME_BLACK)
        bindTheme(R.id.themePink, UserSettingsStore.THEME_PINK)
        bindTheme(R.id.themeBlue, UserSettingsStore.THEME_BLUE)
        bindTheme(R.id.themeAmis, UserSettingsStore.THEME_AMIS)
        refreshThemeState()
    }
    override fun onResume() { super.onResume(); ThemeApplier.apply(this); refreshThemeState() }
    private fun bindTheme(id: Int, theme: String) {
        findViewById<MaterialCardView>(id).setOnClickListener {
            UserSettingsStore.setTheme(this, theme)
            ThemeApplier.apply(this)
            refreshThemeState()
        }
    }
    private fun refreshThemeState() {
        val current = UserSettingsStore.getTheme(this)
        listOf(R.id.themeDefault to UserSettingsStore.THEME_DEFAULT, R.id.themeBlack to UserSettingsStore.THEME_BLACK, R.id.themePink to UserSettingsStore.THEME_PINK, R.id.themeBlue to UserSettingsStore.THEME_BLUE, R.id.themeAmis to UserSettingsStore.THEME_AMIS).forEach { (id, key) ->
            val card = findViewById<MaterialCardView>(id)
            card.strokeWidth = if (key == current) (2 * resources.displayMetrics.density).toInt() else 0
            card.strokeColor = ThemeApplier.current(this).primary
        }
        findViewById<TextView>(R.id.tvCurrentTheme).text = when(current){ UserSettingsStore.THEME_BLACK -> "当前：黑色主题"; UserSettingsStore.THEME_PINK -> "当前：粉色主题"; UserSettingsStore.THEME_BLUE -> "当前：蓝色主题"; UserSettingsStore.THEME_AMIS -> "当前：爱弥斯主题"; else -> "当前：默认主题" }
    }
}

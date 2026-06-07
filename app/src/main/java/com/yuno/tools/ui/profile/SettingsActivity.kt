package com.yuno.tools.ui.profile
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.yuno.tools.R
import com.yuno.tools.data.UserSettingsStore
import com.yuno.tools.util.ThemeApplier

class SettingsActivity : AppCompatActivity() {
    private val pickAvatar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        UserSettingsStore.persistUriPermission(this, uri)
        UserSettingsStore.setAvatarUri(this, uri.toString())
        loadAvatar()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        ThemeApplier.apply(this)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnChooseAvatar).setOnClickListener { pickAvatar.launch(arrayOf("image/*")) }
        bindTheme(R.id.themeDefault, UserSettingsStore.THEME_DEFAULT)
        bindTheme(R.id.themeBlack, UserSettingsStore.THEME_BLACK)
        bindTheme(R.id.themePink, UserSettingsStore.THEME_PINK)
        bindTheme(R.id.themeBlue, UserSettingsStore.THEME_BLUE)
        loadAvatar(); refreshThemeState()
    }
    override fun onResume() { super.onResume(); ThemeApplier.apply(this); loadAvatar(); refreshThemeState() }
    private fun bindTheme(id: Int, theme: String) {
        findViewById<MaterialCardView>(id).setOnClickListener {
            UserSettingsStore.setTheme(this, theme)
            ThemeApplier.apply(this)
            refreshThemeState()
        }
    }
    private fun loadAvatar() {
        val iv = findViewById<ImageView>(R.id.ivAvatarPreview)
        val uri = UserSettingsStore.getAvatarUri(this)
        if (uri.isNotBlank()) Glide.with(iv).load(uri).circleCrop().placeholder(R.drawable.bg_circle_blue).into(iv) else iv.setImageResource(R.drawable.ic_profile)
    }
    private fun refreshThemeState() {
        val current = UserSettingsStore.getTheme(this)
        listOf(R.id.themeDefault to UserSettingsStore.THEME_DEFAULT, R.id.themeBlack to UserSettingsStore.THEME_BLACK, R.id.themePink to UserSettingsStore.THEME_PINK, R.id.themeBlue to UserSettingsStore.THEME_BLUE).forEach { (id, key) ->
            val card = findViewById<MaterialCardView>(id)
            card.strokeWidth = if (key == current) (2 * resources.displayMetrics.density).toInt() else 0
            card.strokeColor = ThemeApplier.current(this).primary
        }
        findViewById<TextView>(R.id.tvCurrentTheme).text = when(current){ UserSettingsStore.THEME_BLACK -> "当前：黑色主题"; UserSettingsStore.THEME_PINK -> "当前：粉色主题"; UserSettingsStore.THEME_BLUE -> "当前：蓝色主题"; else -> "当前：默认主题" }
    }
}

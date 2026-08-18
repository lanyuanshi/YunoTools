package com.yuno.tools.data
import android.content.Context
import android.net.Uri

object UserSettingsStore {
    private const val PREF = "user_settings"
    private const val KEY_AVATAR = "avatar_uri"
    private const val KEY_THEME = "theme"
    private const val KEY_DYNAMIC_THEME_SOUND = "dynamic_theme_sound"
    private const val KEY_DYNAMIC_THEME_BLUR = "dynamic_theme_blur"
    private const val KEY_MUSIC_BAR_STYLE = "music_bar_style"
    private const val KEY_MUSIC_SPECTRUM_STYLE = "music_spectrum_style"
    private const val KEY_LYRIC_HIGHLIGHT_STYLE = "lyric_highlight_style"
    const val THEME_DEFAULT = "default"
    const val THEME_BLACK = "black"
    const val THEME_PINK = "pink"
    const val THEME_BLUE = "blue"
    const val THEME_AMIS = "amis"
    const val THEME_YUNO = "yuno"
    const val THEME_FEI_XUE_1 = "fei_xue_1"
    const val THEME_FEI_XUE_2 = "fei_xue_2"
    const val THEME_FEI_XUE_3 = "fei_xue_3"
    const val THEME_USER_IMAGE = "user_image"
    const val MUSIC_BAR_BLUE = "blue"
    const val MUSIC_BAR_RED = "red"
    const val MUSIC_BAR_GREEN = "green"
    const val MUSIC_BAR_PURPLE = "purple"
    const val MUSIC_BAR_MULTI = "multi"
    const val MUSIC_SPECTRUM_UP = "up"
    const val MUSIC_SPECTRUM_WAVE = "wave"
    const val LYRIC_HIGHLIGHT_BLUE = "blue"
    const val LYRIC_HIGHLIGHT_RED = "red"
    const val LYRIC_HIGHLIGHT_GREEN = "green"
    const val LYRIC_HIGHLIGHT_PURPLE = "purple"
    const val LYRIC_HIGHLIGHT_ORANGE = "orange"

    fun getAvatarUri(context: Context): String = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_AVATAR, "") ?: ""
    fun setAvatarUri(context: Context, uri: String) { context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_AVATAR, uri).apply() }
    fun getTheme(context: Context): String = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_THEME, THEME_DEFAULT) ?: THEME_DEFAULT
    fun setTheme(context: Context, theme: String) { context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_THEME, theme).apply() }
    fun isDynamicTheme(context: Context): Boolean = false
    fun getDynamicThemeSound(context: Context): Boolean = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_DYNAMIC_THEME_SOUND, false)
    fun setDynamicThemeSound(context: Context, enabled: Boolean) { context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_DYNAMIC_THEME_SOUND, enabled).apply() }
    fun getDynamicThemeBlur(context: Context): Int = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_DYNAMIC_THEME_BLUR, 18).coerceIn(0, 40)
    fun setDynamicThemeBlur(context: Context, blur: Int) { context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt(KEY_DYNAMIC_THEME_BLUR, blur.coerceIn(0, 40)).apply() }
    fun getMusicBarStyle(context: Context): String = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_MUSIC_BAR_STYLE, MUSIC_BAR_MULTI) ?: MUSIC_BAR_MULTI
    fun setMusicBarStyle(context: Context, style: String) { context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_MUSIC_BAR_STYLE, style).apply() }
    fun getMusicSpectrumStyle(context: Context): String {
        val style = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_MUSIC_SPECTRUM_STYLE, MUSIC_SPECTRUM_UP) ?: MUSIC_SPECTRUM_UP
        return when (style) {
            MUSIC_SPECTRUM_UP, MUSIC_SPECTRUM_WAVE -> style
            else -> MUSIC_SPECTRUM_UP
        }
    }
    fun setMusicSpectrumStyle(context: Context, style: String) {
        val safeStyle = when (style) {
            MUSIC_SPECTRUM_UP, MUSIC_SPECTRUM_WAVE -> style
            else -> MUSIC_SPECTRUM_UP
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_MUSIC_SPECTRUM_STYLE, safeStyle).apply()
    }
    fun getLyricHighlightStyle(context: Context): String = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_LYRIC_HIGHLIGHT_STYLE, LYRIC_HIGHLIGHT_BLUE) ?: LYRIC_HIGHLIGHT_BLUE
    fun setLyricHighlightStyle(context: Context, style: String) { context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_LYRIC_HIGHLIGHT_STYLE, style).apply() }
    fun persistUriPermission(context: Context, uri: Uri) {
        try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
    }
}

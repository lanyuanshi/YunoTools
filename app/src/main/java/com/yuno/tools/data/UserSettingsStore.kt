package com.yuno.tools.data
import android.content.Context
import android.net.Uri

object UserSettingsStore {
    private const val PREF = "user_settings"
    private const val KEY_AVATAR = "avatar_uri"
    private const val KEY_THEME = "theme"
    private const val KEY_MUSIC_BAR_STYLE = "music_bar_style"
    const val THEME_DEFAULT = "default"
    const val THEME_BLACK = "black"
    const val THEME_PINK = "pink"
    const val THEME_BLUE = "blue"
    const val THEME_AMIS = "amis"
    const val THEME_YUNO = "yuno"
    const val THEME_FEI_XUE_1 = "fei_xue_1"
    const val THEME_FEI_XUE_2 = "fei_xue_2"
    const val THEME_FEI_XUE_3 = "fei_xue_3"
    const val MUSIC_BAR_BLUE = "blue"
    const val MUSIC_BAR_RED = "red"
    const val MUSIC_BAR_GREEN = "green"
    const val MUSIC_BAR_PURPLE = "purple"
    const val MUSIC_BAR_MULTI = "multi"

    fun getAvatarUri(context: Context): String = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_AVATAR, "") ?: ""
    fun setAvatarUri(context: Context, uri: String) { context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_AVATAR, uri).apply() }
    fun getTheme(context: Context): String = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_THEME, THEME_DEFAULT) ?: THEME_DEFAULT
    fun setTheme(context: Context, theme: String) { context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_THEME, theme).apply() }
    fun getMusicBarStyle(context: Context): String = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_MUSIC_BAR_STYLE, MUSIC_BAR_MULTI) ?: MUSIC_BAR_MULTI
    fun setMusicBarStyle(context: Context, style: String) { context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_MUSIC_BAR_STYLE, style).apply() }
    fun persistUriPermission(context: Context, uri: Uri) {
        try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
    }
}

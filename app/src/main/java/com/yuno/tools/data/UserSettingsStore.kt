package com.yuno.tools.data
import android.content.Context
import android.net.Uri

object UserSettingsStore {
    private const val PREF = "user_settings"
    private const val KEY_AVATAR = "avatar_uri"
    private const val KEY_THEME = "theme"
    const val THEME_DEFAULT = "default"
    const val THEME_BLACK = "black"
    const val THEME_PINK = "pink"
    const val THEME_BLUE = "blue"
    const val THEME_AMIS = "amis"
    const val THEME_YUNO = "yuno"
    const val THEME_FEI_XUE_1 = "fei_xue_1"

    fun getAvatarUri(context: Context): String = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_AVATAR, "") ?: ""
    fun setAvatarUri(context: Context, uri: String) { context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_AVATAR, uri).apply() }
    fun getTheme(context: Context): String = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_THEME, THEME_DEFAULT) ?: THEME_DEFAULT
    fun setTheme(context: Context, theme: String) { context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_THEME, theme).apply() }
    fun persistUriPermission(context: Context, uri: Uri) {
        try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
    }
}

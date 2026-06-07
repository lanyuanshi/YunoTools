package com.yuno.tools.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ParseHistoryStore {
    private const val PREFS = "parse_history_prefs"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 80
    private val gson = Gson()

    data class ParseHistoryItem(
        val id: Long = System.currentTimeMillis(),
        val title: String = "",
        val coverUrl: String = "",
        val sourceUrl: String = "",
        val parsedAt: Long = System.currentTimeMillis(),
        val type: String = "video",
        val result: VideoParseResult = VideoParseResult()
    )

    fun add(context: Context, result: VideoParseResult, sourceUrl: String) {
        val list = getAll(context).toMutableList()
        val normalizedTitle = result.title.ifBlank { if (result.isImageSet) "图集解析" else "视频解析" }
        val item = ParseHistoryItem(
            title = normalizedTitle,
            coverUrl = result.coverUrl.ifBlank { result.images.firstOrNull().orEmpty() },
            sourceUrl = sourceUrl,
            type = if (result.isImageSet) "image_set" else "video",
            result = result.copy(title = normalizedTitle)
        )
        list.removeAll { it.sourceUrl == sourceUrl || (it.result.videoUrl.isNotBlank() && it.result.videoUrl == result.videoUrl) }
        list.add(0, item)
        save(context, list.take(MAX_ITEMS))
    }

    fun getAll(context: Context): List<ParseHistoryItem> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, "[]") ?: "[]"
        return runCatching {
            val type = object : TypeToken<List<ParseHistoryItem>>() {}.type
            gson.fromJson<List<ParseHistoryItem>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ITEMS).apply()
    }

    private fun save(context: Context, items: List<ParseHistoryItem>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, gson.toJson(items))
            .apply()
    }
}

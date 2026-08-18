package com.yuno.tools.util

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MusicSearchHelper {
    private const val KUWO_API = "https://api.qzqi.com/api/v1/KuwoMusic"
    private const val PREFS = "music_qzqi_api"
    private const val KEY_API = "api_key"

    enum class OnlineSource(val label: String) {
        KUWO("酷我音乐 · QZQI")
    }

    data class TimedLyric(
        val timeMs: Long,
        val text: String
    )

    data class OnlineSong(
        val title: String,
        val artist: String,
        val source: OnlineSource,
        val pageUrl: String,
        val playUrl: String?,
        val songId: String = ""
    )

    fun saveQzqiApiKey(context: Context, key: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API, key.trim().removePrefix("Bearer "))
            .apply()
    }

    fun getQzqiApiKey(context: Context): String {
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_API, "")
            .orEmpty()
            .trim()
            .removePrefix("Bearer ")
            .trim()
    }

    fun searchOnline(context: Context, keyword: String, callback: (List<OnlineSong>) -> Unit) {
        Thread {
            val trimmed = keyword.trim()
            if (trimmed.isBlank()) {
                callback(emptyList())
                return@Thread
            }
            val songs = runCatching { searchKuwo(context.applicationContext, trimmed) }.getOrElse { emptyList() }
                .distinctBy { it.pageUrl.ifBlank { itemKey(it) } }
            callback(songs)
        }.start()
    }

    @Deprecated("Use context-aware overload so QZQI API key can be loaded")
    fun searchOnline(keyword: String, callback: (List<OnlineSong>) -> Unit) {
        callback(emptyList())
    }

    private fun searchKuwo(context: Context, keyword: String): List<OnlineSong> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val apiKey = getQzqiApiKey(context)
        if (apiKey.isBlank()) return emptyList()
        val raw = requestText("$KUWO_API?msg=$encoded&apikey=${URLEncoder.encode(apiKey, "UTF-8")}", apiKey)
        val root = JSONObject(raw.trim())
        val code = root.optInt("code", root.optInt("status", 200))
        val msg = root.optString("msg", root.optString("message"))
        if (code == 0 && (msg.contains("API Key", true) || msg.contains("无效") || msg.contains("缺少"))) return emptyList()
        val arr = kuwoDataArray(root) ?: return emptyList()
        val songs = mutableListOf<OnlineSong>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { parseKuwoSong(it) }?.let(songs::add)
        }
        return songs.distinctBy { itemKey(it) }
    }

    private fun parseKuwoSong(obj: JSONObject): OnlineSong? {
        val nested = firstObject(obj, "song", "music", "info") ?: obj
        val songId = firstNotBlank(
            nested.optString("song_id"), nested.optString("songid"), nested.optString("rid"), nested.optString("id"),
            nested.optString("musicId"), nested.optString("mid")
        )
        val title = cleanField(firstNotBlank(
            nested.optString("name"), nested.optString("title"), nested.optString("song"),
            nested.optString("songname"), nested.optString("songName"), nested.optString("musicName")
        ))
        val artist = cleanField(firstNotBlank(
            nested.optString("artist"), nested.optString("singer"), nested.optString("author"),
            nested.optString("artistName"), nested.optString("singername"), nested.optString("singerName"), "酷我音乐"
        ))
        val album = cleanField(firstNotBlank(nested.optString("album"), nested.optString("albumName"), nested.optString("albumname")))
        val duration = cleanField(firstNotBlank(nested.optString("duration"), nested.optString("time")))
        val playUrl = firstNotBlank(
            nested.optString("play_url"), nested.optString("playUrl"), nested.optString("url"),
            nested.optString("music_url"), nested.optString("musicUrl"), nested.optString("download_url"),
            nested.optString("downloadUrl"), nested.optString("mp3"), nested.optString("flac"), nested.optString("src")
        ).replace("\\/", "/")
        val pageUrl = firstNotBlank(
            nested.optString("link"), nested.optString("pageUrl"), nested.optString("share"),
            if (songId.isNotBlank()) "https://www.kuwo.cn/play_detail/$songId" else playUrl
        )
        if (title.isBlank() || (playUrl.isBlank() && pageUrl.isBlank())) return null
        val descArtist = listOf(artist, album, duration).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "酷我音乐" }
        return OnlineSong(title, descArtist, OnlineSource.KUWO, pageUrl, playUrl.takeIf { isLikelyPlayableUrl(it) }, songId)
    }

    private fun kuwoDataArray(root: JSONObject): JSONArray? {
        root.optJSONArray("data")?.let { return it }
        root.optJSONArray("result")?.let { return it }
        root.optJSONArray("list")?.let { return it }
        val data = root.optJSONObject("data") ?: root.optJSONObject("result") ?: return null
        data.optJSONArray("list")?.let { return it }
        data.optJSONArray("songs")?.let { return it }
        data.optJSONArray("items")?.let { return it }
        data.optJSONArray("data")?.let { return it }
        data.optJSONArray("music")?.let { return it }
        if (data.has("url") || data.has("playUrl") || data.has("name") || data.has("title")) return JSONArray().put(data)
        return null
    }

    private fun firstObject(obj: JSONObject, vararg keys: String): JSONObject? {
        for (k in keys) obj.optJSONObject(k)?.let { return it }
        return null
    }

    fun refreshPlayableSong(context: Context, title: String, artist: String, songId: String, pageUrl: String): OnlineSong? {
        val cleanTitle = cleanField(title)
        val cleanArtist = cleanField(artist).substringBefore(" · ").trim()
        val queries = listOf(
            listOf(cleanTitle, cleanArtist).filter { it.isNotBlank() }.joinToString(" "),
            cleanTitle,
            songId
        ).filter { it.isNotBlank() }.distinct()
        for (query in queries) {
            val songs = runCatching { searchKuwo(context.applicationContext, query) }.getOrElse { emptyList() }
            val matched = songs.firstOrNull { song ->
                (songId.isNotBlank() && song.songId == songId) ||
                    (pageUrl.isNotBlank() && song.pageUrl == pageUrl) ||
                    (song.title.equals(cleanTitle, true) && (cleanArtist.isBlank() || song.artist.contains(cleanArtist, true)))
            } ?: songs.firstOrNull { it.title.contains(cleanTitle, true) || cleanTitle.contains(it.title, true) }
            if (matched?.playUrl?.isNotBlank() == true) return matched
        }
        return null
    }

    @Deprecated("Use context-aware overload so QZQI API key can be loaded")
    fun refreshPlayableSong(title: String, artist: String, songId: String, pageUrl: String): OnlineSong? = null

    fun fetchKuwoLyrics(songId: String): List<String> = fetchKuwoTimedLyrics(songId).map { it.text }.distinct()

    fun fetchKuwoTimedLyrics(songId: String): List<TimedLyric> {
        val cleanedId = songId.trim()
        if (cleanedId.isBlank()) return emptyList()
        val raw = requestText("https://kuwo.cn/openapi/v1/www/lyric/getlyric?musicId=$cleanedId", "")
        val root = JSONObject(raw.trim())
        val data = root.optJSONObject("data") ?: return emptyList()
        val arr = data.optJSONArray("lrclist") ?: return emptyList()
        val lines = mutableListOf<TimedLyric>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val lyric = obj.optString("lineLyric").trim()
            if (lyric.isBlank()) continue
            val timeMs = parseLyricTimeMs(obj.optString("time"))
            lines.add(TimedLyric(timeMs, lyric))
        }
        return lines.distinctBy { it.timeMs to it.text }.sortedBy { it.timeMs }
    }

    private fun parseLyricTimeMs(raw: String): Long {
        val text = raw.trim()
        if (text.isBlank()) return 0L
        return if (text.contains(':')) {
            val parts = text.split(':')
            val minutes = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            val seconds = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            (minutes * 60_000L + (seconds * 1000.0).toLong()).coerceAtLeast(0L)
        } else {
            val value = text.toDoubleOrNull() ?: return 0L
            if (value < 10_000) (value * 1000.0).toLong() else value.toLong()
        }
    }

    private fun itemKey(song: OnlineSong): String = song.songId.ifBlank { song.title + "|" + song.artist + "|" + song.playUrl.orEmpty() }

    private fun requestText(urlStr: String, apiKey: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) YunoTools/1.2.46")
        conn.setRequestProperty("Accept", "application/json,text/plain,*/*")
        conn.setRequestProperty("Referer", "https://api.qzqi.com/")
        if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.connectTimeout = 8000
        conn.readTimeout = 15000
        return try {
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } finally {
            conn.disconnect()
        }
    }

    private fun isLikelyPlayableUrl(url: String): Boolean {
        val cleaned = decodeHtmlEntities(url).trim()
        return cleaned.startsWith("http", ignoreCase = true) &&
            (Regex("\\.(mp3|m4a|aac|wav|flac|ogg|opus|mflac)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(cleaned) || cleaned.contains("kuwo", true))
    }

    private fun cleanField(text: String): String {
        return decodeHtmlEntities(text)
            .takeUnless { it.equals("null", ignoreCase = true) || it.equals("undefined", ignoreCase = true) }
            .orEmpty()
            .trim()
    }

    private fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#34;", "\"")
            .replace("&#39;", "'")
            .trim()
    }

    private fun firstNotBlank(vararg values: String): String = values.map(::cleanField).firstOrNull { it.isNotBlank() }.orEmpty()

    fun uriFromPublicUrl(url: String): Uri = Uri.parse(decodeHtmlEntities(url))
}

package com.yuno.tools.util

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MusicSearchHelper {
    private const val KUWO_API = "https://api.mmp.cc/api/kuwo"

    enum class OnlineSource(val label: String) {
        KUWO("酷我音乐")
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

    fun searchOnline(keyword: String, callback: (List<OnlineSong>) -> Unit) {
        Thread {
            val trimmed = keyword.trim()
            if (trimmed.isBlank()) {
                callback(emptyList())
                return@Thread
            }
            val songs = runCatching { searchKuwo(trimmed) }.getOrElse { emptyList() }
                .distinctBy { it.pageUrl.ifBlank { itemKey(it) } }
            callback(songs)
        }.start()
    }

    private fun searchKuwo(keyword: String): List<OnlineSong> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val raw = requestText("$KUWO_API?action=search_song&msg=$encoded")
        val root = JSONObject(raw.trim())
        val code = root.optInt("code", 200)
        if (code != 200 && code != 1) return emptyList()
        val arr = kuwoDataArray(root) ?: return emptyList()
        val songs = mutableListOf<OnlineSong>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            parseKuwoSong(obj)?.let(songs::add)
        }
        return songs.distinctBy { itemKey(it) }
    }

    private fun parseKuwoSong(obj: JSONObject): OnlineSong? {
        val songId = firstNotBlank(obj.optString("song_id"), obj.optString("rid"), obj.optString("id"))
        val title = cleanField(firstNotBlank(obj.optString("name"), obj.optString("title"), obj.optString("song")))
        val artist = cleanField(firstNotBlank(obj.optString("artist"), obj.optString("singer"), obj.optString("author"), "酷我音乐"))
        val album = cleanField(obj.optString("album"))
        val duration = cleanField(obj.optString("duration"))
        val playUrl = firstNotBlank(
            obj.optString("play_url"),
            obj.optString("playUrl"),
            obj.optString("url"),
            obj.optString("music_url")
        ).replace("\\/", "/")
        val pageUrl = firstNotBlank(
            obj.optString("link"),
            obj.optString("pageUrl"),
            if (songId.isNotBlank()) "https://www.kuwo.cn/play_detail/$songId" else playUrl
        )
        if (title.isBlank() || (playUrl.isBlank() && pageUrl.isBlank())) return null
        val descArtist = listOf(artist, album, duration).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "酷我音乐" }
        return OnlineSong(title, descArtist, OnlineSource.KUWO, pageUrl, playUrl.takeIf { isPublicAudioUrl(it) }, songId)
    }

    private fun kuwoDataArray(root: JSONObject): JSONArray? {
        root.optJSONArray("data")?.let { return it }
        val data = root.optJSONObject("data") ?: return null
        return data.optJSONArray("list") ?: data.optJSONArray("songs") ?: data.optJSONArray("items") ?: data.optJSONArray("data")
    }

    fun fetchKuwoLyrics(songId: String): List<String> = fetchKuwoTimedLyrics(songId).map { it.text }.distinct()

    fun fetchKuwoTimedLyrics(songId: String): List<TimedLyric> {
        val cleanedId = songId.trim()
        if (cleanedId.isBlank()) return emptyList()
        val raw = requestText("https://kuwo.cn/openapi/v1/www/lyric/getlyric?musicId=$cleanedId")
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
        return lines.distinctBy { it.timeMs to it.text }
            .sortedBy { it.timeMs }
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

    private fun requestText(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) YunoTools/1.2.00")
        conn.setRequestProperty("Accept", "application/json,text/plain,*/*")
        conn.setRequestProperty("Referer", "https://api.mmp.cc/")
        conn.connectTimeout = 8000
        conn.readTimeout = 15000
        return try {
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } finally {
            conn.disconnect()
        }
    }

    private fun isPublicAudioUrl(url: String): Boolean {
        val cleaned = decodeHtmlEntities(url).trim()
        return cleaned.startsWith("http", ignoreCase = true) &&
            Regex("\\.(mp3|m4a|aac|wav|flac|ogg|opus|mflac)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)
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

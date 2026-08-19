package com.yuno.tools.util

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
object MusicSearchHelper {
    private const val NEXTMUSIC_API = "https://nextmusic.toubiec.cn"
    private const val NEXTMUSIC_DEBUG_API = "http://localhost:3000"
    private const val CLIENT_IP_API = "/api/ip"
    private const val SEARCH_API = "/api/search"
    private const val SONG_INFO_API = "/api/getSongInfo"
    private const val SONG_URL_API = "/api/getSongUrl"
    private const val LYRIC_API = "/api/getSongLyric"
    @Volatile private var cachedClientIp: String = ""

    enum class OnlineSource(val label: String) {
        NETEASE("网易云音乐")
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

    fun searchOnline(@Suppress("UNUSED_PARAMETER") context: Context?, keyword: String, callback: (List<OnlineSong>) -> Unit) {
        Thread {
            val trimmed = keyword.trim()
            if (trimmed.isBlank()) {
                callback(emptyList())
                return@Thread
            }
            val songs = runCatching { searchNextMusic(trimmed) }.getOrElse { emptyList() }
            callback(songs.distinctBy { itemKey(it) }.take(24))
        }.start()
    }

    @Deprecated("Use context-aware overload")
    fun searchOnline(keyword: String, callback: (List<OnlineSong>) -> Unit) {
        searchOnline(null as Context?, keyword, callback)
    }

    private fun searchNextMusic(keyword: String): List<OnlineSong> {
        val raw = requestJson(SEARCH_API, mapOf("keyword" to keyword, "type" to 1, "limit" to 50, "offset" to 0))
        val root = JSONObject(raw)
        val data = root.opt("data") ?: return emptyList()
        val candidates = when (data) {
            is JSONArray -> data.toJsonObjects()
            is JSONObject -> data.optJSONArray("songs")?.toJsonObjects()
                ?: data.optJSONArray("list")?.toJsonObjects()
                ?: data.optJSONArray("data")?.toJsonObjects()
                ?: emptyList()
            else -> emptyList()
        }
        val songs = mutableListOf<OnlineSong>()
        for (obj in candidates) {
            parseSong(obj)?.let { candidate ->
                val playable = refreshPlayableSong(null, candidate.title, candidate.artist, candidate.songId, candidate.pageUrl)
                if (playable?.playUrl?.isNotBlank() == true) songs.add(playable)
            }
            if (songs.size >= 12) break
        }
        return songs
    }

    private fun parseSong(obj: JSONObject): OnlineSong? {
        val id = obj.optLong("id", 0L).takeIf { it > 0 }?.toString().orEmpty()
        if (id.isBlank()) return null
        val title = cleanField(obj.optString("name")).ifBlank { cleanField(obj.optString("songName")) }
        if (title.isBlank()) return null
        val artist = firstNonBlank(
            joinNames(obj.optJSONArray("artists")),
            cleanField(obj.optString("singer")),
            cleanField(obj.optString("artist")),
            cleanField(obj.optString("singerName")),
            cleanField(obj.optString("artistName")),
            "网易云音乐"
        )
        val album = firstNonBlank(
            cleanField(obj.optJSONObject("album")?.optString("name").orEmpty()),
            cleanField(obj.optString("album")),
            cleanField(obj.optString("albumName"))
        )
        val duration = formatDuration(obj.optLong("duration", 0L).takeIf { it > 1000 } ?: obj.optLong("duration", 0L))
        val desc = listOf(artist, album, duration).filter { it.isNotBlank() }.joinToString(" · ")
        val pageUrl = "https://music.163.com/song?id=$id"
        return OnlineSong(title, desc, OnlineSource.NETEASE, pageUrl, null, id)
    }

    fun refreshPlayableSong(@Suppress("UNUSED_PARAMETER") context: Context?, title: String, artist: String, songId: String, pageUrl: String): OnlineSong? {
        val id = songId.trim().ifBlank { Regex("""(?:id=|/song\?id=)(\d+)""").find(pageUrl)?.groupValues?.getOrNull(1).orEmpty() }
        if (id.isBlank()) return null
        val info = runCatching { fetchSongInfo(id) }.getOrNull()
        val playUrl = runCatching { fetchPlayUrl(id) }.getOrNull() ?: info?.optString("url").orEmpty()
        if (playUrl.isBlank()) return null
        val cleanTitle = cleanField(title).ifBlank { info?.optString("name").orEmpty() }.ifBlank { "在线音乐" }
        val cleanArtist = cleanField(artist).ifBlank { info?.optString("singer").orEmpty() }.ifBlank { "网易云音乐" }
        return OnlineSong(cleanTitle, cleanArtist, OnlineSource.NETEASE, "https://music.163.com/song?id=$id", playUrl.replace("http://", "https://"), id)
    }

    @Deprecated("Use context-aware overload")
    fun refreshPlayableSong(title: String, artist: String, songId: String, pageUrl: String): OnlineSong? =
        refreshPlayableSong(null, title, artist, songId, pageUrl)

    fun fetchKuwoLyrics(songId: String): List<String> = fetchKuwoTimedLyrics(songId).map { it.text }.distinct()

    fun fetchKuwoTimedLyrics(songId: String): List<TimedLyric> {
        val id = songId.trim()
        if (id.isBlank()) return emptyList()
        return runCatching {
            val raw = requestJson(LYRIC_API, mapOf("id" to id))
            val root = JSONObject(raw)
            val data = root.optJSONObject("data") ?: return emptyList()
            val lrc = data.optString("lrc").ifBlank { data.optJSONObject("lrc")?.optString("lyric").orEmpty() }
            parseLrc(lrc)
        }.getOrElse { emptyList() }
    }

    private fun fetchSongInfo(id: String): JSONObject? {
        val raw = requestJson(SONG_INFO_API, mapOf("id" to id))
        val root = JSONObject(raw)
        if (root.optInt("code", 0) != 200) return null
        return root.optJSONObject("data")
    }

    private fun fetchPlayUrl(id: String): String? {
        val raw = requestJson(SONG_URL_API, mapOf("id" to id, "level" to "standard"))
        val root = JSONObject(raw)
        if (root.optInt("code", 0) == 429) return null
        val data = root.optJSONObject("data") ?: return null
        val url = data.optString("url").trim()
        if (url.isBlank()) return null
        return url.takeIf { isLikelyPlayableUrl(it) }
    }

    private fun parseLrc(lrc: String): List<TimedLyric> {
        val result = mutableListOf<TimedLyric>()
        val pattern = Regex("""\[(\d{1,2}:\d{1,2}(?:\.\d{1,3})?)\](.*)""")
        lrc.lines().forEach { line ->
            val m = pattern.find(line.trim()) ?: return@forEach
            val text = cleanField(m.groupValues.getOrNull(2).orEmpty())
            if (text.isBlank()) return@forEach
            result.add(TimedLyric(parseLyricTimeMs(m.groupValues[1]), text))
        }
        return result.distinctBy { it.timeMs to it.text }.sortedBy { it.timeMs }
    }

    private fun parseLyricTimeMs(raw: String): Long {
        val text = raw.trim()
        if (text.isBlank()) return 0L
        val parts = text.split(':')
        if (parts.size >= 2) {
            val minutes = parts[0].toLongOrNull() ?: 0L
            val seconds = parts[1].toDoubleOrNull() ?: 0.0
            return (minutes * 60_000L + (seconds * 1000.0).toLong()).coerceAtLeast(0L)
        }
        val value = text.toDoubleOrNull() ?: return 0L
        return if (value < 10_000) (value * 1000.0).toLong() else value.toLong()
    }

    private fun itemKey(song: OnlineSong): String = song.source.name + "|" + song.songId.ifBlank { song.title + "|" + song.artist + "|" + song.playUrl.orEmpty() }

    private fun requestJson(path: String, payload: Map<String, Any>): String {
        val bases = listOf(NEXTMUSIC_API, NEXTMUSIC_DEBUG_API)
        var lastError: String? = null
        for (base in bases) {
            val body = JSONObject(payload + mapOf("timestamp" to System.currentTimeMillis()) + fetchClientIp().let { if (it.isBlank()) emptyMap() else mapOf("ip" to it) }).toString()
            val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doInput = true
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 YunoTools/1.2.47")
                setRequestProperty("Accept", "application/json,text/plain,*/*")
                setRequestProperty("Content-Type", "application/json;charset=UTF-8")
                setRequestProperty("Referer", base + "/")
                connectTimeout = 8000
                readTimeout = 15000
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (text.isNotBlank()) return text
                lastError = "HTTP $code empty response"
            } catch (e: Exception) {
                lastError = e.message
            } finally {
                conn.disconnect()
            }
        }
        throw IllegalStateException(lastError ?: "request failed")
    }

    private fun fetchClientIp(): String {
        cachedClientIp.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching {
            val conn = (URL(NEXTMUSIC_API + CLIENT_IP_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doInput = true
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 YunoTools/1.2.47")
                setRequestProperty("Accept", "application/json,text/plain,*/*")
                setRequestProperty("Content-Type", "application/json;charset=UTF-8")
                setRequestProperty("Referer", NEXTMUSIC_API + "/")
                connectTimeout = 6000
                readTimeout = 8000
            }
            try {
                conn.outputStream.use { it.write(JSONObject(mapOf("timestamp" to System.currentTimeMillis())).toString().toByteArray(Charsets.UTF_8)) }
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONObject(text)
                root.optJSONObject("data")?.optString("ip").orEmpty().trim().also { if (it.isNotBlank()) cachedClientIp = it }
            } finally {
                conn.disconnect()
            }
        }.getOrDefault("")
    }

    private fun isLikelyPlayableUrl(url: String): Boolean {
        val cleaned = decodeHtmlEntities(url).trim()
        return cleaned.startsWith("http", ignoreCase = true) &&
            Regex("""\.(mp3|m4a|aac|wav|flac|ogg|opus|mflac|mp4)(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return ""
        val sec = ms / 1000L
        return "%d:%02d".format(sec / 60L, sec % 60L)
    }

    private fun cleanField(text: String): String {
        return decodeHtmlEntities(text)
            .takeUnless { it.equals("null", ignoreCase = true) || it.equals("undefined", ignoreCase = true) }
            .orEmpty()
            .trim()
    }

    private fun firstNonBlank(vararg values: String): String = values.firstOrNull { it.isNotBlank() } ?: ""

    private fun joinNames(arr: JSONArray?): String {
        if (arr == null) return ""
        val names = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val name = cleanField(item.optString("name").orEmpty())
            if (name.isNotBlank()) names.add(name)
        }
        return names.distinct().joinToString("/")
    }

    private fun JSONArray.toJsonObjects(): List<JSONObject> {
        val list = mutableListOf<JSONObject>()
        for (i in 0 until length()) {
            optJSONObject(i)?.let(list::add)
        }
        return list
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

    fun uriFromPublicUrl(url: String): Uri = Uri.parse(decodeHtmlEntities(url))
}

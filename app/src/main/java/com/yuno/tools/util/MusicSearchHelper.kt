package com.yuno.tools.util

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MusicSearchHelper {
    private const val NETEASE_SEARCH_API = "https://music.163.com/api/search/get/web"
    private const val NETEASE_PLAYER_API = "https://music.163.com/api/song/enhance/player/url"
    private const val NETEASE_LYRIC_API = "https://music.163.com/api/song/lyric"
    private const val KUGOU_SEARCH_API = "http://mobilecdn.kugou.com/api/v3/search/song"
    private const val KUGOU_PLAY_API = "https://m.kugou.com/app/i/getSongInfo.php"

    enum class OnlineSource(val label: String) {
        NETEASE("网易云音乐"),
        KUGOU("酷狗音乐")
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

    fun searchOnline(context: Context?, keyword: String, callback: (List<OnlineSong>) -> Unit) {
        Thread {
            val trimmed = keyword.trim()
            if (trimmed.isBlank()) {
                callback(emptyList())
                return@Thread
            }
            val songs = mutableListOf<OnlineSong>()
            songs += runCatching { searchNetease(trimmed) }.getOrElse { emptyList() }
            songs += runCatching { searchKugou(trimmed) }.getOrElse { emptyList() }
            callback(songs.distinctBy { itemKey(it) }.take(24))
        }.start()
    }

    @Deprecated("Use context-aware overload")
    fun searchOnline(keyword: String, callback: (List<OnlineSong>) -> Unit) {
        searchOnline(null as Context?, keyword, callback)
    }

    private fun searchNetease(keyword: String): List<OnlineSong> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val searchUrl = "$NETEASE_SEARCH_API?csrf_token=&hlpretag=&hlposttag=&s=$encoded&type=1&offset=0&total=true&limit=30"
        val raw = requestText(searchUrl, referer = "https://music.163.com/search/")
        val root = JSONObject(raw.trim())
        val arr = root.optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
        val songs = mutableListOf<OnlineSong>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            parseNeteaseSong(obj)?.let { candidate ->
                val playable = refreshPlayableSong(null, candidate.title, candidate.artist, candidate.songId, candidate.pageUrl)
                if (playable?.playUrl?.isNotBlank() == true) songs.add(playable)
            }
            if (songs.size >= 12) break
        }
        return songs
    }

    private fun searchKugou(keyword: String): List<OnlineSong> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val searchUrl = "$KUGOU_SEARCH_API?format=json&keyword=$encoded&page=1&pagesize=30"
        val raw = requestText(searchUrl, referer = "https://m.kugou.com/search/index/")
        val arr = JSONObject(raw.trim()).optJSONObject("data")?.optJSONArray("info") ?: return emptyList()
        val songs = mutableListOf<OnlineSong>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            parseKugouSong(obj)?.let { candidate ->
                val playable = refreshPlayableSong(null, candidate.title, candidate.artist, candidate.songId, candidate.pageUrl)
                if (playable?.playUrl?.isNotBlank() == true) songs.add(playable)
            }
            if (songs.size >= 12) break
        }
        return songs
    }

    private fun parseNeteaseSong(obj: JSONObject): OnlineSong? {
        val id = obj.optLong("id", 0L).takeIf { it > 0 }?.toString().orEmpty()
        if (id.isBlank()) return null
        val title = cleanField(obj.optString("name"))
        if (title.isBlank()) return null
        val artists = obj.optJSONArray("artists")
        val artist = joinNames(artists).ifBlank { "网易云音乐" }
        val album = cleanField(obj.optJSONObject("album")?.optString("name").orEmpty())
        val duration = formatDuration(obj.optLong("duration", 0L))
        val desc = listOf(artist, album, duration).filter { it.isNotBlank() }.joinToString(" · ")
        val pageUrl = "https://music.163.com/song?id=$id"
        return OnlineSong(title, desc, OnlineSource.NETEASE, pageUrl, null, id)
    }

    private fun parseKugouSong(obj: JSONObject): OnlineSong? {
        val hash = cleanField(obj.optString("hash")).uppercase()
        if (hash.isBlank()) return null
        val fileParts = cleanKugouFileName(obj.optString("filename"))
        val title = cleanField(obj.optString("songname")).ifBlank { fileParts.first }
        if (title.isBlank()) return null
        val singer = cleanField(obj.optString("singername")).ifBlank { fileParts.second }
        val album = cleanField(obj.optString("album_name"))
        val duration = formatDuration(obj.optLong("duration", 0L) * 1000L)
        val desc = listOf(singer.ifBlank { "酷狗音乐" }, album, duration).filter { it.isNotBlank() }.joinToString(" · ")
        val pageUrl = "https://m.kugou.com/song/#hash=$hash"
        return OnlineSong(title, desc, OnlineSource.KUGOU, pageUrl, null, "kg:$hash")
    }

    private fun cleanKugouFileName(filename: String): Pair<String, String> {
        val text = cleanField(filename)
        val parts = text.split(" - ", limit = 2)
        return if (parts.size == 2) parts[1].trim() to parts[0].trim() else text to ""
    }

    private fun joinNames(arr: JSONArray?): String {
        if (arr == null) return ""
        val names = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val name = cleanField(arr.optJSONObject(i)?.optString("name").orEmpty())
            if (name.isNotBlank()) names.add(name)
        }
        return names.distinct().joinToString("/")
    }

    fun refreshPlayableSong(context: Context?, title: String, artist: String, songId: String, pageUrl: String): OnlineSong? {
        val rawId = songId.trim()
        if (rawId.startsWith("kg:")) {
            val hash = rawId.removePrefix("kg:").ifBlank { Regex("hash=([A-Fa-f0-9]+)").find(pageUrl)?.groupValues?.getOrNull(1).orEmpty() }
            if (hash.isBlank()) return null
            val playUrl = fetchKugouPlayUrl(hash) ?: return null
            val cleanTitle = cleanField(title).ifBlank { "在线音乐" }
            val cleanArtist = cleanField(artist).ifBlank { "酷狗音乐" }
            return OnlineSong(cleanTitle, cleanArtist, OnlineSource.KUGOU, "https://m.kugou.com/song/#hash=${hash.uppercase()}", playUrl, "kg:${hash.uppercase()}")
        }
        val id = rawId.ifBlank { Regex("(?:id=|/song\\?id=)(\\d+)").find(pageUrl)?.groupValues?.getOrNull(1).orEmpty() }
        if (id.isBlank()) return null
        val playUrl = fetchNeteasePlayUrl(id) ?: return null
        val cleanTitle = cleanField(title).ifBlank { "在线音乐" }
        val cleanArtist = cleanField(artist).ifBlank { "网易云音乐" }
        return OnlineSong(cleanTitle, cleanArtist, OnlineSource.NETEASE, "https://music.163.com/song?id=$id", playUrl, id)
    }

    @Deprecated("Use context-aware overload")
    fun refreshPlayableSong(title: String, artist: String, songId: String, pageUrl: String): OnlineSong? =
        refreshPlayableSong(null, title, artist, songId, pageUrl)

    private fun fetchNeteasePlayUrl(id: String): String? {
        val url = "$NETEASE_PLAYER_API?id=$id&ids=[$id]&br=320000"
        val raw = requestText(url, referer = "https://music.163.com/")
        val data = JSONObject(raw.trim()).optJSONArray("data")?.optJSONObject(0) ?: return null
        val code = data.optInt("code", 0)
        val play = cleanField(data.optString("url"))
        if (code != 200 || play.isBlank()) return null
        return play.replace("http://", "https://").takeIf { isLikelyPlayableUrl(it) }
    }

    private fun fetchKugouPlayUrl(hash: String): String? {
        val encodedHash = URLEncoder.encode(hash, "UTF-8")
        val raw = requestText("$KUGOU_PLAY_API?cmd=playInfo&hash=$encodedHash", referer = "https://m.kugou.com/")
        val root = JSONObject(raw.trim())
        if (root.optInt("status", 0) != 1) return null
        val play = cleanField(root.optString("url"))
        if (play.isBlank()) return null
        return play.replace("http://", "https://").takeIf { isLikelyPlayableUrl(it) }
    }

    fun fetchKuwoLyrics(songId: String): List<String> = fetchKuwoTimedLyrics(songId).map { it.text }.distinct()

    fun fetchKuwoTimedLyrics(songId: String): List<TimedLyric> {
        val id = songId.trim()
        if (id.isBlank() || id.startsWith("kg:")) return emptyList()
        return runCatching {
            val raw = requestText("$NETEASE_LYRIC_API?id=$id&lv=1&kv=1&tv=-1", referer = "https://music.163.com/")
            val lrc = JSONObject(raw.trim()).optJSONObject("lrc")?.optString("lyric").orEmpty()
            parseLrc(lrc)
        }.getOrElse { emptyList() }
    }

    private fun parseLrc(lrc: String): List<TimedLyric> {
        val result = mutableListOf<TimedLyric>()
        val pattern = Regex("\\[(\\d{1,2}:\\d{1,2}(?:\\.\\d{1,3})?)\\](.*)")
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

    private fun requestText(urlStr: String, referer: String = "https://music.163.com/"): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 YunoTools/1.2.47")
        conn.setRequestProperty("Accept", "application/json,text/plain,*/*")
        conn.setRequestProperty("Referer", referer)
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
            Regex("\\.(mp3|m4a|aac|wav|flac|ogg|opus|mflac)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)
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

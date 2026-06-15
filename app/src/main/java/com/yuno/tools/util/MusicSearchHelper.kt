package com.yuno.tools.util

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MusicSearchHelper {
    private const val MIGU_API = "https://api.xcvts.cn/api/music/migu"

    enum class OnlineSource(val label: String) {
        MIGU("咪咕音乐")
    }

    data class OnlineSong(
        val title: String,
        val artist: String,
        val source: OnlineSource,
        val pageUrl: String,
        val playUrl: String?
    )

    fun searchOnline(keyword: String, callback: (List<OnlineSong>) -> Unit) {
        Thread {
            val trimmed = keyword.trim()
            if (trimmed.isBlank()) {
                callback(emptyList())
                return@Thread
            }
            val songs = runCatching { searchMigu(trimmed) }.getOrElse { emptyList() }
                .distinctBy { it.title + "|" + it.artist + "|" + it.playUrl }
                .sortedWith(compareByDescending<OnlineSong> { !it.playUrl.isNullOrBlank() }.thenBy { it.title })
            callback(songs)
        }.start()
    }

    private fun searchMigu(keyword: String): List<OnlineSong> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val candidates = listOf(
            "$MIGU_API?name=$encoded",
            "$MIGU_API?msg=$encoded",
            "$MIGU_API?keyword=$encoded",
            "$MIGU_API?search=$encoded",
            "$MIGU_API?query=$encoded",
            "$MIGU_API?key=$encoded"
        )
        val errors = mutableListOf<Exception>()
        for (url in candidates) {
            try {
                val raw = requestText(url)
                val songs = parseMiguResponse(raw, keyword)
                if (songs.isNotEmpty()) return songs
            } catch (e: Exception) {
                errors.add(e)
            }
        }
        val postBodies = listOf(
            "name=$encoded",
            "msg=$encoded",
            "keyword=$encoded",
            "search=$encoded",
            "query=$encoded",
            "key=$encoded"
        )
        for (body in postBodies) {
            try {
                val raw = requestText(MIGU_API, method = "POST", body = body)
                val songs = parseMiguResponse(raw, keyword)
                if (songs.isNotEmpty()) return songs
            } catch (e: Exception) {
                errors.add(e)
            }
        }
        if (errors.isNotEmpty()) throw errors.first()
        return emptyList()
    }

    private fun parseMiguResponse(raw: String, keyword: String): List<OnlineSong> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return emptyList()
        if (trimmed.startsWith("[")) return parseSongArray(JSONArray(trimmed), keyword)

        val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return emptyList()
        val code = root.optInt("code", root.optInt("status", 200))
        if (code == 400 || code == 404) {
            val message = root.optString("message")
            if (message.contains("请输入歌名") || message.contains("歌名")) return emptyList()
        }

        val direct = parseSongObject(root, keyword)
        if (direct != null) return listOf(direct)

        val data = root.opt("data") ?: root.opt("result") ?: root.opt("songs") ?: root.opt("list") ?: root.opt("rows") ?: root.opt("items")
        return when (data) {
            is JSONArray -> parseSongArray(data, keyword)
            is JSONObject -> {
                val nested = data.optJSONArray("list") ?: data.optJSONArray("songs") ?: data.optJSONArray("data") ?: data.optJSONArray("result") ?: data.optJSONArray("rows") ?: data.optJSONArray("items")
                if (nested != null) parseSongArray(nested, keyword) else listOfNotNull(parseSongObject(data, keyword))
            }
            else -> emptyList()
        }
    }

    private fun parseSongArray(arr: JSONArray, keyword: String): List<OnlineSong> {
        val list = mutableListOf<OnlineSong>()
        for (i in 0 until arr.length()) {
            val item = when (val value = arr.opt(i)) {
                is JSONObject -> value
                is String -> runCatching { JSONObject(value) }.getOrNull()
                else -> null
            } ?: continue
            parseSongObject(item, keyword)?.let(list::add)
        }
        return list
    }

    private fun parseSongObject(obj: JSONObject, keyword: String): OnlineSong? {
        val playUrl = firstNotBlank(
            obj.optString("url"),
            obj.optString("playUrl"),
            obj.optString("play_url"),
            obj.optString("music"),
            obj.optString("musicUrl"),
            obj.optString("mp3"),
            obj.optString("songUrl"),
            obj.optString("src")
        ).replace("\\/", "/")
        if (!isPublicAudioUrl(playUrl)) return null

        val title = cleanTitle(firstNotBlank(
            obj.optString("name"),
            obj.optString("song"),
            obj.optString("title"),
            obj.optString("songName"),
            obj.optString("musicName"),
            keyword
        ))
        val artist = cleanTitle(firstNotBlank(
            obj.optString("singer"),
            obj.optString("artist"),
            obj.optString("author"),
            obj.optString("singers"),
            obj.optString("singerName"),
            "咪咕音乐"
        ))
        val pageUrl = firstNotBlank(
            obj.optString("link"),
            obj.optString("pageUrl"),
            obj.optString("songLink"),
            playUrl
        ).replace("\\/", "/")
        return OnlineSong(title, artist, OnlineSource.MIGU, pageUrl, playUrl)
    }

    private fun requestText(urlStr: String, method: String = "GET", body: String? = null): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) YunoTools/1.1.77")
        conn.setRequestProperty("Accept", "application/json,text/plain,*/*")
        conn.setRequestProperty("Referer", "https://api.xcvts.cn/")
        conn.setRequestProperty("Origin", "https://api.xcvts.cn")
        conn.connectTimeout = 8000
        conn.readTimeout = 12000
        if (method.equals("POST", ignoreCase = true) && body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
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
            (Regex("\\.(mp3|m4a|aac|wav|flac)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(cleaned) ||
                cleaned.contains("migu", ignoreCase = true))
    }

    private fun cleanTitle(text: String): String {
        return decodeHtmlEntities(text)
            .replace("咪咕音乐", "")
            .replace("在线试听", "")
            .replace("免费下载", "")
            .replace("MP3", "", ignoreCase = true)
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '_', '|', '·')
            .ifBlank { "未知歌曲" }
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

    private fun firstNotBlank(vararg values: String): String = values.firstOrNull { it.isNotBlank() }.orEmpty()

    fun uriFromPublicUrl(url: String): Uri = Uri.parse(decodeHtmlEntities(url))
}

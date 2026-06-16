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

    private data class MiguResourceInfo(
        val title: String?,
        val artist: String?,
        val album: String?,
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
        val songs = mutableListOf<OnlineSong>()
        val errors = mutableListOf<Exception>()

        for (index in 1..20) {
            try {
                val raw = requestText("$MIGU_API?gm=$encoded&n=$index&num=20&type=json")
                parseMiguResponse(raw, keyword).forEach { song ->
                    if (songs.none { it.pageUrl == song.pageUrl && it.title == song.title }) songs.add(song)
                }
            } catch (e: Exception) {
                errors.add(e)
            }
        }
        if (songs.isNotEmpty()) return songs

        val candidates = listOf(
            "$MIGU_API?gm=$encoded&type=json",
            "$MIGU_API?name=$encoded",
            "$MIGU_API?msg=$encoded",
            "$MIGU_API?keyword=$encoded"
        )
        for (url in candidates) {
            try {
                val raw = requestText(url)
                val parsed = parseMiguResponse(raw, keyword)
                if (parsed.isNotEmpty()) return parsed
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
            obj.optString("music_url"),
            obj.optString("musicUrl"),
            obj.optString("mp3"),
            obj.optString("songUrl"),
            obj.optString("src"),
            obj.optString("listenUrl"),
            obj.optString("streamUrl"),
            obj.optString("downUrl"),
            obj.optString("previewUrl")
        ).replace("\\/", "/")
        val pageUrl = firstNotBlank(
            obj.optString("link"),
            obj.optString("pageUrl"),
            obj.optString("songLink"),
            playUrl
        ).replace("\\/", "/")
        if (playUrl.isBlank() && pageUrl.isBlank()) return null

        val copyrightId = extractMiguCopyrightId(pageUrl) ?: extractMiguCopyrightId(playUrl)
        val resourceInfo = copyrightId?.let { fetchMiguResourceInfo(it) }

        val resolvedPlayUrl = firstNotBlank(
            playUrl,
            resourceInfo?.playUrl.orEmpty()
        ).replace("\\/", "/")

        val title = cleanTitle(firstNotBlank(
            obj.optString("title"),
            obj.optString("name"),
            obj.optString("song"),
            obj.optString("songName"),
            obj.optString("musicName"),
            resourceInfo?.title.orEmpty(),
            if (!copyrightId.isNullOrBlank()) "咪咕歌曲 $copyrightId" else keyword
        ))
        val artist = cleanTitle(firstNotBlank(
            obj.optString("singer"),
            obj.optString("artist"),
            obj.optString("author"),
            obj.optString("singers"),
            obj.optString("singerName"),
            resourceInfo?.artist.orEmpty(),
            resourceInfo?.album.orEmpty(),
            "咪咕音乐"
        ))
        return OnlineSong(title, artist, OnlineSource.MIGU, pageUrl, resolvedPlayUrl.takeIf { isPublicAudioUrl(it) })
    }

    private fun extractMiguCopyrightId(url: String): String? {
        val cleaned = decodeHtmlEntities(url).substringBefore('?').trim('/')
        if (cleaned.isBlank()) return null
        return Regex("([0-9A-Za-z]{8,})$").find(cleaned)?.groupValues?.getOrNull(1)
    }

    private fun fetchMiguResourceInfo(copyrightId: String): MiguResourceInfo? {
        val endpoints = listOf(
            "https://app.c.nf.migu.cn/MIGUM3.0/v1.0/content/resourceinfo.do?copyrightId=$copyrightId&resourceType=2",
            "https://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/resourceinfo.do?copyrightId=$copyrightId&resourceType=2",
            "https://c.musicapp.migu.cn/MIGUM3.0/v1.0/content/resourceinfo.do?copyrightId=$copyrightId&resourceType=2"
        )
        for (endpoint in endpoints) {
            val info = runCatching { parseMiguResourceInfo(requestText(endpoint, referer = "https://music.migu.cn/")) }.getOrNull()
            if (info != null && (!info.title.isNullOrBlank() || !info.artist.isNullOrBlank() || !info.playUrl.isNullOrBlank())) {
                return info
            }
        }
        return null
    }

    private fun parseMiguResourceInfo(raw: String): MiguResourceInfo? {
        val root = runCatching { JSONObject(raw.trim()) }.getOrNull() ?: return null
        val resource = root.optJSONArray("resource")?.optJSONObject(0)
            ?: root.optJSONObject("resource")
            ?: root.optJSONObject("data")
            ?: root.optJSONObject("result")
            ?: return null
        val title = firstNotBlank(
            resource.optString("songName"),
            resource.optString("title"),
            resource.optString("name")
        )
        val artist = firstNotBlank(
            resource.optString("singer"),
            resource.optString("singerName"),
            resource.optString("artist")
        )
        val album = firstNotBlank(
            resource.optString("album"),
            resource.optString("albumName")
        )
        val playUrl = firstPublicAudioUrl(resource)
        return MiguResourceInfo(title.takeIf { it.isNotBlank() }, artist.takeIf { it.isNotBlank() }, album.takeIf { it.isNotBlank() }, playUrl)
    }

    private fun firstPublicAudioUrl(value: Any?): String? {
        return when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val nested = value.opt(key)
                    if (nested is String) {
                        val cleaned = cleanField(nested).replace("\\/", "/")
                        if (isPublicAudioUrl(cleaned)) return cleaned
                    } else {
                        firstPublicAudioUrl(nested)?.let { return it }
                    }
                }
                null
            }
            is JSONArray -> {
                for (i in 0 until value.length()) firstPublicAudioUrl(value.opt(i))?.let { return it }
                null
            }
            is String -> cleanField(value).replace("\\/", "/").takeIf { isPublicAudioUrl(it) }
            else -> null
        }
    }

    private fun requestText(urlStr: String, method: String = "GET", body: String? = null, referer: String = "https://api.xcvts.cn/"): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) YunoTools/1.1.80")
        conn.setRequestProperty("Accept", "application/json,text/plain,*/*")
        conn.setRequestProperty("Referer", referer)
        conn.setRequestProperty("Origin", Uri.parse(referer).let { "${it.scheme}://${it.host}" })
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
        if (!cleaned.startsWith("http", ignoreCase = true)) return false
        if (Regex("\\.(jpg|jpeg|png|webp|gif|bmp)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)) return false
        return Regex("\\.(mp3|m4a|aac|wav|flac)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(cleaned) ||
            cleaned.contains("/audio/", ignoreCase = true) ||
            (cleaned.contains("/music/", ignoreCase = true) && cleaned.contains("url", ignoreCase = true))
    }

    private fun cleanTitle(text: String): String {
        return decodeHtmlEntities(text)
            .takeUnless { it.equals("null", ignoreCase = true) || it.equals("undefined", ignoreCase = true) }
            .orEmpty()
            .replace("咪咕音乐", "")
            .replace("在线试听", "")
            .replace("免费下载", "")
            .replace("MP3", "", ignoreCase = true)
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '_', '|', '·')
            .ifBlank { "未知歌曲" }
    }

    private fun cleanField(text: String): String {
        val cleaned = decodeHtmlEntities(text)
        return cleaned.takeUnless { it.equals("null", ignoreCase = true) || it.equals("undefined", ignoreCase = true) }.orEmpty()
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

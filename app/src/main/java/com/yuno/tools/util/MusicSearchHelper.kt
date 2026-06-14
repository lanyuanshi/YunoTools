package com.yuno.tools.util

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object MusicSearchHelper {
    enum class OnlineSource(val label: String) {
        NETEASE_TOPLIST("网易云榜单"),
        GEQUHAI("歌曲海"),
        INTERNET_ARCHIVE("Internet Archive")
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
            val executor = Executors.newFixedThreadPool(3)
            val latch = CountDownLatch(3)
            val allSongs = Collections.synchronizedList(mutableListOf<OnlineSong>())
            val jobs = listOf<() -> List<OnlineSong>>(
                { searchNeteaseToplist(trimmed) },
                { searchGequhai(trimmed) },
                { searchInternetArchive(trimmed) }
            )
            jobs.forEach { job ->
                executor.execute {
                    try {
                        allSongs += job()
                    } catch (_: Exception) {
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await(5, TimeUnit.SECONDS)
            executor.shutdownNow()
            val songs = allSongs
                .distinctBy { it.source.name + "|" + it.pageUrl + "|" + it.title }
                .sortedWith(compareByDescending<OnlineSong> { !it.playUrl.isNullOrBlank() }.thenBy { it.source.ordinal })
            callback(songs)
        }.start()
    }

    private fun searchNeteaseToplist(keyword: String): List<OnlineSong> {
        return try {
            val encoded = URLEncoder.encode(keyword.trim(), "UTF-8")
            val apiUrl = "https://netease-cloud-music-api.fe-mm.com/search?keywords=$encoded&limit=40"
            val raw = requestText(apiUrl, "https://netease-music.fe-mm.com/#/music/toplist", "application/json,*/*")
            val songs = JSONObject(raw).optJSONObject("result")?.optJSONArray("songs") ?: JSONArray()
            buildList {
                for (i in 0 until songs.length()) {
                    val item = songs.optJSONObject(i) ?: continue
                    val id = item.optLong("id", 0L)
                    if (id <= 0L) continue
                    val title = cleanTitle(item.optString("name", keyword))
                    val artists = item.optJSONArray("artists")
                    val artist = buildList {
                        if (artists != null) {
                            for (j in 0 until artists.length()) {
                                val name = artists.optJSONObject(j)?.optString("name").orEmpty()
                                if (name.isNotBlank()) add(name)
                            }
                        }
                    }.joinToString(", ").ifBlank { "网易云音乐" }
                    add(
                        OnlineSong(
                            title = title,
                            artist = cleanTitle(artist),
                            source = OnlineSource.NETEASE_TOPLIST,
                            pageUrl = "https://music.163.com/#/song?id=$id",
                            playUrl = "https://music.163.com/song/media/outer/url?id=$id.mp3"
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun searchGequhai(keyword: String): List<OnlineSong> {
        return try {
            val encoded = URLEncoder.encode(keyword.trim(), "UTF-8")
            val raw = requestText("https://www.gequhai.com/s/$encoded", "https://www.gequhai.com/?ref=codernav.com", "text/html,application/xhtml+xml,*/*")
            Regex("""href=["'](/play/\d+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(raw)
                .take(32)
                .mapNotNull { match ->
                    val pageUrl = normalizeUrl(match.groupValues[1], "https://www.gequhai.com")
                    val fallbackTitle = cleanTitle(cleanHtml(match.groupValues[2]))
                    parseGequhaiDetail(pageUrl, fallbackTitle)
                }
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseGequhaiDetail(pageUrl: String, fallbackTitle: String): OnlineSong? {
        return try {
            val raw = requestText(pageUrl, "https://www.gequhai.com/?ref=codernav.com", "text/html,application/xhtml+xml,*/*")
            val title = extractJsString(raw, "mp3_title").ifBlank {
                Regex("""<h1[^>]*>(.*?)</h1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(raw)?.groupValues?.get(1)?.let(::cleanHtml).orEmpty()
            }.ifBlank { fallbackTitle }.ifBlank { "歌曲海音乐" }
            val artist = extractJsString(raw, "mp3_author").ifBlank { "未知艺人" }
            val jsUrl = extractJsString(raw, "mp3_url").ifBlank { extractJsString(raw, "mp3_url_a") }
            val apiUrl = resolveGequhaiMusicUrl(raw, pageUrl)
            val playUrl = apiUrl?.takeIf { isPublicAudioUrl(it) } ?: jsUrl.takeIf { isPublicAudioUrl(it) } ?: findAudioUrl(raw)
            OnlineSong(cleanTitle(title), cleanTitle(artist), OnlineSource.GEQUHAI, pageUrl, playUrl)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveGequhaiMusicUrl(raw: String, pageUrl: String): String? {
        return try {
            val playId = Regex("""window\.play_id\s*=\s*['\"]([^'\"]+)['\"]""", RegexOption.IGNORE_CASE)
                .find(raw)?.groupValues?.get(1).orEmpty()
            val mp3Type = Regex("""window\.mp3_type\s*=\s*(\d+)""", RegexOption.IGNORE_CASE)
                .find(raw)?.groupValues?.get(1).orEmpty().ifBlank { "0" }
            if (playId.isBlank()) return null
            val response = postForm(
                "https://www.gequhai.com/api/music",
                mapOf("id" to playId, "type" to mp3Type),
                pageUrl
            )
            JSONObject(response).optJSONObject("data")?.optString("url")
                ?.replace("\\/", "/")
                ?.let(::decodeHtmlEntities)
                ?.trim()
        } catch (_: Exception) {
            null
        }
    }

    private fun searchInternetArchive(keyword: String): List<OnlineSong> {
        return try {
            val query = URLEncoder.encode("(title:$keyword OR creator:$keyword) AND mediatype:audio", "UTF-8")
            val url = "https://archive.org/advancedsearch.php?q=$query&fl[]=identifier&fl[]=title&fl[]=creator&rows=8&page=1&output=json"
            val raw = requestText(url, "https://archive.org/", "application/json,*/*")
            val docs = JSONObject(raw).getJSONObject("response").getJSONArray("docs")
            buildList {
                for (i in 0 until docs.length()) {
                    val doc = docs.getJSONObject(i)
                    val identifier = doc.optString("identifier")
                    if (identifier.isBlank()) continue
                    val title = cleanTitle(doc.optString("title", identifier))
                    val creator = cleanTitle(jsonValueToText(doc.opt("creator"))).ifBlank { "公开音频" }
                    val playUrl = findArchiveAudioUrl(identifier)
                    if (!playUrl.isNullOrBlank()) {
                        add(OnlineSong(title, creator, OnlineSource.INTERNET_ARCHIVE, "https://archive.org/details/$identifier", playUrl))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun findArchiveAudioUrl(identifier: String): String? {
        return try {
            val raw = requestText("https://archive.org/metadata/$identifier", "https://archive.org/", "application/json,*/*")
            val files = JSONObject(raw).getJSONArray("files")
            for (i in 0 until files.length()) {
                val file = files.getJSONObject(i)
                val name = file.optString("name")
                val format = file.optString("format")
                if (Regex("""\.(mp3|m4a|aac|wav)$""", RegexOption.IGNORE_CASE).containsMatchIn(name) ||
                    format.contains("MP3", ignoreCase = true) || format.contains("VBR", ignoreCase = true)
                ) {
                    val encodedName = name.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
                    return "https://archive.org/download/$identifier/$encodedName"
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun postForm(urlStr: String, form: Map<String, String>, referer: String): String {
        val body = form.entries.joinToString("&") { (key, value) ->
            URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
        }.toByteArray(Charsets.UTF_8)
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        conn.setRequestProperty("Referer", referer)
        conn.setRequestProperty("Origin", "https://www.gequhai.com")
        conn.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
        conn.setRequestProperty("X-Custom-Header", "SecretKey")
        conn.connectTimeout = 2500
        conn.readTimeout = 2500
        return try {
            conn.outputStream.use { it.write(body) }
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun requestText(urlStr: String, referer: String, accept: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        conn.setRequestProperty("Referer", referer)
        conn.setRequestProperty("Accept", accept)
        conn.connectTimeout = 2500
        conn.readTimeout = 2500
        return try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun findAudioUrl(raw: String): String? {
        return Regex("""https?://[^\s"'<>]+\.(?:mp3|m4a|aac|wav)(?:\?[^\s"'<>]*)?""", RegexOption.IGNORE_CASE)
            .findAll(raw)
            .map { decodeHtmlEntities(it.value) }
            .firstOrNull { isPublicAudioUrl(it) }
    }

    private fun isPublicAudioUrl(url: String): Boolean {
        val cleaned = decodeHtmlEntities(url)
        return cleaned.startsWith("http", ignoreCase = true) &&
            (Regex("""\.(mp3|m4a|aac|wav)(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(cleaned) ||
                cleaned.contains("music.163.com/song/media/outer/url", ignoreCase = true))
    }

    private fun extractJsString(raw: String, key: String): String {
        val pattern = Regex("""(?:window\.)?$key\s*=\s*['\"]([^'\"]*)['\"]""", RegexOption.IGNORE_CASE)
        return pattern.find(raw)?.groupValues?.get(1)?.let(::decodeHtmlEntities).orEmpty().trim()
    }

    private fun normalizeUrl(href: String, base: String): String {
        return when {
            href.startsWith("http://") || href.startsWith("https://") -> href
            href.startsWith("//") -> "https:$href"
            href.startsWith("/") -> base + href
            else -> "$base/$href"
        }
    }

    private fun splitTitleArtist(text: String): Pair<String, String> {
        val normalized = cleanTitle(text)
        val seps = listOf(" - ", " — ", " – ", "_", "--")
        for (sep in seps) {
            val idx = normalized.indexOf(sep)
            if (idx > 0 && idx < normalized.length - sep.length) {
                val left = normalized.substring(0, idx).trim()
                val right = normalized.substring(idx + sep.length).trim()
                if (left.isNotBlank() && right.isNotBlank()) return left to right
            }
        }
        return normalized to "未知艺人"
    }

    private fun cleanTitle(text: String): String {
        return decodeHtmlEntities(text)
            .replace("网易云榜单", "")
            .replace("网易云音乐", "")
            .replace("歌曲海", "")
            .replace("在线试听", "")
            .replace("免费下载", "")
            .replace("MP3", "", ignoreCase = true)
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '_', '|', '·')
            .ifBlank { "未知歌曲" }
    }

    private fun cleanHtml(html: String): String {
        return decodeHtmlEntities(
            html.replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " " )
                .trim()
        )
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

    private fun jsonValueToText(value: Any?): String {
        return when (value) {
            null -> ""
            is JSONArray -> buildList { for (i in 0 until value.length()) add(value.optString(i)) }.joinToString(", ")
            else -> value.toString()
        }
    }

    fun uriFromPublicUrl(url: String): Uri = Uri.parse(decodeHtmlEntities(url))
}

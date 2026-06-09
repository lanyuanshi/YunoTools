package com.yuno.tools.util

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MusicSearchHelper {
    enum class OnlineSource(val label: String) {
        GEQUGOU("歌曲狗"),
        GEQUHAI("歌曲海")
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
            val songs = (searchGequgou(keyword) + searchGequhai(keyword))
                .distinctBy { it.source.name + "|" + it.pageUrl + "|" + it.title }
            callback(songs)
        }.start()
    }

    private fun searchGequgou(keyword: String): List<OnlineSong> {
        return try {
            val encoded = URLEncoder.encode(keyword.trim(), "UTF-8")
            val raw = requestText("https://www.gequgou.com/search?ac=$encoded", "https://www.gequgou.com/", "text/html,application/xhtml+xml,*/*")
            Regex("""href=["'](/music/[^"']+?\.html)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(raw)
                .mapNotNull { match ->
                    val pageUrl = normalizeUrl(match.groupValues[1], "https://www.gequgou.com")
                    val fallbackTitle = cleanTitle(cleanHtml(match.groupValues[2]))
                    parseGequgouDetail(pageUrl, fallbackTitle)
                }
                .take(24)
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseGequgouDetail(pageUrl: String, fallbackTitle: String): OnlineSong? {
        return try {
            val raw = requestText(pageUrl, "https://www.gequgou.com/", "text/html,application/xhtml+xml,*/*")
            val h1 = Regex("""<h1[^>]*>(.*?)</h1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(raw)?.groupValues?.get(1)?.let(::cleanHtml).orEmpty()
            val htmlTitle = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(raw)?.groupValues?.get(1)?.let(::cleanHtml).orEmpty()
            val fullTitle = cleanTitle(h1.ifBlank { fallbackTitle }.ifBlank { htmlTitle }.ifBlank { "歌曲狗音乐" })
            val (title, artist) = splitTitleArtist(fullTitle)
            OnlineSong(title, artist, OnlineSource.GEQUGOU, pageUrl, findAudioUrl(raw))
        } catch (_: Exception) {
            null
        }
    }

    private fun searchGequhai(keyword: String): List<OnlineSong> {
        return try {
            val encoded = URLEncoder.encode(keyword.trim(), "UTF-8")
            val raw = requestText("https://www.gequhai.com/s/$encoded", "https://www.gequhai.com/", "text/html,application/xhtml+xml,*/*")
            Regex("""href=["'](/play/\d+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(raw)
                .mapNotNull { match ->
                    val pageUrl = normalizeUrl(match.groupValues[1], "https://www.gequhai.com")
                    val fallbackTitle = cleanTitle(cleanHtml(match.groupValues[2]))
                    parseGequhaiDetail(pageUrl, fallbackTitle)
                }
                .take(24)
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseGequhaiDetail(pageUrl: String, fallbackTitle: String): OnlineSong? {
        return try {
            val raw = requestText(pageUrl, "https://www.gequhai.com/", "text/html,application/xhtml+xml,*/*")
            val title = extractJsString(raw, "mp3_title").ifBlank {
                Regex("""<h1[^>]*>(.*?)</h1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(raw)?.groupValues?.get(1)?.let(::cleanHtml).orEmpty()
            }.ifBlank { fallbackTitle }.ifBlank { "歌曲海音乐" }
            val artist = extractJsString(raw, "mp3_author").ifBlank { "未知艺人" }
            val jsUrl = extractJsString(raw, "mp3_url").ifBlank { extractJsString(raw, "mp3_url_a") }
            val playUrl = jsUrl.takeIf { isPublicAudioUrl(it) } ?: findAudioUrl(raw)
            OnlineSong(cleanTitle(title), cleanTitle(artist), OnlineSource.GEQUHAI, pageUrl, playUrl)
        } catch (_: Exception) {
            null
        }
    }

    private fun requestText(urlStr: String, referer: String, accept: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        conn.setRequestProperty("Referer", referer)
        conn.setRequestProperty("Accept", accept)
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
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
            Regex("""\.(mp3|m4a|aac|wav)(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)
    }

    private fun extractJsString(raw: String, key: String): String {
        val pattern = Regex("""(?:window\.)?$key\s*=\s*['"]([^'"]*)['"]""", RegexOption.IGNORE_CASE)
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
            .replace("歌曲狗", "")
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
                .replace(Regex("\\s+"), " ")
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

    fun uriFromPublicUrl(url: String): Uri = Uri.parse(decodeHtmlEntities(url))
}

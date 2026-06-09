package com.yuno.tools.util

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MusicSearchHelper {
    enum class OnlineSource(val label: String) {
        GEQUBAO("歌曲宝"),
        ITINGWA("听蛙")
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
            val songs = (searchGequbao(keyword) + searchITingwa(keyword))
                .distinctBy { it.source.name + "|" + it.pageUrl + "|" + it.title }
            callback(songs)
        }.start()
    }

    private fun searchGequbao(keyword: String): List<OnlineSong> {
        return try {
            val urlStr = "https://www.gequbao.com/s/" + URLEncoder.encode(keyword, "UTF-8")
            val raw = requestText(urlStr, "https://www.gequbao.com/", "text/html,application/xhtml+xml,*/*")
            if (raw.contains("Just a moment", ignoreCase = true) || raw.contains("Cloudflare", ignoreCase = true)) {
                return emptyList()
            }
            Regex("href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(raw)
                .mapNotNull { match ->
                    val href = normalizeUrl(match.groupValues[1], "https://www.gequbao.com")
                    val title = cleanHtml(match.groupValues[2])
                    if (href.contains("gequbao.com") && title.isNotBlank() && title.length <= 80) {
                        OnlineSong(title, "歌曲宝", OnlineSource.GEQUBAO, href, findAudioUrl(raw))
                    } else null
                }
                .take(20)
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun searchITingwa(keyword: String): List<OnlineSong> {
        return try {
            val urlStr = "https://so.itingwa.com/?c=index&t=1&k=" + URLEncoder.encode(keyword, "UTF-8")
            val raw = requestText(urlStr, "https://www.itingwa.com/", "text/html,application/xhtml+xml,*/*")
            val ids = Regex("(?:https?://www\\.itingwa\\.com)?/listen/(\\d+)")
                .findAll(raw)
                .map { it.groupValues[1] }
                .distinct()
                .take(20)
                .toList()
            ids.mapNotNull { parseITingwaDetail(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseITingwaDetail(id: String): OnlineSong? {
        return try {
            val pageUrl = "https://www.itingwa.com/listen/$id"
            val raw = requestText(pageUrl, "https://www.itingwa.com/", "text/html,application/xhtml+xml,*/*")
            val title = Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(raw)?.groupValues?.get(1)?.let(::cleanHtml).orEmpty().ifBlank { "听蛙音乐" }
            val artist = Regex("id=[\"']music_singer[\"'][^>]*>.*?<a[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(raw)?.groupValues?.get(1)?.let(::cleanHtml).orEmpty().ifBlank { "未知艺人" }
            val playUrl = Regex("""init-data=["']([^"']+\.(?:mp3|m4a|aac|wav)(?:\?[^"']*)?)["']""", RegexOption.IGNORE_CASE)
                .find(raw)?.groupValues?.get(1) ?: findAudioUrl(raw)
            OnlineSong(title, artist, OnlineSource.ITINGWA, pageUrl, playUrl)
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
        return Regex("https?://[^\\s\"'<>]+\\.(?:mp3|m4a|aac|wav)(?:\\?[^\\s\"'<>]*)?", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.value
    }

    private fun normalizeUrl(href: String, base: String): String {
        return when {
            href.startsWith("http://") || href.startsWith("https://") -> href
            href.startsWith("//") -> "https:$href"
            href.startsWith("/") -> base + href
            else -> "$base/$href"
        }
    }

    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun uriFromPublicUrl(url: String): Uri = Uri.parse(url)
}

package com.yuno.tools.util

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MusicSearchHelper {
    data class MiguSong(
        val title: String,
        val artist: String,
        val contentId: String,
        val copyrightId: String,
        val playUrl: String?
    )

    fun searchMigu(keyword: String, callback: (List<MiguSong>) -> Unit) {
        Thread {
            val songs = searchMiguWeb(keyword).ifEmpty { searchMiguMeta(keyword) }
            callback(songs)
        }.start()
    }

    private fun searchMiguWeb(keyword: String): List<MiguSong> {
        return try {
            val urlStr = "https://m.music.migu.cn/migu/remoting/scr_search_tag?keyword=" +
                    URLEncoder.encode(keyword, "UTF-8") +
                    "&pgc=1&rows=20&type=2"
            val raw = requestText(urlStr, "https://m.music.migu.cn/v4/search")
            if (!raw.trimStart().startsWith("{")) return emptyList()
            val jsonObj = JSONObject(raw)
            val results = jsonObj.optJSONArray("musics") ?: return emptyList()
            val songs = mutableListOf<MiguSong>()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val name = item.optString("songName", "未知歌曲")
                val artist = item.optString("singerName", "未知")
                val contentId = item.optString("id", "")
                val copyrightId = item.optString("copyrightId", "")
                val mp3 = item.optString("mp3", "").trim()
                songs.add(MiguSong(name, artist, contentId, copyrightId, mp3.takeIf { it.startsWith("http") }))
            }
            songs
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun searchMiguMeta(keyword: String): List<MiguSong> {
        return try {
            val urlStr = "https://app.c.nf.migu.cn/MIGUM3.0/v1.0/content/search_all.do?text=" +
                    URLEncoder.encode(keyword, "UTF-8") +
                    "&pageNo=1&pageSize=20&searchSwitch=" + "%7B%22song%22%3A1%7D"
            val raw = requestText(urlStr, "https://music.migu.cn/")
            val jsonObj = JSONObject(raw)
            val results = jsonObj.getJSONObject("songResultData").getJSONArray("result")
            val songs = mutableListOf<MiguSong>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val name = item.optString("name", "未知歌曲")
                val artist = try {
                    item.getJSONArray("singers").getJSONObject(0).optString("name", "未知")
                } catch (e: Exception) {
                    "未知"
                }
                val contentId = item.optString("contentId", "")
                val copyrightId = item.optString("copyrightId", "")
                songs.add(MiguSong(name, artist, contentId, copyrightId, null))
            }
            songs
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun requestText(urlStr: String, referer: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        conn.setRequestProperty("Referer", referer)
        conn.setRequestProperty("Accept", "application/json,text/plain,*/*")
        conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        return try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    fun uriFromPublicUrl(url: String): Uri = Uri.parse(url)
}

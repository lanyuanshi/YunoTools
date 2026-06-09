package com.yuno.tools.util

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MusicSearchHelper {
    data class MiguSong(val title: String, val artist: String, val contentId: String)

    fun searchMigu(keyword: String, callback: (List<MiguSong>) -> Unit) {
        Thread {
            try {
                val urlStr = "https://app.c.nf.migu.cn/MIGUM3.0/v1.0/content/search_all.do?text=" +
                        URLEncoder.encode(keyword, "UTF-8") +
                        "&pageNo=1&pageSize=20&searchSwitch=" + "%7B%22song%22%3A1%7D"
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.setRequestProperty("Referer", "https://music.migu.cn/")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                val raw = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
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
                    songs.add(MiguSong(name, artist, contentId))
                }
                callback(songs)
            } catch (e: Exception) {
                callback(emptyList())
            }
        }.start()
    }

    fun buildListenUrl(contentId: String): Uri {
        return Uri.parse(
            "https://freetyst.nf.migu.cn/public/product9/" +
                    contentId.substring(0, 2) + "/" +
                    contentId.substring(2, 4) + "/" +
                    contentId + "/" + contentId + ".mp3"
        )
    }
}

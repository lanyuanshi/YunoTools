package com.yuno.tools.util

import java.util.regex.Pattern

object UrlExtractor {

    private val URL_PATTERNS = listOf(
        // 豆包分享线程 / AI 生成图
        Pattern.compile("https?://(?:www\\.)?doubao\\.com/thread/[a-zA-Z0-9]+"),
        // 抖音
        Pattern.compile("https?://v\\.douyin\\.com/[a-zA-Z0-9]+"),
        Pattern.compile("https?://www\\.iesdouyin\\.com/share/video/\\d+"),
        // 快手
        Pattern.compile("https?://v\\.kuaishou\\.com/[a-zA-Z0-9]+"),
        Pattern.compile("https?://www\\.kuaishou\\.com/short-video/[a-zA-Z0-9]+"),
        // 小红书
        Pattern.compile("https?://www\\.xiaohongshu\\.com/discovery/item/[a-zA-Z0-9]+"),
        Pattern.compile("https?://xhslink\\.com/[a-zA-Z0-9]+"),
        // 哔哩哔哩
        Pattern.compile("https?://b23\\.tv/[a-zA-Z0-9]+"),
        Pattern.compile("https?://www\\.bilibili\\.com/video/[a-zA-Z0-9]+"),
        // 西瓜视频
        Pattern.compile("https?://v\\.ixigua\\.com/[a-zA-Z0-9]+"),
        // 微视
        Pattern.compile("https?://video\\.weishi\\.qq\\.com/[a-zA-Z0-9]+"),
        // 通用短链
        Pattern.compile("https?://[a-zA-Z0-9]+\\.[a-zA-Z]+/[a-zA-Z0-9]+")
    )

    fun extractUrl(text: String): String? {
        for (pattern in URL_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                return matcher.group()
            }
        }
        return null
    }
}
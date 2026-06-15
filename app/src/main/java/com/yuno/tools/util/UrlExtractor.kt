package com.yuno.tools.util

import java.util.regex.Pattern

object UrlExtractor {

    private const val SHORT_CODE = "[a-zA-Z0-9_-]+"
    private const val URL_TAIL = "(?:[/?#][^\\s\\u4e00-\\u9fa5]*)?"

    private val URL_PATTERNS = listOf(
        // 豆包分享线程 / AI 生成图
        Pattern.compile("https?://(?:www\\.)?doubao\\.com/thread/$SHORT_CODE$URL_TAIL", Pattern.CASE_INSENSITIVE),
        // 抖音：短链 token 可能包含 -、_，结尾可能带 /
        Pattern.compile("https?://v\\.douyin\\.com/$SHORT_CODE/?$URL_TAIL", Pattern.CASE_INSENSITIVE),
        Pattern.compile("https?://(?:www\\.)?iesdouyin\\.com/share/video/\\d+$URL_TAIL", Pattern.CASE_INSENSITIVE),
        Pattern.compile("https?://(?:www\\.)?douyin\\.com/video/\\d+$URL_TAIL", Pattern.CASE_INSENSITIVE),
        // 快手
        Pattern.compile("https?://v\\.kuaishou\\.com/$SHORT_CODE/?$URL_TAIL", Pattern.CASE_INSENSITIVE),
        Pattern.compile("https?://(?:www\\.)?kuaishou\\.com/short-video/$SHORT_CODE$URL_TAIL", Pattern.CASE_INSENSITIVE),
        // 小红书
        Pattern.compile("https?://(?:www\\.)?xiaohongshu\\.com/(?:discovery/item|explore)/$SHORT_CODE$URL_TAIL", Pattern.CASE_INSENSITIVE),
        Pattern.compile("https?://xhslink\\.com/$SHORT_CODE/?$URL_TAIL", Pattern.CASE_INSENSITIVE),
        // 哔哩哔哩
        Pattern.compile("https?://b23\\.tv/$SHORT_CODE/?$URL_TAIL", Pattern.CASE_INSENSITIVE),
        Pattern.compile("https?://(?:www\\.)?bilibili\\.com/video/[a-zA-Z0-9_-]+$URL_TAIL", Pattern.CASE_INSENSITIVE),
        // 西瓜视频
        Pattern.compile("https?://v\\.ixigua\\.com/$SHORT_CODE/?$URL_TAIL", Pattern.CASE_INSENSITIVE),
        // 微视
        Pattern.compile("https?://video\\.weishi\\.qq\\.com/$SHORT_CODE$URL_TAIL", Pattern.CASE_INSENSITIVE),
        // 通用 URL 兜底：从整段分享文案中取出第一条 http/https 链接
        Pattern.compile("https?://[^\\s\\u4e00-\\u9fa5]+", Pattern.CASE_INSENSITIVE)
    )

    fun extractUrl(text: String): String? {
        for (pattern in URL_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                return cleanUrl(matcher.group())
            }
        }
        return null
    }

    private fun cleanUrl(url: String): String {
        return url.trim()
            .trimEnd('，', '。', '、', '；', ';', ',', '.', '!', '！', '?', '？', ')', '）', ']', '】', '}', '》', '"', '\'')
    }
}

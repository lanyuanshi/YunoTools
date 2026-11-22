package com.yuno.tools.cloud

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement

data class CloudApiResponse<T>(
    @SerializedName("code") val code: Int = -1,
    @SerializedName("data") val data: T? = null,
    @SerializedName("msg") val msg: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("correlation_id") val correlationId: String? = null
) {
    fun message(): String = msg?.takeIf { it.isNotBlank() }
        ?: error?.takeIf { it.isNotBlank() }
        ?: "请求失败（code=$code）"
}

data class CloudToken(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("access_expires") val accessExpires: String? = null,
    @SerializedName("refresh_expires") val refreshExpires: String? = null
)

data class CloudUserGroup(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("permission") val permission: String? = null
)

data class CloudUser(
    @SerializedName("id") val id: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("nickname") val nickname: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("credit") val credit: Long? = null,
    @SerializedName("group") val group: CloudUserGroup? = null,
    @SerializedName("pined") val pined: List<JsonElement>? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("anonymous") val anonymous: Boolean? = null
) {
    fun displayName(): String = nickname?.takeIf { it.isNotBlank() } ?: email?.takeIf { it.isNotBlank() } ?: "未命名用户"
}

data class CloudLoginData(
    @SerializedName("user") val user: CloudUser? = null,
    @SerializedName("token") val token: CloudToken? = null
)

data class CloudCaptcha(
    @SerializedName("image") val image: String? = null,
    @SerializedName("ticket") val ticket: String? = null
)

data class CloudPrepareLogin(
    @SerializedName("password_enabled") val passwordEnabled: Boolean? = null,
    @SerializedName("webauthn_enabled") val webauthnEnabled: Boolean? = null,
    @SerializedName("sso_enabled") val ssoEnabled: Boolean? = null,
    @SerializedName("qq_enabled") val qqEnabled: Boolean? = null
)

data class CloudCapacity(
    @SerializedName("total") val total: Long? = null,
    @SerializedName("used") val used: Long? = null,
    @SerializedName("storage_pack_total") val storagePackTotal: Long? = null
) {
    fun effectiveTotal(): Long = (total ?: 0L) + (storagePackTotal ?: 0L)
}

data class CloudStoragePolicy(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("max_size") val maxSize: Long? = null,
    @SerializedName("relay") val relay: Boolean? = null
)

data class CloudFileProps(
    @SerializedName("capability") val capability: String? = null,
    @SerializedName("max_page_size") val maxPageSize: Int? = null,
    @SerializedName("order_by_options") val orderByOptions: List<String>? = null,
    @SerializedName("order_direction_options") val orderDirectionOptions: List<String>? = null
)

data class CloudPagination(
    @SerializedName("page") val page: Int? = null,
    @SerializedName("page_size") val pageSize: Int? = null,
    @SerializedName("is_cursor") val isCursor: Boolean? = null,
    @SerializedName("next_page_token") val nextPageToken: String? = null
)

data class CloudFile(
    @SerializedName("type") val type: Int? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("permission") val permission: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("size") val size: Long? = null,
    @SerializedName("metadata") val metadata: Map<String, String?>? = null,
    @SerializedName("path") val path: String? = null,
    @SerializedName("capability") val capability: String? = null,
    @SerializedName("owned") val owned: Boolean? = null,
    @SerializedName("primary_entity") val primaryEntity: String? = null,
    @SerializedName("context_hint") val contextHint: String? = null
) {
    val isFolder: Boolean get() = type == 1
    val isFile: Boolean get() = type == 0
    val uri: String get() = path ?: ""
    val fileName: String get() = name ?: uri.substringAfterLast('/')
    val extension: String get() = fileName.substringAfterLast('.', "").lowercase()
    val kind: FileKind
        get() {
            if (isFolder) return FileKind.FOLDER
            return when (extension) {
                "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg" -> FileKind.IMAGE
                "mp4", "mkv", "avi", "mov", "webm", "m4v", "ts", "flv", "wmv" -> FileKind.VIDEO
                "mp3", "flac", "wav", "aac", "ogg", "m4a", "wma" -> FileKind.AUDIO
                "txt", "md", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "json", "xml", "csv", "apk", "zip", "rar", "7z" -> FileKind.DOCUMENT
                else -> FileKind.OTHER
            }
        }
}

enum class FileKind(val label: String) {
    FOLDER("文件夹"),
    IMAGE("图片"),
    VIDEO("视频"),
    AUDIO("音频"),
    DOCUMENT("文档"),
    OTHER("文件")
}

data class CloudFileListData(
    @SerializedName("files") val files: List<CloudFile>? = null,
    @SerializedName("parent") val parent: CloudFile? = null,
    @SerializedName("props") val props: CloudFileProps? = null,
    @SerializedName("context_hint") val contextHint: String? = null,
    @SerializedName("mixed_type") val mixedType: Boolean? = null,
    @SerializedName("storage_policy") val storagePolicy: CloudStoragePolicy? = null,
    @SerializedName("pagination") val pagination: CloudPagination? = null
)

data class CloudFileInfoData(
    @SerializedName("files") val files: List<CloudFile>? = null,
    @SerializedName("data") val single: CloudFile? = null
)

data class CloudFileUrlItem(
    @SerializedName("url") val url: String? = null,
    @SerializedName("stream_saver_display_name") val displayName: String? = null
)

data class CloudFileUrlData(
    @SerializedName("urls") val urls: List<CloudFileUrlItem>? = null,
    @SerializedName("expires") val expires: String? = null
)

data class CloudShareOwner(
    @SerializedName("id") val id: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("nickname") val nickname: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
) {
    fun displayName(): String = nickname?.takeIf { it.isNotBlank() } ?: email?.takeIf { it.isNotBlank() } ?: "未知用户"
}

data class CloudShare(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("visited") val visited: Int? = null,
    @SerializedName("downloaded") val downloaded: Int? = null,
    @SerializedName("price") val price: Int? = null,
    @SerializedName("unlocked") val unlocked: Boolean? = null,
    @SerializedName("source_type") val sourceType: Int? = null,
    @SerializedName("owner") val owner: CloudShareOwner? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("expires") val expires: String? = null,
    @SerializedName("expired") val expired: Boolean? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("is_private") val isPrivate: Boolean? = null,
    @SerializedName("password") val password: String? = null,
    @SerializedName("source_uri") val sourceUri: String? = null,
    @SerializedName("share_view") val shareView: Boolean? = null,
    @SerializedName("show_readme") val showReadme: Boolean? = null,
    @SerializedName("password_protected") val passwordProtected: Boolean? = null,
    @SerializedName("permission_setting") val permissionSetting: JsonElement? = null
) {
    val folder: Boolean get() = sourceType == 1
}

data class CloudShareListData(
    @SerializedName("shares") val shares: List<CloudShare>? = null,
    @SerializedName("pagination") val pagination: CloudPagination? = null
)

data class CloudUploadSession(
    @SerializedName("session_id") val sessionId: String? = null,
    @SerializedName("upload_id") val uploadId: String? = null,
    @SerializedName("chunk_size") val chunkSize: Long? = null,
    @SerializedName("expires") val expires: Long? = null,
    @SerializedName("upload_urls") val uploadUrls: List<String>? = null,
    @SerializedName("credential") val credential: String? = null,
    @SerializedName("completeURL") val completeUrl: String? = null,
    @SerializedName("storage_policy") val storagePolicy: CloudStoragePolicy? = null,
    @SerializedName("mime_type") val mimeType: String? = null,
    @SerializedName("upload_policy") val uploadPolicy: String? = null,
    @SerializedName("uri") val uri: String? = null,
    @SerializedName("callback_secret") val callbackSecret: String? = null
)

data class CloudSiteBasic(
    @SerializedName("instance_id") val instanceId: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("captcha_type") val captchaType: String? = null,
    @SerializedName("user") val user: CloudUser? = null
)

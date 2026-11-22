package com.yuno.tools.cloud

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.yuno.tools.YunoApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okio.BufferedSink
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class CloudApiException(val apiCode: Int, message: String) : Exception(message)

object CloudDriveSettings {
    private const val PREF = "cloud_drive_settings"
    private const val KEY_BASE_URL = "base_url"
    private const val DEFAULT_BASE_URL = "https://www.lyyp.cloud"

    fun getBaseUrl(context: Context): String {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        return normalizeBaseUrl(raw)
    }

    fun getWebBaseUrl(context: Context): String = getBaseUrl(context).removeSuffix("/api/v4/").removeSuffix("/api/v4")

    fun setBaseUrl(context: Context, url: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_BASE_URL, normalizeBaseUrl(url))
            .apply()
        CloudDriveClient.reset()
    }

    private fun normalizeBaseUrl(raw: String): String {
        var value = raw.trim()
        if (value.isEmpty()) value = DEFAULT_BASE_URL
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "https://$value"
        value = value.trimEnd('/')
        if (!value.endsWith("/api/v4")) value += "/api/v4"
        return "$value/"
    }
}

object CloudDriveSession {
    private const val PREF = "cloud_drive_session"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_JSON = "user_json"
    private val gson = Gson()

    fun accessToken(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_ACCESS, "") ?: ""

    fun hasSession(context: Context): Boolean = accessToken(context).isNotBlank()

    fun userId(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_USER_ID, "") ?: ""

    fun currentUser(context: Context): CloudUser? {
        val json = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_USER_JSON, null) ?: return null
        return runCatching { gson.fromJson(json, CloudUser::class.java) }.getOrNull()
    }

    fun saveLogin(context: Context, data: CloudLoginData) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        data.token?.accessToken?.let { prefs.putString(KEY_ACCESS, it) }
        data.token?.refreshToken?.let { prefs.putString(KEY_REFRESH, it) }
        data.user?.id?.let { prefs.putString(KEY_USER_ID, it) }
        data.user?.let { prefs.putString(KEY_USER_JSON, gson.toJson(it)) }
        prefs.apply()
    }

    fun saveTokenPair(context: Context, token: CloudToken) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        token.accessToken?.let { prefs.putString(KEY_ACCESS, it) }
        token.refreshToken?.let { prefs.putString(KEY_REFRESH, it) }
        prefs.apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun refreshToken(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_REFRESH, "") ?: ""

    suspend fun refresh(context: Context): Boolean {
        val token = refreshToken(context)
        if (token.isBlank()) return false
        return runCatching {
            val api = CloudDriveClient.api(context)
            val body = JsonObject().apply { addProperty("refresh_token", token) }
            val response = api.refreshToken(body)
            val envelope = response.body()
            if (response.isSuccessful && envelope?.code == 0 && envelope.data != null) {
                saveTokenPair(context, envelope.data)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }
}

interface CloudDriveApi {
    @GET("site/config/basic")
    suspend fun siteBasic(): Response<CloudApiResponse<CloudSiteBasic>>

    @GET("session/prepare")
    suspend fun prepareLogin(@Query("email") email: String): Response<CloudApiResponse<CloudPrepareLogin>>

    @GET("site/captcha")
    suspend fun captcha(): Response<CloudApiResponse<CloudCaptcha>>

    @POST("session/token")
    suspend fun login(@Body body: JsonObject): Response<CloudApiResponse<JsonElement>>

    @POST("session/token/2fa")
    suspend fun login2fa(@Body body: JsonObject): Response<CloudApiResponse<JsonElement>>

    @POST("session/token/refresh")
    suspend fun refreshToken(@Body body: JsonObject): Response<CloudApiResponse<CloudToken>>

    @HTTP(method = "DELETE", path = "session/token", hasBody = true)
    suspend fun logout(@Body body: JsonObject): Response<CloudApiResponse<JsonElement>>

    @GET("user/info/{userId}")
    suspend fun userInfo(@Path("userId") userId: String): Response<CloudApiResponse<CloudUser>>

    @GET("user/capacity")
    suspend fun capacity(): Response<CloudApiResponse<CloudCapacity>>

    @GET("file")
    suspend fun listFiles(@QueryMap params: Map<String, String>): Response<CloudApiResponse<CloudFileListData>>

    @GET("file/info")
    suspend fun fileInfo(
        @Query("uri") uri: String? = null,
        @Query("id") id: String? = null,
        @Query("extended") extended: Boolean = false,
        @Query("folder_summary") folderSummary: Boolean = false
    ): Response<CloudApiResponse<CloudFile>>

    @POST("file/create")
    suspend fun createFile(@Body body: JsonObject): Response<CloudApiResponse<CloudFile>>

    @POST("file/rename")
    suspend fun renameFile(@Body body: JsonObject): Response<CloudApiResponse<CloudFile>>

    @POST("file/move")
    suspend fun moveFiles(@Body body: JsonObject): Response<CloudApiResponse<JsonElement>>

    @POST("file/restore")
    suspend fun restoreFiles(@Body body: JsonObject): Response<CloudApiResponse<JsonElement>>

    @HTTP(method = "DELETE", path = "file", hasBody = true)
    suspend fun deleteFiles(@Body body: JsonObject): Response<CloudApiResponse<JsonElement>>

    @POST("file/url")
    suspend fun fileUrls(@Body body: JsonObject): Response<CloudApiResponse<CloudFileUrlData>>

    @PUT("file/content")
    suspend fun updateFileContent(@Query("uri") uri: String): Response<CloudApiResponse<JsonElement>>

    @PUT("file/upload")
    suspend fun createUploadSession(@Body body: JsonObject): Response<CloudApiResponse<CloudUploadSession>>

    @HTTP(method = "DELETE", path = "file/upload", hasBody = true)
    suspend fun deleteUploadSession(@Body body: JsonObject): Response<CloudApiResponse<JsonElement>>

    @PUT("share")
    suspend fun createShare(@Body body: JsonObject): Response<CloudApiResponse<JsonElement>>

    @POST("share/{id}")
    suspend fun editShare(@Path("id") id: String, @Body body: JsonObject): Response<CloudApiResponse<JsonElement>>

    @GET("share")
    suspend fun listShares(@QueryMap params: Map<String, String>): Response<CloudApiResponse<CloudShareListData>>

    @HTTP(method = "DELETE", path = "share/{id}", hasBody = false)
    suspend fun deleteShare(@Path("id") id: String): Response<CloudApiResponse<JsonElement>>

    @GET("share/info/{id}")
    suspend fun shareInfo(
        @Path("id") id: String,
        @Query("password") password: String? = null,
        @Query("count_views") countViews: Boolean = false,
        @Query("owner_extended") ownerExtended: Boolean = false
    ): Response<CloudApiResponse<CloudShare>>
}

object CloudDriveClient {
    private const val TIMEOUT_SECONDS = 60L
    private val gson = Gson()
    private var cachedBaseUrl = ""
    private var cachedApi: CloudDriveApi? = null
    private var cachedHttp: OkHttpClient? = null
    private var cachedRawHttp: OkHttpClient? = null

    fun reset() {
        cachedBaseUrl = ""
        cachedApi = null
        cachedHttp = null
        cachedRawHttp = null
    }

    fun api(context: Context): CloudDriveApi {
        val base = CloudDriveSettings.getBaseUrl(context)
        if (cachedApi != null && cachedBaseUrl == base) return cachedApi!!
        val authClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val path = request.url.encodedPath
                val skipAuth = path.contains("/session/prepare") ||
                    path.contains("/session/token") ||
                    path.contains("/site/")
                val token = CloudDriveSession.accessToken(YunoApp.instance)
                val builder = request.newBuilder()
                if (!skipAuth && token.isNotBlank()) {
                    builder.header("Authorization", "Bearer $token")
                }
                chain.proceed(builder.build())
            }
            .build()
        cachedHttp = authClient
        cachedRawHttp = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        cachedApi = Retrofit.Builder()
            .baseUrl(base)
            .client(authClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CloudDriveApi::class.java)
        cachedBaseUrl = base
        return cachedApi!!
    }

    fun httpClient(): OkHttpClient {
        api(YunoApp.instance)
        return cachedHttp!!
    }

    fun rawHttpClient(): OkHttpClient {
        api(YunoApp.instance)
        return cachedRawHttp!!
    }

    fun apiBaseUrl(context: Context): String = CloudDriveSettings.getBaseUrl(context)

    fun gson(): Gson = gson
}

class CloudRepository {
    private val gson = CloudDriveClient.gson()

    suspend fun <T> call(context: Context, block: suspend (CloudDriveApi) -> Response<CloudApiResponse<T>>): T? {
        val api = CloudDriveClient.api(context)
        var retried = false
        while (true) {
            val response = block(api)
            val envelope = response.body()
            if (response.isSuccessful && envelope != null) {
                if (envelope.code == 0) return envelope.data
                if (!retried && (envelope.code == 401 || envelope.code == 40020) && CloudDriveSession.refresh(context)) {
                    retried = true
                    continue
                }
                throw CloudApiException(envelope.code, envelope.message())
            }
            val errorText = response.errorBody()?.string()?.take(300).orEmpty()
            throw CloudApiException(response.code(), errorText.ifBlank { "HTTP ${response.code()}" })
        }
    }

    suspend fun siteBasic(context: Context): CloudSiteBasic? = call(context) { it.siteBasic() }
    suspend fun captcha(context: Context): CloudCaptcha? = call(context) { it.captcha() }
    suspend fun prepareLogin(context: Context, email: String): CloudPrepareLogin? = call(context) { it.prepareLogin(email) }

    suspend fun login(context: Context, email: String, password: String, captcha: String?, ticket: String?): CloudLoginData {
        val body = JsonObject().apply {
            addProperty("email", email)
            addProperty("password", password)
            if (!captcha.isNullOrBlank()) addProperty("captcha", captcha)
            if (!ticket.isNullOrBlank()) addProperty("ticket", ticket)
        }
        return parseLoginResponse(context) { api -> api.login(body) }
    }

    suspend fun login2fa(context: Context, sessionId: String, code: String): CloudLoginData {
        val body = JsonObject().apply {
            addProperty("session_id", sessionId)
            addProperty("opt", code)
        }
        return parseLoginResponse(context) { api -> api.login2fa(body) }
    }

    private suspend fun parseLoginResponse(
        context: Context,
        block: suspend (CloudDriveApi) -> Response<CloudApiResponse<JsonElement>>
    ): CloudLoginData {
        val api = CloudDriveClient.api(context)
        val response = block(api)
        val envelope = response.body()
            ?: throw CloudApiException(response.code(), response.errorBody()?.string().orEmpty().ifBlank { "HTTP ${response.code()}" })
        if (envelope.code == 203 && envelope.data != null && envelope.data.isJsonPrimitive) {
            throw CloudTwoFactorRequiredException(envelope.data.asString)
        }
        if (envelope.code == 0 && envelope.data != null) {
            val data = gson.fromJson(envelope.data, CloudLoginData::class.java)
            CloudDriveSession.saveLogin(context, data)
            return data
        }
        throw CloudApiException(envelope.code, envelope.message())
    }

    suspend fun logout(context: Context) {
        runCatching {
            val api = CloudDriveClient.api(context)
            val prefs = context.getSharedPreferences("cloud_drive_session", Context.MODE_PRIVATE)
            val refresh = prefs.getString("refresh_token", "") ?: ""
            val body = JsonObject().apply { addProperty("refresh_token", refresh) }
            api.logout(body)
        }
        CloudDriveSession.clear(context)
    }

    suspend fun ensureProfile(context: Context): CloudUser? {
        CloudDriveSession.currentUser(context)?.let { return it }
        val id = CloudDriveSession.userId(context)
        if (id.isBlank()) return null
        val user = call(context) { it.userInfo(id) }
        user?.let { u ->
            CloudDriveSession.saveLogin(context, CloudLoginData(user = u, token = null))
        }
        return user
    }

    suspend fun capacity(context: Context): CloudCapacity? = call(context) { it.capacity() }

    suspend fun listFiles(context: Context, uri: String, page: Int = 0, pageSize: Int = 100, orderBy: String = "name", orderDirection: String = "asc", nextPageToken: String? = null): CloudFileListData? {
        val params = mutableMapOf(
            "uri" to uri,
            "page" to page.toString(),
            "page_size" to pageSize.toString(),
            "order_by" to orderBy,
            "order_direction" to orderDirection
        )
        if (!nextPageToken.isNullOrBlank()) params["next_page_token"] = nextPageToken
        return call(context) { it.listFiles(params) }
    }

    suspend fun fileInfo(context: Context, uri: String, extended: Boolean = false): CloudFile? = call(context) { it.fileInfo(uri = uri, extended = extended) }

    suspend fun createFolder(context: Context, uri: String): CloudFile? {
        val body = JsonObject().apply {
            addProperty("uri", uri)
            addProperty("type", "folder")
            addProperty("err_on_conflict", false)
        }
        return call(context) { it.createFile(body) }
    }

    suspend fun createEmptyFile(context: Context, uri: String): CloudFile? {
        val body = JsonObject().apply {
            addProperty("uri", uri)
            addProperty("type", "file")
            addProperty("err_on_conflict", false)
        }
        return call(context) { it.createFile(body) }
    }

    suspend fun rename(context: Context, uri: String, newName: String): CloudFile? {
        val body = JsonObject().apply {
            addProperty("uri", uri)
            addProperty("new_name", newName)
        }
        return call(context) { it.renameFile(body) }
    }

    suspend fun move(context: Context, uris: List<String>, dst: String, copy: Boolean = false) {
        val body = JsonObject().apply {
            add("uris", com.google.gson.JsonArray().apply { uris.forEach { add(it) } })
            addProperty("dst", dst)
            addProperty("copy", copy)
        }
        call<JsonElement>(context) { it.moveFiles(body) }
    }

    suspend fun delete(context: Context, uris: List<String>, skipSoftDelete: Boolean = false) {
        val body = JsonObject().apply {
            add("uris", com.google.gson.JsonArray().apply { uris.forEach { add(it) } })
            addProperty("skip_soft_delete", skipSoftDelete)
        }
        call<JsonElement>(context) { it.deleteFiles(body) }
    }

    suspend fun restore(context: Context, uris: List<String>) {
        val body = JsonObject().apply {
            add("uris", com.google.gson.JsonArray().apply { uris.forEach { add(it) } })
        }
        call<JsonElement>(context) { it.restoreFiles(body) }
    }

    suspend fun fileUrl(context: Context, uri: String, download: Boolean = true, archive: Boolean = false): CloudFileUrlItem? {
        val body = JsonObject().apply {
            add("uris", com.google.gson.JsonArray().apply { add(uri) })
            addProperty("download", download)
            addProperty("skip_error", false)
            if (archive) addProperty("archive", true)
        }
        val data = call(context) { it.fileUrls(body) }
        return data?.urls?.firstOrNull { !it.url.isNullOrBlank() }
    }

    suspend fun createUploadSession(context: Context, uri: String, size: Long, mimeType: String?, lastModified: Long?): CloudUploadSession? {
        val body = JsonObject().apply {
            addProperty("uri", uri)
            addProperty("size", size)
            if (!mimeType.isNullOrBlank()) addProperty("mime_type", mimeType)
            if (lastModified != null && lastModified > 0) addProperty("last_modified", lastModified)
            addProperty("entity_type", "version")
        }
        return call(context) { it.createUploadSession(body) }
    }

    suspend fun deleteUploadSession(context: Context, sessionId: String, uri: String) {
        val body = JsonObject().apply {
            addProperty("id", sessionId)
            addProperty("uri", uri)
        }
        call<JsonElement>(context) { it.deleteUploadSession(body) }
    }

    suspend fun createShare(context: Context, uri: String, privateLink: Boolean, password: String?, expireSeconds: Long?, shareView: Boolean = true, showReadme: Boolean = false): String? {
        val body = JsonObject().apply {
            addProperty("uri", uri)
            addProperty("is_private", privateLink)
            addProperty("share_view", shareView)
            addProperty("show_readme", showReadme)
            if (privateLink && !password.isNullOrBlank()) addProperty("password", password)
            if (expireSeconds != null && expireSeconds > 0) addProperty("expire", expireSeconds)
        }
        val data = call<JsonElement>(context) { it.createShare(body) }
        return data?.takeIf { it.isJsonPrimitive }?.asString
    }

    suspend fun listShares(context: Context, pageSize: Int = 50): List<CloudShare> {
        val params = mapOf("page_size" to pageSize.toString(), "order_by" to "created_at", "order_direction" to "desc")
        return call(context) { it.listShares(params) }?.shares.orEmpty()
    }

    suspend fun deleteShare(context: Context, id: String) {
        call<JsonElement>(context) { it.deleteShare(id) }
    }

    suspend fun shareInfo(context: Context, id: String, password: String? = null): CloudShare? =
        call(context) { it.shareInfo(id, password = password, countViews = true, ownerExtended = true) }
}

class CloudTwoFactorRequiredException(val sessionId: String) : Exception("需要两步验证")

data class CloudUploadSource(
    val uri: Uri,
    val name: String,
    val mimeType: String?,
    val size: Long,
    val lastModified: Long?
)

object CloudUploadHelper {
    private const val SMALL_UPLOAD_BYTES = 16L * 1024 * 1024
    private val octetStream = "application/octet-stream".toMediaType()

    fun querySource(context: Context, uri: Uri, preferredName: String? = null): CloudUploadSource {
        var name = preferredName.orEmpty()
        var size = -1L
        var lastModified: Long? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (name.isBlank() && nameIndex >= 0) name = cursor.getString(nameIndex).orEmpty()
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        if (size < 0) {
            size = runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
            }.getOrNull() ?: -1L
        }
        if (name.isBlank()) name = "upload_${System.currentTimeMillis()}"
        val mime = context.contentResolver.getType(uri)
        return CloudUploadSource(uri, name, mime, size, lastModified)
    }

    suspend fun upload(
        context: Context,
        repository: CloudRepository,
        source: CloudUploadSource,
        targetDirUri: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): String {
        val targetUri = joinCloudUri(targetDirUri, source.name)
        val size = if (source.size >= 0) source.size else querySource(context, source.uri, source.name).size
        if (size < 0) throw CloudApiException(-1, "无法读取文件大小")
        if (size <= SMALL_UPLOAD_BYTES) {
            putFileContent(context, targetUri, source, size, onProgress)
            return targetUri
        }
        val session = repository.createUploadSession(context, targetUri, size, source.mimeType, source.lastModified)
            ?: throw CloudApiException(-1, "创建上传会话失败")
        val sessionId = session.sessionId ?: throw CloudApiException(-1, "上传会话缺少 ID")
        try {
            val chunkSize = session.chunkSize?.takeIf { it > 0 } ?: size
            val policyType = session.storagePolicy?.type?.lowercase()
            val useCloudreveUploader = session.storagePolicy?.relay == true || policyType == null || policyType == "local"
            if (useCloudreveUploader) {
                uploadViaCloudreve(context, sessionId, source, size, chunkSize, onProgress)
            } else if (policyType == "remote" && !session.uploadUrls.isNullOrEmpty() && !session.credential.isNullOrBlank()) {
                uploadRemoteChunks(context, session, source, size, chunkSize, onProgress)
            } else {
                runCatching { repository.deleteUploadSession(context, sessionId, targetUri) }
                putFileContent(context, targetUri, source, size, onProgress)
            }
        } catch (e: Exception) {
            runCatching { repository.deleteUploadSession(context, sessionId, targetUri) }
            if (e is CloudApiException) throw e
            throw CloudApiException(-1, e.message ?: "上传失败")
        }
        return targetUri
    }

    private suspend fun putFileContent(
        context: Context,
        targetUri: String,
        source: CloudUploadSource,
        size: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        val apiBase = CloudDriveSettings.getBaseUrl(context)
        val url = apiBase.trimEnd('/') + "/file/content?uri=" + Uri.encode(targetUri)
        val token = CloudDriveSession.accessToken(context)
        val request = Request.Builder()
            .url(url)
            .put(ProgressRequestBody(context, source.uri, size, 0, size) { uploaded ->
                onProgress(uploaded, size)
            })
            .header("Content-Type", "application/octet-stream")
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
            .build()
        CloudDriveClient.rawHttpClient().newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val envelope = runCatching { gsonParse(text) }.getOrNull()
            if (!response.isSuccessful || envelope == null || envelope.code != 0) {
                throw CloudApiException(envelope?.code ?: response.code, envelope?.message() ?: text.ifBlank { "HTTP ${response.code}" })
            }
        }
    }

    private suspend fun uploadViaCloudreve(
        context: Context,
        sessionId: String,
        source: CloudUploadSource,
        size: Long,
        chunkSize: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        val apiBase = CloudDriveSettings.getBaseUrl(context).trimEnd('/')
        val token = CloudDriveSession.accessToken(context)
        val totalChunks = ceil(size.toDouble() / chunkSize.toDouble()).toInt().coerceAtLeast(1)
        var uploaded = 0L
        for (index in 0 until totalChunks) {
            val offset = index * chunkSize
            val length = minOf(chunkSize, size - offset)
            val url = "$apiBase/file/upload/$sessionId/$index"
            val request = Request.Builder()
                .url(url)
                .post(ProgressRequestBody(context, source.uri, size, offset, length) { uploadedBytes ->
                    onProgress(uploadedBytes, size)
                })
                .header("Content-Type", "application/octet-stream")
                .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
                .build()
            CloudDriveClient.rawHttpClient().newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val envelope = runCatching { gsonParse(text) }.getOrNull()
                if (!response.isSuccessful || envelope == null || envelope.code != 0) {
                    throw CloudApiException(envelope?.code ?: response.code, envelope?.message() ?: text.ifBlank { "HTTP ${response.code}" })
                }
            }
            uploaded += length
            onProgress(uploaded, size)
        }
    }

    private suspend fun uploadRemoteChunks(
        context: Context,
        session: CloudUploadSession,
        source: CloudUploadSource,
        size: Long,
        chunkSize: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        val base = session.uploadUrls!!.first()
        val totalChunks = ceil(size.toDouble() / chunkSize.toDouble()).toInt().coerceAtLeast(1)
        var uploaded = 0L
        for (index in 0 until totalChunks) {
            val offset = index * chunkSize
            val length = minOf(chunkSize, size - offset)
            val separator = if (base.contains('?')) "&" else "?"
            val url = "$base${separator}chunk=$index"
            val request = Request.Builder()
                .url(url)
                .post(ProgressRequestBody(context, source.uri, size, offset, length) { uploadedBytes ->
                    onProgress(uploadedBytes, size)
                })
                .header("Content-Type", session.mimeType ?: "application/octet-stream")
                .header("Authorization", session.credential!!)
                .build()
            CloudDriveClient.rawHttpClient().newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val envelope = runCatching { gsonParse(text) }.getOrNull()
                if (!response.isSuccessful || envelope == null || envelope.code != 0) {
                    throw CloudApiException(envelope?.code ?: response.code, envelope?.message() ?: text.ifBlank { "HTTP ${response.code}" })
                }
            }
            uploaded += length
            onProgress(uploaded, size)
        }
    }

    private fun gsonParse(text: String): CloudApiResponse<JsonElement>? {
        val type = object : TypeToken<CloudApiResponse<JsonElement>>() {}.type
        return runCatching { CloudDriveClient.gson().fromJson<CloudApiResponse<JsonElement>>(text, type) }.getOrNull()
    }

    fun joinCloudUri(dirUri: String, name: String): String {
        val base = dirUri.trimEnd('/')
        return "$base/${Uri.encode(name)}"
    }

    fun parentCloudUri(uri: String): String {
        val clean = uri.substringBefore('?').trimEnd('/')
        if (clean == "cloudreve://my" || clean == "cloudreve://trash" ||
            clean == "cloudreve://shared_with_me" || clean == "cloudreve://shared_by_me"
        ) return clean
        val idx = clean.lastIndexOf('/')
        if (idx <= "cloudreve://".length) return "cloudreve://my"
        return clean.substring(0, idx)
    }

    class ProgressRequestBody(
        private val context: Context,
        private val uri: Uri,
        private val totalSize: Long,
        private val offset: Long,
        private val length: Long,
        private val onProgress: (Long) -> Unit
    ) : RequestBody() {
        override fun contentType() = octetStream
        override fun contentLength() = length

        override fun writeTo(sink: BufferedSink) {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw CloudApiException(-1, "无法打开文件")
            try {
                FileInputStream(pfd.fileDescriptor).use { input ->
                    input.channel.position(offset)
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (written < length) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), length - written).toInt())
                        if (read <= 0) break
                        sink.write(buffer, 0, read)
                        written += read
                        onProgress(offset + written)
                    }
                    if (written != length) throw CloudApiException(-1, "文件读取不完整")
                }
            } finally {
                pfd.close()
            }
        }
    }
}

package com.yuno.tools.ui.tools

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yuno.tools.R
import com.yuno.tools.cloud.CloudApiException
import com.yuno.tools.cloud.CloudCapacity
import com.yuno.tools.cloud.CloudDriveClient
import com.yuno.tools.cloud.CloudDriveSession
import com.yuno.tools.cloud.CloudDriveSettings
import com.yuno.tools.cloud.CloudFile
import com.yuno.tools.cloud.CloudRepository
import com.yuno.tools.cloud.CloudShare
import com.yuno.tools.cloud.CloudTwoFactorRequiredException
import com.yuno.tools.cloud.CloudUploadHelper
import com.yuno.tools.cloud.CloudUploadSource
import com.yuno.tools.cloud.FileKind
import com.yuno.tools.util.DownloadUtil
import com.yuno.tools.util.ThemeApplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.util.Locale

class CloudDriveActivity : AppCompatActivity() {
    private val repository = CloudRepository()
    private val formatter = DecimalFormat("0.#")

    private lateinit var tvStatus: TextView
    private lateinit var tvCapacityText: TextView
    private lateinit var progressCapacity: ProgressBar
    private lateinit var btnAccount: TextView
    private lateinit var panelLogin: LinearLayout
    private lateinit var panelMain: LinearLayout
    private lateinit var etServer: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etCaptcha: EditText
    private lateinit var ivCaptcha: ImageView
    private lateinit var btnCaptchaRefresh: TextView
    private lateinit var rowCaptcha: LinearLayout
    private lateinit var etTwoFa: EditText
    private lateinit var btnLogin: TextView
    private lateinit var tvUser: TextView
    private lateinit var tvPath: TextView
    private lateinit var etSearch: EditText
    private lateinit var rootTabs: LinearLayout
    private lateinit var filterRow: LinearLayout
    private lateinit var listBox: LinearLayout
    private lateinit var btnLoadMore: TextView
    private lateinit var progressList: ProgressBar

    private var currentRoot = "cloudreve://my"
    private var currentPath = "cloudreve://my"
    private var activeFilter = FileFilter.ALL
    private var orderBy = "name"
    private var orderDirection = "asc"
    private var page = 0
    private var pageSize = 100
    private var hasMore = false
    private var loading = false
    private var files = mutableListOf<CloudFile>()
    private var captchaTicket: String? = null
    private var pendingTwoFaSession: String? = null
    private var uploadRunning = false

    private data class RootTab(val label: String, val uri: String)
    private val rootTabsData = listOf(
        RootTab("我的文件", "cloudreve://my"),
        RootTab("共享给我", "cloudreve://shared_with_me"),
        RootTab("我的分享", "cloudreve://shared_by_me"),
        RootTab("回收站", "cloudreve://trash")
    )

    private enum class FileFilter(val label: String) {
        ALL("全部"),
        FOLDER("文件夹"),
        IMAGE("图片"),
        VIDEO("视频"),
        AUDIO("音频"),
        DOCUMENT("文档")
    }

    private val pickUploadFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) uploadUris(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloud_drive)
        ThemeApplier.apply(this)
        bindViews()
        setupRootTabs()
        setupFilters()
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                reloadFiles()
                true
            } else false
        }
        showSessionState()
        loadSiteInfo()
    }

    private fun bindViews() {
        tvStatus = findViewById(R.id.tvCloudStatus)
        tvCapacityText = findViewById(R.id.tvCloudCapacityText)
        progressCapacity = findViewById(R.id.progressCloudCapacity)
        btnAccount = findViewById(R.id.btnCloudAccount)
        panelLogin = findViewById(R.id.panelCloudLogin)
        panelMain = findViewById(R.id.panelCloudMain)
        etServer = findViewById(R.id.etCloudServer)
        etEmail = findViewById(R.id.etCloudEmail)
        etPassword = findViewById(R.id.etCloudPassword)
        etCaptcha = findViewById(R.id.etCloudCaptcha)
        ivCaptcha = findViewById(R.id.ivCloudCaptcha)
        btnCaptchaRefresh = findViewById(R.id.btnCloudCaptchaRefresh)
        rowCaptcha = findViewById(R.id.rowCloudCaptcha)
        etTwoFa = findViewById(R.id.etCloudTwoFa)
        btnLogin = findViewById(R.id.btnCloudLogin)
        tvUser = findViewById(R.id.tvCloudUser)
        tvPath = findViewById(R.id.tvCloudPath)
        etSearch = findViewById(R.id.etCloudSearch)
        rootTabs = findViewById(R.id.cloudRootTabs)
        filterRow = findViewById(R.id.cloudFilterRow)
        listBox = findViewById(R.id.cloudListBox)
        btnLoadMore = findViewById(R.id.btnCloudLoadMore)
        progressList = findViewById(R.id.progressCloudList)

        findViewById<ImageView>(R.id.btnCloudBack).setOnClickListener { finish() }
        btnAccount.setOnClickListener {
            if (CloudDriveSession.hasSession(this)) confirmLogout() else panelLogin.requestFocus()
        }
        btnCaptchaRefresh.setOnClickListener { loadCaptcha() }
        btnLogin.setOnClickListener { submitLogin() }
        findViewById<TextView>(R.id.btnCloudRefresh).setOnClickListener { reloadFiles() }
        findViewById<TextView>(R.id.btnCloudNewFolder).setOnClickListener { createFolderDialog() }
        findViewById<TextView>(R.id.btnCloudUpload).setOnClickListener { pickUploadFiles.launch(arrayOf("*/*")) }
        findViewById<TextView>(R.id.btnCloudSort).setOnClickListener { showSortDialog() }
        findViewById<TextView>(R.id.btnCloudShareFiles).setOnClickListener { shareCurrentFolder() }
        findViewById<TextView>(R.id.btnCloudShares).setOnClickListener { showMyShares() }
        findViewById<TextView>(R.id.btnCloudTrash).setOnClickListener { switchRoot("cloudreve://trash") }
        findViewById<TextView>(R.id.btnCloudLogout).setOnClickListener { confirmLogout() }
        tvPath.setOnClickListener { goParent() }
        tvPath.setOnLongClickListener {
            copyText("当前路径", tvPath.text.toString())
            true
        }
        btnLoadMore.setOnClickListener { loadFiles(reset = false) }
    }

    private fun showSessionState() {
        val logged = CloudDriveSession.hasSession(this)
        panelLogin.visibility = if (logged) View.GONE else View.VISIBLE
        panelMain.visibility = if (logged) View.VISIBLE else View.GONE
        btnAccount.text = if (logged) "账号" else "登录"
        if (logged) {
            tvUser.text = CloudDriveSession.currentUser(this)?.displayName() ?: "已登录"
            loadProfileAndCapacity()
            loadFiles(reset = true)
        } else {
            tvStatus.text = "未连接蓝云鸢盘"
            tvCapacityText.text = "--"
            progressCapacity.progress = 0
            listBox.removeAllViews()
            loadCaptcha()
        }
    }

    private fun loadSiteInfo() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { repository.siteBasic(this@CloudDriveActivity) }.onSuccess { basic ->
                basic?.title?.takeIf { it.isNotBlank() }?.let { title ->
                    withContext(Dispatchers.Main) { findViewById<TextView>(R.id.tvCloudTitle).text = title }
                }
            }
        }
    }

    private fun loadCaptcha() {
        lifecycleScope.launch(Dispatchers.IO) {
            val captcha = runCatching { repository.captcha(this@CloudDriveActivity) }.getOrNull()
            withContext(Dispatchers.Main) {
                val ticket = captcha?.ticket
                val image = captcha?.image
                if (ticket.isNullOrBlank() || image.isNullOrBlank()) {
                    rowCaptcha.visibility = View.GONE
                    captchaTicket = null
                    return@withContext
                }
                rowCaptcha.visibility = View.VISIBLE
                btnCaptchaRefresh.visibility = View.VISIBLE
                ivCaptcha.visibility = View.VISIBLE
                captchaTicket = ticket
                val base64 = image.substringAfter("base64,", image)
                val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
                val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                if (bitmap != null) ivCaptcha.setImageBitmap(bitmap) else ivCaptcha.setImageDrawable(null)
            }
        }
    }

    private fun submitLogin() {
        val pending = pendingTwoFaSession
        if (pending != null) {
            val code = etTwoFa.text.toString().trim()
            if (code.isBlank()) {
                toast("请输入两步验证码")
                return
            }
            performLogin2fa(pending, code)
            return
        }
        val server = etServer.text.toString().trim()
        if (server.isNotBlank()) CloudDriveSettings.setBaseUrl(this, server)
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        if (email.isBlank() || password.isBlank()) {
            toast("请输入邮箱和密码")
            return
        }
        val captchaText = etCaptcha.text.toString().trim().ifBlank { null }
        setLoginEnabled(false)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                repository.login(
                    this@CloudDriveActivity,
                    email,
                    password,
                    captchaText,
                    captchaTicket
                )
                withContext(Dispatchers.Main) { onLoginSuccess() }
            } catch (e: CloudTwoFactorRequiredException) {
                withContext(Dispatchers.Main) {
                    pendingTwoFaSession = e.sessionId
                    etTwoFa.visibility = View.VISIBLE
                    btnLogin.text = "验证并登录"
                    toast("账号已开启两步验证，请输入验证码")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toast(loginErrorText(e))
                    loadCaptcha()
                }
            } finally {
                withContext(Dispatchers.Main) { setLoginEnabled(true) }
            }
        }
    }

    private fun performLogin2fa(sessionId: String, code: String) {
        setLoginEnabled(false)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                repository.login2fa(this@CloudDriveActivity, sessionId, code)
                withContext(Dispatchers.Main) { onLoginSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast(loginErrorText(e)) }
            } finally {
                withContext(Dispatchers.Main) { setLoginEnabled(true) }
            }
        }
    }

    private fun setLoginEnabled(enabled: Boolean) {
        btnLogin.isEnabled = enabled
        btnLogin.alpha = if (enabled) 1f else 0.6f
        btnLogin.text = if (pendingTwoFaSession == null) "登录" else "验证并登录"
    }

    private fun loginErrorText(e: Exception): String = when (e) {
        is CloudApiException -> e.message ?: "登录失败"
        else -> e.message ?: "登录失败"
    }

    private fun onLoginSuccess() {
        pendingTwoFaSession = null
        etTwoFa.visibility = View.GONE
        etTwoFa.setText("")
        btnLogin.text = "登录"
        showSessionState()
        toast("登录成功")
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("退出登录")
            .setMessage("确定退出当前蓝云鸢盘账号吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("退出") { _, _ -> logout() }
            .show()
    }

    private fun logout() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { repository.logout(this@CloudDriveActivity) }
            withContext(Dispatchers.Main) {
                files.clear()
                currentRoot = "cloudreve://my"
                currentPath = currentRoot
                showSessionState()
                toast("已退出")
            }
        }
    }

    private fun loadProfileAndCapacity() {
        lifecycleScope.launch(Dispatchers.IO) {
            val user = runCatching { repository.ensureProfile(this@CloudDriveActivity) }.getOrNull()
            val capacity = runCatching { repository.capacity(this@CloudDriveActivity) }.getOrNull()
            withContext(Dispatchers.Main) {
                user?.let { tvUser.text = "${it.displayName()} · ${it.group?.name.orEmpty()}".trim(' ', '·') }
                renderCapacity(capacity)
            }
        }
    }

    private fun renderCapacity(capacity: CloudCapacity?) {
        if (capacity == null) {
            tvStatus.text = "已连接 · 容量读取失败"
            return
        }
        val total = capacity.effectiveTotal()
        val used = capacity.used ?: 0L
        val percent = if (total > 0) ((used * 100) / total).toInt().coerceIn(0, 100) else 0
        progressCapacity.progress = percent
        tvCapacityText.text = if (total > 0) "$percent%" else formatBytes(used)
        tvStatus.text = "已连接 · 已用 ${formatBytes(used)} / ${formatBytes(total)}"
    }

    private fun setupRootTabs() {
        rootTabs.removeAllViews()
        rootTabsData.forEach { tab ->
            val selected = tab.uri == currentRoot
            rootTabs.addView(tabPill(tab.label, selected) {
                switchRoot(tab.uri)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)).apply {
                rightMargin = dp(8)
            })
        }
    }

    private fun switchRoot(uri: String) {
        currentRoot = uri
        currentPath = uri
        etSearch.setText("")
        setupRootTabs()
        updatePathText()
        reloadFiles()
    }

    private fun setupFilters() {
        filterRow.removeAllViews()
        FileFilter.values().forEach { filter ->
            filterRow.addView(tabPill(filter.label, filter == activeFilter) {
                activeFilter = filter
                setupFilters()
                reloadFiles()
            }, LinearLayout.LayoutParams(dp(76), dp(34)).apply { rightMargin = dp(8) })
        }
    }

    private fun reloadFiles() {
        page = 0
        files.clear()
        loadFiles(reset = true)
    }

    private fun loadFiles(reset: Boolean) {
        if (!CloudDriveSession.hasSession(this) || loading) return
        loading = true
        progressList.visibility = View.VISIBLE
        btnLoadMore.visibility = View.GONE
        val requestPage = if (reset) 0 else page
        val requestPageSize = pageSize
        val uri = filesUri()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val data = repository.listFiles(
                    this@CloudDriveActivity,
                    uri,
                    page = requestPage,
                    pageSize = requestPageSize,
                    orderBy = orderBy,
                    orderDirection = orderDirection
                )
                val remoteFiles = data?.files.orEmpty()
                val nextPageSize = data?.props?.maxPageSize?.coerceIn(50, 500) ?: requestPageSize
                withContext(Dispatchers.Main) {
                    if (reset) files.clear()
                    files.addAll(remoteFiles)
                    renderFiles()
                    val returned = remoteFiles.size
                    pageSize = nextPageSize
                    hasMore = returned >= requestPageSize
                    page = requestPage + 1
                    btnLoadMore.visibility = if (hasMore) View.VISIBLE else View.GONE
                    tvStatus.text = if (files.isEmpty()) "目录为空" else "已加载 ${files.size} 个项目"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toast(errorText(e, "读取目录失败"))
                    if (reset) renderFiles()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    loading = false
                    progressList.visibility = View.GONE
                }
            }
        }
    }

    private fun filesUri(): String {
        val query = etSearch.text.toString().trim()
        if (query.isBlank()) return currentPath
        val encoded = Uri.encode(query, "")
        val base = currentPath.substringBefore('?')
        return "$base?name=$encoded&case_folding=true"
    }

    private fun updatePathText() {
        tvPath.text = currentPath
    }

    private fun goParent() {
        val parent = CloudUploadHelper.parentCloudUri(currentPath)
        if (parent == currentPath) return
        currentPath = parent
        updatePathText()
        reloadFiles()
    }

    private fun renderFiles() {
        listBox.removeAllViews()
        val query = etSearch.text.toString().trim()
        val filtered = files.filter { matchesFilter(it) }
        if (filtered.isEmpty()) {
            listBox.addView(emptyCard(if (query.isBlank()) "当前目录为空" else "没有匹配“$query”的文件"))
            return
        }
        filtered.forEach { file ->
            listBox.addView(fileCard(file), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
    }

    private fun matchesFilter(file: CloudFile): Boolean {
        return when (activeFilter) {
            FileFilter.ALL -> true
            FileFilter.FOLDER -> file.isFolder
            FileFilter.IMAGE -> file.kind == FileKind.IMAGE
            FileFilter.VIDEO -> file.kind == FileKind.VIDEO
            FileFilter.AUDIO -> file.kind == FileKind.AUDIO
            FileFilter.DOCUMENT -> file.kind == FileKind.DOCUMENT
        }
    }

    private fun fileCard(file: CloudFile): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg("#FFFFFF", 18)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener {
                if (file.isFolder) openFolder(file) else showFileActions(file)
            }
        }
        val icon = TextView(this).apply {
            text = kindSymbol(file)
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(kindColor(file))
            background = bg(kindBg(file), 14)
        }
        card.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(10) })
        val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textBox.addView(TextView(this).apply {
            text = file.fileName
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#111827"))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        textBox.addView(TextView(this).apply {
            text = fileMeta(file)
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        card.addView(textBox, LinearLayout.LayoutParams(0, -2, 1f))
        val action = TextView(this).apply {
            text = "操作"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#2563EB"))
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = bg("#EFF6FF", 12)
            setOnClickListener { showFileActions(file) }
        }
        card.addView(action)
        return card
    }

    private fun kindSymbol(file: CloudFile): String = when (file.kind) {
        FileKind.FOLDER -> "夹"
        FileKind.IMAGE -> "图"
        FileKind.VIDEO -> "视"
        FileKind.AUDIO -> "音"
        FileKind.DOCUMENT -> "文"
        FileKind.OTHER -> "云"
    }

    private fun kindColor(file: CloudFile): Int = when (file.kind) {
        FileKind.FOLDER -> Color.parseColor("#2563EB")
        FileKind.IMAGE -> Color.parseColor("#16A34A")
        FileKind.VIDEO -> Color.parseColor("#DC2626")
        FileKind.AUDIO -> Color.parseColor("#9333EA")
        FileKind.DOCUMENT -> Color.parseColor("#7C3AED")
        FileKind.OTHER -> Color.parseColor("#0F766E")
    }

    private fun kindBg(file: CloudFile): String = when (file.kind) {
        FileKind.FOLDER -> "#DBEAFE"
        FileKind.IMAGE -> "#DCFCE7"
        FileKind.VIDEO -> "#FEE2E2"
        FileKind.AUDIO -> "#F3E8FF"
        FileKind.DOCUMENT -> "#EDE9FE"
        FileKind.OTHER -> "#CCFBF1"
    }

    private fun fileMeta(file: CloudFile): String {
        val kind = if (file.isFolder) "文件夹" else file.kind.label
        val date = file.updatedAt?.take(16)?.replace('T', ' ') ?: "未知时间"
        val size = if (file.isFolder) "" else " · ${formatBytes(file.size ?: 0L)}"
        return "$kind · $date$size"
    }

    private fun emptyCard(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#64748B"))
        setPadding(dp(18), dp(28), dp(18), dp(28))
        background = bg("#FFFFFF", 18)
    }

    private fun openFolder(file: CloudFile) {
        if (file.uri.isBlank()) {
            toast("该文件没有可用路径")
            return
        }
        currentPath = file.uri.substringBefore('?')
        etSearch.setText("")
        updatePathText()
        reloadFiles()
    }

    private fun showFileActions(file: CloudFile) {
        val items = mutableListOf<String>()
        if (isTrashRoot()) {
            items += "恢复"
            items += "彻底删除"
        } else {
            if (file.isFolder) items += "下载压缩包" else items += "下载"
            items += "复制下载链接"
            items += "详情"
            items += "重命名"
            items += "移动/复制"
            items += "分享"
            items += "删除到回收站"
        }
        AlertDialog.Builder(this)
            .setTitle(file.fileName)
            .setItems(items.toTypedArray()) { _, which -> handleFileAction(file, items[which]) }
            .show()
    }

    private fun handleFileAction(file: CloudFile, action: String) {
        when (action) {
            "下载", "下载压缩包" -> downloadFile(file)
            "复制下载链接" -> copyDownloadLink(file)
            "详情" -> showFileInfo(file)
            "重命名" -> renameDialog(file)
            "移动/复制" -> moveCopyDialog(file)
            "分享" -> shareFileDialog(file)
            "删除到回收站" -> deleteFilesDialog(listOf(file), skipSoftDelete = false)
            "恢复" -> restoreFilesDialog(listOf(file))
            "彻底删除" -> deleteFilesDialog(listOf(file), skipSoftDelete = true)
        }
    }

    private fun downloadFile(file: CloudFile) {
        if (file.uri.isBlank()) {
            toast("文件路径为空")
            return
        }
        tvStatus.text = "正在获取下载地址…"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val item = repository.fileUrl(this@CloudDriveActivity, file.uri, download = true, archive = file.isFolder)
                    ?: throw CloudApiException(-1, "未返回下载地址")
                val url = item.url ?: throw CloudApiException(-1, "下载地址为空")
                val name = item.displayName?.takeIf { it.isNotBlank() } ?: file.fileName
                withContext(Dispatchers.Main) {
                    DownloadUtil.downloadFile(this@CloudDriveActivity, url, name, file.mimeGuess())
                    tvStatus.text = "已开始下载 $name"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "下载地址获取失败"
                    toast(errorText(e, "获取下载地址失败"))
                }
            }
        }
    }

    private fun CloudFile.mimeGuess(): String = when (kind) {
        FileKind.IMAGE -> "image/*"
        FileKind.VIDEO -> "video/*"
        FileKind.AUDIO -> "audio/*"
        FileKind.DOCUMENT -> "application/octet-stream"
        FileKind.FOLDER -> "application/zip"
        FileKind.OTHER -> "application/octet-stream"
    }

    private fun copyDownloadLink(file: CloudFile) {
        if (file.uri.isBlank()) {
            toast("文件路径为空")
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val item = repository.fileUrl(this@CloudDriveActivity, file.uri, download = false)
                    ?: throw CloudApiException(-1, "未返回链接")
                val url = item.url ?: throw CloudApiException(-1, "链接为空")
                withContext(Dispatchers.Main) { copyText("下载链接", url) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast(errorText(e, "获取链接失败")) }
            }
        }
    }

    private fun showFileInfo(file: CloudFile) {
        tvStatus.text = "正在读取详情…"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val info = repository.fileInfo(this@CloudDriveActivity, file.uri, extended = true) ?: file
                withContext(Dispatchers.Main) {
                    tvStatus.text = "详情已加载"
                    AlertDialog.Builder(this@CloudDriveActivity)
                        .setTitle(info.fileName)
                        .setMessage(
                            "类型：${if (info.isFolder) "文件夹" else info.kind.label}\n" +
                                "大小：${if (info.isFolder) "--" else formatBytes(info.size ?: 0L)}\n" +
                                "创建：${info.createdAt.orEmpty()}\n" +
                                "修改：${info.updatedAt.orEmpty()}\n" +
                                "路径：${info.uri}\n" +
                                "ID：${info.id.orEmpty()}"
                        )
                        .setPositiveButton("关闭", null)
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "详情读取失败"
                    toast(errorText(e, "读取详情失败"))
                }
            }
        }
    }

    private fun renameDialog(file: CloudFile) {
        showTextInput("重命名", "新名称", file.fileName) { newName ->
            if (newName.isBlank() || newName == file.fileName) return@showTextInput
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    repository.rename(this@CloudDriveActivity, file.uri, newName)
                    withContext(Dispatchers.Main) {
                        toast("已重命名")
                        reloadFiles()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { toast(errorText(e, "重命名失败")) }
                }
            }
        }
    }

    private fun moveCopyDialog(file: CloudFile) {
        val items = arrayOf("移动到根目录", "复制到根目录", "自定义目标目录…")
        AlertDialog.Builder(this)
            .setTitle("移动/复制：${file.fileName}")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> moveCopy(file, currentRoot, copy = false)
                    1 -> moveCopy(file, currentRoot, copy = true)
                    else -> showTextInput("目标目录", "cloudreve://my/目标文件夹", currentRoot) { dst ->
                        if (dst.isNotBlank()) moveCopy(file, dst, copy = false)
                    }
                }
            }
            .show()
    }

    private fun moveCopy(file: CloudFile, dst: String, copy: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                repository.move(this@CloudDriveActivity, listOf(file.uri), dst, copy)
                withContext(Dispatchers.Main) {
                    toast(if (copy) "已复制" else "已移动")
                    reloadFiles()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast(errorText(e, "移动/复制失败")) }
            }
        }
    }

    private fun deleteFilesDialog(targets: List<CloudFile>, skipSoftDelete: Boolean) {
        if (targets.isEmpty()) return
        val title = if (skipSoftDelete) "彻底删除" else "删除到回收站"
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("确定${title} ${targets.size} 个项目吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        repository.delete(this@CloudDriveActivity, targets.map { it.uri }, skipSoftDelete)
                        withContext(Dispatchers.Main) {
                            toast("操作完成")
                            reloadFiles()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { toast(errorText(e, "删除失败")) }
                    }
                }
            }
            .show()
    }

    private fun restoreFilesDialog(targets: List<CloudFile>) {
        if (targets.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("恢复项目")
            .setMessage("确定恢复 ${targets.size} 个项目吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("恢复") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        repository.restore(this@CloudDriveActivity, targets.map { it.uri })
                        withContext(Dispatchers.Main) {
                            toast("已恢复")
                            reloadFiles()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { toast(errorText(e, "恢复失败")) }
                    }
                }
            }
            .show()
    }

    private fun createFolderDialog() {
        showTextInput("新建文件夹", "文件夹名称", "新建文件夹") { name ->
            if (name.isBlank()) return@showTextInput
            val uri = CloudUploadHelper.joinCloudUri(currentPath.substringBefore('?'), name)
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    repository.createFolder(this@CloudDriveActivity, uri)
                    withContext(Dispatchers.Main) {
                        toast("已创建文件夹")
                        reloadFiles()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { toast(errorText(e, "创建失败")) }
                }
            }
        }
    }

    private fun shareCurrentFolder() {
        if (currentPath == currentRoot && currentRoot != "cloudreve://my") {
            toast("请先进入要分享的文件夹")
            return
        }
        shareFileDialog(CloudFile(type = if (currentPath == currentRoot) 0 else 1, path = currentPath, name = currentPath.substringAfterLast('/')))
    }

    private fun shareFileDialog(file: CloudFile) {
        if (file.uri.isBlank()) {
            toast("文件路径为空")
            return
        }
        val items = arrayOf("公开链接", "私密链接（设置密码）", "取消")
        AlertDialog.Builder(this)
            .setTitle("分享：${file.fileName}")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> createShare(file, privateLink = false, password = null)
                    1 -> showTextInput("分享密码", "4-32 位字母或数字", "") { password ->
                        if (password.isNotBlank()) createShare(file, privateLink = true, password = password)
                    }
                }
            }
            .show()
    }

    private fun createShare(file: CloudFile, privateLink: Boolean, password: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = repository.createShare(
                    this@CloudDriveActivity,
                    file.uri,
                    privateLink = privateLink,
                    password = password,
                    expireSeconds = null,
                    shareView = true,
                    showReadme = file.isFolder
                ) ?: throw CloudApiException(-1, "未返回分享链接")
                withContext(Dispatchers.Main) { copyText("分享链接", url) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast(errorText(e, "创建分享失败")) }
            }
        }
    }

    private fun showMyShares() {
        tvStatus.text = "正在读取分享链接…"
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val shares = repository.listShares(this@CloudDriveActivity, pageSize = 50)
                withContext(Dispatchers.Main) {
                    tvStatus.text = "已加载 ${shares.size} 个分享链接"
                    showShareListDialog(shares)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "分享链接读取失败"
                    toast(errorText(e, "读取分享链接失败"))
                }
            }
        }
    }

    private fun showShareListDialog(shares: List<CloudShare>) {
        if (shares.isEmpty()) {
            AlertDialog.Builder(this).setTitle("我的分享").setMessage("暂无分享链接").setPositiveButton("关闭", null).show()
            return
        }
        val labels = shares.map { share ->
            val name = share.name ?: share.sourceUri?.substringAfterLast('/') ?: share.id.orEmpty()
            val state = if (share.expired == true) "已过期" else if (share.isPrivate == true) "私密" else "公开"
            "$name · $state · ${share.visited ?: 0} 次访问"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("我的分享")
            .setItems(labels) { _, which -> showShareActions(shares[which]) }
            .show()
    }

    private fun showShareActions(share: CloudShare) {
        val url = share.url
        val items = mutableListOf("复制链接", "删除链接")
        AlertDialog.Builder(this)
            .setTitle(share.name ?: "分享链接")
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> if (!url.isNullOrBlank()) copyText("分享链接", url) else toast("链接为空")
                    1 -> share.id?.let { id -> deleteShare(id) }
                }
            }
            .show()
    }

    private fun deleteShare(id: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                repository.deleteShare(this@CloudDriveActivity, id)
                withContext(Dispatchers.Main) {
                    toast("已删除分享链接")
                    showMyShares()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast(errorText(e, "删除分享失败")) }
            }
        }
    }

    private fun uploadUris(uris: List<Uri>) {
        if (uploadRunning) {
            toast("已有上传任务进行中")
            return
        }
        if (currentRoot != "cloudreve://my") {
            toast("当前目录不支持上传，请切换到我的文件")
            return
        }
        uploadRunning = true
        setUploadUiEnabled(false)
        lifecycleScope.launch(Dispatchers.IO) {
            var success = 0
            var failed = 0
            uris.forEachIndexed { index, uri ->
                try {
                    val source = CloudUploadHelper.querySource(this@CloudDriveActivity, uri)
                    withContext(Dispatchers.Main) {
                        tvStatus.text = "正在上传 ${index + 1}/${uris.size}：${source.name}"
                    }
                    CloudUploadHelper.upload(
                        context = this@CloudDriveActivity,
                        repository = repository,
                        source = source,
                        targetDirUri = currentPath.substringBefore('?')
                    ) { uploaded, total ->
                        runOnUiThread {
                            val percent = if (total > 0) ((uploaded * 100) / total).toInt() else 0
                            tvStatus.text = "正在上传 ${index + 1}/${uris.size}：${source.name} $percent%"
                        }
                    }
                    success++
                } catch (e: Exception) {
                    failed++
                    runOnUiThread { toast("上传失败：${sourceNameOr(uri)} ${e.message.orEmpty()}") }
                }
            }
            withContext(Dispatchers.Main) {
                uploadRunning = false
                setUploadUiEnabled(true)
                tvStatus.text = "上传完成：成功 $success，失败 $failed"
                reloadFiles()
            }
        }
    }

    private fun sourceNameOr(uri: Uri): String = runCatching {
        CloudUploadHelper.querySource(this, uri).name
    }.getOrDefault(uri.lastPathSegment ?: uri.toString())

    private fun setUploadUiEnabled(enabled: Boolean) {
        findViewById<TextView>(R.id.btnCloudUpload).isEnabled = enabled
        findViewById<TextView>(R.id.btnCloudUpload).alpha = if (enabled) 1f else 0.5f
    }

    private fun showSortDialog() {
        val items = arrayOf(
            "名称 A-Z",
            "名称 Z-A",
            "大小从小到大",
            "大小从大到小",
            "修改时间 新→旧",
            "修改时间 旧→新",
            "创建时间 新→旧",
            "创建时间 旧→新"
        )
        AlertDialog.Builder(this)
            .setTitle("排序方式")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> setSort("name", "asc")
                    1 -> setSort("name", "desc")
                    2 -> setSort("size", "asc")
                    3 -> setSort("size", "desc")
                    4 -> setSort("updated_at", "desc")
                    5 -> setSort("updated_at", "asc")
                    6 -> setSort("created_at", "desc")
                    else -> setSort("created_at", "asc")
                }
            }
            .show()
    }

    private fun setSort(by: String, direction: String) {
        orderBy = by
        orderDirection = direction
        reloadFiles()
    }

    private fun isTrashRoot(): Boolean = currentRoot == "cloudreve://trash" || currentPath.startsWith("cloudreve://trash")

    private fun showTextInput(title: String, hint: String, initial: String, onConfirm: (String) -> Unit) {
        val input = EditText(this).apply {
            this.hint = hint
            setText(initial)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ -> onConfirm(input.text.toString().trim()) }
            .show()
    }

    private fun tabPill(text: String, selected: Boolean, click: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(if (selected) Color.WHITE else Color.parseColor("#334155"))
        background = bg(if (selected) "#2563EB" else "#E7ECF3", 18)
        setPadding(dp(12), dp(7), dp(12), dp(7))
        setOnClickListener { click() }
    }

    private fun bg(color: String, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = dp(radius).toFloat()
    }

    private fun copyText(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        toast("已复制$text")
        if (label == "分享链接") tvStatus.text = "分享链接已复制"
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun errorText(e: Exception, fallback: String): String {
        val msg = e.message?.takeIf { it.isNotBlank() } ?: fallback
        return if (e is CloudApiException && e.apiCode != 0) "$fallback：$msg" else "$fallback：$msg"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        val tb = gb / 1024.0
        return when {
            tb >= 1 -> "${formatter.format(tb)} TB"
            gb >= 1 -> "${formatter.format(gb)} GB"
            mb >= 1 -> "${formatter.format(mb)} MB"
            kb >= 1 -> "${formatter.format(kb)} KB"
            else -> "$bytes B"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

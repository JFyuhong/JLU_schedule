package cn.jlu.schedule.ui.importer

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import cn.jlu.schedule.R
import cn.jlu.schedule.data.ImportedScheduleStorage
import cn.jlu.schedule.model.CourseSchedule
import cn.jlu.schedule.parser.DoScheduleParser
import cn.jlu.schedule.ui.theme.ThemePalette
import cn.jlu.schedule.ui.theme.ThemePaletteProvider
import cn.jlu.schedule.ui.theme.UiFeedback
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

class ImportBrowserActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var pageProgress: ProgressBar

    private val ioExecutor = Executors.newFixedThreadPool(2)

    private lateinit var cacheSessionDir: File
    private val cacheEntries = Collections.synchronizedList(mutableListOf<CachedFileEntry>())
    private val cachedRequestKeys = Collections.synchronizedSet(mutableSetOf<String>())
    private val saveCounter = AtomicInteger(0)
    private var cachedUserAgent: String = "Mozilla/5.0"
    private val trustedSslHosts = Collections.synchronizedSet(mutableSetOf<String>())
    private lateinit var palette: ThemePalette

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_browser)

        title = "课表导入"
        webView = findViewById(R.id.importWebView)
        urlInput = findViewById(R.id.urlInput)
        pageProgress = findViewById(R.id.pageProgress)
        val goButton = findViewById<Button>(R.id.goButton)
        val importButton = findViewById<Button>(R.id.importFromHereButton)

        initCacheSessionDir()
        setupWebView()
        palette = ThemePaletteProvider.fromContext(this)
        applyTheme(goButton, importButton)

        val assetFileName = intent.getStringExtra(EXTRA_URL_ASSET).orEmpty()
        val startUrl = loadUrlFromAsset(assetFileName)
        if (startUrl.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("地址读取失败")
                .setMessage("未能读取导入地址，请检查 ${assetFileName} 文件内容。")
                .setPositiveButton("确定") { _, _ -> finish() }
                .setCancelable(false)
                .show()
            return
        }

        urlInput.setText(startUrl)
        webView.loadUrl(startUrl)

        goButton.setOnClickListener { loadFromInput() }
        importButton.setOnClickListener { chooseImportModeAndImport() }
        UiFeedback.showMessage(findViewById(android.R.id.content), "先进入到“我的课程”页面，再点从此处导入", palette)
    }

    private fun applyTheme(goButton: Button, importButton: Button) {
        val palette = ThemePaletteProvider.fromContext(this)
        goButton.setBackgroundColor(palette.buttonBackground)
        goButton.setTextColor(palette.buttonText)
        importButton.setBackgroundColor(palette.buttonBackground)
        importButton.setTextColor(palette.buttonText)
        importButton.compoundDrawableTintList = ColorStateList.valueOf(palette.iconTint)
    }

    private fun initCacheSessionDir() {
        val root = File(filesDir, CACHE_ROOT_DIR)
        if (!root.exists()) {
            root.mkdirs()
        }
        cacheSessionDir = File(root, "session_${System.currentTimeMillis()}")
        cacheSessionDir.mkdirs()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            javaScriptCanOpenWindowsAutomatically = true
        }
        cachedUserAgent = webView.settings.userAgentString.orEmpty().ifBlank { "Mozilla/5.0" }
        webView.addJavascriptInterface(CacheJsBridge(), "CacheBridge")

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                pageProgress.progress = newProgress
                pageProgress.visibility = if (newProgress in 1..99) ProgressBar.VISIBLE else ProgressBar.GONE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url?.toString().orEmpty()
                if (target.isNotBlank()) {
                    view?.loadUrl(target)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!url.isNullOrBlank()) {
                    urlInput.setText(url)
                }
                injectNetworkCaptureScript()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    val reason = error?.description?.toString().orEmpty()
                    UiFeedback.showMessage(findViewById(android.R.id.content), "页面加载失败: $reason", palette)
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                val host = runCatching { Uri.parse(error?.url.orEmpty()).host.orEmpty().lowercase(Locale.ROOT) }
                    .getOrDefault("")
                if (host.isBlank()) {
                    handler?.cancel()
                    return
                }

                if (trustedSslHosts.contains(host) || AUTO_TRUST_SSL_HOSTS.contains(host)) {
                    trustedSslHosts.add(host)
                    handler?.proceed()
                    return
                }

                AlertDialog.Builder(this@ImportBrowserActivity)
                    .setTitle("证书校验提醒")
                    .setMessage("当前站点证书校验失败：$host\n是否继续访问？")
                    .setPositiveButton("继续") { _, _ ->
                        trustedSslHosts.add(host)
                        handler?.proceed()
                    }
                    .setNegativeButton("取消") { _, _ ->
                        handler?.cancel()
                    }
                    .setCancelable(false)
                    .create()
                    .also { dialog ->
                        dialog.show()
                        styleDialogButtons(dialog)
                    }
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                request?.let {
                    queueMirrorSave(it)
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
    }

    private fun loadFromInput() {
        val raw = urlInput.text?.toString()?.trim().orEmpty()
        if (raw.isBlank()) {
            UiFeedback.showMessage(findViewById(android.R.id.content), "请输入网址", palette)
            return
        }
        val normalized = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        webView.loadUrl(normalized)
    }

    private fun queueMirrorSave(request: WebResourceRequest) {
        val url = request.url.toString()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return
        }

        if (saveCounter.get() >= MAX_CACHED_FILES) {
            return
        }

        val method = request.method.orEmpty().uppercase(Locale.ROOT)
        val key = "$method|$url"
        if (!cachedRequestKeys.add(key)) {
            return
        }

        val headers = request.requestHeaders.orEmpty()
        val ua = cachedUserAgent
        ioExecutor.execute {
            runCatching {
                val saved = downloadAndSave(url, method, headers, ua)
                if (saved != null) {
                    cacheEntries.add(saved)
                }
            }
        }
    }

    private fun downloadAndSave(
        url: String,
        method: String,
        headers: Map<String, String>,
        userAgent: String
    ): CachedFileEntry? {
        val index = saveCounter.incrementAndGet()
        if (index > MAX_CACHED_FILES) {
            return null
        }

        val suffix = extensionFromUrl(url)
        val digest = sha1(url).take(8)
        val name = "%04d_%s%s".format(index, digest, suffix)
        val file = File(cacheSessionDir, name)

        val connection = URL(url).openConnection() as HttpURLConnection
        if (connection is HttpsURLConnection) {
            // Keep standard TLS validation in release behavior.
        }

        connection.requestMethod = if (method == "GET") "GET" else "GET"
        connection.connectTimeout = 12000
        connection.readTimeout = 12000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", userAgent)
        headers.forEach { (k, v) ->
            if (!k.equals("cookie", ignoreCase = true) && !k.equals("user-agent", ignoreCase = true)) {
                connection.setRequestProperty(k, v)
            }
        }

        val cookie = CookieManager.getInstance().getCookie(url)
        if (!cookie.isNullOrBlank()) {
            connection.setRequestProperty("Cookie", cookie)
        }

        connection.connect()
        val input = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: throw IllegalStateException("HTTP ${connection.responseCode}")
        }

        var total = 0
        BufferedInputStream(input).use { ins ->
            FileOutputStream(file).use { out ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = ins.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_CACHE_FILE_BYTES) {
                        return null
                    }
                    out.write(buffer, 0, read)
                }
            }
        }

        if (total <= 0) {
            runCatching { file.delete() }
            return null
        }

        return CachedFileEntry(url = url, fileName = name, filePath = file.absolutePath, size = total)
    }

    private fun extensionFromUrl(url: String): String {
        val path = url.substringBefore('#').substringBefore('?')
        val seg = path.substringAfterLast('/', "")
        if (!seg.contains(".")) {
            return ".bin"
        }
        val ext = seg.substringAfterLast('.', "bin").lowercase(Locale.ROOT)
        return if (ext.length in 1..8) ".${ext}" else ".bin"
    }

    private fun chooseImportModeAndImport() {
        val modeItems = arrayOf("覆盖当前课表", "新建课表")
        AlertDialog.Builder(this)
            .setTitle("导入方式")
            .setItems(modeItems) { _, which ->
                if (which == 0) {
                    importFromCachedFiles(ImportedScheduleStorage.ImportMode.OVERWRITE_ACTIVE, null)
                } else {
                    val input = EditText(this).apply {
                        hint = "新课表名称（可留空）"
                        setText("课表${System.currentTimeMillis() % 100000}")
                    }
                    AlertDialog.Builder(this)
                        .setTitle("新建课表")
                        .setView(input)
                        .setPositiveButton("开始导入") { _, _ ->
                            importFromCachedFiles(
                                ImportedScheduleStorage.ImportMode.CREATE_NEW,
                                input.text?.toString()?.trim().orEmpty()
                            )
                        }
                        .setNegativeButton("取消", null)
                        .create()
                        .also { dialog ->
                            dialog.show()
                            styleDialogButtons(dialog)
                        }
                }
            }
            .setNegativeButton("取消", null)
            .create()
            .also { dialog ->
                dialog.show()
                styleDialogButtons(dialog)
            }
    }

    private fun importFromCachedFiles(mode: ImportedScheduleStorage.ImportMode, newProfileName: String?) {
        val snapshot = synchronized(cacheEntries) { cacheEntries.toList() }
        UiFeedback.showMessage(findViewById(android.R.id.content), "正在从本地缓存检索课表文件...", palette)

        thread {
            val parseResult = runCatching { parseSchedulesFromCache(snapshot) }
            runOnUiThread {
                parseResult.onSuccess { parsed ->
                    if (parsed.isEmpty()) {
                        AlertDialog.Builder(this)
                            .setTitle("导入失败")
                            .setMessage("未找到可解析的课表数据，请在网页中打开课表详情后重试。")
                            .setPositiveButton("知道了", null)
                            .create()
                            .also { dialog ->
                                dialog.show()
                                styleDialogButtons(dialog)
                            }
                        return@onSuccess
                    }

                    val saveResult = runCatching {
                        ImportedScheduleStorage.importParsedCourses(
                            context = this,
                            courses = parsed,
                            mode = mode,
                            newProfileName = newProfileName?.ifBlank { null }
                        )
                    }

                    saveResult.onSuccess {
                        AlertDialog.Builder(this)
                            .setTitle("导入成功")
                            .setMessage("已从缓存导入 ${it.courseCount} 条课程，当前课表：${it.profileName}")
                            .setPositiveButton("返回课表") { _, _ ->
                                setResult(RESULT_OK)
                                finish()
                            }
                            .setNegativeButton("停留此页", null)
                            .create()
                            .also { dialog ->
                                dialog.show()
                                styleDialogButtons(dialog)
                            }
                    }.onFailure {
                        AlertDialog.Builder(this)
                            .setTitle("导入失败")
                            .setMessage("写入课表失败：${it.message ?: "未知错误"}")
                            .setPositiveButton("关闭", null)
                            .create()
                            .also { dialog ->
                                dialog.show()
                                styleDialogButtons(dialog)
                            }
                    }
                }.onFailure {
                    AlertDialog.Builder(this)
                        .setTitle("导入失败")
                        .setMessage("解析缓存失败：${it.message ?: "未知错误"}")
                        .setPositiveButton("关闭", null)
                        .create()
                        .also { dialog ->
                            dialog.show()
                            styleDialogButtons(dialog)
                        }
                }
            }
        }
    }

    private fun styleDialogButtons(dialog: AlertDialog) {
        UiFeedback.styleDialogSurface(dialog, palette)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { UiFeedback.stylePrimaryButton(it, palette) }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { UiFeedback.styleSecondaryButton(it, palette) }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.let { UiFeedback.styleSecondaryButton(it, palette) }
    }

    private fun parseSchedulesFromCache(entries: List<CachedFileEntry>): List<CourseSchedule> {
        val candidates = prioritizeCandidates(entries)
        val batches = mutableListOf<List<CourseSchedule>>()

        candidates.forEach { entry ->
            val file = File(entry.filePath)
            if (!file.exists() || file.length() <= 0L || file.length() > MAX_PARSE_FILE_BYTES) {
                return@forEach
            }

            val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return@forEach
            if (!looksLikeSchedulePayload(entry.url, text, file.length())) {
                return@forEach
            }

            val parsed = runCatching { DoScheduleParser.parse(text) }
                .getOrDefault(emptyList())
                .filter { it.courseName.isNotBlank() && it.meetings.isNotEmpty() }
            if (parsed.isNotEmpty()) {
                batches.add(parsed)
            }
        }

        val merged = batches.flatten()
        return merged
    }

    private fun prioritizeCandidates(entries: List<CachedFileEntry>): List<CachedFileEntry> {
        return entries.sortedWith(
            compareByDescending<CachedFileEntry> { it.url.contains(TARGET_SCHEDULE_FILE, ignoreCase = true) }
                                .thenByDescending { it.url.contains("modules/xskcb", ignoreCase = true) }
                .thenByDescending { it.fileName.contains(".do", ignoreCase = true) || it.fileName.contains(".json", ignoreCase = true) }
                .thenByDescending { it.size }
        )
    }

        private fun looksLikeSchedulePayload(url: String, text: String, size: Long): Boolean {
                if (size < MIN_SCHEDULE_PAYLOAD_BYTES) {
                        return false
                }
                if (!text.contains("\"datas\"") || !text.contains("\"rows\"")) {
                        return false
                }
                val hasCourseField = text.contains("\"KCM\"")
                val hasTimeField = text.contains("\"YPSJDD\"") || text.contains("\"SKXQ\"")
                if (!(hasCourseField && hasTimeField)) {
                        return false
                }
                if (url.contains(TARGET_SCHEDULE_FILE, ignoreCase = true)) {
                        return true
                }
                return url.contains("modules/xskcb", ignoreCase = true)
        }

        private fun injectNetworkCaptureScript() {
                val script = """
                        (function () {
                            if (window.__jluCacheHooked) return;
                            window.__jluCacheHooked = true;
                            function send(url, text) {
                                try {
                                    if (!url || !text) return;
                                    if (window.CacheBridge && window.CacheBridge.onNetworkResponse) {
                                        window.CacheBridge.onNetworkResponse(String(url), String(text));
                                    }
                                } catch (e) {}
                            }

                            var _open = XMLHttpRequest.prototype.open;
                            var _send = XMLHttpRequest.prototype.send;
                            XMLHttpRequest.prototype.open = function(method, url) {
                                this.__jluUrl = url || '';
                                return _open.apply(this, arguments);
                            };
                            XMLHttpRequest.prototype.send = function() {
                                this.addEventListener('load', function() {
                                    send(this.__jluUrl || '', this.responseText || '');
                                });
                                return _send.apply(this, arguments);
                            };

                            if (window.fetch) {
                                var _fetch = window.fetch;
                                window.fetch = function(input, init) {
                                    var url = (typeof input === 'string') ? input : ((input && input.url) || '');
                                    return _fetch(input, init).then(function(resp) {
                                        try {
                                            var clone = resp.clone();
                                            clone.text().then(function(text) { send(url, text); }).catch(function(){});
                                        } catch (e) {}
                                        return resp;
                                    });
                                };
                            }
                        })();
                """.trimIndent()
                webView.evaluateJavascript(script, null)
        }

        private inner class CacheJsBridge {
                @JavascriptInterface
                fun onNetworkResponse(url: String?, content: String?) {
                        val safeUrl = url.orEmpty().trim()
                        val text = content.orEmpty()
                        if (safeUrl.isBlank() || text.length < MIN_SCHEDULE_PAYLOAD_BYTES) {
                                return
                        }

                        if (!safeUrl.contains("modules/xskcb", ignoreCase = true)
                                && !safeUrl.contains(TARGET_SCHEDULE_FILE, ignoreCase = true)
                        ) {
                                return
                        }

                        if (!text.contains("\"datas\"") || !text.contains("\"rows\"")) {
                                return
                        }

                        val key = "JS|$safeUrl|${sha1(text).take(12)}"
                        if (!cachedRequestKeys.add(key)) {
                                return
                        }

                        ioExecutor.execute {
                                runCatching {
                                        val saved = saveTextPayload(safeUrl, text)
                                        cacheEntries.add(saved)
                                }
                        }
                }
        }

        private fun saveTextPayload(url: String, text: String): CachedFileEntry {
                val index = saveCounter.incrementAndGet()
                val digest = sha1(url + "|" + text.take(128)).take(8)
                val name = "%04d_js_%s.json".format(index, digest)
                val file = File(cacheSessionDir, name)
                file.writeText(text, Charsets.UTF_8)
                return CachedFileEntry(url = url, fileName = name, filePath = file.absolutePath, size = text.toByteArray(Charsets.UTF_8).size)
        }

    private fun loadUrlFromAsset(assetFileName: String): String {
        if (assetFileName.isBlank()) return ""
        return runCatching {
            assets.open(assetFileName).bufferedReader().useLines { lines ->
                lines.map { it.trim() }
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
            }
        }.getOrDefault("")
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    override fun onDestroy() {
        runCatching { ioExecutor.shutdownNow() }
        runCatching {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    data class CachedFileEntry(
        val url: String,
        val fileName: String,
        val filePath: String,
        val size: Int
    )

    companion object {
        private const val TARGET_SCHEDULE_FILE = "cxxszhxqkb.do"
        private const val CACHE_ROOT_DIR = "import_web_cache"
        private const val MAX_PARSE_FILE_BYTES = 2 * 1024 * 1024L
        private const val MAX_CACHE_FILE_BYTES = 3 * 1024 * 1024
        private const val MAX_CACHED_FILES = 500
        private const val MIN_SCHEDULE_PAYLOAD_BYTES = 400
        private val AUTO_TRUST_SSL_HOSTS = setOf(
            "iedu.jlu.edu.cn",
            "vpn.jlu.edu.cn"
        )
        const val EXTRA_URL_ASSET = "extra_url_asset"
    }
}

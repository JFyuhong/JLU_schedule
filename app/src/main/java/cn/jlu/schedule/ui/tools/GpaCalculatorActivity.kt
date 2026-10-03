package cn.jlu.schedule.ui.tools

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import cn.jlu.schedule.R
import cn.jlu.schedule.auth.CampusCookieJar
import cn.jlu.schedule.auth.CasLoginResult
import cn.jlu.schedule.auth.TpassConfig
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.data.GpaCourseStore
import cn.jlu.schedule.domain.GpaCalculator
import cn.jlu.schedule.domain.GpaCourse
import cn.jlu.schedule.domain.GpaGradeType
import cn.jlu.schedule.domain.ImportedGrade
import cn.jlu.schedule.parser.GradeTranscriptParser
import cn.jlu.schedule.remote.JwApiClient
import cn.jlu.schedule.ui.auth.LoginActivity
import cn.jlu.schedule.ui.theme.ThemePalette
import cn.jlu.schedule.ui.theme.ThemePaletteProvider
import cn.jlu.schedule.ui.theme.UiFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 绩点计算器：原生改造自 DailyPotato/JLU-GPA-Calculator 与
 * Coldymemos/JLU-GPA-Calculator-for-Windows-Desktop（已获原作者同意）。
 * 手动录入成绩与学分，逐门可排除，实时计算保研绩点 / 加权平均分 / 算术平均分。
 */
class GpaCalculatorActivity : AccountScopedActivity() {

    private lateinit var palette: ThemePalette
    private val courses = mutableListOf<GpaCourse>()

    private lateinit var valueGpa: TextView
    private lateinit var valueWeighted: TextView
    private lateinit var valueArithmetic: TextView
    private lateinit var resultMeta: TextView
    private lateinit var emptyHint: TextView
    private lateinit var courseList: LinearLayout
    private lateinit var nameInput: EditText
    private lateinit var scoreInput: EditText
    private lateinit var creditInput: EditText
    private lateinit var typePercent: TextView
    private lateinit var typeLevel: TextView
    private lateinit var levelGroup: LinearLayout

    private var gradeType = GpaGradeType.PERCENT
    private var selectedLevel = GpaCalculator.LEVEL_ORDER.first()
    private var levelButtons: List<Pair<String, TextView>> = emptyList()

    private var importing = false
    private lateinit var importButton: Button

    // ===== 教务成绩抓取：隐藏 WebView 打开成绩查询页，捕获页面自身的成绩接口响应 =====
    private lateinit var fetchWebView: WebView
    private val capturedPayloads = ArrayList<Pair<String, String>>()
    private var fetchQuietJob: Job? = null
    private var fetchWatchdogJob: Job? = null
    private var autoQueryJob: Job? = null
    private var fetchSilentLoginTried = false
    private var fetchAutoQueryTries = 0
    private val fetchFinished = AtomicBoolean(false)
    private val fetchServedLoginHtml = AtomicBoolean(false)

    private val loginLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 登录成功后自动重试教务导入
        if (accountData.isCurrent && result.resultCode == RESULT_OK) importFromJw()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePaletteProvider.applyNightMode(this)
        setTheme(ThemePaletteProvider.themeStyleFor(AppPreferences.getThemeColor(this)))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gpa_calculator)

        palette = ThemePaletteProvider.fromContext(this)
        courses.addAll(withAccountData { GpaCourseStore.load(it) }.orEmpty())

        valueGpa = findViewById(R.id.gpaValueGpa)
        valueWeighted = findViewById(R.id.gpaValueWeighted)
        valueArithmetic = findViewById(R.id.gpaValueArithmetic)
        resultMeta = findViewById(R.id.gpaResultMeta)
        emptyHint = findViewById(R.id.gpaEmptyHint)
        courseList = findViewById(R.id.gpaCourseList)
        nameInput = findViewById(R.id.gpaNameInput)
        scoreInput = findViewById(R.id.gpaScoreInput)
        creditInput = findViewById(R.id.gpaCreditInput)
        typePercent = findViewById(R.id.gpaTypePercent)
        typeLevel = findViewById(R.id.gpaTypeLevel)
        levelGroup = findViewById(R.id.gpaLevelGroup)

        applySystemBarInsets()
        applyTheme()
        bindAddForm()
        setupFetchWebView()
        render()
    }

    private fun applyTheme() {
        findViewById<View>(R.id.gpaRoot).setBackgroundColor(palette.pageBackground)
        val title = findViewById<TextView>(R.id.gpaTitle)
        title.setTextColor(palette.textPrimary)
        findViewById<TextView>(R.id.gpaSubtitle).setTextColor(palette.textSecondary)
        val resultCard = findViewById<View>(R.id.gpaResultCard)
        resultCard.background = cardDrawable()

        valueGpa.setTextColor(palette.iconTint)
        valueWeighted.setTextColor(palette.textPrimary)
        valueArithmetic.setTextColor(palette.textPrimary)
        resultMeta.setTextColor(palette.textSecondary)
        emptyHint.setTextColor(palette.textSecondary)

        styleInput(nameInput)
        styleInput(scoreInput)
        styleInput(creditInput)

        val addButton = findViewById<Button>(R.id.gpaAddButton)
        val clearButton = findViewById<Button>(R.id.gpaClearButton)
        UiFeedback.stylePrimaryButton(addButton, palette)
        UiFeedback.styleDangerButton(clearButton, palette)
        importButton = findViewById(R.id.gpaImportButton)
        UiFeedback.styleSecondaryButton(importButton, palette)
    }

    private fun bindAddForm() {
        levelButtons = GpaCalculator.LEVEL_ORDER.map { level ->
            val id = when (level) {
                "优秀" -> R.id.gpaLevelExcellent
                "良好" -> R.id.gpaLevelGood
                "中等" -> R.id.gpaLevelMedium
                "及格" -> R.id.gpaLevelPass
                else -> R.id.gpaLevelFail
            }
            level to findViewById<TextView>(id)
        }

        bindSegment(listOf(typePercent, typeLevel), typePercent) { selected ->
            gradeType = if (selected == typePercent) GpaGradeType.PERCENT else GpaGradeType.LEVEL5
            scoreInput.visibility = if (gradeType == GpaGradeType.PERCENT) View.VISIBLE else View.GONE
            levelGroup.visibility = if (gradeType == GpaGradeType.PERCENT) View.GONE else View.VISIBLE
        }
        bindSegment(levelButtons.map { it.second }, levelButtons.first().second) { selected ->
            selectedLevel = levelButtons.first { it.second.id == selected.id }.first
        }

        findViewById<Button>(R.id.gpaAddButton).setOnClickListener { addCourse() }
        findViewById<Button>(R.id.gpaClearButton).setOnClickListener { confirmClearAll() }
        importButton.setOnClickListener { importFromJw() }
    }

    /**
     * 从教务系统拉取成绩：原生直连模块接口被网关 403 拦截（仅放行页面自身请求），
     * 因此用隐藏 WebView 打开成绩查询页，钩子捕获页面自己的成绩响应。
     */
    private fun importFromJw() {
        if (!accountData.isCurrent) return
        if (importing) return
        importing = true
        importButton.isEnabled = false
        resultMeta.text = getString(R.string.gpa_import_progress)

        lifecycleScope.launch {
            // 通道一：原生 OkHttp 极速直连（无需等待 WebView 下载渲染数兆脚本，直接拉取历年全量）
            val nativeGrades = runCatching { JwApiClient.fetchAllGrades(this@GpaCalculatorActivity) }.getOrDefault(emptyList())
            if (nativeGrades.isNotEmpty()) {
                val semesterCount = nativeGrades.map { it.semesterCode }.filter { it.isNotBlank() }.distinct().size
                if (nativeGrades.size >= 25 || semesterCount > 2) {
                    importing = false
                    importButton.isEnabled = true
                    applyImported(GpaCalculator.mergeImported(nativeGrades))
                    return@launch
                }
            }

            // 通道二：若原生直连未拉到多学期，启动 WebView 自动化多学期协同抓取
            startWebViewGradeFetch()
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun setupFetchWebView() {
        fetchWebView = findViewById(R.id.gpaFetchWebView)
        fetchWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(fetchWebView, true)
        }
        fetchWebView.addJavascriptInterface(GradeCaptureBridge(), "GpaGradeBridge")
        fetchWebView.webViewClient = GradeFetchWebViewClient()
    }

    private fun startWebViewGradeFetch() {
        fetchFinished.set(false)
        fetchServedLoginHtml.set(false)
        fetchSilentLoginTried = false
        fetchAutoQueryTries = 0
        synchronized(capturedPayloads) { capturedPayloads.clear() }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(fetchWebView, true)
        }
        fetchWebView.loadUrl(GRADE_PAGE_URL)
        fetchWatchdogJob?.cancel()
        fetchWatchdogJob = lifecycleScope.launch {
            delay(FETCH_TIMEOUT_MS)
            finishFetch(reason = "timeout")
        }
        scheduleAutoQueryRounds()
    }

    /** 成绩页不保证自动查询：多轮尝试点按钮，并回报 DOM 结构便于诊断 */
    private fun scheduleAutoQueryRounds() {
        autoQueryJob?.cancel()
        autoQueryJob = lifecycleScope.launch {
            val rounds = intArrayOf(2, 6, 11, 17, 23)
            for (i in rounds.indices) {
                if (i > 0) delay((rounds[i] - rounds[i - 1]) * 1000L) else delay(rounds[i] * 1000L)
                if (!accountData.isCurrent || fetchFinished.get()) return@launch
                val round = i + 1
                val bridge = GradeCaptureBridge()
                bridge.autoQuery(round)
            }
        }
    }

    private inner class GradeFetchWebViewClient : WebViewClient() {

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            if (!accountData.isCurrent || fetchFinished.get() || !request.isForMainFrame) return null
            val url = request.url
            if (url.host != TpassConfig.IEDU_HOST || request.method != "GET") return null
            if (url.encodedPath?.endsWith(".do") != true) return null
            return runCatching { buildHookedGradeDocument(url) }
                .onFailure { Log.w(TAG, "grade intercept failed: ${it.message}") }
                .getOrNull()
        }

        override fun onReceivedSslError(
            view: WebView,
            handler: android.webkit.SslErrorHandler,
            error: android.net.http.SslError
        ) {
            handler.cancel()
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (!accountData.isCurrent || fetchFinished.get()) return
            if (fetchServedLoginHtml.compareAndSet(true, false)) {
                handleFetchLogin()
                return
            }
            val host = runCatching { android.net.Uri.parse(url).host ?: "" }.getOrDefault("")
            if (host == TpassConfig.CAS_HOST) {
                handleFetchLogin()
            }
        }
    }

    /** 主文档拦截下载 + 注入捕获钩子；命中 CAS 登录页时标记待重登 */
    private fun buildHookedGradeDocument(target: android.net.Uri): WebResourceResponse {
        val connection = java.net.URL(target.toString()).openConnection() as javax.net.ssl.HttpsURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.instanceFollowRedirects = true
        CookieManager.getInstance().getCookie(target.toString())?.let {
            connection.setRequestProperty("Cookie", it)
        }
        connection.setRequestProperty("User-Agent", TpassConfig.USER_AGENT)
        connection.setRequestProperty("Referer", TpassConfig.IEDU_PORTAL_URL)
        connection.connect()
        val mimeType = connection.contentType?.substringBefore(';')?.trim() ?: "text/html"
        val body = connection.inputStream.use { it.readBytes() }
        Log.i(TAG, "grade page doc $target -> ${connection.responseCode} $mimeType len=${body.size}")

        withAccountData {
            val setCookies = connection.headerFields["Set-Cookie"] ?: emptyList()
            if (setCookies.isNotEmpty()) {
                val cookieManager = CookieManager.getInstance()
                val host = target.host.orEmpty()
                for (cookieHeader in setCookies) {
                    cookieManager.setCookie(target.toString(), cookieHeader)
                    if (host.isNotBlank()) {
                        cookieManager.setCookie("https://$host/", cookieHeader)
                        cookieManager.setCookie("https://$host/jwapp/", cookieHeader)
                        cookieManager.setCookie("https://$host/jwapp/sys/cjcx/", cookieHeader)
                    }
                }
                cookieManager.flush()
                JwApiClient.importAllWebViewCookies(this@GpaCalculatorActivity)
            }
        }

        if (!mimeType.contains("html", ignoreCase = true)) {
            return WebResourceResponse(mimeType, "UTF-8", body.inputStream())
        }
        var html = String(body, Charsets.UTF_8)
        if (html.contains("id=\"loginForm\"") || html.contains("id=\"lt\"")) {
            fetchServedLoginHtml.set(true)
            return WebResourceResponse(mimeType, "UTF-8", html.byteInputStream())
        }
        if (!html.contains("__jluGradeHooked")) {
            val script = "<script>$GRADE_HOOK_JS</script>"
            val headTag = Regex("(?i)<head[^>]*>").find(html)
            html = if (headTag != null) {
                StringBuilder(html).insert(headTag.range.last + 1, script).toString()
            } else {
                script + html
            }
        }
        return WebResourceResponse(mimeType, "UTF-8", html.byteInputStream())
    }

    private fun handleFetchLogin() {
        if (fetchSilentLoginTried) {
            finishFetch(reason = "need-login")
            return
        }
        fetchSilentLoginTried = true
        resultMeta.text = getString(R.string.import_quick_progress_relogin)
        lifecycleScope.launch {
            val result = JwApiClient.silentLogin(this@GpaCalculatorActivity)
            if (result is CasLoginResult.Success) {
                JwApiClient.syncJarToWebView(this@GpaCalculatorActivity)
                if (!fetchFinished.get()) fetchWebView.post { fetchWebView.loadUrl(GRADE_PAGE_URL) }
            } else {
                finishFetch(reason = "need-login")
            }
        }
    }

    private fun onGradePayloadCaptured(url: String, text: String) {
        if (fetchFinished.get() || text.length < MIN_CAPTURE_BYTES) return
        synchronized(capturedPayloads) {
            val key = sha1(url + "|" + text.take(256))
            if (capturedPayloads.any { sha1(it.first + "|" + it.second.take(256)) == key }) return
            capturedPayloads.add(url to text)
            Log.i(TAG, "captured grade-ish payload #${capturedPayloads.size} ($url, ${text.length} bytes)")
        }

        val totalCourses = synchronized(capturedPayloads) {
            capturedPayloads.flatMap { GradeTranscriptParser.parse(it.second) }
                .distinctBy { "${it.semesterCode}_${it.courseCode}_${it.name}" }
        }
        val isFullTranscript = totalCourses.size >= 25 || totalCourses.map { it.semesterCode }.distinct().size > 2

        val quietTime = if (isFullTranscript) 1500L else 7000L
        fetchQuietJob?.cancel()
        fetchQuietJob = lifecycleScope.launch {
            delay(quietTime)
            finishFetch(reason = "quiet")
        }
    }

    private fun finishFetch(reason: String) {
        if (!accountData.isCurrent || !fetchFinished.compareAndSet(false, true)) return
        fetchWatchdogJob?.cancel()
        fetchQuietJob?.cancel()
        val grades = synchronized(capturedPayloads) {
            capturedPayloads.asSequence()
                .filter { GradeTranscriptParser.isLikelyGradePayload(it.second) }
                .flatMap { GradeTranscriptParser.parse(it.second).asSequence() }
                .distinctBy { "${it.semesterCode}_${it.courseCode}_${it.name}" }
                .sortedWith(compareByDescending<ImportedGrade> { it.semesterCode }.thenBy { it.courseCode })
                .toList()
        }
        Log.i(TAG, "grade fetch finish ($reason): ${grades.size} grades from ${capturedPayloads.size} payloads")
        runOnUiThread {
            if (!::importButton.isInitialized || isDestroyed || isFinishing) return@runOnUiThread
            importing = false
            importButton.isEnabled = true
            if (grades.isNotEmpty()) {
                applyImported(GpaCalculator.mergeImported(grades))
            } else {
                render()
                when (reason) {
                    "need-login" -> promptLogin()
                    else -> UiFeedback.showMessage(courseList, getString(R.string.gpa_import_empty), palette)
                }
            }
        }
    }

    private inner class GradeCaptureBridge {
        @JavascriptInterface
        fun onCaptured(url: String?, content: String?) {
            val safeUrl = url.orEmpty()
            val body = content.orEmpty()
            runOnUiThread { onGradePayloadCaptured(safeUrl, body) }
        }

        /** 页面上下文内的自动查询：round 1 回报可点元素，切换【全部】并触发全量 AJAX 查询 */
        @JavascriptInterface
        fun autoQuery(round: Int) {
            if (!accountData.isCurrent || fetchFinished.get()) return
            val js = """
            (function(){
              // 1. 精准模拟原生鼠标事件点击【全部】tab（触发 jqxTabs 切换与 qb.js 模块初始化）
              try {
                var tabs = document.querySelectorAll('#tab li, .cjcx-tab li, ul[role="tablist"] li');
                if (tabs && tabs.length > 1) {
                  tabs[1].dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
                  tabs[1].dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true }));
                  tabs[1].click();
                }
              } catch(e) {}

              // 2. 若 #qb-index-table 已存在，重载全量数据
              try {
                if (window.jQuery && jQuery('#qb-index-table').length && jQuery('#qb-index-table').data('emapdatatable')) {
                  jQuery('#qb-index-table').emapdatatable('reload', { pageSize: 1000, pageNumber: 1 });
                }
              } catch(e) {}

              // 3. 在页面已授权会话上下文中，直接通过 jQuery 发送全量学期查询（pageSize=1000，消除分页截断）
              try {
                if (window.jQuery && window.WIS_EMAP_SERV) {
                  var url = WIS_EMAP_SERV.getAbsPath('modules/cjcx/xscjcx.do');
                  var defaultQuery = [
                    {"name": "SFYX", "caption": "是否有效", "linkOpt": "AND", "builderList": "cbl_m_List", "builder": "m_value_equal", "value": "1", "value_display": "是"},
                    {"name": "SHOWMAXCJ", "caption": "显示最高成绩", "linkOpt": "AND", "builderList": "cbl_m_List", "builder": "m_value_equal", "value": "0", "value_display": "否"}
                  ];
                  jQuery.ajax({
                    url: url,
                    type: 'POST',
                    data: {
                      querySetting: JSON.stringify(defaultQuery),
                      '*order': '-XNXQDM,-KCH,-KXH',
                      pageSize: 1000,
                      pageNumber: 1
                    },
                    dataType: 'text',
                    success: function(resp) {
                      try { GpaGradeBridge.onCaptured(url, resp); } catch(err) {}
                    }
                  });
                  jQuery.ajax({
                    url: url,
                    type: 'POST',
                    data: {
                      querySetting: '[]',
                      '*order': '-XNXQDM,-KCH,-KXH',
                      pageSize: 1000,
                      pageNumber: 1
                    },
                    dataType: 'text',
                    success: function(resp) {
                      try { GpaGradeBridge.onCaptured(url, resp); } catch(err) {}
                    }
                  });
                }
              } catch(e) {}
              try { GpaGradeBridge.onCaptured('autoclick', 'round=$round triggered'); } catch(e) {}
            })()
            """.trimIndent()
            runOnUiThread {
                if (!fetchFinished.get() && !isDestroyed) {
                    runCatching { fetchWebView.evaluateJavascript(js, null) }
                }
            }
        }
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun applyImported(imported: List<GpaCourse>) {
        if (imported.isEmpty()) {
            UiFeedback.showMessage(courseList, getString(R.string.gpa_import_empty), palette)
            render()
            return
        }
        if (courses.isEmpty()) {
            replaceCourses(imported)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.gpa_import_jw))
            .setMessage(getString(R.string.gpa_import_confirm, imported.size, courses.size))
            .setPositiveButton(getString(R.string.gpa_import_replace)) { _, _ -> replaceCourses(imported) }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .create()
            .also { UiFeedback.styleDialogSurface(it, palette); it.show() }
    }

    private fun replaceCourses(imported: List<GpaCourse>) {
        courses.clear()
        courses.addAll(imported)
        persist()
        render()
        UiFeedback.showMessage(courseList, getString(R.string.gpa_import_success, imported.size), palette)
    }

    private fun promptLogin() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.gpa_import_need_login))
            .setMessage(getString(R.string.account_login_hint))
            .setPositiveButton(getString(R.string.gpa_import_go_login)) { _, _ ->
                loginLauncher.launch(Intent(this, LoginActivity::class.java))
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .create()
            .also { UiFeedback.styleDialogSurface(it, palette); it.show() }
    }

    private fun addCourse() {
        val credit = creditInput.text.toString().trim().toDoubleOrNull()
        if (credit == null || !credit.isFinite() || credit <= 0.0) {
            UiFeedback.showMessage(courseList, getString(R.string.gpa_invalid_credit), palette)
            return
        }
        val name = nameInput.text.toString().trim()
        val course = when (gradeType) {
            GpaGradeType.PERCENT -> {
                val score = scoreInput.text.toString().trim().toDoubleOrNull()
                if (score == null || !score.isFinite() || score < GpaCalculator.MIN_SCORE || score > GpaCalculator.MAX_SCORE) {
                    UiFeedback.showMessage(courseList, getString(R.string.gpa_invalid_score), palette)
                    return
                }
                GpaCourse(id = UUID.randomUUID().toString(), name = name, gradeType = GpaGradeType.PERCENT, score = score, credit = credit)
            }
            GpaGradeType.LEVEL5 ->
                GpaCourse(id = UUID.randomUUID().toString(), name = name, gradeType = GpaGradeType.LEVEL5, level = selectedLevel, credit = credit)
        }
        courses.add(course)
        persist()
        nameInput.setText("")
        scoreInput.setText("")
        creditInput.setText("")
        render()
        creditInput.clearFocus()
    }

    private fun toggleIncluded(courseId: String, included: Boolean) {
        val index = courses.indexOfFirst { it.id == courseId }
        if (index < 0) return
        courses[index] = courses[index].copy(included = included)
        persist()
        render()
    }

    private fun deleteCourse(courseId: String) {
        if (courses.removeAll { it.id == courseId }) {
            persist()
            render()
        }
    }

    private fun confirmClearAll() {
        if (courses.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.gpa_clear))
            .setMessage(getString(R.string.gpa_clear_confirm))
            .setPositiveButton(getString(R.string.gpa_clear)) { _, _ ->
                courses.clear()
                withAccountData { GpaCourseStore.clear(it) }
                render()
                UiFeedback.showMessage(courseList, getString(R.string.gpa_cleared), palette)
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .create()
            .also { UiFeedback.styleDialogSurface(it, palette); it.show() }
    }

    private fun persist() {
        runCatching { withAccountData { GpaCourseStore.save(it, courses) } }
            .onFailure { Log.w(TAG, "save gpa courses failed", it) }
    }

    private fun render() {
        val summary = GpaCalculator.calculate(courses)
        valueGpa.text = summary.recommendationGpa?.let { format(it) } ?: DASH
        valueWeighted.text = summary.weightedAverage?.let { format(it) } ?: DASH
        valueArithmetic.text = summary.arithmeticAverage?.let { format(it) } ?: DASH
        resultMeta.text = if (summary.includedCount == 0) {
            getString(R.string.gpa_result_empty)
        } else {
            val excluded = courses.size - summary.includedCount
            val base = getString(
                R.string.gpa_included_summary,
                summary.includedCount,
                format(summary.includedCredits)
            )
            if (excluded > 0) "$base · $excluded ${getString(R.string.gpa_excluded_mark)}" else base
        }

        emptyHint.visibility = if (courses.isEmpty()) View.VISIBLE else View.GONE
        courseList.removeAllViews()
        for (breakdown in summary.breakdowns) {
            courseList.addView(buildCourseRow(breakdown))
        }
    }

    private fun buildCourseRow(breakdown: GpaCalculator.CourseBreakdown): View {
        val course = breakdown.course
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = cardDrawable()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val nameView = TextView(this).apply {
            text = course.displayName
            textSize = 15f
            setTextColor(palette.textPrimary)
            alpha = if (breakdown.included) 1f else 0.55f
        }
        val metaView = TextView(this).apply {
            text = rowMeta(course, breakdown)
            textSize = 12f
            setTextColor(palette.textSecondary)
            alpha = if (breakdown.included) 1f else 0.55f
        }
        info.addView(nameView)
        info.addView(metaView)
        row.addView(info)

        val switch = SwitchCompat(this).apply {
            text = getString(R.string.gpa_included_switch)
            isChecked = breakdown.included
            textSize = 13f
            setOnCheckedChangeListener { _, checked -> toggleIncluded(course.id, checked) }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
        }
        row.addView(switch)

        val delete = TextView(this).apply {
            text = getString(R.string.gpa_delete)
            textSize = 13f
            setTextColor(ColorUtils.blendARGB(0xFFD35454.toInt(), palette.textSecondary, 0.25f))
            setPadding(dp(10), dp(6), 0, dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(4) }
            setOnClickListener { deleteCourse(course.id) }
        }
        row.addView(delete)
        return row
    }

    private fun rowMeta(course: GpaCourse, breakdown: GpaCalculator.CourseBreakdown): String {
        val scoreText = when (course.gradeType) {
            GpaGradeType.PERCENT -> formatScore(course.score)
            GpaGradeType.LEVEL5 -> "${course.level}（折算 ${formatScore(breakdown.effectiveScore)}）"
        }
        val mark = if (breakdown.included) "" else " · ${getString(R.string.gpa_excluded_mark)}"
        return "成绩 $scoreText · 绩点 ${format(breakdown.gradePoint)} · ${formatScore(course.credit)} 学分$mark"
    }

    private fun bindSegment(buttons: List<TextView>, selected: TextView, onSelected: (TextView) -> Unit) {
        var current = selected
        fun restyle() {
            for (button in buttons) {
                val checked = button.id == current.id
                button.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 9f
                    setColor(if (checked) palette.buttonBackground else palette.panelBackground)
                }
                button.setTextColor(if (checked) palette.buttonText else palette.textSecondary)
            }
        }
        restyle()
        for (button in buttons) {
            button.setOnClickListener {
                if (button.id != current.id) {
                    current = button
                    restyle()
                    onSelected(button)
                }
            }
        }
    }

    private fun cardDrawable() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 18f
        setColor(palette.panelAltBackground)
        setStroke(1, ColorUtils.blendARGB(palette.panelAltBackground, palette.iconTint, 0.16f))
    }

    private fun styleInput(input: EditText) {
        input.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f
            setColor(palette.panelBackground)
            setStroke(1, ColorUtils.blendARGB(palette.panelBackground, palette.iconTint, 0.35f))
        }
        input.setPadding(dp(12), dp(10), dp(12), dp(10))
        input.setTextColor(palette.textPrimary)
        input.setHintTextColor(palette.textSecondary)
    }

    private fun applySystemBarInsets() {
        val root = findViewById<View>(R.id.gpaRoot)
        val baseTop = root.paddingTop
        val baseBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = baseTop + bars.top, bottom = baseBottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun formatScore(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.US, "%.1f", value)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        fetchQuietJob?.cancel()
        fetchWatchdogJob?.cancel()
        autoQueryJob?.cancel()
        if (::fetchWebView.isInitialized) {
            fetchWebView.apply {
                loadUrl("about:blank")
                onPause()
            }
        }
        super.onDestroy()
    }

    private companion object {
        const val TAG = "GpaCalculator"
        const val DASH = "—"

        /** 捕获静默期：成绩页可能连续查询多个视图，停稳后再统一解析 */
        const val CAPTURE_QUIET_MS = 4_000L

        /** 整体超时：含登录页重定向与自动点击等待 */
        const val FETCH_TIMEOUT_MS = 28_000L

        const val MIN_CAPTURE_BYTES = 60

        const val GRADE_PAGE_URL = "https://iedu.jlu.edu.cn/jwapp/sys/cjcx/*default/index.do"

        /** XHR/fetch 响应捕获钩子（与一键导入同思路，注入到页面 <head> 后） */
        private val GRADE_HOOK_JS = """
            (function(){
              if(window.__jluGradeHooked) return; window.__jluGradeHooked = true;
              function send(url, text){
                try { GpaGradeBridge.onCaptured(url, text); } catch(e) {}
              }
              var origOpen = XMLHttpRequest.prototype.open;
              var origSend = XMLHttpRequest.prototype.send;
              XMLHttpRequest.prototype.open = function(m, u){ this.__u = u; return origOpen.apply(this, arguments); };
              XMLHttpRequest.prototype.send = function(){
                this.addEventListener('load', function(){
                  var text = '';
                  try { text = this.responseText || ''; } catch(e) {
                    try { text = JSON.stringify(this.response || ''); } catch(e2) {}
                  }
                  try { send(this.__u || '', text); } catch(e) {}
                });
                return origSend.apply(this, arguments);
              };
              var origFetch = window.fetch;
              if (origFetch) {
                window.fetch = function(){
                  var u = arguments[0]; var url = (typeof u === 'string') ? u : (u && u.url) || '';
                  return origFetch.apply(this, arguments).then(function(resp){
                    try { resp.clone().text().then(function(t){ send(url, t); }); } catch(e) {}
                    return resp;
                  });
                };
              }
            })();
        """.trimIndent()
    }
}

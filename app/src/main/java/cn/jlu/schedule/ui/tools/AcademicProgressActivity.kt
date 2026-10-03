package cn.jlu.schedule.ui.tools

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import cn.jlu.schedule.R
import cn.jlu.schedule.auth.CampusCookieJar
import cn.jlu.schedule.auth.CasLoginResult
import cn.jlu.schedule.auth.TpassConfig
import cn.jlu.schedule.data.AcademicProgressPlan
import cn.jlu.schedule.data.AcademicProgressStore
import cn.jlu.schedule.data.AcademicRequirement
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.data.GradeStore
import cn.jlu.schedule.parser.PyfaTranscriptParser
import cn.jlu.schedule.remote.JwApiClient
import cn.jlu.schedule.remote.JwEndpoints
import cn.jlu.schedule.ui.auth.LoginActivity
import cn.jlu.schedule.ui.theme.ThemePalette
import cn.jlu.schedule.ui.theme.ThemePaletteProvider
import cn.jlu.schedule.ui.theme.UiFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

/**
 * 学业完成查询界面：
 * 1. 支持从教务学业完成查询页拉取真实学分达成度；
 * 2. 彻底废除比例平摊计算，本地成绩改为智能启发式分类；
 * 3. 支持全功能手动编辑指标与已修学分，自由增删模块；
 * 4. 教务原系统网页直达。
 */
class AcademicProgressActivity : AccountScopedActivity() {

    private lateinit var palette: ThemePalette
    private lateinit var percentText: TextView
    private lateinit var summaryText: TextView
    private lateinit var sourceHintText: TextView
    private lateinit var totalProgressBar: ProgressBar
    private lateinit var syncProgressBar: ProgressBar
    private lateinit var categoryContainer: LinearLayout
    private lateinit var syncWebBtn: Button
    private lateinit var editPlanBtn: Button
    private lateinit var autoCalcBtn: Button
    private lateinit var syncWebView: WebView

    private var currentPlan: AcademicProgressPlan = AcademicProgressPlan()

    private val isSyncing = AtomicBoolean(false)
    private val fetchFinished = AtomicBoolean(false)
    private val fetchServedLoginHtml = AtomicBoolean(false)
    private var silentLoginTried = false
    private var syncTimeoutJob: Job? = null
    private var domExtractJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePaletteProvider.applyNightMode(this)
        setTheme(ThemePaletteProvider.themeStyleFor(AppPreferences.getThemeColor(this)))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_academic_progress)

        palette = ThemePaletteProvider.fromContext(this)
        initViews()
        setupSyncWebView()
        loadPlan()
    }

    override fun onResume() {
        super.onResume()
        // 若用户从原网页返回，重新载入最新数据
        loadPlan()
    }

    private fun initViews() {
        findViewById<View>(R.id.academicRoot).setBackgroundColor(palette.pageBackground)
        findViewById<View>(R.id.academicHeader).setBackgroundColor(palette.panelAltBackground)

        val backBtn = findViewById<ImageView>(R.id.academicBackBtn)
        backBtn.imageTintList = ColorStateList.valueOf(palette.iconTint)
        backBtn.setOnClickListener { finish() }

        val webBtn = findViewById<ImageView>(R.id.academicOpenWebBtn)
        webBtn.imageTintList = ColorStateList.valueOf(palette.iconTint)
        webBtn.setOnClickListener {
            CampusWebActivity.start(this, JwEndpoints.ACADEMIC_PAGE_URL, getString(R.string.academic_progress_title))
        }

        percentText = findViewById(R.id.academicPercentText)
        summaryText = findViewById(R.id.academicCreditsSummary)
        sourceHintText = findViewById(R.id.academicSourceHint)
        totalProgressBar = findViewById(R.id.academicTotalProgress)
        syncProgressBar = findViewById(R.id.academicSyncProgressBar)
        categoryContainer = findViewById(R.id.academicCategoryContainer)

        syncWebBtn = findViewById(R.id.academicSyncWebBtn)
        editPlanBtn = findViewById(R.id.academicEditPlanBtn)
        autoCalcBtn = findViewById(R.id.academicAutoCalcBtn)
        syncWebView = findViewById(R.id.academicSyncWebView)

        UiFeedback.stylePrimaryButton(syncWebBtn, palette)
        UiFeedback.styleSecondaryButton(editPlanBtn, palette)
        UiFeedback.styleSecondaryButton(autoCalcBtn, palette)

        findViewById<View>(R.id.academicHeroCard).background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18f
            setColor(palette.panelAltBackground)
            setStroke(1, ColorUtils.blendARGB(palette.panelAltBackground, palette.iconTint, 0.16f))
        }

        totalProgressBar.progressTintList = ColorStateList.valueOf(palette.iconTint)
        totalProgressBar.progressBackgroundTintList = ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(palette.iconTint, 40)
        )
        syncProgressBar.indeterminateTintList = ColorStateList.valueOf(palette.iconTint)

        syncWebBtn.setOnClickListener { startSyncFromWeb() }
        editPlanBtn.setOnClickListener { showEditPlanDialog() }
        autoCalcBtn.setOnClickListener { autoCalculateFromGrades() }
    }

    private fun loadPlan() {
        currentPlan = withAccountData { AcademicProgressStore.load(it) } ?: AcademicProgressPlan()
        renderPlan()
    }

    private fun renderPlan() {
        val totalReq = if (currentPlan.totalRequiredCredits > 0.0) currentPlan.totalRequiredCredits else 160.0
        val totalEarned = currentPlan.totalEarnedCredits
        val percent = ((totalEarned / totalReq) * 100).toInt().coerceIn(0, 100)

        percentText.text = "$percent%"
        percentText.setTextColor(palette.textPrimary)
        summaryText.text = String.format(Locale.CHINA, getString(R.string.academic_credits_summary), totalEarned, totalReq, percent)
        totalProgressBar.progress = percent

        sourceHintText.text = when (currentPlan.source) {
            AcademicProgressPlan.SOURCE_WEB -> getString(R.string.academic_source_web)
            AcademicProgressPlan.SOURCE_ESTIMATED -> getString(R.string.academic_source_estimated)
            else -> getString(R.string.academic_source_manual)
        }

        categoryContainer.removeAllViews()

        for (req in currentPlan.requirements) {
            val reqCredits = if (req.requiredCredits > 0.0) req.requiredCredits else 1.0
            val catPercent = ((req.earnedCredits / reqCredits) * 100).toInt().coerceIn(0, 100)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 18, 24, 18)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 12
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 14f
                    setColor(palette.panelAltBackground)
                    setStroke(1, ColorUtils.blendARGB(palette.panelAltBackground, palette.iconTint, 0.12f))
                }
            }

            // 标题行
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val titleView = TextView(this).apply {
                text = req.categoryName
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(palette.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val completionColor = if (palette.isDark) Color.parseColor("#81C784") else Color.parseColor("#388E3C")

            val scoreView = TextView(this).apply {
                text = String.format(Locale.CHINA, "%.1f / %.1f 学分 (%d%%)", req.earnedCredits, req.requiredCredits, catPercent)
                textSize = 13f
                setTextColor(if (catPercent >= 100) completionColor else palette.iconTint)
            }

            headerRow.addView(titleView)
            headerRow.addView(scoreView)
            card.addView(headerRow)

            // 进度条
            val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = catPercent
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    14
                ).apply {
                    topMargin = 16
                }
                progressTintList = ColorStateList.valueOf(
                    if (catPercent >= 100) completionColor else palette.iconTint
                )
                progressBackgroundTintList = ColorStateList.valueOf(
                    ColorUtils.setAlphaComponent(palette.iconTint, 30)
                )
            }
            card.addView(pb)

            categoryContainer.addView(card)
        }
    }

    // ==========================================
    // 1. 原网页同步机制 (Pyfa Web Sync Engine)
    // ==========================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupSyncWebView() {
        syncWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = TpassConfig.USER_AGENT
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(syncWebView, true)
        }
        syncWebView.addJavascriptInterface(PyfaBridge(), "JluPyfaBridge")
        syncWebView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (!accountData.isCurrent || fetchFinished.get() || !request.isForMainFrame) return null
                val url = request.url
                if (url.host != TpassConfig.IEDU_HOST || request.method != "GET") return null
                if (url.encodedPath?.endsWith(".do") != true) return null
                return runCatching { buildHookedPyfaDocument(url) }.getOrNull()
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!accountData.isCurrent || fetchFinished.get()) return
                if (fetchServedLoginHtml.compareAndSet(true, false)) {
                    handleFetchLogin()
                    return
                }
                val host = runCatching { Uri.parse(url).host ?: "" }.getOrDefault("")
                if (host == TpassConfig.CAS_HOST) {
                    handleFetchLogin()
                    return
                }

                // 页面就绪后，多轮触发 DOM 节点/全局模型提取
                scheduleDomExtractRounds()
            }
        }
    }

    private fun startSyncFromWeb() {
        if (!accountData.isCurrent) return
        if (!JwApiClient.canRestoreSession(this)) {
            startActivityForResult(Intent(this, LoginActivity::class.java), REQ_LOGIN)
            return
        }

        if (isSyncing.get()) return
        isSyncing.set(true)
        fetchFinished.set(false)
        fetchServedLoginHtml.set(false)
        silentLoginTried = false

        syncProgressBar.visibility = View.VISIBLE
        syncWebBtn.isEnabled = false
        syncWebBtn.text = getString(R.string.academic_syncing)

        // 注入原生 CookieJar 到 WebView
        JwApiClient.syncJarToWebView(this)

        syncTimeoutJob?.cancel()
        syncTimeoutJob = lifecycleScope.launch {
            delay(SYNC_TIMEOUT_MS)
            if (!fetchFinished.get() && isSyncing.get()) {
                finishSync(success = false, message = getString(R.string.academic_sync_failed))
            }
        }

        syncWebView.loadUrl(JwEndpoints.ACADEMIC_PAGE_URL)
    }

    private fun handleFetchLogin() {
        if (silentLoginTried) {
            finishSync(success = false, message = "教务会话已过期，请重新登录")
            startActivityForResult(Intent(this, LoginActivity::class.java), REQ_LOGIN)
            return
        }
        silentLoginTried = true
        lifecycleScope.launch {
            val result = JwApiClient.silentLogin(this@AcademicProgressActivity)
            if (result is CasLoginResult.Success) {
                JwApiClient.syncJarToWebView(this@AcademicProgressActivity)
                if (!fetchFinished.get()) {
                    syncWebView.post { syncWebView.loadUrl(JwEndpoints.ACADEMIC_PAGE_URL) }
                }
            } else {
                finishSync(success = false, message = "教务会话已过期，请重新登录")
                startActivityForResult(Intent(this@AcademicProgressActivity, LoginActivity::class.java), REQ_LOGIN)
            }
        }
    }

    private fun scheduleDomExtractRounds() {
        domExtractJob?.cancel()
        domExtractJob = lifecycleScope.launch {
            val delays = longArrayOf(1200L, 2500L, 4500L, 7000L)
            for (d in delays) {
                delay(d)
                if (fetchFinished.get() || !isSyncing.get()) return@launch
                val js = DOM_EXTRACT_JS
                runOnUiThread {
                    if (!fetchFinished.get()) {
                        syncWebView.evaluateJavascript(js, null)
                    }
                }
            }
        }
    }

    private fun onPyfaPayloadCaptured(url: String, payload: String) {
        if (!accountData.isCurrent || fetchFinished.get()) return
        val parsed = PyfaTranscriptParser.parse(payload) ?: return

        if (!accountData.isCurrent || !fetchFinished.compareAndSet(false, true)) return
        Log.i(TAG, "Pyfa payload parsed successfully from $url: ${parsed.requirements.size} categories")

        lifecycleScope.launch {
            currentPlan = parsed.copy(
                lastUpdated = System.currentTimeMillis(),
                source = AcademicProgressPlan.SOURCE_WEB
            )
            withAccountData { AcademicProgressStore.save(it, currentPlan) }

            finishSync(
                success = true,
                message = String.format(
                    Locale.CHINA,
                    getString(R.string.academic_sync_success),
                    currentPlan.totalEarnedCredits,
                    currentPlan.totalRequiredCredits
                )
            )
        }
    }

    private fun finishSync(success: Boolean, message: String) {
        runOnUiThread {
            isSyncing.set(false)
            syncTimeoutJob?.cancel()
            domExtractJob?.cancel()
            syncProgressBar.visibility = View.GONE
            syncWebBtn.isEnabled = true
            syncWebBtn.text = getString(R.string.academic_sync_web_button)

            if (success) {
                renderPlan()
            }
            UiFeedback.showMessage(categoryContainer, message, palette)
        }
    }

    private fun buildHookedPyfaDocument(target: Uri): WebResourceResponse {
        val connection = java.net.URL(target.toString()).openConnection() as HttpsURLConnection
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
                        cookieManager.setCookie("https://$host/jwapp/sys/xywccx/", cookieHeader)
                    }
                }
                cookieManager.flush()
                JwApiClient.importAllWebViewCookies(this@AcademicProgressActivity)
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

        if (!html.contains("__jluPyfaHooked")) {
            val script = "<script>$PYFA_HOOK_JS</script>"
            val headTag = Regex("(?i)<head[^>]*>").find(html)
            html = if (headTag != null) {
                StringBuilder(html).insert(headTag.range.last + 1, script).toString()
            } else {
                script + html
            }
        }
        return WebResourceResponse(mimeType, "UTF-8", html.byteInputStream())
    }

    private inner class PyfaBridge {
        @JavascriptInterface
        fun onCaptured(url: String, payload: String) {
            onPyfaPayloadCaptured(url, payload)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (accountData.isCurrent && requestCode == REQ_LOGIN && resultCode == RESULT_OK) {
            startSyncFromWeb()
        }
    }

    // ==========================================
    // 2. 本地成绩启发式分类核算 (废除死板平摊)
    // ==========================================

    private fun autoCalculateFromGrades() {
        val grades = withAccountData { GradeStore.load(it) }.orEmpty()
        if (grades.isEmpty()) {
            UiFeedback.showMessage(categoryContainer, "暂无本地成绩，请先在「成绩查询」中同步教务成绩", palette)
            return
        }

        var totalPassedCredits = 0.0
        var passedCount = 0

        var compulsoryEarned = 0.0
        var electiveEarned = 0.0
        var generalEarned = 0.0
        var practiceEarned = 0.0
        var otherEarned = 0.0

        for (g in grades) {
            val num = g.scoreText.toDoubleOrNull()
            val passed = when {
                num != null && num.isFinite() -> num >= 60.0
                g.scoreText in listOf("优秀", "良好", "中等", "及格", "通过", "合格") -> true
                else -> false
            }
            if (!passed || g.credit <= 0.0) continue

            totalPassedCredits += g.credit
            passedCount++

            val name = g.name
            when {
                isPracticeCourse(name) -> practiceEarned += g.credit
                isGeneralCourse(name) -> generalEarned += g.credit
                isElectiveCourse(name) -> electiveEarned += g.credit
                else -> compulsoryEarned += g.credit
            }
        }

        // 映射到现有的模块配置上
        val currentReqs = currentPlan.requirements
        val updatedReqs = if (currentReqs.size >= 4) {
            listOf(
                currentReqs[0].copy(earnedCredits = roundOneDecimal(compulsoryEarned)),
                currentReqs[1].copy(earnedCredits = roundOneDecimal(electiveEarned)),
                currentReqs[2].copy(earnedCredits = roundOneDecimal(generalEarned)),
                currentReqs[3].copy(earnedCredits = roundOneDecimal(practiceEarned))
            )
        } else {
            listOf(
                AcademicRequirement(getString(R.string.academic_category_compulsory), 75.0, roundOneDecimal(compulsoryEarned)),
                AcademicRequirement(getString(R.string.academic_category_elective), 35.0, roundOneDecimal(electiveEarned)),
                AcademicRequirement(getString(R.string.academic_category_general), 16.0, roundOneDecimal(generalEarned)),
                AcademicRequirement(getString(R.string.academic_category_practice), 34.0, roundOneDecimal(practiceEarned))
            )
        }

        currentPlan = currentPlan.copy(
            totalEarnedCredits = roundOneDecimal(totalPassedCredits),
            requirements = updatedReqs,
            lastUpdated = System.currentTimeMillis(),
            source = AcademicProgressPlan.SOURCE_ESTIMATED
        )
        withAccountData { AcademicProgressStore.save(it, currentPlan) }
        renderPlan()

        UiFeedback.showMessage(
            categoryContainer,
            String.format(Locale.CHINA, getString(R.string.academic_auto_calc_success), passedCount, totalPassedCredits),
            palette
        )
    }

    private fun isPracticeCourse(name: String): Boolean {
        val keywords = listOf("实习", "课程设计", "毕业设计", "毕业论文", "实验", "实训", "见习", "军事训练", "军训", "社会实践", "劳动")
        return keywords.any { name.contains(it) }
    }

    private fun isGeneralCourse(name: String): Boolean {
        val keywords = listOf("通识", "体育", "美育", "艺术", "音乐", "戏剧", "电影", "大学英语", "外语", "思想道德", "马克思", "近现代史", "毛泽东", "形势与政策", "军事理论", "国家安全", "心理健康")
        return keywords.any { name.contains(it) }
    }

    private fun isElectiveCourse(name: String): Boolean {
        val keywords = listOf("选修", "任选", "限选", "方向", "导论", "前沿")
        return keywords.any { name.contains(it) }
    }

    private fun roundOneDecimal(value: Double): Double =
        kotlin.math.round(value * 10.0) / 10.0

    // ==========================================
    // 3. 全功能动态手动编辑弹窗 (支持增删改模块与需修/已修)
    // ==========================================

    private fun showEditPlanDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_academic_plan, null)
        val inputTotalReq = dialogView.findViewById<EditText>(R.id.inputTotalReqCredits)
        val inputTotalEarned = dialogView.findViewById<EditText>(R.id.inputTotalEarnedCredits)
        val autoSumBtn = dialogView.findViewById<TextView>(R.id.btnAutoSumCredits)
        val categoriesContainer = dialogView.findViewById<LinearLayout>(R.id.dialogCategoriesContainer)
        val addCategoryBtn = dialogView.findViewById<Button>(R.id.btnAddCategoryBtn)

        UiFeedback.styleInput(inputTotalReq, palette)
        UiFeedback.styleInput(inputTotalEarned, palette)
        autoSumBtn.setTextColor(palette.iconTint)
        UiFeedback.styleSecondaryButton(addCategoryBtn, palette)

        inputTotalReq.setText(currentPlan.totalRequiredCredits.toString())
        inputTotalEarned.setText(currentPlan.totalEarnedCredits.toString())

        // 填充每个模块视图
        fun addCategoryRow(categoryName: String, reqCredits: Double, earnedCredits: Double) {
            val itemView = layoutInflater.inflate(R.layout.item_edit_academic_module, categoriesContainer, false)
            val nameEdit = itemView.findViewById<EditText>(R.id.editCategoryName)
            val reqEdit = itemView.findViewById<EditText>(R.id.editCategoryReq)
            val earnedEdit = itemView.findViewById<EditText>(R.id.editCategoryEarned)
            val deleteBtn = itemView.findViewById<ImageView>(R.id.btnDeleteCategory)

            itemView.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f
                setColor(palette.panelAltBackground)
                setStroke(1, ColorUtils.blendARGB(palette.panelAltBackground, palette.iconTint, 0.22f))
            }
            nameEdit.setTextColor(palette.textPrimary)
            nameEdit.setHintTextColor(palette.textSecondary)
            UiFeedback.styleInput(reqEdit, palette)
            UiFeedback.styleInput(earnedEdit, palette)
            deleteBtn.imageTintList = ColorStateList.valueOf(
                if (palette.isDark) Color.parseColor("#E57373") else Color.parseColor("#FF7043")
            )

            nameEdit.setText(categoryName)
            reqEdit.setText(reqCredits.toString())
            earnedEdit.setText(earnedCredits.toString())

            deleteBtn.setOnClickListener {
                categoriesContainer.removeView(itemView)
            }
            categoriesContainer.addView(itemView)
        }

        for (req in currentPlan.requirements) {
            addCategoryRow(req.categoryName, req.requiredCredits, req.earnedCredits)
        }

        addCategoryBtn.setOnClickListener {
            addCategoryRow("", 10.0, 0.0)
        }

        autoSumBtn.setOnClickListener {
            var sumReq = 0.0
            var sumEarned = 0.0
            for (i in 0 until categoriesContainer.childCount) {
                val item = categoriesContainer.getChildAt(i)
                val reqVal = item.findViewById<EditText>(R.id.editCategoryReq).text.toString().toDoubleOrNull() ?: 0.0
                val earnedVal = item.findViewById<EditText>(R.id.editCategoryEarned).text.toString().toDoubleOrNull() ?: 0.0
                sumReq += reqVal
                sumEarned += earnedVal
            }
            inputTotalReq.setText(roundOneDecimal(sumReq).toString())
            inputTotalEarned.setText(roundOneDecimal(sumEarned).toString())
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.academic_edit_plan_button)
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val newReqs = mutableListOf<AcademicRequirement>()
                var sumReq = 0.0
                var sumEarned = 0.0

                for (i in 0 until categoriesContainer.childCount) {
                    val item = categoriesContainer.getChildAt(i)
                    val name = item.findViewById<EditText>(R.id.editCategoryName).text.toString().trim()
                    val reqVal = item.findViewById<EditText>(R.id.editCategoryReq).text.toString().toDoubleOrNull() ?: 0.0
                    val earnedVal = item.findViewById<EditText>(R.id.editCategoryEarned).text.toString().toDoubleOrNull() ?: 0.0

                    if (name.isNotBlank()) {
                        newReqs.add(
                            AcademicRequirement(
                                categoryName = name,
                                requiredCredits = roundOneDecimal(reqVal),
                                earnedCredits = roundOneDecimal(earnedVal)
                            )
                        )
                        sumReq += reqVal
                        sumEarned += earnedVal
                    }
                }

                val customTotalReq = inputTotalReq.text.toString().toDoubleOrNull()
                val customTotalEarned = inputTotalEarned.text.toString().toDoubleOrNull()

                val totalReq = if (customTotalReq != null && customTotalReq > 0.0) customTotalReq else sumReq.coerceAtLeast(160.0)
                val totalEarned = customTotalEarned ?: sumEarned

                currentPlan = currentPlan.copy(
                    totalRequiredCredits = roundOneDecimal(totalReq),
                    totalEarnedCredits = roundOneDecimal(totalEarned),
                    requirements = if (newReqs.isNotEmpty()) newReqs else currentPlan.requirements,
                    lastUpdated = System.currentTimeMillis(),
                    source = AcademicProgressPlan.SOURCE_MANUAL
                )
                withAccountData { AcademicProgressStore.save(it, currentPlan) }
                renderPlan()
                UiFeedback.showMessage(categoryContainer, "培养方案与学业指标已更新", palette)
            }
            .setNegativeButton("取消", null)
            .create()

        UiFeedback.styleDialogSurface(dialog, palette)
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { UiFeedback.stylePrimaryButton(it, palette) }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { UiFeedback.styleSecondaryButton(it, palette) }
    }


    override fun onDestroy() {
        syncTimeoutJob?.cancel()
        domExtractJob?.cancel()
        syncWebView.apply {
            loadUrl("about:blank")
            onPause()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AcademicProgress"
        private const val REQ_LOGIN = 301
        private const val SYNC_TIMEOUT_MS = 25000L
        private val PYFA_HOOK_JS = """
            (function(){
              if(window.__jluPyfaHooked) return; window.__jluPyfaHooked = true;
              function send(url, text){
                try { JluPyfaBridge.onCaptured(url, text); } catch(e) {}
              }
              var origOpen = XMLHttpRequest.prototype.open;
              XMLHttpRequest.prototype.open = function(m, u){
                this.__url = u;
                return origOpen.apply(this, arguments);
              };
              var origSend = XMLHttpRequest.prototype.send;
              XMLHttpRequest.prototype.send = function(body){
                var self = this;
                this.addEventListener('load', function(){
                  try {
                    if (self.responseText && (
                        self.responseText.indexOf('FAJDMC') >= 0 ||
                        self.responseText.indexOf('xsgzywcqk') >= 0 ||
                        self.responseText.indexOf('xywcqk') >= 0 ||
                        self.responseText.indexOf('pyfa') >= 0 ||
                        self.responseText.indexOf('YQJDXF') >= 0 ||
                        self.responseText.indexOf('YHDXF') >= 0 ||
                        self.responseText.indexOf('datas') >= 0
                    )) {
                      send(self.__url || '', self.responseText);
                    }
                  } catch(e) {}
                });
                return origSend.apply(this, arguments);
              };
              if (window.fetch) {
                var origFetch = window.fetch;
                window.fetch = function(input, init){
                  return origFetch.apply(this, arguments).then(function(res){
                    try {
                      var clone = res.clone();
                      clone.text().then(function(txt){
                        if (txt && (
                            txt.indexOf('FAJDMC') >= 0 ||
                            txt.indexOf('xsgzywcqk') >= 0 ||
                            txt.indexOf('xywcqk') >= 0 ||
                            txt.indexOf('pyfa') >= 0 ||
                            txt.indexOf('YQJDXF') >= 0 ||
                            txt.indexOf('YHDXF') >= 0 ||
                            txt.indexOf('datas') >= 0
                        )) {
                          var u = (typeof input === 'string') ? input : (input && input.url ? input.url : '');
                          send(u, txt);
                        }
                      }).catch(function(){});
                    } catch(e){}
                    return res;
                  });
                };
              }
            })();
        """.trimIndent()

        private val DOM_EXTRACT_JS = """
            (function(){
              try {
                var extracted = [];
                var nodes = document.querySelectorAll('.jm-node, .mind-node, .jmind-node, [data-node], tr, .node');
                if (nodes && nodes.length > 0) {
                  nodes.forEach(function(el){
                    var t = (el.innerText || el.textContent || '').trim();
                    if (!t || t.length > 100) return;
                    var m = t.match(/([^\d\n\r\/]{2,20})[\s\S]*?(\d+\.?\d*)\s*[\/分]\s*(\d+\.?\d*)/);
                    if (m) {
                      var name = m[1].replace(/[（(【\[:：]/g, '').trim();
                      var n1 = parseFloat(m[2]);
                      var n2 = parseFloat(m[3]);
                      if (name && !isNaN(n1) && !isNaN(n2)) {
                        var earned = Math.min(n1, n2);
                        var req = Math.max(n1, n2);
                        if (t.indexOf('需') > t.indexOf('已') || t.indexOf('应') > t.indexOf('已')) {
                          earned = n1; req = n2;
                        } else if (t.indexOf('已') > t.indexOf('需') || t.indexOf('已') > t.indexOf('应')) {
                          req = n1; earned = n2;
                        }
                        extracted.push({ name: name, required: req, earned: earned });
                      }
                    }
                  });
                }
                if (extracted.length > 0) {
                  JluPyfaBridge.onCaptured('dom_extract', JSON.stringify({
                    type: 'pyfa_dom_extract',
                    modules: extracted
                  }));
                }
              } catch(e){}
            })();
        """.trimIndent()
    }
}

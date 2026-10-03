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
import android.view.LayoutInflater
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
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.data.GpaCourseStore
import cn.jlu.schedule.data.GradeStore
import cn.jlu.schedule.domain.GpaCalculator
import cn.jlu.schedule.domain.ImportedGrade
import cn.jlu.schedule.parser.GradeTranscriptParser
import cn.jlu.schedule.remote.JwApiClient
import cn.jlu.schedule.ui.auth.LoginActivity
import cn.jlu.schedule.ui.theme.ThemePalette
import cn.jlu.schedule.ui.theme.ThemePaletteProvider
import cn.jlu.schedule.ui.theme.UiFeedback
import cn.jlu.schedule.ui.theme.applySystemBarPadding
import cn.jlu.schedule.ui.theme.applySystemBarIcons
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

/**
 * 成绩查询界面：按学期汇总课程成绩、单科绩点、加权均分，支持教务一键同步、手动录入与绩点计算器双向打通。
 */
class GradeInquiryActivity : AccountScopedActivity() {

    private lateinit var palette: ThemePalette
    private lateinit var progressBar: ProgressBar
    private lateinit var gpaText: TextView
    private lateinit var avgText: TextView
    private lateinit var creditsText: TextView
    private lateinit var detailText: TextView
    private lateinit var semesterChips: LinearLayout
    private lateinit var listContainer: LinearLayout
    private lateinit var emptyHint: TextView
    private lateinit var syncBtn: Button
    private lateinit var addBtn: Button
    private lateinit var fromGpaBtn: Button
    private lateinit var toGpaBtn: Button
    private lateinit var syncWebView: WebView

    private val allGrades = mutableListOf<ImportedGrade>()
    private var selectedSemester: String? = null // null means 全部学期

    private val fetchFinished = AtomicBoolean(false)
    private val fetchCapturedAny = AtomicBoolean(false)
    private val fetchServedLoginHtml = AtomicBoolean(false)
    private val capturedBuffer = mutableListOf<Pair<String, String>>()
    private var silentLoginTried = false

    private var autoQueryJob: Job? = null
    private var fetchWatchdogJob: Job? = null
    private var fetchQuietJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePaletteProvider.applyNightMode(this)
        setTheme(ThemePaletteProvider.themeStyleFor(AppPreferences.getThemeColor(this)))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grade_inquiry)
        findViewById<View>(R.id.gradeRoot).applySystemBarPadding()

        palette = ThemePaletteProvider.fromContext(this)
        window.applySystemBarIcons(palette.isDark)
        initViews()
        loadLocalData()
        setupSyncWebView()
    }

    private fun initViews() {
        findViewById<View>(R.id.gradeRoot).setBackgroundColor(palette.pageBackground)
        findViewById<View>(R.id.gradeHeader).setBackgroundColor(palette.panelAltBackground)

        val backBtn = findViewById<ImageView>(R.id.gradeBackBtn)
        backBtn.imageTintList = ColorStateList.valueOf(palette.iconTint)
        backBtn.setOnClickListener { finish() }

        val webBtn = findViewById<ImageView>(R.id.gradeOpenWebBtn)
        webBtn.imageTintList = ColorStateList.valueOf(palette.iconTint)
        webBtn.setOnClickListener {
            CampusWebActivity.start(this, GRADE_PAGE_URL, getString(R.string.grade_inquiry_title))
        }

        progressBar = findViewById(R.id.gradeProgressBar)
        progressBar.indeterminateTintList = ColorStateList.valueOf(palette.iconTint)

        gpaText = findViewById(R.id.gradeSummaryGpa)
        avgText = findViewById(R.id.gradeSummaryAvg)
        creditsText = findViewById(R.id.gradeSummaryCredits)
        detailText = findViewById(R.id.gradeSummaryDetail)
        semesterChips = findViewById(R.id.gradeSemesterChips)
        listContainer = findViewById(R.id.gradeListContainer)
        emptyHint = findViewById(R.id.gradeEmptyHint)

        syncBtn = findViewById(R.id.gradeSyncBtn)
        addBtn = findViewById(R.id.gradeAddBtn)
        fromGpaBtn = findViewById(R.id.gradeFromGpaBtn)
        toGpaBtn = findViewById(R.id.gradeToGpaBtn)

        UiFeedback.stylePrimaryButton(syncBtn, palette)
        UiFeedback.styleSecondaryButton(addBtn, palette)
        UiFeedback.styleSecondaryButton(fromGpaBtn, palette)
        UiFeedback.styleSecondaryButton(toGpaBtn, palette)

        findViewById<View>(R.id.gradeSummaryCard).background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f
            setColor(palette.panelAltBackground)
            setStroke(1, ColorUtils.blendARGB(palette.panelAltBackground, palette.iconTint, 0.16f))
        }

        syncBtn.setOnClickListener { startSyncGrades() }
        addBtn.setOnClickListener { showAddGradeDialog(null) }
        fromGpaBtn.setOnClickListener { syncFromGpaCalculator() }
        toGpaBtn.setOnClickListener { exportToGpaCalculator() }
    }

    private fun loadLocalData() {
        allGrades.clear()
        allGrades.addAll(withAccountData { GradeStore.load(it) }.orEmpty())
        renderGrades()
    }

    private fun renderGrades() {
        val filtered = if (selectedSemester == null) {
            allGrades
        } else {
            allGrades.filter { it.semesterCode == selectedSemester }
        }

        if (filtered.isEmpty()) {
            emptyHint.visibility = View.VISIBLE
            listContainer.visibility = View.GONE
            gpaText.text = "0.00"
            avgText.text = "0.00"
            creditsText.text = "0.0"
            detailText.text = getString(R.string.grade_empty_hint)
        } else {
            emptyHint.visibility = View.GONE
            listContainer.visibility = View.VISIBLE
            computeAndRenderSummary(filtered)
            renderGradeList(filtered)
        }

        renderSemesterChips()
    }

    private fun computeAndRenderSummary(grades: List<ImportedGrade>) {
        var totalCreditWeighted = 0.0
        var totalScoreWeighted = 0.0
        var totalGradePoints = 0.0

        for (grade in grades) {
            val scoreNum = grade.scoreText.toDoubleOrNull()
            val credit = grade.credit
            val effectiveScore = when {
                scoreNum != null && scoreNum.isFinite() && scoreNum in GpaCalculator.MIN_SCORE..GpaCalculator.MAX_SCORE -> scoreNum
                else -> GpaCalculator.LEVEL_SCORES[grade.scoreText] ?: -1.0
            }
            if (effectiveScore >= 0.0 && credit > 0.0) {
                totalCreditWeighted += credit
                totalScoreWeighted += effectiveScore * credit
                totalGradePoints += GpaCalculator.gradePointFor(effectiveScore) * credit
            }
        }

        val gpa = if (totalCreditWeighted > 0.0) totalGradePoints / totalCreditWeighted else 0.0
        val avg = if (totalCreditWeighted > 0.0) totalScoreWeighted / totalCreditWeighted else 0.0

        gpaText.text = String.format(Locale.CHINA, "%.2f", gpa)
        avgText.text = String.format(Locale.CHINA, "%.2f", avg)
        creditsText.text = String.format(Locale.CHINA, "%.1f", totalCreditWeighted)
        detailText.text = String.format(Locale.CHINA, getString(R.string.grade_summary_format), grades.size, totalCreditWeighted, avg)
    }

    private fun renderSemesterChips() {
        semesterChips.removeAllViews()
        val semesters = allGrades.map { it.semesterCode }.filter { it.isNotBlank() }.distinct()

        // 全部学期 chip
        addSemesterChip(getString(R.string.grade_semester_all), selectedSemester == null) {
            selectedSemester = null
            renderGrades()
        }

        for (sem in semesters) {
            addSemesterChip(sem, selectedSemester == sem) {
                selectedSemester = sem
                renderGrades()
            }
        }
    }

    private fun addSemesterChip(title: String, isSelected: Boolean, onClick: () -> Unit) {
        val chip = TextView(this).apply {
            text = title
            textSize = 13f
            setPadding(28, 14, 28, 14)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 16
            }
            layoutParams = params
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f
                if (isSelected) {
                    setColor(palette.buttonBackground)
                    setStroke(1, ColorUtils.blendARGB(palette.buttonBackground, palette.iconTint, 0.4f))
                } else {
                    setColor(palette.panelAltBackground)
                    setStroke(1, ColorUtils.blendARGB(palette.panelAltBackground, palette.iconTint, 0.2f))
                }
            }
            setTextColor(if (isSelected) palette.buttonText else palette.textPrimary)
            setOnClickListener { onClick() }
        }
        semesterChips.addView(chip)
    }

    private fun renderGradeList(grades: List<ImportedGrade>) {
        listContainer.removeAllViews()

        for (grade in grades) {
            val scoreNum = grade.scoreText.toDoubleOrNull()
            val effectiveScore = when {
                scoreNum != null && scoreNum.isFinite() && scoreNum in GpaCalculator.MIN_SCORE..GpaCalculator.MAX_SCORE -> scoreNum
                else -> GpaCalculator.LEVEL_SCORES[grade.scoreText] ?: -1.0
            }
            val gp = if (effectiveScore >= 0.0) GpaCalculator.gradePointFor(effectiveScore) else 0.0

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 20, 24, 20)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 14
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 14f
                    setColor(palette.panelAltBackground)
                    setStroke(1, ColorUtils.blendARGB(palette.panelAltBackground, palette.iconTint, 0.12f))
                }
                setOnClickListener { showEditDeleteDialog(grade) }
                setOnLongClickListener {
                    showEditDeleteDialog(grade)
                    true
                }
            }

            val textColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameView = TextView(this).apply {
                text = grade.name
                textSize = 15f
                setTextColor(palette.textPrimary)
            }
            val subView = TextView(this).apply {
                val codeInfo = if (grade.courseCode.isNotBlank()) "${grade.courseCode} · " else ""
                val semInfo = if (grade.semesterCode.isNotBlank()) " · ${grade.semesterCode}" else ""
                text = "$codeInfo${grade.credit}学分 · 绩点 ${String.format(Locale.CHINA, "%.1f", gp)}$semInfo"
                textSize = 12f
                setTextColor(ColorUtils.blendARGB(palette.textPrimary, palette.pageBackground, 0.4f))
            }
            textColumn.addView(nameView)
            textColumn.addView(subView)

            val badgeView = TextView(this).apply {
                text = grade.scoreText
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(24, 8, 24, 8)
                val badgeColor = if (palette.isDark) {
                    when {
                        effectiveScore >= 90.0 -> Color.parseColor("#81C784") // 柔和绿
                        effectiveScore >= 80.0 -> Color.parseColor("#90CAF9") // 柔和蓝
                        effectiveScore >= 60.0 -> Color.parseColor("#FFB74D") // 柔和琥珀橙
                        else -> Color.parseColor("#E57373") // 柔和珊瑚红
                    }
                } else {
                    when {
                        effectiveScore >= 90.0 -> Color.parseColor("#388E3C") // 绿色
                        effectiveScore >= 80.0 -> Color.parseColor("#1976D2") // 蓝色
                        effectiveScore >= 60.0 -> Color.parseColor("#F57C00") // 橙色
                        else -> Color.parseColor("#D32F2F") // 红色
                    }
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10f
                    val bgAlpha = if (palette.isDark) 45 else 35
                    setColor(ColorUtils.setAlphaComponent(badgeColor, bgAlpha))
                }
                setTextColor(badgeColor)
            }

            card.addView(textColumn)
            card.addView(badgeView)
            listContainer.addView(card)
        }
    }

    private fun showEditDeleteDialog(grade: ImportedGrade) {
        val options = arrayOf(
            getString(R.string.grade_edit_title),
            getString(R.string.manage_delete)
        )
        AlertDialog.Builder(this)
            .setTitle(grade.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddGradeDialog(grade)
                    1 -> confirmDeleteGrade(grade)
                }
            }
            .create()
            .also {
                UiFeedback.styleDialogSurface(it, palette)
                it.show()
            }
    }

    private fun confirmDeleteGrade(grade: ImportedGrade) {
        AlertDialog.Builder(this)
            .setTitle(R.string.grade_delete_title)
            .setMessage(getString(R.string.grade_delete_message, grade.name))
            .setPositiveButton(R.string.manage_delete) { _, _ ->
                allGrades.removeAll { it == grade || (it.name == grade.name && it.courseCode == grade.courseCode && it.semesterCode == grade.semesterCode) }
                withAccountData { GradeStore.save(it, allGrades) }
                withAccountData { GradeStore.syncToGpaCourses(it, allGrades) }
                renderGrades()
                UiFeedback.showMessage(listContainer, getString(R.string.manage_deleted), palette)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
            .also {
                UiFeedback.styleDialogSurface(it, palette)
                it.show()
                it.getButton(AlertDialog.BUTTON_POSITIVE)?.let { btn -> UiFeedback.styleDangerButton(btn, palette) }
                it.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { btn -> UiFeedback.styleSecondaryButton(btn, palette) }
            }
    }

    private fun showAddGradeDialog(existing: ImportedGrade?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_grade, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.inputGradeName)
        val creditInput = dialogView.findViewById<EditText>(R.id.inputGradeCredit)
        val scoreInput = dialogView.findViewById<EditText>(R.id.inputGradeScore)
        val semesterInput = dialogView.findViewById<EditText>(R.id.inputGradeSemester)
        val codeInput = dialogView.findViewById<EditText>(R.id.inputGradeCode)

        UiFeedback.styleInput(nameInput, palette)
        UiFeedback.styleInput(creditInput, palette)
        UiFeedback.styleInput(scoreInput, palette)
        UiFeedback.styleInput(semesterInput, palette)
        UiFeedback.styleInput(codeInput, palette)

        if (existing != null) {
            nameInput.setText(existing.name)
            creditInput.setText(if (existing.credit % 1.0 == 0.0) existing.credit.toInt().toString() else existing.credit.toString())
            scoreInput.setText(existing.scoreText)
            semesterInput.setText(existing.semesterCode)
            codeInput.setText(existing.courseCode)
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing != null) R.string.grade_edit_title else R.string.grade_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val name = nameInput.text.toString().trim()
                val credit = creditInput.text.toString().trim().toDoubleOrNull()
                val score = scoreInput.text.toString().trim()
                val semester = semesterInput.text.toString().trim()
                val code = codeInput.text.toString().trim()

                if (name.isBlank()) {
                    UiFeedback.showMessage(listContainer, getString(R.string.manage_name_empty), palette)
                    return@setPositiveButton
                }
                if (credit == null || credit <= 0.0) {
                    UiFeedback.showMessage(listContainer, getString(R.string.gpa_invalid_credit), palette)
                    return@setPositiveButton
                }
                if (score.isBlank()) {
                    UiFeedback.showMessage(listContainer, getString(R.string.gpa_invalid_score), palette)
                    return@setPositiveButton
                }

                val newGrade = ImportedGrade(
                    courseCode = code,
                    name = name,
                    credit = credit,
                    scoreText = score,
                    semesterCode = semester,
                    isCustom = true
                )

                if (existing != null) {
                    val idx = allGrades.indexOfFirst { it == existing }
                    if (idx >= 0) allGrades[idx] = newGrade else allGrades.add(newGrade)
                } else {
                    allGrades.add(0, newGrade)
                }

                withAccountData { GradeStore.save(it, allGrades) }
                withAccountData { GradeStore.syncToGpaCourses(it, allGrades) }
                renderGrades()
                UiFeedback.showMessage(listContainer, if (existing != null) "已修改成绩" else "已添加成绩", palette)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
            .also {
                UiFeedback.styleDialogSurface(it, palette)
                it.show()
                it.getButton(AlertDialog.BUTTON_POSITIVE)?.let { btn -> UiFeedback.stylePrimaryButton(btn, palette) }
                it.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { btn -> UiFeedback.styleSecondaryButton(btn, palette) }
            }
    }

    private fun syncFromGpaCalculator() {
        val gpaCourses = withAccountData { GpaCourseStore.load(it) }.orEmpty()
        if (gpaCourses.isEmpty()) {
            UiFeedback.showMessage(listContainer, "绩点计算器中暂无课程数据", palette)
            return
        }
        val converted = GradeStore.convertFromGpaCourses(gpaCourses)
        if (converted.isEmpty()) {
            UiFeedback.showMessage(listContainer, "未找到可导入的课程数据", palette)
            return
        }

        allGrades.clear()
        allGrades.addAll(converted)
        withAccountData { GradeStore.save(it, allGrades) }
        renderGrades()
        UiFeedback.showMessage(
            listContainer,
            String.format(Locale.CHINA, getString(R.string.grade_imported_from_gpa), converted.size),
            palette
        )
    }

    private fun exportToGpaCalculator() {
        if (allGrades.isNotEmpty()) {
            withAccountData { GradeStore.syncToGpaCourses(it, allGrades) }
        }
        startActivity(Intent(this, GpaCalculatorActivity::class.java))
    }

    private fun startSyncGrades() {
        if (!accountData.isCurrent) return
        if (!JwApiClient.canRestoreSession(this)) {
            promptNeedLogin()
            return
        }

        progressBar.visibility = View.VISIBLE
        syncBtn.isEnabled = false
        syncBtn.text = getString(R.string.grade_syncing)

        lifecycleScope.launch {
            // 通道一：原生 OkHttp 极速直连（无需等待 WebView 下载渲染数兆脚本，直接拉取历年全量）
            val nativeGrades = runCatching { JwApiClient.fetchAllGrades(this@GradeInquiryActivity) }.getOrDefault(emptyList())
            if (nativeGrades.isNotEmpty()) {
                val semesterCount = nativeGrades.map { it.semesterCode }.filter { it.isNotBlank() }.distinct().size
                if (nativeGrades.size >= 25 || semesterCount > 2) {
                    applySyncedGrades(nativeGrades)
                    return@launch
                }
            }

            // 通道二：若原生直连未拉到多学期，启动 WebView 自动化多学期协同抓取
            startWebViewSync()
        }
    }

    private fun applySyncedGrades(parsed: List<ImportedGrade>) {
        fetchWatchdogJob?.cancel()
        fetchQuietJob?.cancel()
        autoQueryJob?.cancel()
        fetchFinished.set(true)

        progressBar.visibility = View.GONE
        syncBtn.isEnabled = true
        syncBtn.text = getString(R.string.grade_sync_button)

        val merged = GradeStore.mergeSyncedGrades(allGrades, parsed)
        allGrades.clear()
        allGrades.addAll(merged)
        withAccountData { GradeStore.save(it, allGrades) }
        withAccountData { GradeStore.syncToGpaCourses(it, allGrades) }
        renderGrades()
        UiFeedback.showMessage(
            listContainer,
            String.format(Locale.CHINA, getString(R.string.grade_sync_success), parsed.size),
            palette
        )
    }

    private fun startWebViewSync() {
        JwApiClient.syncJarToWebView(this)

        fetchFinished.set(false)
        fetchCapturedAny.set(false)
        fetchServedLoginHtml.set(false)
        silentLoginTried = false
        synchronized(capturedBuffer) { capturedBuffer.clear() }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(syncWebView, true)
        }

        fetchWatchdogJob?.cancel()
        fetchWatchdogJob = lifecycleScope.launch {
            delay(FETCH_TIMEOUT_MS)
            finishFetch(reason = "timeout")
        }

        scheduleAutoQueryRounds()
        syncWebView.loadUrl(GRADE_PAGE_URL)
    }

    /** 成绩页全量查询：切换到【全部】标签并直接发起全量学期查询 */
    private fun scheduleAutoQueryRounds() {
        autoQueryJob?.cancel()
        autoQueryJob = lifecycleScope.launch {
            val rounds = intArrayOf(1, 2, 4, 7, 12, 18)
            for (i in rounds.indices) {
                if (i > 0) delay((rounds[i] - rounds[i - 1]) * 1000L) else delay(rounds[i] * 1000L)
                if (!accountData.isCurrent || fetchFinished.get()) return@launch
                val round = i + 1
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
                        runCatching { syncWebView.evaluateJavascript(js, null) }
                    }
                }
            }
        }
    }

    private fun handleFetchLogin() {
        if (silentLoginTried) {
            finishFetch(reason = "need-login")
            return
        }
        silentLoginTried = true
        lifecycleScope.launch {
            val result = JwApiClient.silentLogin(this@GradeInquiryActivity)
            if (result is CasLoginResult.Success) {
                JwApiClient.syncJarToWebView(this@GradeInquiryActivity)
                if (!fetchFinished.get()) {
                    syncWebView.post { syncWebView.loadUrl(GRADE_PAGE_URL) }
                }
            } else {
                finishFetch(reason = "need-login")
            }
        }
    }

    private fun onGradePayloadCaptured(url: String, text: String) {
        if (fetchFinished.get() || text.length < MIN_CAPTURE_BYTES) return
        if (!GradeTranscriptParser.isLikelyGradePayload(text)) return

        val parsedCount = runCatching { GradeTranscriptParser.parse(text).size }.getOrDefault(0)

        synchronized(capturedBuffer) {
            val key = sha1(url + "|" + text.take(256))
            if (capturedBuffer.any { sha1(it.first + "|" + it.second.take(256)) == key }) return
            capturedBuffer.add(url to text)
            Log.i(TAG, "captured grade payload #${capturedBuffer.size} ($url, ${text.length} bytes, parsed $parsedCount courses)")
        }

        fetchCapturedAny.set(true)

        val totalCourses = synchronized(capturedBuffer) {
            capturedBuffer.flatMap { GradeTranscriptParser.parse(it.second) }
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
        autoQueryJob?.cancel()

        val snapshot = synchronized(capturedBuffer) { capturedBuffer.toList() }
        val parsed = snapshot.asSequence()
            .filter { GradeTranscriptParser.isLikelyGradePayload(it.second) }
            .flatMap { GradeTranscriptParser.parse(it.second).asSequence() }
            .distinctBy { "${it.semesterCode}_${it.courseCode}_${it.name}" }
            .sortedWith(compareByDescending<ImportedGrade> { it.semesterCode }.thenBy { it.courseCode })
            .toList()

        runOnUiThread {
            if (parsed.isNotEmpty()) {
                applySyncedGrades(parsed)
            } else {
                progressBar.visibility = View.GONE
                syncBtn.isEnabled = true
                syncBtn.text = getString(R.string.grade_sync_button)
                when (reason) {
                    "need-login" -> promptNeedLogin()
                    else -> UiFeedback.showMessage(listContainer, getString(R.string.gpa_import_empty), palette)
                }
            }
        }
    }


    private fun promptNeedLogin() {
        AlertDialog.Builder(this)
            .setTitle(R.string.gpa_import_jw)
            .setMessage(R.string.gpa_import_need_login)
            .setPositiveButton(R.string.gpa_import_go_login) { _, _ ->
                startActivityForResult(Intent(this, LoginActivity::class.java), REQ_LOGIN)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .create()
            .also {
                UiFeedback.styleDialogSurface(it, palette)
                it.show()
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (accountData.isCurrent && requestCode == REQ_LOGIN && resultCode == RESULT_OK) {
            startSyncGrades()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupSyncWebView() {
        syncWebView = findViewById(R.id.gradeSyncWebView)
        syncWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = TpassConfig.USER_AGENT
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(syncWebView, true)
        }
        syncWebView.addJavascriptInterface(GradeBridge(), "GpaGradeBridge")
        syncWebView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (!accountData.isCurrent || fetchFinished.get() || !request.isForMainFrame) return null
                val url = request.url
                if (url.host != TpassConfig.IEDU_HOST || request.method != "GET") return null
                if (url.encodedPath?.endsWith(".do") != true) return null
                return runCatching { buildHookedGradeDocument(url) }.getOrNull()
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
                }
            }
        }
    }

    private fun buildHookedGradeDocument(target: Uri): WebResourceResponse {
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
                        cookieManager.setCookie("https://$host/jwapp/sys/cjcx/", cookieHeader)
                    }
                }
                cookieManager.flush()
                JwApiClient.importAllWebViewCookies(this@GradeInquiryActivity)
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

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private inner class GradeBridge {
        @JavascriptInterface
        fun onCaptured(url: String, payload: String) {
            onGradePayloadCaptured(url, payload)
        }
    }

    override fun onDestroy() {
        autoQueryJob?.cancel()
        fetchWatchdogJob?.cancel()
        fetchQuietJob?.cancel()
        syncWebView.apply {
            loadUrl("about:blank")
            onPause()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GradeInquiry"
        private const val REQ_LOGIN = 201
        private const val MIN_CAPTURE_BYTES = 60
        private const val CAPTURE_QUIET_MS = 3500L
        private const val FETCH_TIMEOUT_MS = 28000L
        private const val GRADE_PAGE_URL = "https://iedu.jlu.edu.cn/jwapp/sys/cjcx/*default/index.do"

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

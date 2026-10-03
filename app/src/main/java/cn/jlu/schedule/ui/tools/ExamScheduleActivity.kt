package cn.jlu.schedule.ui.tools

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import cn.jlu.schedule.auth.TpassConfig
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.data.ExamItem
import cn.jlu.schedule.data.ExamStore
import cn.jlu.schedule.parser.ExamScheduleParser
import cn.jlu.schedule.remote.JwApiClient
import cn.jlu.schedule.remote.JwEndpoints
import cn.jlu.schedule.ui.auth.LoginActivity
import cn.jlu.schedule.ui.theme.ThemePalette
import cn.jlu.schedule.ui.theme.ThemePaletteProvider
import cn.jlu.schedule.ui.theme.UiFeedback
import cn.jlu.schedule.ui.theme.applySystemBarPadding
import cn.jlu.schedule.ui.theme.applySystemBarIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.launch

/**
 * 考试查询界面：期末/期中考试日程、地点、座位号及倒计时，支持从教务同步与手动自定义添加。
 */
class ExamScheduleActivity : AccountScopedActivity() {

    private lateinit var palette: ThemePalette
    private lateinit var progressBar: ProgressBar
    private lateinit var countdownTitle: TextView
    private lateinit var countdownText: TextView
    private lateinit var countdownDetail: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var emptyHint: TextView
    private lateinit var syncBtn: Button
    private lateinit var addBtn: Button
    private lateinit var syncWebView: WebView

    private val allExams = mutableListOf<ExamItem>()
    private val fetchFinished = AtomicBoolean(false)
    private val capturedBuffer = mutableListOf<String>()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePaletteProvider.applyNightMode(this)
        setTheme(ThemePaletteProvider.themeStyleFor(AppPreferences.getThemeColor(this)))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exam_schedule)
        findViewById<View>(R.id.examRoot).applySystemBarPadding()

        palette = ThemePaletteProvider.fromContext(this)
        window.applySystemBarIcons(palette.isDark)
        initViews()
        loadLocalData()
        setupSyncWebView()
    }

    private fun initViews() {
        findViewById<View>(R.id.examRoot).setBackgroundColor(palette.pageBackground)
        findViewById<View>(R.id.examHeader).setBackgroundColor(palette.panelAltBackground)

        val backBtn = findViewById<ImageView>(R.id.examBackBtn)
        backBtn.imageTintList = ColorStateList.valueOf(palette.iconTint)
        backBtn.setOnClickListener { finish() }

        val webBtn = findViewById<ImageView>(R.id.examOpenWebBtn)
        webBtn.imageTintList = ColorStateList.valueOf(palette.iconTint)
        webBtn.setOnClickListener {
            CampusWebActivity.start(this, JwEndpoints.EXAM_PAGE_URL, getString(R.string.exam_schedule_title))
        }

        progressBar = findViewById(R.id.examProgressBar)
        progressBar.indeterminateTintList = ColorStateList.valueOf(palette.iconTint)

        countdownTitle = findViewById(R.id.examCountdownTitle)
        countdownText = findViewById(R.id.examCountdownText)
        countdownDetail = findViewById(R.id.examCountdownDetail)
        listContainer = findViewById(R.id.examListContainer)
        emptyHint = findViewById(R.id.examEmptyHint)

        syncBtn = findViewById(R.id.examSyncBtn)
        addBtn = findViewById(R.id.examAddBtn)

        UiFeedback.stylePrimaryButton(syncBtn, palette)
        UiFeedback.styleSecondaryButton(addBtn, palette)

        findViewById<View>(R.id.examCountdownCard).background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f
            setColor(palette.panelAltBackground)
            setStroke(1, ColorUtils.blendARGB(palette.panelAltBackground, palette.iconTint, 0.16f))
        }

        syncBtn.setOnClickListener { startSyncExams() }
        addBtn.setOnClickListener { showAddExamDialog() }
    }

    private fun loadLocalData() {
        allExams.clear()
        allExams.addAll(withAccountData { ExamStore.load(it) }.orEmpty())
        renderExams()
    }

    private fun renderExams() {
        allExams.sortBy { if (it.timestamp > 0) it.timestamp else Long.MAX_VALUE }

        val now = System.currentTimeMillis()
        val upcoming = allExams.filter { it.timestamp == 0L || it.timestamp > now }
        val nextExam = allExams.filter { it.timestamp > now }.minByOrNull { it.timestamp }

        if (nextExam != null) {
            val diff = nextExam.timestamp - now
            val days = diff / (1000 * 60 * 60 * 24)
            val hours = (diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)

            countdownTitle.text = String.format(Locale.CHINA, getString(R.string.exam_countdown_next), nextExam.courseName)
            countdownText.text = if (days > 0) {
                String.format(Locale.CHINA, getString(R.string.exam_countdown_days), days, hours)
            } else {
                val mins = (diff % (1000 * 60 * 60)) / (1000 * 60)
                String.format(Locale.CHINA, getString(R.string.exam_countdown_hours), hours, mins)
            }
            countdownDetail.text = "${nextExam.examTimeText} · ${nextExam.location} · ${nextExam.seatNumber}"
        } else {
            countdownTitle.text = "近期考试倒计时"
            if (allExams.isEmpty()) {
                countdownText.text = "暂无考试安排"
                countdownDetail.text = "点击下方同步或手动添加考试日程"
            } else {
                countdownText.text = "全部考试已结束 🎉"
                countdownDetail.text = "本学期安排的 ${allExams.size} 门考试均已完成"
            }
        }

        if (allExams.isEmpty()) {
            emptyHint.visibility = View.VISIBLE
            listContainer.visibility = View.GONE
        } else {
            emptyHint.visibility = View.GONE
            listContainer.visibility = View.VISIBLE
            renderExamList(allExams)
        }
    }

    private fun renderExamList(exams: List<ExamItem>) {
        listContainer.removeAllViews()

        for (exam in exams) {
            val isPast = exam.timestamp in 1 until System.currentTimeMillis()

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
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
                if (isPast) alpha = 0.55f
            }

            // 顶行：课程名与状态标签
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val nameView = TextView(this).apply {
                text = exam.courseName
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(palette.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val badgeView = TextView(this).apply {
                text = if (isPast) "已结束" else exam.examType
                textSize = 12f
                setPadding(16, 6, 16, 6)
                val badgeColor = if (isPast) Color.GRAY else palette.iconTint
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8f
                    setColor(ColorUtils.setAlphaComponent(badgeColor, 35))
                }
                setTextColor(badgeColor)
            }

            topRow.addView(nameView)
            topRow.addView(badgeView)
            card.addView(topRow)

            // 信息行：时间
            val timeView = TextView(this).apply {
                text = "🕒  ${if (exam.examTimeText.isNotBlank()) exam.examTimeText else "时间待定"}"
                textSize = 13f
                setTextColor(ColorUtils.blendARGB(palette.textPrimary, palette.pageBackground, 0.35f))
                setPadding(0, 10, 0, 4)
            }
            card.addView(timeView)

            // 地点与座位
            val placeText = buildString {
                if (exam.location.isNotBlank()) append("📍  ${exam.location}") else append("📍  地点待通知")
                if (exam.seatNumber.isNotBlank()) append("  ·  座位 ${exam.seatNumber}")
            }
            val placeView = TextView(this).apply {
                text = placeText
                textSize = 13f
                setTextColor(ColorUtils.blendARGB(palette.textPrimary, palette.pageBackground, 0.35f))
            }
            card.addView(placeView)

            // 长按删除操作
            card.setOnLongClickListener {
                showDeleteExamDialog(exam)
                true
            }

            listContainer.addView(card)
        }
    }

    private fun showDeleteExamDialog(exam: ExamItem) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("删除考试")
            .setMessage("确定从考程列表中移除「${exam.courseName}」吗？")
            .setPositiveButton("删除") { _, _ ->
                allExams.removeAll { it.id == exam.id }
                withAccountData { ExamStore.save(it, allExams) }
                renderExams()
            }
            .setNegativeButton("取消", null)
            .create()
        UiFeedback.styleDialogSurface(dialog, palette)
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { UiFeedback.styleDangerButton(it, palette) }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { UiFeedback.styleSecondaryButton(it, palette) }
    }

    private fun showAddExamDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_exam, null)
        val courseInput = view.findViewById<EditText>(R.id.inputExamCourse)
        val timeInput = view.findViewById<EditText>(R.id.inputExamTime)
        val locationInput = view.findViewById<EditText>(R.id.inputExamLocation)
        val seatInput = view.findViewById<EditText>(R.id.inputExamSeat)
        val typeInput = view.findViewById<EditText>(R.id.inputExamType)

        UiFeedback.styleInput(courseInput, palette)
        UiFeedback.styleInput(timeInput, palette)
        UiFeedback.styleInput(locationInput, palette)
        UiFeedback.styleInput(seatInput, palette)
        UiFeedback.styleInput(typeInput, palette)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.exam_dialog_title)
            .setView(view)
            .setPositiveButton("添加") { _, _ ->
                val course = courseInput.text.toString().trim()
                if (course.isBlank()) return@setPositiveButton
                val time = timeInput.text.toString().trim()
                val location = locationInput.text.toString().trim()
                val seat = seatInput.text.toString().trim()
                val type = typeInput.text.toString().trim().ifBlank { "期末考试" }

                val timestamp = runCatching {
                    val match = Regex("""\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}""").find(time)
                    if (match != null) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).parse(match.value)?.time ?: 0L
                    } else 0L
                }.getOrDefault(0L)

                val item = ExamItem(
                    id = UUID.randomUUID().toString(),
                    courseName = course,
                    examTimeText = time,
                    location = location,
                    seatNumber = seat,
                    examType = type,
                    timestamp = timestamp,
                    isCustom = true
                )
                allExams.add(item)
                withAccountData { ExamStore.save(it, allExams) }
                renderExams()
            }
            .setNegativeButton("取消", null)
            .create()
        UiFeedback.styleDialogSurface(dialog, palette)
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { UiFeedback.stylePrimaryButton(it, palette) }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { UiFeedback.styleSecondaryButton(it, palette) }
    }

    private fun startSyncExams() {
        if (!accountData.isCurrent) return
        if (!JwApiClient.canRestoreSession(this)) {
            promptNeedLogin()
            return
        }
        if (!syncBtn.isEnabled) return
        progressBar.visibility = View.VISIBLE
        syncBtn.isEnabled = false
        syncBtn.text = "正在验证校园会话…"
        lifecycleScope.launch {
            val valid = JwApiClient.ensureSession(this@ExamScheduleActivity)
            if (!valid) {
                progressBar.visibility = View.GONE
                syncBtn.isEnabled = true
                syncBtn.text = getString(R.string.exam_sync_button)
                promptNeedLogin()
                return@launch
            }
            JwApiClient.syncJarToWebView(this@ExamScheduleActivity)
            beginExamFetch()
        }
    }

    private fun beginExamFetch() {
        fetchFinished.set(false)
        capturedBuffer.clear()
        syncBtn.text = "正在同步考程…"

        mainHandler.postDelayed({
            if (!fetchFinished.get()) {
                onFetchQuietPeriodReached()
            }
        }, 22000L)

        syncWebView.loadUrl(JwEndpoints.EXAM_PAGE_URL)
    }

    private fun onFetchQuietPeriodReached() {
        if (!accountData.isCurrent || !fetchFinished.compareAndSet(false, true)) return
        val snapshot = synchronized(capturedBuffer) { capturedBuffer.toList() }
        val parsed = snapshot.flatMap { ExamScheduleParser.parse(it) }

        runOnUiThread {
            progressBar.visibility = View.GONE
            syncBtn.isEnabled = true
            syncBtn.text = getString(R.string.exam_sync_button)

            if (parsed.isNotEmpty()) {
                // 保留用户自定义的校外/自建考试
                val customs = allExams.filter { it.isCustom }
                allExams.clear()
                allExams.addAll(parsed)
                allExams.addAll(customs)
                withAccountData { ExamStore.save(it, allExams) }
                renderExams()
                UiFeedback.showMessage(listContainer, "已成功同步 ${parsed.size} 门考试安排", palette)
            } else if (snapshot.isNotEmpty()) {
                UiFeedback.showMessage(listContainer, "教务系统暂未发布考场安排", palette)
            } else {
                UiFeedback.showMessage(listContainer, "未能读取教务考试数据，请检查网络或重新登录", palette)
            }
        }
    }

    private fun promptNeedLogin() {
        AlertDialog.Builder(this)
            .setTitle(R.string.exam_schedule_title)
            .setMessage(R.string.gpa_import_need_login)
            .setPositiveButton(R.string.gpa_import_go_login) { _, _ ->
                startActivityForResult(Intent(this, LoginActivity::class.java), REQ_LOGIN)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (accountData.isCurrent && requestCode == REQ_LOGIN && resultCode == RESULT_OK) {
            startSyncExams()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupSyncWebView() {
        syncWebView = findViewById(R.id.examSyncWebView)
        syncWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = TpassConfig.USER_AGENT
        }
        syncWebView.addJavascriptInterface(ExamBridge(), "ExamScheduleBridge")
        syncWebView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (!accountData.isCurrent || fetchFinished.get() || !request.isForMainFrame) return null
                val url = request.url
                if (url.host != TpassConfig.IEDU_HOST || request.method != "GET") return null
                if (url.encodedPath?.endsWith(".do") != true) return null
                return runCatching { buildHookedExamDocument(url) }.getOrNull()
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
            }
        }
    }

    private fun buildHookedExamDocument(target: Uri): WebResourceResponse {
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
        if (!mimeType.contains("html", ignoreCase = true)) {
            return WebResourceResponse(mimeType, "UTF-8", body.inputStream())
        }
        var html = String(body, Charsets.UTF_8)
        if (!html.contains("__jluExamHooked")) {
            val script = "<script>$EXAM_HOOK_JS</script>"
            val headTag = Regex("(?i)<head[^>]*>").find(html)
            html = if (headTag != null) {
                StringBuilder(html).insert(headTag.range.last + 1, script).toString()
            } else {
                script + html
            }
        }
        return WebResourceResponse(mimeType, "UTF-8", html.byteInputStream())
    }

    private inner class ExamBridge {
        @JavascriptInterface
        fun onCaptured(url: String, payload: String) {
            if (!accountData.isCurrent) return
            if (ExamScheduleParser.isLikelyExamPayload(payload)) {
                synchronized(capturedBuffer) { capturedBuffer.add(payload) }
                mainHandler.removeCallbacksAndMessages(null)
                mainHandler.postDelayed({ onFetchQuietPeriodReached() }, 2500L)
            }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        syncWebView.apply {
            loadUrl("about:blank")
            onPause()
        }
        super.onDestroy()
    }

    companion object {
        private const val REQ_LOGIN = 202
        private val EXAM_HOOK_JS = """
            (function(){
              if(window.__jluExamHooked) return; window.__jluExamHooked = true;
              function send(url, text){
                try { ExamScheduleBridge.onCaptured(url, text); } catch(e) {}
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

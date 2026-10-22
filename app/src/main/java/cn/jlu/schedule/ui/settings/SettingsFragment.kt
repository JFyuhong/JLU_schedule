package cn.jlu.schedule.ui.settings

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cn.jlu.schedule.MainActivity
import cn.jlu.schedule.R
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.data.CustomBackgroundStorage
import cn.jlu.schedule.data.ImportedScheduleStorage
import cn.jlu.schedule.data.ScheduleRepository
import cn.jlu.schedule.data.SemesterStartDatePolicy
import cn.jlu.schedule.domain.SectionTimes
import cn.jlu.schedule.ui.theme.ThemePalette
import cn.jlu.schedule.ui.theme.BackgroundReadability
import cn.jlu.schedule.ui.theme.ThemePaletteProvider
import cn.jlu.schedule.ui.theme.UiFeedback
import cn.jlu.schedule.ui.theme.GlassSurface
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class SettingsFragment : Fragment() {
    private var pendingCropFile: File? = null
    private var accountCheckJob: Job? = null

    private val manageProfilesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            // 数据变化由 ScheduleRepository 状态流驱动，无需额外刷新
        }

    private val accountLoginLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                view?.let { refreshAccountCard(it) }
                UiFeedback.showMessage(view, "校园账号登录成功", paletteForFeedback())
            }
        }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            startCrop(uri)
        }
    }

    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            when (result.resultCode) {
                Activity.RESULT_OK -> {
                    val context = requireContext()
                    val file = requireNotNull(pendingCropFile) { "裁剪图片已丢失，请重新选择" }
                    val output = requireNotNull(result.data?.let(UCrop::getOutput)) { "裁剪未返回图片" }
                    require(output == FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取裁剪图片" }
                    val saved = CustomBackgroundStorage.commitCrop(context.filesDir, file)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", saved)
                    AppPreferences.setCustomBackgroundUri(context, uri.toString())
                    (activity as? MainActivity)?.refreshCustomBackground()
                    view?.let { refreshBackgroundPanels(it) }
                    UiFeedback.showMessage(view, "背景已更新", paletteForFeedback())
                }
                UCrop.RESULT_ERROR -> throw result.data?.let(UCrop::getError)
                    ?: IllegalStateException("无法裁剪所选图片")
                // Cancelled: keep the current background and its preference.
            }
        } catch (error: Exception) {
            Log.w("CustomBackground", "Could not save cropped background", error)
            UiFeedback.showMessage(view, "背景导入失败，请重新选择图片", paletteForFeedback())
        } finally {
            pendingCropFile?.delete()
            pendingCropFile = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filename = savedInstanceState?.getString("pending_crop_filename")
        if (filename != null && filename == File(filename).name) {
            pendingCropFile = File(requireContext().cacheDir, "background_crops/$filename")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingCropFile?.let { outState.putString("pending_crop_filename", it.name) }
    }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // 无论授权结果如何，开关状态已持久化；重新排程即可
            applyReminderSchedule()
        }

    private val exportTextLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) {
                writeExport(uri, backup = false)
            }
        }

    private val backupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                writeExport(uri, backup = true)
            }
        }

    private val restoreLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                confirmRestore(uri)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val palette = ThemePaletteProvider.fromContext(requireContext())
        refreshBackgroundPanels(view)

        listOf(
            view.findViewById<ImageView>(R.id.iconTimetable),
            view.findViewById<ImageView>(R.id.iconAccount),
            view.findViewById<ImageView>(R.id.iconAppearance),
            view.findViewById<ImageView>(R.id.iconReminder),
            view.findViewById<ImageView>(R.id.iconBackground),
            view.findViewById<ImageView>(R.id.iconData),
            view.findViewById<ImageView>(R.id.iconAbout),
        ).forEach { icon ->
            icon.imageTintList = ColorStateList.valueOf(palette.iconTint)
            icon.background = GlassSurface.drawable(icon.context, palette, GlassSurface.Variant.Control, 14f)
        }
        GlassSurface.apply(view.findViewById(R.id.accountStatusPanel), palette, GlassSurface.Variant.Control, 18f)

        // 可点击行
        val semesterStartDateText = view.findViewById<TextView>(R.id.semesterStartDateText)
        val reminderTimeText = view.findViewById<TextView>(R.id.reminderTimeText)
        val reminderSwitch = view.findViewById<SwitchCompat>(R.id.reminderSwitch)
        val showNonCurrentSwitch = view.findViewById<SwitchCompat>(R.id.showNonCurrentSwitch)

        view.findViewById<View>(R.id.rowManageTimetable).setOnClickListener {
            manageProfilesLauncher.launch(Intent(requireContext(), TimetableManageActivity::class.java))
        }

        // 校园账号卡片
        view.findViewById<View>(R.id.rowAccountLogin).setOnClickListener {
            accountLoginLauncher.launch(
                Intent(requireContext(), cn.jlu.schedule.ui.auth.LoginActivity::class.java)
                    .putExtra(cn.jlu.schedule.ui.auth.LoginActivity.EXTRA_EDIT_CREDENTIALS, true)
            )
        }
        view.findViewById<View>(R.id.rowAccountLogout).setOnClickListener {
            accountCheckJob?.cancel()
            view.findViewById<TextView>(R.id.accountStatusText).text = "正在退出…"
            view.findViewById<TextView>(R.id.accountIdentityText)
                .setText(R.string.settings_account_not_saved)
            cn.jlu.schedule.auth.JluCredentialStore.clear(requireContext())
            cn.jlu.schedule.remote.JwApiClient.clearSession(requireContext()) {
                if (isAdded && this.view === view) {
                    refreshAccountCard(view)
                    UiFeedback.showMessage(view, "已退出登录", paletteForFeedback())
                }
            }
        }
        view.findViewById<View>(R.id.rowSemesterStart).setOnClickListener {
            showSemesterDatePicker(semesterStartDateText)
        }
        view.findViewById<View>(R.id.rowChooseBackground).setOnClickListener {
            pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        view.findViewById<View>(R.id.rowClearBackground).setOnClickListener {
            AppPreferences.setCustomBackgroundUri(requireContext(), null)
            runCatching { File(requireContext().filesDir, "custom_background.jpg").delete() }
            (activity as? MainActivity)?.refreshCustomBackground()
            refreshBackgroundPanels(view)
            UiFeedback.showMessage(view, "已恢复默认背景", palette)
        }
        view.findViewById<View>(R.id.rowExportText).setOnClickListener {
            val dateLabel = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
            exportTextLauncher.launch("JLU课表_$dateLabel.txt")
        }
        view.findViewById<View>(R.id.rowBackup).setOnClickListener {
            val dateLabel = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
            backupLauncher.launch("JLU_schedule_backup_$dateLabel.json")
        }
        view.findViewById<View>(R.id.rowRestore).setOnClickListener {
            restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }

        // 检查新版本
        val checkUpdateRow = view.findViewById<View>(R.id.rowCheckUpdate)
        val versionText = view.findViewById<TextView>(R.id.updateVersionText)
        val currentVersionName = runCatching {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        }.getOrNull() ?: "未知版本"
        versionText.text = "v$currentVersionName"
        view.findViewById<TextView>(R.id.settingsFooterVersion).text = "JLU Schedule $currentVersionName"

        checkUpdateRow.setOnClickListener {
            versionText.text = getString(R.string.update_checking)
            checkUpdateRow.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val repo = cn.jlu.schedule.update.repository.UpdateRepository()
                val result = repo.checkUpdate(requireContext(), force = true)
                if (!isAdded) return@launch
                versionText.text = "v$currentVersionName"
                checkUpdateRow.isEnabled = true
                when (result) {
                    is cn.jlu.schedule.update.model.UpdateCheckResult.UpdateAvailable -> {
                        cn.jlu.schedule.update.ui.UpdateDialogHelper.showUpdateDialog(
                            activity = requireActivity(),
                            payload = result.payload,
                            preferredMirror = result.preferredMirror
                        )
                    }
                    is cn.jlu.schedule.update.model.UpdateCheckResult.NoUpdate -> {
                        UiFeedback.showMessage(
                            view,
                            result.reason.ifBlank { getString(R.string.update_is_latest) },
                            palette
                        )
                    }
                    is cn.jlu.schedule.update.model.UpdateCheckResult.MirrorMismatch -> {
                        androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle("更新警告")
                            .setMessage(result.message)
                            .setPositiveButton(R.string.close, null)
                            .show()
                    }
                    is cn.jlu.schedule.update.model.UpdateCheckResult.Error -> {
                        androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle("检查更新失败")
                            .setMessage(result.message)
                            .setPositiveButton(R.string.close, null)
                            .show()
                    }
                }
            }
        }

        // 分段选择器
        bindSegment(
            view.findViewById(R.id.defaultOpenGroup),
            palette,
            if (AppPreferences.getDefaultOpenPage(requireContext()) == AppPreferences.PAGE_TODAY) {
                R.id.defaultOpenToday
            } else {
                R.id.defaultOpenTimetable
            }
        ) { id ->
            val page = if (id == R.id.defaultOpenToday) AppPreferences.PAGE_TODAY else AppPreferences.PAGE_TIMETABLE
            AppPreferences.setDefaultOpenPage(requireContext(), page)
        }

        bindSegment(
            view.findViewById(R.id.themeGroup),
            palette,
            when (AppPreferences.getThemeColor(requireContext())) {
                AppPreferences.THEME_OCEAN -> R.id.themeOcean
                AppPreferences.THEME_MINT -> R.id.themeMint
                else -> R.id.themeWarm
            }
        ) { id ->
            AppPreferences.setThemeColor(
                requireContext(),
                when (id) {
                    R.id.themeOcean -> AppPreferences.THEME_OCEAN
                    R.id.themeMint -> AppPreferences.THEME_MINT
                    else -> AppPreferences.THEME_WARM
                }
            )
            // 主题色参与 Activity 主题属性，需重建生效
            activity?.recreate()
        }

        bindSegment(
            view.findViewById(R.id.darkModeGroup),
            palette,
            when (AppPreferences.getDarkMode(requireContext())) {
                AppPreferences.DARK_LIGHT -> R.id.darkModeLight
                AppPreferences.DARK_DARK -> R.id.darkModeDark
                else -> R.id.darkModeSystem
            }
        ) { id ->
            AppPreferences.setDarkMode(
                requireContext(),
                when (id) {
                    R.id.darkModeLight -> AppPreferences.DARK_LIGHT
                    R.id.darkModeDark -> AppPreferences.DARK_DARK
                    else -> AppPreferences.DARK_SYSTEM
                }
            )
            ThemePaletteProvider.applyNightMode(requireContext())
        }

        bindSegment(
            view.findViewById(R.id.fontScaleGroup),
            palette,
            when {
                AppPreferences.getTimetableFontScale(requireContext()) < 0.98f -> R.id.fontSmall
                AppPreferences.getTimetableFontScale(requireContext()) > 1.08f -> R.id.fontLarge
                else -> R.id.fontNormal
            }
        ) { id ->
            AppPreferences.setTimetableFontScale(
                requireContext(),
                when (id) {
                    R.id.fontSmall -> 0.95f
                    R.id.fontLarge -> 1.15f
                    else -> 1.0f
                }
            )
            activity?.recreate()
        }

        // 学期开始日期（Repository 驱动）
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ScheduleRepository.timetable.collect { data ->
                    if (data != null) {
                        semesterStartDateText.text =
                            data.semesterStart.format(DateTimeFormatter.ofPattern("yyyy/M/d"))
                    }
                }
            }
        }
        ScheduleRepository.refresh(requireContext())

        // 非本周课程开关
        showNonCurrentSwitch.isChecked = AppPreferences.isShowNonCurrentCourses(requireContext())
        showNonCurrentSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppPreferences.setShowNonCurrentCourses(requireContext(), isChecked)
            activity?.recreate()
        }

        // 每日提醒
        reminderSwitch.isChecked = AppPreferences.isReminderEnabled(requireContext())
        updateReminderTimeText(reminderTimeText)
        view.findViewById<View>(R.id.rowReminderTime).setOnClickListener {
            val minuteOfDay = AppPreferences.getReminderMinute(requireContext())
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    AppPreferences.setReminderMinute(requireContext(), hour * 60 + minute)
                    updateReminderTimeText(reminderTimeText)
                    applyReminderSchedule()
                },
                minuteOfDay / 60,
                minuteOfDay % 60,
                true
            ).show()
        }
        reminderSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppPreferences.setReminderEnabled(requireContext(), isChecked)
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                applyReminderSchedule()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            refreshAccountCard(it)
            refreshBackgroundPanels(it)
        }
    }

    private fun refreshBackgroundPanels(view: View) {
        val palette = ThemePaletteProvider.fromContext(view.context)
        // 卡片与分隔线按主题色板着色
        listOf(
            view.findViewById<LinearLayout>(R.id.settingsCardTimetable),
            view.findViewById<LinearLayout>(R.id.settingsCardAccount),
            view.findViewById<LinearLayout>(R.id.settingsCardAppearance),
            view.findViewById<LinearLayout>(R.id.settingsCardReminder),
            view.findViewById<LinearLayout>(R.id.settingsCardBackground),
            view.findViewById<LinearLayout>(R.id.settingsCardData),
            view.findViewById<LinearLayout>(R.id.settingsCardAbout),
        ).forEach { card ->
            GlassSurface.apply(card, palette, GlassSurface.Variant.Panel, 24f)
        }
        BackgroundReadability.protectPanel(view.findViewById(R.id.settingsHeader), palette)
        view.findViewById<TextView>(R.id.settingsFooterVersion).apply {
            BackgroundReadability.protectPanel(this, palette, radiusDp = 12f)
            val padding = (8 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
    }

    override fun onDestroyView() {
        accountCheckJob?.cancel()
        accountCheckJob = null
        super.onDestroyView()
    }

    private fun refreshAccountCard(view: View) {
        accountCheckJob?.cancel()
        val context = view.context
        val studentId = cn.jlu.schedule.auth.JluCredentialStore.studentId(context)
        view.findViewById<TextView>(R.id.accountStatusText).text = "正在验证…"
        view.findViewById<TextView>(R.id.accountIdentityText).text = if (studentId == null) {
            getString(R.string.settings_account_not_saved)
        } else {
            getString(R.string.settings_account_saved, studentId)
        }
        view.findViewById<TextView>(R.id.accountLoginLabel).text =
            getString(if (studentId == null) R.string.settings_account_login else R.string.settings_account_relogin)
        accountCheckJob = viewLifecycleOwner.lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) {
                cn.jlu.schedule.remote.JwApiClient.checkIeduSession(context)
            }
            if (this@SettingsFragment.view !== view) return@launch
            view.findViewById<TextView>(R.id.accountStatusText).text = when (status) {
                cn.jlu.schedule.remote.JwApiClient.SessionStatus.VALID -> "已登录"
                cn.jlu.schedule.remote.JwApiClient.SessionStatus.EXPIRED -> "登录已过期"
                cn.jlu.schedule.remote.JwApiClient.SessionStatus.UNAVAILABLE -> "暂时无法连接服务器"
                cn.jlu.schedule.remote.JwApiClient.SessionStatus.CERTIFICATE_ERROR -> "证书校验失败"
            }
        }
    }

    private fun paletteForFeedback(): ThemePalette = ThemePaletteProvider.fromContext(requireContext())

    private fun bindSegment(
        container: LinearLayout,
        palette: ThemePalette,
        selectedId: Int,
        onSelected: (Int) -> Unit
    ) {
        // 选中状态保存在闭包内并即时重绘：不依赖页面重建（"启动默认打开"等
        // 不触发 recreate 的选项，以及 NightMode 视觉不变时的模式切换）
        var currentSelection = selectedId
        val density = resources.displayMetrics.density
        container.background = GradientDrawable().apply {
            cornerRadius = 12f * density
            setColor(if (palette.isDark) palette.panelAltBackground else palette.panelBackground)
        }

        fun restyle() {
            val count = container.childCount
            for (i in 0 until count) {
                val child = container.getChildAt(i) as? TextView ?: continue
                val checked = child.id == currentSelection
                child.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 9f * density
                    setColor(if (checked) palette.buttonBackground else Color.TRANSPARENT)
                }
                child.setTextColor(if (checked) palette.buttonText else palette.textPrimary)
                child.isSelected = checked
            }
        }

        restyle()
        val count = container.childCount
        for (i in 0 until count) {
            val child = container.getChildAt(i) as? TextView ?: continue
            child.setOnClickListener {
                if (child.id != currentSelection) {
                    currentSelection = child.id
                    restyle()
                    onSelected(child.id)
                }
            }
        }
    }

    private fun updateReminderTimeText(textView: TextView) {
        val minuteOfDay = AppPreferences.getReminderMinute(requireContext())
        textView.text = String.format(Locale.getDefault(), "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)
    }

    private fun applyReminderSchedule() {
        val context = context ?: return
        cn.jlu.schedule.reminder.ReminderScheduler.reschedule(context)
    }

    private fun writeExport(uri: Uri, backup: Boolean) {
        val anchor = view
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val content = if (backup) {
                        ImportedScheduleStorage.exportBackup(requireContext().filesDir)
                    } else {
                        ImportedScheduleStorage.exportActiveTimetableText(
                            requireContext().filesDir,
                            SectionTimes.DEFAULT_RANGES
                        )
                    }
                    requireContext().contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(content.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("无法打开输出位置")
                }
            }
            withContext(Dispatchers.Main) {
                val palette = ThemePaletteProvider.fromContext(requireContext())
                when {
                    result.isSuccess && backup ->
                        UiFeedback.showMessage(anchor, getString(R.string.settings_backup_success), palette)
                    result.isSuccess ->
                        UiFeedback.showMessage(anchor, getString(R.string.settings_export_success), palette)
                    backup ->
                        UiFeedback.showMessage(anchor, getString(R.string.settings_backup_failed), palette)
                    else ->
                        UiFeedback.showMessage(anchor, getString(R.string.settings_export_failed), palette)
                }
            }
        }
    }

    private fun confirmRestore(uri: Uri) {
        val ctx = context ?: return
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.settings_restore))
            .setMessage(getString(R.string.settings_restore_confirm))
            .setPositiveButton(getString(R.string.action_save)) { _, _ ->
                val anchor = view
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = runCatching {
                        val content = withContext(Dispatchers.IO) {
                            ctx.contentResolver.openInputStream(uri)?.use { stream ->
                                stream.readBytes().toString(Charsets.UTF_8)
                            } ?: throw IllegalStateException("无法读取文件")
                        }
                        ScheduleRepository.restoreBackup(ctx, content).getOrThrow()
                    }
                    withContext(Dispatchers.Main) {
                        val palette = ThemePaletteProvider.fromContext(ctx)
                        result.onSuccess { count ->
                            UiFeedback.showMessage(anchor, getString(R.string.settings_restore_success, count), palette)
                        }.onFailure {
                            UiFeedback.showMessage(anchor, getString(R.string.settings_restore_failed), palette)
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .create()
            .also { dialog -> dialog.show() }
    }

    private fun showSemesterDatePicker(dateTextView: TextView) {
        val ctx = context ?: return
        val owner = viewLifecycleOwner
        val current = ScheduleRepository.timetable.value?.semesterStart
            ?: ImportedScheduleStorage.getActiveSemesterStartDate(ctx.filesDir)
        val dateFormat = DateTimeFormatter.ofPattern("yyyy/M/d")

        fun saveDate(date: LocalDate) {
            owner.lifecycleScope.launch {
                ScheduleRepository.setActiveSemesterStartDate(ctx, date)
                    .onSuccess {
                        dateTextView.text = date.format(dateFormat)
                        UiFeedback.showMessage(
                            dateTextView,
                            getString(R.string.settings_first_week_saved, date.format(dateFormat)),
                            paletteForFeedback()
                        )
                    }
                    .onFailure {
                        UiFeedback.showMessage(
                            dateTextView,
                            getString(R.string.settings_first_week_save_failed),
                            paletteForFeedback()
                        )
                    }
            }
        }

        DatePickerDialog(
            ctx,
            { _, year, month, dayOfMonth ->
                val selected = LocalDate.of(year, month + 1, dayOfMonth)
                val monday = SemesterStartDatePolicy.normalizeToWeekStart(selected)
                if (selected != monday) {
                    androidx.appcompat.app.AlertDialog.Builder(ctx)
                        .setTitle(R.string.settings_first_week_adjust_title)
                        .setMessage(getString(
                            R.string.settings_first_week_adjust_message,
                            selected.format(dateFormat),
                            monday.format(dateFormat)
                        ))
                        .setPositiveButton(R.string.settings_first_week_adjust_save) { _, _ -> saveDate(monday) }
                        .setNegativeButton(R.string.action_cancel, null)
                        .show()
                } else {
                    saveDate(monday)
                }
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth
        ).apply { setTitle(R.string.settings_row_first_week) }.show()
    }

    private fun startCrop(sourceUri: Uri) {
        runCatching {
            val context = requireContext()
            pendingCropFile?.delete()
            val outputFile = CustomBackgroundStorage.createCropFile(context.cacheDir)
            pendingCropFile = outputFile
            val destinationUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
            val options = UCrop.Options().apply {
                setFreeStyleCropEnabled(true)
                setHideBottomControls(false)
            }
            val cropIntent = UCrop.of(sourceUri, destinationUri)
                .withMaxResultSize(2160, 3840)
                .withOptions(options)
                .getIntent(context)
                .setClass(context, BackgroundCropActivity::class.java)
            cropLauncher.launch(cropIntent)
        }.onFailure { error ->
            pendingCropFile?.delete()
            pendingCropFile = null
            Log.w("CustomBackground", "Could not open image cropper", error)
            UiFeedback.showMessage(view, "无法打开图片裁剪，请重新选择图片", paletteForFeedback())
        }
    }
}

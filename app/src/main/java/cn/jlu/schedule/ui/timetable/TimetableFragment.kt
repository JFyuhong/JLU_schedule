package cn.jlu.schedule.ui.timetable

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import cn.jlu.schedule.R
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.data.ImportedScheduleStorage
import cn.jlu.schedule.data.ScheduleRepository
import cn.jlu.schedule.domain.SectionTimes
import cn.jlu.schedule.model.WeekParity
import cn.jlu.schedule.model.Weekday
import cn.jlu.schedule.remote.JwApiClient
import cn.jlu.schedule.ui.auth.LoginActivity
import cn.jlu.schedule.ui.importer.ImportBrowserActivity
import cn.jlu.schedule.ui.importer.QuickImportActivity
import cn.jlu.schedule.ui.theme.ThemePaletteProvider
import cn.jlu.schedule.ui.theme.BackgroundReadability
import cn.jlu.schedule.ui.theme.UiFeedback
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class TimetableFragment : Fragment() {
    private val weekdayLabels = mapOf(
        Weekday.MONDAY to "一",
        Weekday.TUESDAY to "二",
        Weekday.WEDNESDAY to "三",
        Weekday.THURSDAY to "四",
        Weekday.FRIDAY to "五",
        Weekday.SATURDAY to "六",
        Weekday.SUNDAY to "日"
    )

    private lateinit var weekText: TextView
    private lateinit var dateText: TextView
    private lateinit var weekPager: ViewPager2
    private lateinit var addCourseButton: ImageButton
    private lateinit var importScheduleButton: ImageButton
    private var currentWeekIndex: Int = 1
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null
    private var pageChangeRegistered: Boolean = false
    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        // 数据变化由 ScheduleRepository 状态流驱动
    }

    private val quickImportLoginLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && isAdded) {
                UiFeedback.showMessage(
                    view,
                    "登录成功，开始一键导入",
                    ThemePaletteProvider.fromContext(requireContext())
                )
                startQuickImport()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_timetable, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        weekText = view.findViewById(R.id.currentWeekText)
        dateText = view.findViewById(R.id.currentDateText)
        weekPager = view.findViewById(R.id.weekPager)
        addCourseButton = view.findViewById(R.id.addCourseButton)
        importScheduleButton = view.findViewById(R.id.importScheduleButton)

        val palette = ThemePaletteProvider.fromContext(requireContext())
        BackgroundReadability.protectPanel(
            view.findViewById(R.id.timetableHeader), palette, radiusDp = 0f
        )
        addCourseButton.imageTintList = ColorStateList.valueOf(palette.iconTint)
        importScheduleButton.imageTintList = ColorStateList.valueOf(palette.iconTint)
        addCourseButton.backgroundTintList = ColorStateList.valueOf(palette.buttonBackground)
        importScheduleButton.backgroundTintList = ColorStateList.valueOf(palette.buttonBackground)

        observeTimetable()

        importScheduleButton.setOnClickListener {
            showImportSourceDialog()
        }

        addCourseButton.setOnClickListener {
            showManualAddDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        ScheduleRepository.refresh(requireContext())
    }

    override fun onDestroyView() {
        pageChangeCallback?.let { callback ->
            if (pageChangeRegistered) {
                runCatching { weekPager.unregisterOnPageChangeCallback(callback) }
                pageChangeRegistered = false
            }
        }
        pageChangeCallback = null
        super.onDestroyView()
    }

    private fun observeTimetable() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ScheduleRepository.timetable.collect { data ->
                    if (data != null) {
                        renderTimetable(data)
                    }
                }
            }
        }
    }

    private var currentUiData: ScheduleRepository.TimetableUiData? = null

    private fun renderTimetable(data: ScheduleRepository.TimetableUiData) {
        if (!isAdded || view == null) {
            return
        }
        currentUiData = data
        val ctx = requireContext()
        pageChangeCallback?.let { callback ->
            if (pageChangeRegistered) {
                runCatching { weekPager.unregisterOnPageChangeCallback(callback) }
                pageChangeRegistered = false
            }
        }

        val fontScale = AppPreferences.getTimetableFontScale(ctx)
        val palette = ThemePaletteProvider.fromContext(ctx)
        val hasCustomBackground = !AppPreferences.getCustomBackgroundUri(ctx).isNullOrBlank()
        val showNonCurrent = AppPreferences.isShowNonCurrentCourses(ctx)

        currentWeekIndex = data.currentWeek
        val today = LocalDate.now()
        val currentSection = WeekTimetableRenderer.resolveCurrentSection(SectionTimes.DEFAULT_RANGES)
        val adapter = WeekPagerAdapter(
            courses = data.courses,
            totalWeeks = data.totalWeeks,
            periodRanges = SectionTimes.DEFAULT_RANGES,
            weekdayLabels = weekdayLabels,
            today = today,
            currentSection = currentSection,
            semesterStart = data.semesterStart,
            baseWeek = currentWeekIndex,
            showNonCurrent = showNonCurrent,
            fontScale = fontScale,
            palette = palette,
            hasCustomBackground = hasCustomBackground,
            onCourseClick = { primary, allItems ->
                if (isAdded && !parentFragmentManager.isStateSaved) {
                    runCatching {
                        CourseDetailBottomSheet.show(
                            context = requireContext(),
                            primary = primary,
                            allCourses = allItems,
                            periodRanges = SectionTimes.DEFAULT_RANGES,
                            onCoverPinned = {
                                currentUiData?.let { renderTimetable(it) }
                            },
                            onDeleteCourse = { selected ->
                                viewLifecycleOwner.lifecycleScope.launch {
                                    val result = ScheduleRepository.deleteCourse(
                                        requireContext(), selected.courseIndex, selected.course
                                    )
                                    if (!isAdded) return@launch
                                    val message = when {
                                        result.getOrNull() == true -> getString(R.string.course_detail_deleted)
                                        result.getOrNull() == false -> getString(R.string.course_detail_delete_failed)
                                        else -> result.exceptionOrNull()?.message ?: getString(R.string.course_detail_delete_failed)
                                    }
                                    UiFeedback.showMessage(view, message, ThemePaletteProvider.fromContext(requireContext()))
                                }
                            }
                        )
                    }
                }
            }
        )
        // 保留用户当前浏览的周，避免数据刷新时被强制跳回本周
        val previousPosition = weekPager.adapter?.let { weekPager.currentItem } ?: -1
        weekPager.adapter = adapter
        val targetPosition = if (previousPosition >= 0) {
            previousPosition.coerceAtMost(data.totalWeeks - 1)
        } else {
            currentWeekIndex - 1
        }
        weekPager.setCurrentItem(targetPosition, false)
        updateHeader(targetPosition + 1)

        val callback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateHeader(position + 1)
            }
        }
        pageChangeCallback = callback
        weekPager.registerOnPageChangeCallback(callback)
        pageChangeRegistered = true
    }

    private fun showImportSourceDialog() {
        val ctx = requireContext()
        val palette = ThemePaletteProvider.fromContext(ctx)
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(34, 30, 34, 20)
        }
        val quickImport = Button(ctx).apply { text = getString(R.string.import_quick) }
        val inCampus = Button(ctx).apply { text = "我在校内" }
        val offCampus = Button(ctx).apply { text = "我在校外" }
        val cancel = Button(ctx).apply { text = "取消" }
        UiFeedback.stylePrimaryButton(quickImport, palette)
        UiFeedback.styleSecondaryButton(inCampus, palette)
        UiFeedback.styleSecondaryButton(offCampus, palette)
        UiFeedback.styleSecondaryButton(cancel, palette)
        panel.addView(quickImport)
        panel.addView(inCampus, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 10 })
        panel.addView(offCampus, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 10 })
        panel.addView(cancel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 10 })

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("选择导入网络")
            .setView(panel)
            .create()
        dialog.show()
        UiFeedback.styleDialogSurface(dialog, palette)

        quickImport.setOnClickListener {
            dialog.dismiss()
            startQuickImport()
        }
        inCampus.setOnClickListener {
            dialog.dismiss()
            launchImportForAsset("target.url")
        }
        offCampus.setOnClickListener {
            dialog.dismiss()
            launchImportForAsset("VPN.url")
        }
        cancel.setOnClickListener {
            dialog.dismiss()
        }
    }

    /** 一键导入：复用内置浏览器登录 Cookie，隐藏 WebView 自动获取课表并入库 */
    private fun startQuickImport() {
        val ctx = requireContext()
        val webViewSession = runCatching {
            android.webkit.CookieManager.getInstance()
                .getCookie(cn.jlu.schedule.auth.TpassConfig.IEDU_PORTAL_URL)
        }.getOrNull()?.isNotBlank() == true
        if (!webViewSession && !JwApiClient.canRestoreSession(ctx)) {
            UiFeedback.showMessage(view, getString(R.string.import_quick_need_login), ThemePaletteProvider.fromContext(ctx))
            quickImportLoginLauncher.launch(Intent(ctx, LoginActivity::class.java))
            return
        }
        val palette = ThemePaletteProvider.fromContext(ctx)
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(34, 30, 34, 20)
        }
        val overwrite = Button(ctx).apply { text = "覆盖当前课表" }
        val createNew = Button(ctx).apply { text = "新建课表导入" }
        val cancel = Button(ctx).apply { text = "取消" }
        UiFeedback.stylePrimaryButton(overwrite, palette)
        UiFeedback.styleSecondaryButton(createNew, palette)
        UiFeedback.styleSecondaryButton(cancel, palette)
        panel.addView(overwrite)
        panel.addView(createNew, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 10 })
        panel.addView(cancel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 10 })
        val modeDialog = AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.import_quick))
            .setView(panel)
            .create()
        modeDialog.show()
        UiFeedback.styleDialogSurface(modeDialog, palette)

        overwrite.setOnClickListener {
            modeDialog.dismiss()
            launchQuickImport(createNew = false, newName = null)
        }
        createNew.setOnClickListener {
            modeDialog.dismiss()
            promptQuickImportName()
        }
        cancel.setOnClickListener { modeDialog.dismiss() }
    }

    private val quickImportLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            // 数据刷新由 ScheduleRepository 状态流驱动
        }

    private fun launchQuickImport(createNew: Boolean, newName: String?) {
        val intent = Intent(requireContext(), QuickImportActivity::class.java)
            .putExtra(QuickImportActivity.EXTRA_CREATE_NEW, createNew)
            .putExtra(QuickImportActivity.EXTRA_NEW_NAME, newName)
        quickImportLauncher.launch(intent)
    }

    private fun promptQuickImportName() {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            setSingleLine(true)
            hint = "新课表名称"
        }
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(34, 24, 34, 0)
            addView(input)
        }
        val dialog = AlertDialog.Builder(ctx)
            .setTitle("新建课表导入")
            .setView(panel)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        UiFeedback.styleDialogSurface(dialog, ThemePaletteProvider.fromContext(ctx))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                input.error = "课表名不能为空"
                return@setOnClickListener
            }
            dialog.dismiss()
            launchQuickImport(createNew = true, newName = name)
        }
    }

    private fun launchImportForAsset(assetFile: String) {
        val intent = Intent(requireContext(), ImportBrowserActivity::class.java).apply {
            putExtra(ImportBrowserActivity.EXTRA_URL_ASSET, assetFile)
        }
        importLauncher.launch(intent)
    }

    private fun updateHeader(week: Int) {
        val today = LocalDate.now()
        val weekDay = weekdayLabels[weekdayFromJava(today.dayOfWeek.value)] ?: "一"
        weekText.text = if (week == currentWeekIndex) {
            "第${week}周 周${weekDay}"
        } else {
            "第${week}周（非本周）"
        }
        dateText.text = today.format(DateTimeFormatter.ofPattern("yyyy/M/d"))
    }

    private fun showManualAddDialog() {
        val ctx = requireContext()
        val formView = layoutInflater.inflate(R.layout.dialog_add_course, null)
        val nameInput = formView.findViewById<EditText>(R.id.inputCourseName)
        val teacherInput = formView.findViewById<EditText>(R.id.inputTeacher)
        val locationInput = formView.findViewById<EditText>(R.id.inputLocation)
        val weekdaySpinner = formView.findViewById<Spinner>(R.id.inputWeekday)
        val startSectionInput = formView.findViewById<EditText>(R.id.inputStartSection)
        val endSectionInput = formView.findViewById<EditText>(R.id.inputEndSection)
        val startWeekInput = formView.findViewById<EditText>(R.id.inputStartWeek)
        val endWeekInput = formView.findViewById<EditText>(R.id.inputEndWeek)
        val parityGroup = formView.findViewById<RadioGroup>(R.id.inputParityGroup)

        // 面板与输入框按当前主题着色
        val dialogPalette = ThemePaletteProvider.fromContext(ctx)
        formView.findViewById<LinearLayout>(R.id.addCoursePanel).background =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f
                setColor(dialogPalette.panelAltBackground)
                setStroke(2, ColorUtils.blendARGB(dialogPalette.panelAltBackground, dialogPalette.iconTint, 0.22f))
            }
        // 每个输入框必须持有独立的 GradientDrawable 实例：共享实例会在首帧后被
        // 最后一个不同尺寸的视图改写 bounds，聚焦重绘时边框按过期尺寸渲染（框变短）
        listOf(
            nameInput, teacherInput, locationInput,
            startSectionInput, endSectionInput, startWeekInput, endWeekInput, weekdaySpinner
        ).forEach { input ->
            input.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f
                setColor(dialogPalette.panelBackground)
                setStroke(2, ColorUtils.blendARGB(dialogPalette.panelBackground, dialogPalette.iconTint, 0.18f))
            }
        }

        val weekdayItems = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        weekdaySpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, weekdayItems)

        val guessWeek = currentWeekIndex.coerceAtLeast(1)
        startSectionInput.setText("1")
        endSectionInput.setText("2")
        startWeekInput.setText(String.format(Locale.getDefault(), "%d", guessWeek))
        endWeekInput.setText(String.format(Locale.getDefault(), "%d", guessWeek))

        val cancelButton = formView.findViewById<android.widget.Button>(R.id.cancelManualAddButton)
        val saveButton = formView.findViewById<android.widget.Button>(R.id.saveManualAddButton)

        val dialog = AlertDialog.Builder(ctx)
            .setView(formView)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            UiFeedback.styleSecondaryButton(cancelButton, dialogPalette)
            UiFeedback.stylePrimaryButton(saveButton, dialogPalette)
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        saveButton.setOnClickListener {
            val name = nameInput.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                nameInput.error = "课程名不能为空"
                UiFeedback.showMessage(view, "课程名不能为空", ThemePaletteProvider.fromContext(ctx))
                return@setOnClickListener
            }
            if (name.length > MAX_MANUAL_COURSE_NAME_LENGTH) {
                nameInput.error = "课程名过长"
                UiFeedback.showMessage(view, "课程名最多 ${MAX_MANUAL_COURSE_NAME_LENGTH} 个字符", ThemePaletteProvider.fromContext(ctx))
                return@setOnClickListener
            }

            val teacher = teacherInput.text?.toString()?.trim().orEmpty()
            if (teacher.length > MAX_MANUAL_TEXT_LENGTH) {
                teacherInput.error = "教师名称过长"
                UiFeedback.showMessage(view, "教师名称最多 ${MAX_MANUAL_TEXT_LENGTH} 个字符", ThemePaletteProvider.fromContext(ctx))
                return@setOnClickListener
            }
            val location = locationInput.text?.toString()?.trim().orEmpty()
            if (location.length > MAX_MANUAL_TEXT_LENGTH) {
                locationInput.error = "地点过长"
                UiFeedback.showMessage(view, "地点最多 ${MAX_MANUAL_TEXT_LENGTH} 个字符", ThemePaletteProvider.fromContext(ctx))
                return@setOnClickListener
            }

            val startSection = parseBoundedInt(startSectionInput, 1, 12, "开始节")
                ?: return@setOnClickListener
            val endSection = parseBoundedInt(endSectionInput, 1, 12, "结束节")
                ?: return@setOnClickListener
            if (endSection < startSection) {
                endSectionInput.error = "结束节不能小于开始节"
                UiFeedback.showMessage(view, "结束节不能小于开始节", ThemePaletteProvider.fromContext(ctx))
                return@setOnClickListener
            }

            val startWeek = parseBoundedInt(startWeekInput, 1, 30, "开始周")
                ?: return@setOnClickListener
            val endWeek = parseBoundedInt(endWeekInput, 1, 30, "结束周")
                ?: return@setOnClickListener
            if (endWeek < startWeek) {
                endWeekInput.error = "结束周不能小于开始周"
                UiFeedback.showMessage(view, "结束周不能小于开始周", ThemePaletteProvider.fromContext(ctx))
                return@setOnClickListener
            }
            val weekday = when (weekdaySpinner.selectedItemPosition) {
                0 -> Weekday.MONDAY
                1 -> Weekday.TUESDAY
                2 -> Weekday.WEDNESDAY
                3 -> Weekday.THURSDAY
                4 -> Weekday.FRIDAY
                5 -> Weekday.SATURDAY
                else -> Weekday.SUNDAY
            }
            val parity = when (parityGroup.checkedRadioButtonId) {
                R.id.parityOdd -> WeekParity.ODD
                R.id.parityEven -> WeekParity.EVEN
                else -> WeekParity.ALL
            }

            // 防抖：保存开始后立即禁用按钮，避免双击重复添加
            saveButton.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val result = ScheduleRepository.addManualCourse(
                    ctx,
                    ImportedScheduleStorage.ManualCourseInput(
                        courseName = name,
                        teacher = teacher,
                        location = location,
                        weekday = weekday,
                        startSection = startSection,
                        endSection = endSection,
                        startWeek = startWeek,
                        endWeek = endWeek,
                        parity = parity
                    )
                )
                result.onSuccess {
                    UiFeedback.showMessage(view, "已添加到当前课表", ThemePaletteProvider.fromContext(ctx))
                    dialog.dismiss()
                }.onFailure { error ->
                    saveButton.isEnabled = true
                    UiFeedback.showMessage(view, error.message ?: "添加失败", ThemePaletteProvider.fromContext(ctx))
                }
            }
        }
        dialog.show()
    }

    private fun parseBoundedInt(input: EditText, min: Int, max: Int, label: String): Int? {
        val raw = input.text?.toString()?.trim().orEmpty()
        val value = raw.toIntOrNull()
        if (value == null) {
            input.error = "${label}必须是数字"
            UiFeedback.showMessage(view, "${label}必须是数字", ThemePaletteProvider.fromContext(requireContext()))
            return null
        }
        if (value !in min..max) {
            input.error = "${label}范围是 ${min}-${max}"
            UiFeedback.showMessage(view, "${label}范围是 ${min}-${max}", ThemePaletteProvider.fromContext(requireContext()))
            return null
        }
        input.error = null
        return value
    }

    private fun weekdayFromJava(dayValue: Int): Weekday {
        return when (dayValue) {
            1 -> Weekday.MONDAY
            2 -> Weekday.TUESDAY
            3 -> Weekday.WEDNESDAY
            4 -> Weekday.THURSDAY
            5 -> Weekday.FRIDAY
            6 -> Weekday.SATURDAY
            else -> Weekday.SUNDAY
        }
    }

    companion object {
        private const val MAX_MANUAL_COURSE_NAME_LENGTH = 40
        private const val MAX_MANUAL_TEXT_LENGTH = 40
    }
}

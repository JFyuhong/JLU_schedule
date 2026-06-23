package cn.jlu.schedule.ui.timetable

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
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
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import cn.jlu.schedule.R
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.data.ImportedScheduleStorage
import cn.jlu.schedule.domain.WeekScheduleCalculator
import cn.jlu.schedule.model.WeekParity
import cn.jlu.schedule.model.Weekday
import cn.jlu.schedule.ui.importer.ImportBrowserActivity
import cn.jlu.schedule.ui.theme.ThemePaletteProvider
import cn.jlu.schedule.ui.theme.UiFeedback
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class TimetableFragment : Fragment() {
    private val periodTimeRanges = listOf(
        "08:00-08:45",
        "08:45-09:40",
        "10:00-10:45",
        "10:45-11:40",
        "13:30-14:15",
        "14:15-15:10",
        "15:30-16:15",
        "16:15-17:10",
        "18:20-19:05",
        "19:05-19:50",
        "20:00-20:45",
        "20:45-21:30"
    )

    private val weekdayLabels = mapOf(
        Weekday.MONDAY to "一",
        Weekday.TUESDAY to "二",
        Weekday.WEDNESDAY to "三",
        Weekday.THURSDAY to "四",
        Weekday.FRIDAY to "五",
        Weekday.SATURDAY to "六",
        Weekday.SUNDAY to "日"
    )

    private val cardColors = intArrayOf(
        0xFFFAD8C0.toInt(),
        0xFFC9E7FF.toInt(),
        0xFFD8F4D2.toInt(),
        0xFFFFE6A8.toInt(),
        0xFFE6D7FF.toInt(),
        0xFFFFD7E0.toInt(),
        0xFFD8F0EE.toInt(),
        0xFFFFE1C4.toInt()
    )

    private lateinit var weekText: TextView
    private lateinit var dateText: TextView
    private lateinit var weekPager: ViewPager2
    private lateinit var addCourseButton: ImageButton
    private lateinit var importScheduleButton: ImageButton
    private var currentWeekIndex: Int = 1
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null
    private var pageChangeRegistered: Boolean = false
    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            refreshTimetable()
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
        addCourseButton.imageTintList = ColorStateList.valueOf(palette.iconTint)
        importScheduleButton.imageTintList = ColorStateList.valueOf(palette.iconTint)

        refreshTimetable()

        importScheduleButton.setOnClickListener {
            showImportSourceDialog()
        }

        addCourseButton.setOnClickListener {
            showManualAddDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTimetable()
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

    private fun refreshTimetable() {
        if (!isAdded || view == null) {
            return
        }
        val ctx = requireContext()
        pageChangeCallback?.let { callback ->
            if (pageChangeRegistered) {
                runCatching { weekPager.unregisterOnPageChangeCallback(callback) }
                pageChangeRegistered = false
            }
        }

        val courses = runCatching { ImportedScheduleStorage.loadCoursesOrSampleAsset(ctx, "sample_schedule.do") }
            .getOrElse { emptyList() }
        val semesterStart = ImportedScheduleStorage.getActiveSemesterStartDate(ctx)
        val fontScale = AppPreferences.getTimetableFontScale(ctx)
        val theme = AppPreferences.getThemeColor(ctx)
        val hasCustomBackground = !AppPreferences.getCustomBackgroundUri(ctx).isNullOrBlank()
        val showNonCurrent = AppPreferences.isShowNonCurrentCourses(ctx)

        val totalWeeks = WeekScheduleCalculator.totalWeeks(courses)
        currentWeekIndex = guessCurrentWeek(totalWeeks, semesterStart)
        val today = LocalDate.now()
        val currentSection = WeekTimetableRenderer.resolveCurrentSection(periodTimeRanges)
        val adapter = WeekPagerAdapter(
            courses = courses,
            totalWeeks = totalWeeks,
            periodRanges = periodTimeRanges,
            weekdayLabels = weekdayLabels,
            cardColors = cardColors,
            today = today,
            currentSection = currentSection,
            semesterStart = semesterStart,
            baseWeek = currentWeekIndex,
            showNonCurrent = showNonCurrent,
            fontScale = fontScale,
            theme = theme,
            hasCustomBackground = hasCustomBackground,
            onCourseClick = { item ->
                if (isAdded && !parentFragmentManager.isStateSaved) {
                    runCatching {
                        CourseDetailBottomSheet.show(requireContext(), item, periodTimeRanges)
                    }
                }
            }
        )
        weekPager.adapter = adapter

        weekPager.setCurrentItem(currentWeekIndex - 1, false)
        updateHeader(currentWeekIndex)

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
        val inCampus = Button(ctx).apply { text = "我在校内" }
        val offCampus = Button(ctx).apply { text = "我在校外" }
        val cancel = Button(ctx).apply { text = "取消" }
        UiFeedback.styleSecondaryButton(inCampus, palette)
        UiFeedback.styleSecondaryButton(offCampus, palette)
        UiFeedback.styleSecondaryButton(cancel, palette)
        panel.addView(inCampus)
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

            ImportedScheduleStorage.addManualCourseToActive(
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
            UiFeedback.showMessage(view, "已添加到当前课表", ThemePaletteProvider.fromContext(ctx))
            dialog.dismiss()
            activity?.recreate()
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

    private fun guessCurrentWeek(totalWeeks: Int, semesterStart: LocalDate): Int {
        val now = LocalDate.now()
        val days = java.time.temporal.ChronoUnit.DAYS.between(semesterStart, now).toInt()
        val computed = days / 7 + 1
        return computed.coerceIn(1, totalWeeks)
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

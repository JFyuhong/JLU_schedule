package cn.jlu.schedule.ui.today

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cn.jlu.schedule.R
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.data.ScheduleRepository
import cn.jlu.schedule.domain.SectionTimes
import cn.jlu.schedule.domain.WeekScheduleCalculator
import cn.jlu.schedule.model.Weekday
import cn.jlu.schedule.ui.theme.ThemePaletteProvider
import cn.jlu.schedule.ui.theme.GlassSurface
import cn.jlu.schedule.ui.timetable.CourseCardColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class TodayScheduleFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_today_schedule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ScheduleRepository.timetable.collect { data ->
                    if (data != null) {
                        renderToday(data)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 跨午夜或数据变更后回到本页时刷新，避免显示过期日程
        ScheduleRepository.refresh(requireContext())
    }

    @SuppressLint("SetTextI18n")
    private fun renderToday(data: ScheduleRepository.TimetableUiData) {
        val view = this.view ?: return
        val title = view.findViewById<TextView>(R.id.todayTitle)
        val subTitle = view.findViewById<TextView>(R.id.todaySubTitle)
        val countChip = view.findViewById<TextView>(R.id.todayCountChip)
        val firstClass = view.findViewById<TextView>(R.id.todayFirstClass)
        val lastClass = view.findViewById<TextView>(R.id.todayLastClass)
        val list = view.findViewById<LinearLayout>(R.id.todayCourseList)
        val root = view.findViewById<LinearLayout>(R.id.todayContainer)

        val periodTimeRanges = SectionTimes.DEFAULT_RANGES
        val palette = ThemePaletteProvider.fromContext(requireContext())
        val themeColors = ThemeColors(
            card = palette.panelBackground,
            text = palette.textPrimary,
            subText = palette.textSecondary,
            emptyCard = palette.panelAltBackground
        )
        val hasCustomBackground = !AppPreferences.getCustomBackgroundUri(requireContext()).isNullOrBlank()
        root.setBackgroundColor(
            if (hasCustomBackground) Color.TRANSPARENT else palette.pageBackground
        )
        GlassSurface.apply(view.findViewById(R.id.todaySummaryCard), palette, GlassSurface.Variant.Panel, 24f)
        countChip.background = GlassSurface.drawable(requireContext(), palette, GlassSurface.Variant.Control, 18f)
        firstClass.background = GlassSurface.drawable(requireContext(), palette, GlassSurface.Variant.Panel, 18f)
        lastClass.background = GlassSurface.drawable(requireContext(), palette, GlassSurface.Variant.Panel, 18f)
        title.setTextColor(themeColors.text)
        subTitle.setTextColor(themeColors.subText)
        countChip.setTextColor(themeColors.text)
        firstClass.setTextColor(themeColors.subText)
        lastClass.setTextColor(themeColors.subText)

        val today = LocalDate.now()
        val todayWeekday = weekdayFromJava(today.dayOfWeek.value)
        title.text = "今日日程"
        subTitle.text = String.format(
            Locale.getDefault(),
            "%s 周%s",
            today.format(DateTimeFormatter.ofPattern("yyyy/M/d")),
            label(todayWeekday)
        )

        val ctx = requireContext()
        val courses = data.courses
        val week = WeekScheduleCalculator.activeWeekOrNull(data.semesterStart, today, data.totalWeeks)
        val todayMeetings = week?.let { WeekScheduleCalculator.meetingsForWeek(courses, it) }.orEmpty()
            .filter { it.meeting.weekday == todayWeekday }
            .distinctBy {
                listOf(
                    it.course.courseName,
                    it.course.teacher,
                    it.meeting.startSection.toString(),
                    it.meeting.endSection.toString(),
                    it.meeting.location
                ).joinToString("|")
            }
            .sortedBy { it.meeting.startSection }

        list.removeAllViews()
        if (todayMeetings.isEmpty()) {
            countChip.text = "0 门课"
            firstClass.text = "最早：无"
            lastClass.text = "最晚：无"
            list.addView(TextView(ctx).apply {
                text = "今天没有课程安排"
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(themeColors.text)
                setPadding(18, 24, 18, 24)
                background = roundedBackground(themeColors.emptyCard)
            })
            return
        }

        countChip.text = String.format(Locale.getDefault(), "%d 门课", todayMeetings.size)
        val firstStart = todayMeetings.minOf { it.meeting.startSection }.coerceIn(1, periodTimeRanges.size)
        val lastEnd = todayMeetings.maxOf { it.meeting.endSection }.coerceIn(1, periodTimeRanges.size)
        firstClass.text = String.format(
            Locale.getDefault(),
            "最早：%s",
            periodTimeRanges[firstStart - 1].substringBefore('-')
        )
        lastClass.text = String.format(
            Locale.getDefault(),
            "最晚：%s",
            periodTimeRanges[lastEnd - 1].substringAfter('-')
        )

        val activeSection = currentSection()
        todayMeetings.forEachIndexed { index, item ->
            val start = item.meeting.startSection.coerceIn(1, periodTimeRanges.size)
            val end = item.meeting.endSection.coerceIn(start, periodTimeRanges.size)
            val isActive = activeSection != null && start <= activeSection && activeSection <= end

            // 时间列：开始-结束时刻
            val timeColumn = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4)
                }
                if (hasCustomBackground) {
                    background = roundedBackground(palette.panelAltBackground)
                    setPadding(0, dp(6), 0, dp(6))
                }
                addView(TextView(ctx).apply {
                    text = periodTimeRanges[start - 1].substringBefore('-')
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(themeColors.text)
                })
                addView(TextView(ctx).apply {
                    text = periodTimeRanges[end - 1].substringAfter('-')
                    textSize = 10f
                    setTextColor(themeColors.subText)
                    alpha = if (hasCustomBackground) 1f else 0.8f
                })
            }

            // 课程卡：左侧色条与课表格子同色，便于跨页面对应
            val accent = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(4), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    marginEnd = dp(10)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 2f
                    setColor(CourseCardColors.forCourse(item.courseIndex, palette.isDark))
                }
            }

            val content = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                val nameRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(ctx).apply {
                        text = item.course.courseName
                        textSize = 15f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(themeColors.text)
                    })
                    if (isActive) {
                        addView(TextView(ctx).apply {
                            text = "进行中"
                            textSize = 10f
                            setTextColor(Color.WHITE)
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = dp(9).toFloat()
                                setColor(themeColors.subText)
                            }
                            setPadding(dp(7), dp(2), dp(7), dp(2))
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                marginStart = dp(8)
                            }
                        })
                    }
                }
                addView(nameRow)
                addView(TextView(ctx).apply {
                    text = "第${start}-${end}节  ${periodTimeRanges[start - 1].substringBefore('-')}-" +
                        periodTimeRanges[end - 1].substringAfter('-')
                    textSize = 13f
                    setTextColor(themeColors.subText)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(3)
                    }
                })
                addView(TextView(ctx).apply {
                    text = item.meeting.location.ifBlank { "教室待定" }
                    textSize = 13f
                    setTextColor(themeColors.subText)
                    alpha = if (hasCustomBackground) 1f else 0.9f
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(2)
                    }
                })
            }

            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(16).toFloat()
                    setColor(if (isActive) themeColors.emptyCard else themeColors.card)
                }
                addView(accent)
                addView(content)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = if (index == 0) 0 else dp(10)
                }
            }

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(timeColumn)
                addView(card)
            }
            list.addView(row)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun currentSection(): Int? {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val now = java.time.LocalTime.now().format(formatter)
        for ((index, range) in SectionTimes.DEFAULT_RANGES.withIndex()) {
            val parts = range.split("-")
            if (parts.size == 2 && parts[0] <= now && now < parts[1]) {
                return index + 1
            }
        }
        return null
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

    private fun label(weekday: Weekday): String {
        return when (weekday) {
            Weekday.MONDAY -> "一"
            Weekday.TUESDAY -> "二"
            Weekday.WEDNESDAY -> "三"
            Weekday.THURSDAY -> "四"
            Weekday.FRIDAY -> "五"
            Weekday.SATURDAY -> "六"
            Weekday.SUNDAY -> "日"
        }
    }

    private fun roundedBackground(fill: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18f
            setColor(fill)
        }
    }

    private fun withAlpha(color: Int, alphaFactor: Float): Int {
        val alpha = (((color ushr 24) and 0xFF) * alphaFactor).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    private data class ThemeColors(
        val card: Int,
        val text: Int,
        val subText: Int,
        val emptyCard: Int
    )
}

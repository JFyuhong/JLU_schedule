package cn.jlu.schedule.ui.today

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import cn.jlu.schedule.R
import cn.jlu.schedule.data.ImportedScheduleStorage
import cn.jlu.schedule.domain.WeekScheduleCalculator
import cn.jlu.schedule.model.Weekday
import cn.jlu.schedule.ui.theme.ThemePaletteProvider
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class TodayScheduleFragment : Fragment() {
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_today_schedule, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val title = view.findViewById<TextView>(R.id.todayTitle)
        val subTitle = view.findViewById<TextView>(R.id.todaySubTitle)
        val countChip = view.findViewById<TextView>(R.id.todayCountChip)
        val firstClass = view.findViewById<TextView>(R.id.todayFirstClass)
        val lastClass = view.findViewById<TextView>(R.id.todayLastClass)
        val list = view.findViewById<LinearLayout>(R.id.todayCourseList)
        val root = view.findViewById<LinearLayout>(R.id.todayContainer)

        val palette = ThemePaletteProvider.fromContext(requireContext())
        val themeColors = ThemeColors(
            card = palette.panelBackground,
            text = palette.textPrimary,
            subText = palette.textSecondary,
            emptyCard = palette.panelAltBackground
        )
        root.setBackgroundColor(palette.pageBackground and 0x44FFFFFF)
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
        val courses = runCatching { ImportedScheduleStorage.loadCoursesOrSampleAsset(ctx, "sample_schedule.do") }
            .getOrElse { emptyList() }
        val semesterStart = ImportedScheduleStorage.getActiveSemesterStartDate(ctx)
        val totalWeeks = WeekScheduleCalculator.totalWeeks(courses)
        val week = guessCurrentWeek(totalWeeks, semesterStart)
        val todayMeetings = WeekScheduleCalculator.meetingsForWeek(courses, week)
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

        todayMeetings.forEachIndexed { index, item ->
            val start = item.meeting.startSection.coerceIn(1, periodTimeRanges.size)
            val end = item.meeting.endSection.coerceIn(start, periodTimeRanges.size)
            val card = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = if (index == 0) 0 else 10
                }
                text = buildString {
                    append(item.course.courseName)
                    append("\n")
                    append("第${start}-${end}节  ")
                    append(periodTimeRanges[start - 1].substringBefore('-'))
                    append("-")
                    append(periodTimeRanges[end - 1].substringAfter('-'))
                    append("\n")
                    append(item.meeting.location.ifBlank { "教室待定" })
                }
                textSize = 14f
                setTextColor(themeColors.text)
                setPadding(18, 16, 18, 16)
                background = roundedBackground(themeColors.card)
            }
            list.addView(card)
        }
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

    private fun guessCurrentWeek(totalWeeks: Int, semesterStart: LocalDate): Int {
        val now = LocalDate.now()
        val days = java.time.temporal.ChronoUnit.DAYS.between(semesterStart, now).toInt()
        val computed = days / 7 + 1
        return computed.coerceIn(1, totalWeeks)
    }

    private fun roundedBackground(fill: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18f
            setColor(fill)
        }
    }

    private data class ThemeColors(
        val card: Int,
        val text: Int,
        val subText: Int,
        val emptyCard: Int
    )
}

package cn.jlu.schedule.ui.timetable

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import androidx.cardview.widget.CardView
import cn.jlu.schedule.R
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.domain.CourseMeetingRef
import cn.jlu.schedule.model.WeekParity
import cn.jlu.schedule.model.Weekday
import com.google.android.material.bottomsheet.BottomSheetDialog

object CourseDetailBottomSheet {
    private val weekdayLabels = mapOf(
        Weekday.MONDAY to "周一",
        Weekday.TUESDAY to "周二",
        Weekday.WEDNESDAY to "周三",
        Weekday.THURSDAY to "周四",
        Weekday.FRIDAY to "周五",
        Weekday.SATURDAY to "周六",
        Weekday.SUNDAY to "周日"
    )

    fun show(context: Context, item: CourseMeetingRef, periodRanges: List<String>) {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_course_detail, null)
        val detailCard = view.findViewById<CardView>(R.id.detailCard)

        val colors = when (AppPreferences.getThemeColor(context)) {
            AppPreferences.THEME_OCEAN -> ThemeColors(
                card = 0xFFE8F4FF.toInt(),
                title = 0xFF1B3C62.toInt(),
                body = 0xFF2F4C70.toInt(),
                meta = 0xFF5C7394.toInt()
            )

            AppPreferences.THEME_MINT -> ThemeColors(
                card = 0xFFE8F8F0.toInt(),
                title = 0xFF1F4C3A.toInt(),
                body = 0xFF325E4C.toInt(),
                meta = 0xFF5E7F72.toInt()
            )

            else -> ThemeColors(
                card = 0xFFFFF0DE.toInt(),
                title = 0xFF5C3B1D.toInt(),
                body = 0xFF734D2A.toInt(),
                meta = 0xFF926E47.toInt()
            )
        }

        detailCard.setCardBackgroundColor(colors.card)

        val meeting = item.meeting
        val title = view.findViewById<TextView>(R.id.detailTitle)
        val teacher = view.findViewById<TextView>(R.id.detailTeacher)
        val time = view.findViewById<TextView>(R.id.detailTime)
        val weeks = view.findViewById<TextView>(R.id.detailWeeks)
        val location = view.findViewById<TextView>(R.id.detailLocation)
        val meta = view.findViewById<TextView>(R.id.detailMeta)

        title.setTextColor(colors.title)
        teacher.setTextColor(colors.body)
        time.setTextColor(colors.body)
        weeks.setTextColor(colors.body)
        location.setTextColor(colors.body)
        meta.setTextColor(colors.meta)

        title.text = item.course.courseName
        teacher.text = "教师：${item.course.teacher.ifBlank { "未知" }}"
        time.text =
            "节次：${weekdayLabels[meeting.weekday]} 第${meeting.startSection}-${meeting.endSection}节 (${sectionRangeTime(periodRanges, meeting.startSection, meeting.endSection)})"
        weeks.text = "周次：${meetingWeekText(item)}"
        location.text = "地点：${meeting.location.ifBlank { "未标注" }}"
        meta.text =
            "学期：${item.course.semester}   学分：${item.course.credit ?: "N/A"}"

        dialog.setContentView(view)
        dialog.show()
    }

    private fun sectionRangeTime(periodRanges: List<String>, start: Int, end: Int): String {
        val startText = periodRanges.getOrNull(start - 1)?.substringBefore('-') ?: "--:--"
        val endText = periodRanges.getOrNull(end - 1)?.substringAfter('-') ?: "--:--"
        return "$startText-$endText"
    }

    private fun meetingWeekText(item: CourseMeetingRef): String {
        val rules = item.meeting.weekRules
        if (rules.isEmpty()) {
            return item.course.rawWeekText.ifBlank { "未标注" }
        }
        return rules.joinToString("，") { rule ->
            val range = if (rule.startWeek == rule.endWeek) {
                "${rule.startWeek}周"
            } else {
                "${rule.startWeek}-${rule.endWeek}周"
            }
            when (rule.parity) {
                WeekParity.ALL -> range
                WeekParity.ODD -> "$range(单周)"
                WeekParity.EVEN -> "$range(双周)"
            }
        }
    }

    private data class ThemeColors(
        val card: Int,
        val title: Int,
        val body: Int,
        val meta: Int
    )
}

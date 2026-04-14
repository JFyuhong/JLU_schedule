package cn.jlu.schedule

import cn.jlu.schedule.model.CourseSchedule
import cn.jlu.schedule.model.WeekParity
import cn.jlu.schedule.model.WeekRule

object TimetableFormatter {
    fun render(courses: List<CourseSchedule>): String {
        val sorted = courses.sortedBy { course ->
            course.meetings.minOfOrNull { it.weekday.ordinal * 100 + it.startSection } ?: Int.MAX_VALUE
        }

        if (sorted.isEmpty()) {
            return "No courses found."
        }

        val lines = mutableListOf<String>()
        lines += "Total courses: ${sorted.size}"

        sorted.forEachIndexed { index, course ->
            lines += ""
            lines += "${index + 1}. ${course.courseName} | ${course.teacher} | ${course.semester}"
            lines += "   credit: ${course.credit ?: "N/A"}"
            if (course.meetings.isEmpty()) {
                lines += "   meeting: N/A"
            } else {
                course.meetings.forEach { meeting ->
                    val weekText = if (meeting.weekRules.isEmpty()) {
                        "weeks unknown"
                    } else {
                        meeting.weekRules.joinToString(",") { renderWeekRule(it) }
                    }
                    lines += "   - ${meeting.weekday.displayName} sec ${meeting.startSection}-${meeting.endSection} | $weekText | ${meeting.location.ifBlank { "location unknown" }}"
                }
            }
        }

        return lines.joinToString("\n")
    }

    private fun renderWeekRule(rule: WeekRule): String {
        val range = if (rule.startWeek == rule.endWeek) {
            "${rule.startWeek}"
        } else {
            "${rule.startWeek}-${rule.endWeek}"
        }
        val parity = when (rule.parity) {
            WeekParity.ALL -> ""
            WeekParity.ODD -> "(odd)"
            WeekParity.EVEN -> "(even)"
        }
        return "$range$parity"
    }
}

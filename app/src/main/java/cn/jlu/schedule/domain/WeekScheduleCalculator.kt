package cn.jlu.schedule.domain

import cn.jlu.schedule.model.CourseSchedule
import cn.jlu.schedule.model.MeetingTime
import cn.jlu.schedule.model.WeekParity

object WeekScheduleCalculator {
    fun totalWeeks(courses: List<CourseSchedule>): Int {
        val maxWeek = courses
            .flatMap { it.meetings }
            .flatMap { it.weekRules }
            .maxOfOrNull { it.endWeek }
        return (maxWeek ?: 20).coerceAtLeast(1)
    }

    fun meetingsForWeek(courses: List<CourseSchedule>, week: Int): List<CourseMeetingRef> {
        return courses.flatMapIndexed { index, course ->
            course.meetings
                .filter { isMeetingActiveInWeek(it, week) }
                .map { meeting -> CourseMeetingRef(index, course, meeting) }
        }
    }

    fun meetingsForDisplayWeek(
        courses: List<CourseSchedule>,
        week: Int,
        baseWeek: Int,
        showNonCurrent: Boolean
    ): List<CourseMeetingDisplayRef> {
        val referenceWeek = maxOf(baseWeek, week)
        return courses.flatMapIndexed { index, course ->
            course.meetings.mapNotNull { meeting ->
                val isCurrentWeek = isMeetingActiveInWeek(meeting, week)
                if (isCurrentWeek) {
                    return@mapNotNull CourseMeetingDisplayRef(
                        courseIndex = index,
                        course = course,
                        meeting = meeting,
                        isCurrentWeek = true,
                        nextActiveWeek = week
                    )
                }

                if (!showNonCurrent) {
                    return@mapNotNull null
                }

                val nextActiveWeek = nextActiveWeekFrom(meeting, referenceWeek) ?: return@mapNotNull null
                CourseMeetingDisplayRef(
                    courseIndex = index,
                    course = course,
                    meeting = meeting,
                    isCurrentWeek = false,
                    nextActiveWeek = nextActiveWeek
                )
            }
        }
    }

    private fun nextActiveWeekFrom(meeting: MeetingTime, fromWeek: Int): Int? {
        if (meeting.weekRules.isEmpty()) {
            return fromWeek
        }
        val maxEndWeek = meeting.weekRules.maxOfOrNull { it.endWeek } ?: return null
        for (week in fromWeek..maxEndWeek) {
            if (isMeetingActiveInWeek(meeting, week)) {
                return week
            }
        }
        return null
    }

    private fun isMeetingActiveInWeek(meeting: MeetingTime, week: Int): Boolean {
        if (meeting.weekRules.isEmpty()) {
            return true
        }
        return meeting.weekRules.any { rule ->
            week in rule.startWeek..rule.endWeek && when (rule.parity) {
                WeekParity.ALL -> true
                WeekParity.ODD -> week % 2 == 1
                WeekParity.EVEN -> week % 2 == 0
            }
        }
    }
}

data class CourseMeetingRef(
    val courseIndex: Int,
    val course: CourseSchedule,
    val meeting: MeetingTime
)

data class CourseMeetingDisplayRef(
    val courseIndex: Int,
    val course: CourseSchedule,
    val meeting: MeetingTime,
    val isCurrentWeek: Boolean,
    val nextActiveWeek: Int
) {
    fun toCourseMeetingRef(): CourseMeetingRef {
        return CourseMeetingRef(courseIndex, course, meeting)
    }
}

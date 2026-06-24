package cn.jlu.schedule.data

import cn.jlu.schedule.model.CourseSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object SemesterStartDatePolicy {
    private val codePattern = Regex("""(20\d{2})\D+(20\d{2})\D*([12])""")
    private val yearRangePattern = Regex("""(20\d{2})\D+(20\d{2})""")

    fun inferFromCourses(courses: List<CourseSchedule>, fallback: LocalDate = defaultForToday()): LocalDate {
        return inferFromCoursesOrNull(courses) ?: fallback
    }

    fun inferFromCoursesOrNull(courses: List<CourseSchedule>): LocalDate? {
        val dominantSemester = courses
            .map { it.semester.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: return null
        return inferFromSemesterLabelOrNull(dominantSemester)
    }

    fun inferFromSemesterLabelOrNull(label: String): LocalDate? {
        val key = normalizedSemesterKey(label)
        if (key != null) {
            val parts = key.split("-")
            if (parts.size == 3) {
                return startDateForTerm(
                    firstYear = parts[0].toIntOrNull() ?: return null,
                    secondYear = parts[1].toIntOrNull() ?: return null,
                    term = parts[2]
                )
            }
        }

        return null
    }

    fun normalizedSemesterKey(label: String): String? {
        val normalized = label.trim()
        if (normalized.isBlank()) {
            return null
        }

        codePattern.find(normalized)?.let { match ->
            val firstYear = match.groupValues[1].toIntOrNull() ?: return null
            val secondYear = match.groupValues[2].toIntOrNull() ?: return null
            return "$firstYear-$secondYear-${match.groupValues[3]}"
        }

        yearRangePattern.find(normalized)?.let { match ->
            val firstYear = match.groupValues[1].toIntOrNull() ?: return null
            val secondYear = match.groupValues[2].toIntOrNull() ?: return null
            val term = when {
                normalized.contains("第一") || normalized.contains("第1") || normalized.contains("秋") -> "1"
                normalized.contains("第二") || normalized.contains("第2") || normalized.contains("春") -> "2"
                else -> null
            }
            if (term != null) {
                return "$firstYear-$secondYear-$term"
            }
        }

        return null
    }

    fun defaultForToday(today: LocalDate = LocalDate.now()): LocalDate {
        return if (today.monthValue >= 8) {
            mondayOnOrBefore(LocalDate.of(today.year, 9, 1))
        } else {
            mondayOnOrBefore(LocalDate.of(today.year, 2, 24))
        }
    }

    fun normalizeToWeekStart(date: LocalDate): LocalDate {
        return mondayOnOrBefore(date)
    }

    private fun startDateForTerm(firstYear: Int, secondYear: Int, term: String): LocalDate? {
        return when (term) {
            "1" -> mondayOnOrBefore(LocalDate.of(firstYear, 9, 1))
            "2" -> mondayOnOrBefore(LocalDate.of(secondYear, 2, 24))
            else -> null
        }
    }

    private fun mondayOnOrBefore(date: LocalDate): LocalDate {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}

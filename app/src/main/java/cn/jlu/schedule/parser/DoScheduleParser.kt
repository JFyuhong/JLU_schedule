package cn.jlu.schedule.parser

import cn.jlu.schedule.model.CourseSchedule
import cn.jlu.schedule.model.MeetingTime
import cn.jlu.schedule.model.RawCourseRow
import cn.jlu.schedule.model.RawScheduleResponse
import cn.jlu.schedule.model.WeekParity
import cn.jlu.schedule.model.WeekRule
import cn.jlu.schedule.model.Weekday
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

object DoScheduleParser {
    private const val DEFAULT_TOTAL_WEEKS = 20
    private const val MAX_WEEK = 30

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val segmentRegex =
        Regex("""^\s*(.*?)\s*星期([一二三四五六日天1-7])\s*[,，]?\s*第\s*(\d+)\s*节?\s*[-—–~～至]\s*第?\s*(\d+)\s*节?\s*(.*)$""")
    private val weekdayMarkerRegex = Regex("""星期[一二三四五六日天1-7]""")
    private const val WEEK_NUMBER_PATTERN = """第?\s*\d+\s*(?:[-—–~～至]\s*\d+)?"""
    private const val WEEK_GROUP_PATTERN = """$WEEK_NUMBER_PATTERN(?:\s*[,，、]\s*$WEEK_NUMBER_PATTERN)*\s*周(?:\s*[（(]\s*[单双]\s*[)）]|\s*[单双])?"""
    private val weekPrefixRegex = Regex("""(?:$WEEK_GROUP_PATTERN(?:\s*[,，、]\s*$WEEK_GROUP_PATTERN)*|待定|[单双]周)\s*[,，]?\s*$""")
    private val weekItemRegex = Regex("""第?(\d+)\s*(?:[-—–~～至]\s*(\d+))?\s*周(?:\s*[（(]\s*(单|双)\s*[)）]|\s*(单|双))?""")
    private val discreteWeeksRegex = Regex("""^第?\s*(\d+(?:\s*[,，、]\s*\d+)+)\s*周?\s*(?:[（(]\s*(单|双)\s*[)）])?\s*周?$""")

    fun parse(rawContent: String): List<CourseSchedule> {
        val response = json.decodeFromString<RawScheduleResponse>(rawContent)
        val rows = response.datas.orEmpty().values
            .flatMap { it.rows.orEmpty() }
            .distinctBy { row ->
                listOf(
                    row.courseName.orEmpty(),
                    row.teacher.orEmpty(),
                    row.arrangedTimeText.orEmpty(),
                    row.weekText.orEmpty(),
                    row.weekdayNumber.asText(),
                    row.startSection.asText(),
                    row.endSection.asText(),
                    row.classroomName.orEmpty(),
                    row.buildingName.orEmpty()
                ).joinToString("|")
            }
        return rows.map { row ->
            CourseSchedule(
                courseName = row.courseName.orEmpty(),
                teacher = row.teacher.orEmpty(),
                semester = row.semesterDisplay?.trim()?.takeIf { it.isNotBlank() }
                    ?: row.semesterCode.orEmpty().trim(),
                credit = row.credit.asDoubleOrNull(),
                rawWeekText = row.weekText.orEmpty(),
                meetings = parseMeetings(row)
            )
        }
    }

    private fun parseMeetings(row: RawCourseRow): List<MeetingTime> {
        val arranged = row.arrangedTimeText.orEmpty().trim()
        if (arranged.isBlank()) {
            return fallbackMeeting(row)?.let(::listOf).orEmpty()
        }

        val segments = splitSegments(arranged)
        val parsed = segments.mapNotNull { parseSegment(it, row) }
        return if (parsed.isEmpty()) {
            fallbackMeeting(row)?.let(::listOf).orEmpty()
        } else {
            parsed
        }
    }

    private fun splitSegments(text: String): List<String> {
        // Commas separate both fields and meetings, and also occur inside week lists/locations.
        // Find each weekday and its preceding week expression instead of splitting on commas.
        val weekdays = weekdayMarkerRegex.findAll(text).toList()
        if (weekdays.isEmpty()) return listOf(text)
        val starts = mutableListOf(0)
        for (index in 1 until weekdays.size) {
            val betweenStart = weekdays[index - 1].range.last + 1
            val between = text.substring(betweenStart, weekdays[index].range.first)
            val weekPrefix = weekPrefixRegex.find(between)
            starts += if (weekPrefix != null) {
                betweenStart + weekPrefix.range.first
            } else {
                val separator = between.indexOfLast { it == ';' || it == '；' || it == '\n' }
                if (separator >= 0) betweenStart + separator + 1 else weekdays[index].range.first
            }
        }
        return starts.mapIndexed { index, start ->
            text.substring(start, starts.getOrElse(index + 1) { text.length }).trimFieldSeparators()
        }.filter { it.isNotBlank() }
    }

    private fun parseSegment(segment: String, row: RawCourseRow): MeetingTime? {
        val match = segmentRegex.matchEntire(segment) ?: return null
        val weekExpr = match.groupValues[1].trimFieldSeparators().ifBlank { row.weekText.orEmpty() }
        val weekdayText = match.groupValues[2].trim()
        val start = match.groupValues[3].toIntOrNull() ?: return null
        val end = match.groupValues[4].toIntOrNull() ?: return null
        val location = match.groupValues[5].trimFieldSeparators().ifBlank {
            row.classroomName ?: row.buildingName.orEmpty()
        }

        return MeetingTime(
            weekday = parseWeekdayFromChinese(weekdayText) ?: return null,
            startSection = minOf(start, end),
            endSection = maxOf(start, end),
            weekRules = parseWeekRules(weekExpr),
            location = location
        )
    }

    private fun fallbackMeeting(row: RawCourseRow): MeetingTime? {
        val weekday = parseWeekdayFromNumber(row.weekdayNumber.asIntOrNull() ?: return null) ?: return null
        val start = row.startSection.asIntOrNull() ?: return null
        val end = row.endSection.asIntOrNull() ?: return null
        val location = (row.classroomName ?: row.buildingName).orEmpty()

        return MeetingTime(
            weekday = weekday,
            startSection = minOf(start, end),
            endSection = maxOf(start, end),
            weekRules = parseWeekRules(row.weekText.orEmpty()),
            location = location
        )
    }

    private fun parseWeekRules(weekExpr: String): List<WeekRule> {
        val expr = weekExpr.trim()
        if (expr.isBlank()) {
            return listOf(defaultWeekRule(WeekParity.ALL))
        }

        if (!expr.any { it.isDigit() }) {
            val parity = when {
                expr.contains("单") -> WeekParity.ODD
                expr.contains("双") -> WeekParity.EVEN
                else -> WeekParity.ALL
            }
            return listOf(defaultWeekRule(parity))
        }

        discreteWeeksRegex.find(expr)?.let { match ->
            val parity = when (match.groupValues[2]) {
                "单" -> WeekParity.ODD
                "双" -> WeekParity.EVEN
                else -> WeekParity.ALL
            }
            val weeks = match.groupValues[1].split(",", "，", "、")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..MAX_WEEK }
            if (weeks.isNotEmpty()) {
                return weeks.map { WeekRule(it, it, parity) }
            }
        }

        val rules = expr.split(",", "，", "、", ";", "；")
            .mapNotNull(::parseWeekItem)
        return rules.ifEmpty { listOf(defaultWeekRule(WeekParity.ALL)) }
    }

    private fun parseWeekItem(part: String): WeekRule? {
        val match = weekItemRegex.find(part.trim()) ?: return null
        val start = match.groupValues[1].toIntOrNull() ?: return null
        val end = match.groupValues[2].toIntOrNull() ?: start
        val parity = when (match.groupValues[3].ifBlank { match.groupValues[4] }) {
            "单" -> WeekParity.ODD
            "双" -> WeekParity.EVEN
            else -> WeekParity.ALL
        }
        if (start !in 1..MAX_WEEK || end !in 1..MAX_WEEK) {
            return null
        }
        return if (start <= end) WeekRule(start, end, parity) else WeekRule(end, start, parity)
    }

    private fun defaultWeekRule(parity: WeekParity): WeekRule {
        return WeekRule(1, DEFAULT_TOTAL_WEEKS, parity)
    }

    private fun parseWeekdayFromChinese(value: String): Weekday? = when (value) {
        "一", "1" -> Weekday.MONDAY
        "二", "2" -> Weekday.TUESDAY
        "三", "3" -> Weekday.WEDNESDAY
        "四", "4" -> Weekday.THURSDAY
        "五", "5" -> Weekday.FRIDAY
        "六", "6" -> Weekday.SATURDAY
        "日", "天", "7" -> Weekday.SUNDAY
        else -> null
    }

    private fun String.trimFieldSeparators(): String = trim { it.isWhitespace() || it in ",，;；" }

    private fun parseWeekdayFromNumber(value: Int): Weekday? = when (value) {
        1 -> Weekday.MONDAY
        2 -> Weekday.TUESDAY
        3 -> Weekday.WEDNESDAY
        4 -> Weekday.THURSDAY
        5 -> Weekday.FRIDAY
        6 -> Weekday.SATURDAY
        7 -> Weekday.SUNDAY
        else -> null
    }

    private fun JsonElement?.asIntOrNull(): Int? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.intOrNull ?: primitive.content.trim().toIntOrNull()
    }

    private fun JsonElement?.asDoubleOrNull(): Double? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.doubleOrNull ?: primitive.content.trim().toDoubleOrNull()
    }

    private fun JsonElement?.asText(): String {
        val primitive = this as? JsonPrimitive ?: return ""
        if (primitive is JsonNull) return ""
        return primitive.content
    }
}

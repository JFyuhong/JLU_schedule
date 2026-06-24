package cn.jlu.schedule.parser

import cn.jlu.schedule.data.SemesterStartDatePolicy
import cn.jlu.schedule.model.CourseSchedule
import java.io.File
import java.time.LocalDate

object ScheduleImportCacheParser {
    private const val TARGET_SCHEDULE_FILE = "cxxszhxqkb.do"
    private const val MAX_PARSE_FILE_BYTES = 2 * 1024 * 1024L
    private const val MIN_SCHEDULE_PAYLOAD_BYTES = 80

    data class CacheEntry(
        val url: String,
        val fileName: String,
        val filePath: String,
        val size: Int,
        val sequence: Int
    )

    data class ParseResult(
        val courses: List<CourseSchedule>,
        val selectedSemester: String,
        val inferredSemesterStartDate: LocalDate?,
        val scannedEntries: Int,
        val parsedBatchCount: Int,
        val rejectedEntries: List<RejectedEntry>
    )

    data class RejectedEntry(
        val entry: CacheEntry,
        val reason: String,
        val detail: String? = null
    )

    private data class ParsedScheduleBatch(
        val entry: CacheEntry,
        val courses: List<CourseSchedule>,
        val semesterKey: String,
        val semesterLabel: String
    )

    fun parse(
        entries: List<CacheEntry>,
        courseParser: (String) -> List<CourseSchedule> = DoScheduleParser::parse
    ): ParseResult {
        val candidates = prioritizeCandidates(entries)
        val batches = mutableListOf<ParsedScheduleBatch>()
        val rejected = mutableListOf<RejectedEntry>()

        candidates.forEach { entry ->
            val file = File(entry.filePath)
            if (!file.exists()) {
                rejected += RejectedEntry(entry, "文件不存在")
                return@forEach
            }
            val fileLength = file.length()
            if (fileLength <= 0L) {
                rejected += RejectedEntry(entry, "文件为空")
                return@forEach
            }
            if (fileLength > MAX_PARSE_FILE_BYTES) {
                rejected += RejectedEntry(entry, "文件过大", "${fileLength} bytes")
                return@forEach
            }

            val text = try {
                file.readText(Charsets.UTF_8)
            } catch (error: Throwable) {
                rejected += RejectedEntry(entry, "读取失败", error.message)
                return@forEach
            }

            if (!looksLikeSchedulePayload(entry.url, text, fileLength)) {
                rejected += RejectedEntry(entry, "不是课表响应")
                return@forEach
            }

            val parsed = try {
                courseParser(text)
            } catch (error: Throwable) {
                rejected += RejectedEntry(entry, "解析失败", error.message)
                return@forEach
            }.filter { it.courseName.isNotBlank() && it.meetings.isNotEmpty() }

            if (parsed.isEmpty()) {
                rejected += RejectedEntry(entry, "课表为空")
                return@forEach
            }

            batches += ParsedScheduleBatch(
                entry = entry,
                courses = parsed,
                semesterKey = dominantSemesterKey(parsed),
                semesterLabel = dominantSemesterLabel(parsed)
            )
        }

        if (batches.isEmpty()) {
            return ParseResult(
                courses = emptyList(),
                selectedSemester = "",
                inferredSemesterStartDate = null,
                scannedEntries = candidates.size,
                parsedBatchCount = 0,
                rejectedEntries = rejected
            )
        }

        val latestBatch = batches.maxBy { it.entry.sequence }
        val selectedBatches = if (latestBatch.semesterKey.isBlank()) {
            listOf(latestBatch)
        } else {
            batches.filter { it.semesterKey == latestBatch.semesterKey }
        }
        val selectedCourses = selectedBatches
            .sortedBy { it.entry.sequence }
            .flatMap { it.courses }
            .let { courses ->
                if (latestBatch.semesterKey.isBlank()) {
                    courses
                } else {
                    courses.filter { course ->
                        val courseKey = semesterKey(course.semester)
                        courseKey.isBlank() || courseKey == latestBatch.semesterKey
                    }
                }
            }

        return ParseResult(
            courses = selectedCourses,
            selectedSemester = latestBatch.semesterLabel.ifBlank { latestBatch.semesterKey },
            inferredSemesterStartDate = SemesterStartDatePolicy.inferFromCoursesOrNull(selectedCourses),
            scannedEntries = candidates.size,
            parsedBatchCount = batches.size,
            rejectedEntries = rejected
        )
    }

    fun looksLikeSchedulePayload(url: String, text: String, size: Long): Boolean {
        if (size < MIN_SCHEDULE_PAYLOAD_BYTES) {
            return false
        }
        if (!isLikelyScheduleUrl(url)) {
            return false
        }
        if (!text.contains("\"datas\"") || !text.contains("\"rows\"")) {
            return false
        }
        val hasCourseField = text.contains("\"KCM\"")
        val hasTimeField = text.contains("\"YPSJDD\"") || text.contains("\"SKXQ\"")
        return hasCourseField && hasTimeField
    }

    fun isLikelyScheduleUrl(url: String): Boolean {
        return url.contains(TARGET_SCHEDULE_FILE, ignoreCase = true) ||
            url.contains("modules/xskcb", ignoreCase = true)
    }

    private fun dominantSemesterKey(courses: List<CourseSchedule>): String {
        return courses
            .map { semesterKey(it.semester) }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            .orEmpty()
    }

    private fun dominantSemesterLabel(courses: List<CourseSchedule>): String {
        val key = dominantSemesterKey(courses)
        if (key.isBlank()) {
            return ""
        }
        return courses
            .map { it.semester.trim() }
            .filter { it.isNotBlank() && semesterKey(it) == key }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            .orEmpty()
    }

    private fun semesterKey(label: String): String {
        val raw = label.trim()
        if (raw.isBlank()) {
            return ""
        }
        return SemesterStartDatePolicy.normalizedSemesterKey(raw) ?: raw
    }

    private fun prioritizeCandidates(entries: List<CacheEntry>): List<CacheEntry> {
        return entries.sortedWith(
            compareByDescending<CacheEntry> { it.url.contains(TARGET_SCHEDULE_FILE, ignoreCase = true) }
                .thenByDescending { it.url.contains("modules/xskcb", ignoreCase = true) }
                .thenByDescending {
                    it.fileName.contains(".do", ignoreCase = true) ||
                        it.fileName.contains(".json", ignoreCase = true)
                }
                .thenByDescending { it.sequence }
                .thenByDescending { it.size }
        )
    }
}

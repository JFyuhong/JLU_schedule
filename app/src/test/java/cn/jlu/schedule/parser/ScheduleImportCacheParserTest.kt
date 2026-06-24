package cn.jlu.schedule.parser

import cn.jlu.schedule.model.CourseSchedule
import cn.jlu.schedule.model.MeetingTime
import cn.jlu.schedule.model.WeekParity
import cn.jlu.schedule.model.WeekRule
import cn.jlu.schedule.model.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScheduleImportCacheParserTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun parse_selectsLatestSemesterAndFiltersOlderBatches() {
        val older = cacheFile("old.json", "https://example.edu/modules/xskcb/cxxszhxqkb.do", 1)
        val latest = cacheFile("new.json", "https://example.edu/modules/xskcb/cxxszhxqkb.do", 2)

        val result = ScheduleImportCacheParser.parse(
            entries = listOf(older, latest),
            courseParser = { text ->
                if (text.contains("2025-2026")) {
                    listOf(course("旧学期课程", "2025-2026学年第二学期"))
                } else {
                    listOf(course("新学期课程", "2026-2027学年第一学期"))
                }
            }
        )

        assertEquals("2026-2027学年第一学期", result.selectedSemester)
        assertEquals(1, result.courses.size)
        assertEquals("新学期课程", result.courses.single().courseName)
        assertEquals(2, result.parsedBatchCount)
    }

    @Test
    fun parse_mergesBatchesFromSameLatestSemesterInSequenceOrder() {
        val first = cacheFile("first.json", "https://example.edu/modules/xskcb/cxxszhxqkb.do", 4)
        val second = cacheFile("second.json", "https://example.edu/modules/xskcb/cxxszhxqkb.do", 5)

        val result = ScheduleImportCacheParser.parse(
            entries = listOf(second, first),
            courseParser = { text ->
                if (text.contains("first")) {
                    listOf(course("A", "2026-2027-1"))
                } else {
                    listOf(course("B", "2026-2027-1"))
                }
            }
        )

        assertEquals(listOf("A", "B"), result.courses.map { it.courseName })
    }

    @Test
    fun parse_keepsEquivalentSemesterFormatsAndBlankSemesterRowsFromLatestBatch() {
        val latest = cacheFile("mixed.json", "https://example.edu/modules/xskcb/cxxszhxqkb.do", 3)

        val result = ScheduleImportCacheParser.parse(
            entries = listOf(latest),
            courseParser = {
                listOf(
                    course("代码学期", "2026-2027-1"),
                    course("显示学期", "2026-2027学年第一学期"),
                    course("空学期字段", "")
                )
            }
        )

        assertEquals(3, result.courses.size)
        assertEquals(listOf("代码学期", "显示学期", "空学期字段"), result.courses.map { it.courseName })
    }

    @Test
    fun parse_rejectsMalformedSchedulePayloadWithoutThrowing() {
        val broken = cacheFile("broken.json", "https://example.edu/modules/xskcb/cxxszhxqkb.do", 1)

        val result = ScheduleImportCacheParser.parse(
            entries = listOf(broken),
            courseParser = { error("bad json") }
        )

        assertTrue(result.courses.isEmpty())
        assertEquals("解析失败", result.rejectedEntries.single().reason)
    }

    private fun cacheFile(
        name: String,
        url: String,
        sequence: Int
    ): ScheduleImportCacheParser.CacheEntry {
        val file = temporaryFolder.newFile(name)
        val marker = name.substringBefore('.')
        file.writeText(
            """
                {
                  "datas": {
                    "xskcb": {
                      "rows": [
                        {
                          "KCM": "$marker",
                          "YPSJDD": "1-16周 星期一 第1节-第2节 教室",
                          "XNXQDM_DISPLAY": "${if (marker == "old") "2025-2026学年第二学期" else "2026-2027学年第一学期"}"
                        }
                      ]
                    }
                  },
                  "padding": "${"x".repeat(500)}"
                }
            """.trimIndent(),
            Charsets.UTF_8
        )
        return ScheduleImportCacheParser.CacheEntry(
            url = url,
            fileName = name,
            filePath = file.absolutePath,
            size = file.length().toInt(),
            sequence = sequence
        )
    }

    private fun course(name: String, semester: String): CourseSchedule {
        return CourseSchedule(
            courseName = name,
            teacher = "",
            semester = semester,
            credit = null,
            rawWeekText = "1-16周",
            meetings = listOf(
                MeetingTime(
                    weekday = Weekday.MONDAY,
                    startSection = 1,
                    endSection = 2,
                    weekRules = listOf(WeekRule(1, 16, WeekParity.ALL)),
                    location = "教室"
                )
            )
        )
    }
}

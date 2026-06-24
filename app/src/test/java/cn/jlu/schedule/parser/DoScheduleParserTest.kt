package cn.jlu.schedule.parser

import cn.jlu.schedule.model.WeekParity
import cn.jlu.schedule.model.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DoScheduleParserTest {
    @Test
    fun parse_acceptsNumericFieldsReturnedAsStrings() {
        val courses = DoScheduleParser.parse(
            scheduleJson(
                """
                    {
                      "KCM": "高等数学",
                      "SKJS": "张三",
                      "XNXQDM": "2026-2027-1",
                      "XF": "3.5",
                      "ZCMC": "1-16周",
                      "SKXQ": "1",
                      "KSJC": "3",
                      "JSJC": "4",
                      "JASMC": "逸夫楼A101"
                    }
                """.trimIndent()
            )
        )

        assertEquals(1, courses.size)
        val course = courses.single()
        assertEquals("高等数学", course.courseName)
        assertEquals(3.5, course.credit!!, 0.0001)
        val meeting = course.meetings.single()
        assertEquals(Weekday.MONDAY, meeting.weekday)
        assertEquals(3, meeting.startSection)
        assertEquals(4, meeting.endSection)
        assertEquals("逸夫楼A101", meeting.location)
    }

    @Test
    fun parse_splitsMultipleArrangedSegmentsAndParityRules() {
        val courses = DoScheduleParser.parse(
            scheduleJson(
                """
                    {
                      "KCM": "大学物理",
                      "SKJS": "李四",
                      "XNXQDM_DISPLAY": "2026-2027学年第一学期",
                      "XF": 2,
                      "YPSJDD": "1-8周(单) 星期二 第1节-第2节 逸夫楼B201,9-16周(双) 星期四 第7节-第8节 中心校区C301",
                      "ZCMC": "1-16周",
                      "SKXQ": 2,
                      "KSJC": 1,
                      "JSJC": 2
                    }
                """.trimIndent()
            )
        )

        val course = courses.single()
        assertEquals("2026-2027学年第一学期", course.semester)
        assertEquals(2, course.meetings.size)

        val first = course.meetings[0]
        assertEquals(Weekday.TUESDAY, first.weekday)
        assertEquals(1, first.startSection)
        assertEquals(2, first.endSection)
        assertEquals(WeekParity.ODD, first.weekRules.single().parity)
        assertEquals(1, first.weekRules.single().startWeek)
        assertEquals(8, first.weekRules.single().endWeek)

        val second = course.meetings[1]
        assertEquals(Weekday.THURSDAY, second.weekday)
        assertEquals(WeekParity.EVEN, second.weekRules.single().parity)
    }

    @Test
    fun parse_deduplicatesRepeatedRowsAfterNumericStringNormalization() {
        val raw = scheduleJson(
            """
                {
                  "KCM": "线性代数",
                  "SKJS": "王五",
                  "XNXQDM": "2026-2027-1",
                  "XF": "2",
                  "ZCMC": "1-8周",
                  "SKXQ": "3",
                  "KSJC": "5",
                  "JSJC": "6",
                  "JASMC": "三教302"
                },
                {
                  "KCM": "线性代数",
                  "SKJS": "王五",
                  "XNXQDM": "2026-2027-1",
                  "XF": 2,
                  "ZCMC": "1-8周",
                  "SKXQ": 3,
                  "KSJC": 5,
                  "JSJC": 6,
                  "JASMC": "三教302"
                }
            """.trimIndent()
        )

        val courses = DoScheduleParser.parse(raw)

        assertEquals(1, courses.size)
        assertNotNull(courses.single().meetings.single())
    }

    @Test
    fun parse_usesSemesterCodeWhenDisplayValueIsBlank() {
        val courses = DoScheduleParser.parse(
            scheduleJson(
                """
                    {
                      "KCM": "概率论",
                      "SKJS": "赵六",
                      "XNXQDM": "2026-2027-1",
                      "XNXQDM_DISPLAY": "",
                      "YPSJDD": "1-16周 星期五 第1节-第2节 三教101"
                    }
                """.trimIndent()
            )
        )

        assertEquals("2026-2027-1", courses.single().semester)
    }

    private fun scheduleJson(rows: String): String {
        return """
            {
              "datas": {
                "xskcb": {
                  "rows": [
                    $rows
                  ]
                }
              }
            }
        """.trimIndent()
    }
}

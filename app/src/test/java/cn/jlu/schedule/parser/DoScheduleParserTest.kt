package cn.jlu.schedule.parser

import cn.jlu.schedule.model.WeekParity
import cn.jlu.schedule.model.Weekday
import cn.jlu.schedule.domain.WeekScheduleCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DoScheduleParserTest {
    @Test
    fun parse_keepsTeachingArrangementsInTheirOwnWeeksAndLocations() {
        val courses = DoScheduleParser.parse(
            javaClass.getResource("/issue3-split-teaching-arrangements.json")!!.readText()
        )
        assertEquals(3, courses.size)
        assertEquals(emptyList<String>(), WeekScheduleCalculator.meetingsForWeek(courses, 5).map { it.course.teacher })
        assertEquals(listOf("老师B"), WeekScheduleCalculator.meetingsForWeek(courses, 6).map { it.course.teacher })
        assertEquals(emptyList<String>(), WeekScheduleCalculator.meetingsForWeek(courses, 9).map { it.course.teacher })
        assertEquals(listOf("老师C"), WeekScheduleCalculator.meetingsForWeek(courses, 10).map { it.course.teacher })
        val online = WeekScheduleCalculator.meetingsForWeek(courses, 13).single()
        assertEquals("老师A", online.course.teacher)
        assertEquals("线上教学-前卫", online.meeting.location)
        assertEquals(Weekday.FRIDAY, online.meeting.weekday)
    }

    @Test
    fun parse_keepsCommaSeparatedLocationsAndDiscreteWeeksAcrossMultipleMeetings() {
        val course = DoScheduleParser.parse(scheduleJson("""
            {"KCM":"实验", "YPSJDD":"第1,3,5周（单）,星期1,第1节-第2节,A楼,实验室；6—8周,星期日,第3-4节,B楼", "JASMC":"错误的兜底教室"}
        """.trimIndent())).single()
        assertEquals(2, course.meetings.size)
        assertEquals(listOf(1, 3, 5), course.meetings[0].weekRules.map { it.startWeek })
        assertEquals("A楼,实验室", course.meetings[0].location)
        assertEquals(Weekday.SUNDAY, course.meetings[1].weekday)
        assertEquals(6, course.meetings[1].weekRules.single().startWeek)
        assertEquals(8, course.meetings[1].weekRules.single().endWeek)
        assertEquals("B楼", course.meetings[1].location)
    }

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

    @Test
    fun parse_supportsDiscreteWeekList() {
        val courses = DoScheduleParser.parse(
            scheduleJson(
                """
                    {
                      "KCM": "体育",
                      "SKJS": "钱七",
                      "YPSJDD": "第1,3,5,7周 星期三 第3节-第4节 田径场"
                    }
                """.trimIndent()
            )
        )

        val rules = courses.single().meetings.single().weekRules
        assertEquals(listOf(1, 3, 5, 7), rules.map { it.startWeek })
        assertEquals(listOf(1, 3, 5, 7), rules.map { it.endWeek })
        assertEquals(listOf(WeekParity.ALL, WeekParity.ALL, WeekParity.ALL, WeekParity.ALL), rules.map { it.parity })
    }

    @Test
    fun parse_supportsDiscreteWeekListWithFullWidthParenParity() {
        val courses = DoScheduleParser.parse(
            scheduleJson(
                """
                    {
                      "KCM": "英语",
                      "SKJS": "孙八",
                      "YPSJDD": "第1,3,5周（单） 星期一 第1节-第2节 外语楼"
                    }
                """.trimIndent()
            )
        )

        val rules = courses.single().meetings.single().weekRules
        assertEquals(listOf(1, 3, 5), rules.map { it.startWeek })
        assertEquals(listOf(WeekParity.ODD, WeekParity.ODD, WeekParity.ODD), rules.map { it.parity })
    }

    @Test
    fun parse_supportsFullWidthParenParityOnRange() {
        val courses = DoScheduleParser.parse(
            scheduleJson(
                """
                    {
                      "KCM": "化学",
                      "SKJS": "周九",
                      "YPSJDD": "1-16周（双） 星期二 第5节-第6节 实验楼"
                    }
                """.trimIndent()
            )
        )

        val rule = courses.single().meetings.single().weekRules.single()
        assertEquals(1, rule.startWeek)
        assertEquals(16, rule.endWeek)
        assertEquals(WeekParity.EVEN, rule.parity)
    }

    @Test
    fun parse_splitsSegmentsSeparatedByChineseComma() {
        val courses = DoScheduleParser.parse(
            scheduleJson(
                """
                    {
                      "KCM": "数据结构",
                      "SKJS": "吴十",
                      "YPSJDD": "1-8周(单) 星期二 第1节-第2节 A楼，9-16周(双) 星期四 第7节-第8节 B楼"
                    }
                """.trimIndent()
            )
        )

        val meetings = courses.single().meetings
        assertEquals(2, meetings.size)
        assertEquals(WeekParity.ODD, meetings[0].weekRules.single().parity)
        assertEquals("A楼", meetings[0].location)
        assertEquals(WeekParity.EVEN, meetings[1].weekRules.single().parity)
        assertEquals("B楼", meetings[1].location)
    }

    @Test
    fun parse_supportsCompactSectionFormat() {
        val courses = DoScheduleParser.parse(
            scheduleJson(
                """
                    {
                      "KCM": "力学",
                      "SKJS": "郑一",
                      "YPSJDD": "1-16周 星期一 第1-2节 教三302"
                    }
                """.trimIndent()
            )
        )

        val meeting = courses.single().meetings.single()
        assertEquals(1, meeting.startSection)
        assertEquals(2, meeting.endSection)
    }

    @Test
    fun parse_swapsReversedWeekAndSectionRanges() {
        val courses = DoScheduleParser.parse(
            scheduleJson(
                """
                    {
                      "KCM": "光学",
                      "SKJS": "王二",
                      "YPSJDD": "16-1周 星期一 第4节-第2节 教室"
                    }
                """.trimIndent()
            )
        )

        val meeting = courses.single().meetings.single()
        assertEquals(2, meeting.startSection)
        assertEquals(4, meeting.endSection)
        val rule = meeting.weekRules.single()
        assertEquals(1, rule.startWeek)
        assertEquals(16, rule.endWeek)
    }

    @Test
    fun parse_defaultsToFullRangeWhenWeekTextIsUnparseable() {
        val courses = DoScheduleParser.parse(
            scheduleJson(
                """
                    {
                      "KCM": "选修",
                      "SKJS": "李三",
                      "YPSJDD": "待定 星期五 第1节-第2节 教室"
                    }
                """.trimIndent()
            )
        )

        val rule = courses.single().meetings.single().weekRules.single()
        assertEquals(1, rule.startWeek)
        assertEquals(20, rule.endWeek)
        assertEquals(WeekParity.ALL, rule.parity)
    }

    @Test
    fun parse_defaultsToFullRangeWithParityForBareParityWeekText() {
        val courses = DoScheduleParser.parse(
            scheduleJson(
                """
                    {
                      "KCM": "实验",
                      "SKJS": "张三",
                      "ZCMC": "双周",
                      "SKXQ": 3,
                      "KSJC": 5,
                      "JSJC": 6
                    }
                """.trimIndent()
            )
        )

        val meeting = courses.single().meetings.single()
        val rule = meeting.weekRules.single()
        assertEquals(WeekParity.EVEN, rule.parity)
        assertEquals(1, rule.startWeek)
        assertEquals(20, rule.endWeek)
    }

    @Test
    fun parse_toleratesExplicitNullDatas() {
        assertEquals(0, DoScheduleParser.parse("""{"datas": null}""").size)
    }

    @Test
    fun parse_toleratesExplicitNullRows() {
        val courses = DoScheduleParser.parse(
            """
                {
                  "datas": {
                    "xskcb": {
                      "rows": null
                    }
                  }
                }
            """.trimIndent()
        )
        assertEquals(0, courses.size)
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

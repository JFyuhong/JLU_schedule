package cn.jlu.schedule.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SemesterStartDatePolicyTest {
    @Test
    fun inferFromSemesterLabel_returnsFallTermMonday() {
        assertEquals(
            LocalDate.of(2026, 8, 31),
            SemesterStartDatePolicy.inferFromSemesterLabelOrNull("2026-2027学年第一学期")
        )
    }

    @Test
    fun inferFromSemesterLabel_returnsSpringTermMonday() {
        assertEquals(
            LocalDate.of(2027, 2, 22),
            SemesterStartDatePolicy.inferFromSemesterLabelOrNull("2026-2027学年第二学期")
        )
    }
}

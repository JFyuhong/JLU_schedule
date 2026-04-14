package cn.jlu.schedule.data

import android.content.Context
import cn.jlu.schedule.model.CourseSchedule
import cn.jlu.schedule.parser.DoScheduleParser

class ScheduleAssetRepository(private val context: Context) {
    fun loadCourses(fileName: String): List<CourseSchedule> {
        val content = context.assets.open(fileName).bufferedReader().use { it.readText() }
        return DoScheduleParser.parse(content)
    }
}

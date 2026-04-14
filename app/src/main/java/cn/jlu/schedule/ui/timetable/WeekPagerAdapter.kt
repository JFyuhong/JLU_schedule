package cn.jlu.schedule.ui.timetable

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import cn.jlu.schedule.R
import cn.jlu.schedule.domain.WeekScheduleCalculator
import cn.jlu.schedule.domain.CourseMeetingRef
import cn.jlu.schedule.model.CourseSchedule
import cn.jlu.schedule.model.Weekday
import java.time.LocalDate

class WeekPagerAdapter(
    private val courses: List<CourseSchedule>,
    private val totalWeeks: Int,
    private val periodRanges: List<String>,
    private val weekdayLabels: Map<Weekday, String>,
    private val cardColors: IntArray,
    private val today: LocalDate,
    private val currentSection: Int?,
    private val semesterStart: LocalDate,
    private val baseWeek: Int,
    private val showNonCurrent: Boolean,
    private val fontScale: Float,
    private val theme: String,
    private val hasCustomBackground: Boolean,
    private val onCourseClick: (CourseMeetingRef) -> Unit
) : RecyclerView.Adapter<WeekPagerAdapter.WeekViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_week_timetable_page, parent, false)
        return WeekViewHolder(view)
    }

    override fun onBindViewHolder(holder: WeekViewHolder, position: Int) {
        val week = position + 1
        holder.bind(week, weekStartDate(week))
    }

    override fun getItemCount(): Int = totalWeeks

    inner class WeekViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val headerRow: LinearLayout = itemView.findViewById(R.id.headerRow)
        private val bodyRow: LinearLayout = itemView.findViewById(R.id.bodyRow)

        private val renderer = WeekTimetableRenderer(periodRanges, weekdayLabels, cardColors, fontScale, theme, hasCustomBackground)

        fun bind(week: Int, weekStart: LocalDate) {
            val metrics = TimetableMetrics.create(itemView.context)
            val weekItems = WeekScheduleCalculator.meetingsForDisplayWeek(courses, week, baseWeek, showNonCurrent)
            renderer.render(headerRow, bodyRow, metrics, weekItems, onCourseClick, weekStart, today, currentSection)
        }
    }

    private fun weekStartDate(week: Int): LocalDate {
        return semesterStart.plusDays(((week - 1) * 7).toLong())
    }
}

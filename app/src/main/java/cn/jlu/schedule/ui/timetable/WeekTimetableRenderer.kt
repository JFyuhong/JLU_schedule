package cn.jlu.schedule.ui.timetable

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import cn.jlu.schedule.domain.CourseMeetingDisplayRef
import cn.jlu.schedule.domain.CourseMeetingRef
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.model.Weekday
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class WeekTimetableRenderer(
    private val periodRanges: List<String>,
    private val weekdayLabels: Map<Weekday, String>,
    private val cardColors: IntArray,
    private val fontScale: Float,
    private val theme: String,
    private val hasCustomBackground: Boolean
) {
    private val weekdays = listOf(
        Weekday.MONDAY,
        Weekday.TUESDAY,
        Weekday.WEDNESDAY,
        Weekday.THURSDAY,
        Weekday.FRIDAY,
        Weekday.SATURDAY,
        Weekday.SUNDAY
    )

    private val palette = when (theme) {
        AppPreferences.THEME_OCEAN -> ThemePalette(
            header = 0xFFDCEEFF.toInt(),
            headerToday = 0xFFBFDFFF.toInt(),
            leftColumn = 0xFFE8F4FF.toInt(),
            dayCell = 0xFFF2F8FF.toInt(),
            dayToday = 0xFFD6EBFF.toInt(),
            primaryText = 0xFF1F3A56.toInt(),
            secondaryText = 0xFF3E5876.toInt()
        )

        AppPreferences.THEME_MINT -> ThemePalette(
            header = 0xFFD7F3E6.toInt(),
            headerToday = 0xFFBFEAD6.toInt(),
            leftColumn = 0xFFE7F8EF.toInt(),
            dayCell = 0xFFF0FBF5.toInt(),
            dayToday = 0xFFD4F4E4.toInt(),
            primaryText = 0xFF1D4A3A.toInt(),
            secondaryText = 0xFF396458.toInt()
        )

        else -> ThemePalette(
            header = 0xFFFFE9C9.toInt(),
            headerToday = 0xFFFFD7B5.toInt(),
            leftColumn = 0xFFFFEFD6.toInt(),
            dayCell = 0xFFFFF5E6.toInt(),
            dayToday = 0xFFFFE8CD.toInt(),
            primaryText = 0xFF6A4321.toInt(),
            secondaryText = 0xFF8B6740.toInt()
        )
    }

    private val panelAlpha = if (hasCustomBackground) 0.34f else 1f

    fun render(
        headerRow: LinearLayout,
        bodyRow: LinearLayout,
        metrics: TimetableMetrics.Spec,
        items: List<CourseMeetingDisplayRef>,
        onCourseClick: (CourseMeetingRef) -> Unit,
        weekStart: LocalDate,
        today: LocalDate,
        currentSection: Int?
    ) {
        headerRow.removeAllViews()
        bodyRow.removeAllViews()
        headerRow.setPadding(metrics.outerPadding, 0, metrics.outerPadding, 0)
        bodyRow.setPadding(metrics.outerPadding, 0, metrics.outerPadding, 0)

        buildHeader(headerRow, metrics, weekStart, today)
        buildBody(bodyRow, metrics, items, onCourseClick, weekStart, today, currentSection)
    }

    @SuppressLint("SetTextI18n")
    private fun buildHeader(
        headerRow: LinearLayout,
        m: TimetableMetrics.Spec,
        weekStart: LocalDate,
        today: LocalDate
    ) {
        val context = headerRow.context
        val corner = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(m.leftColumnWidth, m.headerCellHeight)
            text = String.format(Locale.getDefault(), "%d月", weekStart.monthValue)
            gravity = Gravity.CENTER
            textSize = 11f * fontScale
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.primaryText)
            background = roundedBackground(0x00000000, radius = 10f)
            includeFontPadding = false
        }
        headerRow.addView(corner)

        weekdays.forEachIndexed { index, weekday ->
            val date = weekStart.plusDays(index.toLong())
            val isToday = date == today
            val dayHeader = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(m.dayColumnWidth, m.headerCellHeight).apply {
                    marginStart = m.cellGap
                }
                text = String.format(
                    Locale.getDefault(),
                    "%s\n%s",
                    weekdayLabels[weekday] ?: "一",
                    date.format(DateTimeFormatter.ofPattern("M/d"))
                )
                gravity = Gravity.CENTER
                textSize = 11f * fontScale
                setTypeface(typeface, Typeface.BOLD)
                includeFontPadding = false
                setTextColor(palette.primaryText)
                background = if (isToday) {
                    roundedBackground(withAlpha(palette.headerToday, panelAlpha), radius = 10f)
                } else {
                    roundedBackground(withAlpha(palette.header, panelAlpha), radius = 10f)
                }
            }
            headerRow.addView(dayHeader)
        }
    }

    private fun buildBody(
        bodyRow: LinearLayout,
        m: TimetableMetrics.Spec,
        items: List<CourseMeetingDisplayRef>,
        onCourseClick: (CourseMeetingRef) -> Unit,
        weekStart: LocalDate,
        today: LocalDate,
        currentSection: Int?
    ) {
        val context = bodyRow.context
        val leftColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(m.leftColumnWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        periodRanges.forEachIndexed { index, time ->
            val periodCell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(m.leftColumnWidth, m.sectionHeight)
                background = roundedBackground(withAlpha(palette.leftColumn, panelAlpha), radius = 8f)
            }
            val label = TextView(context).apply {
                text = String.format(Locale.getDefault(), "%d", index + 1)
                textSize = 12f * fontScale
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setTypeface(typeface, Typeface.BOLD)
                includeFontPadding = false
                setTextColor(palette.primaryText)
            }
            val startTime = time.substringBefore('-')
            val endTime = time.substringAfter('-')
            val timeLabel = TextView(context).apply {
                text = startTime
                textSize = 9f * fontScale
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                includeFontPadding = false
                setTextColor(palette.secondaryText)
            }
            val timeLabelEnd = TextView(context).apply {
                text = endTime
                textSize = 9f * fontScale
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                includeFontPadding = false
                setTextColor(palette.secondaryText)
            }
            periodCell.addView(label)
            periodCell.addView(timeLabel)
            periodCell.addView(timeLabelEnd)
            leftColumn.addView(periodCell)
        }
        bodyRow.addView(leftColumn)

        weekdays.forEachIndexed { index, weekday ->
            val date = weekStart.plusDays(index.toLong())
            bodyRow.addView(buildDayColumn(bodyRow, m, weekday, items, onCourseClick, date == today, currentSection))
        }
    }

    private fun buildDayColumn(
        parent: LinearLayout,
        m: TimetableMetrics.Spec,
        weekday: Weekday,
        items: List<CourseMeetingDisplayRef>,
        onCourseClick: (CourseMeetingRef) -> Unit,
        isToday: Boolean,
        currentSection: Int?
    ): FrameLayout {
        val context = parent.context
        val columnHeight = m.sectionHeight * periodRanges.size
        val dayColumn = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(m.dayColumnWidth, columnHeight).apply {
                marginStart = m.cellGap
            }
            background = if (isToday) {
                roundedBackground(withAlpha(palette.dayToday, panelAlpha), radius = 8f)
            } else {
                roundedBackground(withAlpha(palette.dayCell, panelAlpha), radius = 8f)
            }
        }

        val dayItems = resolveOverlapForDay(items.filter { it.meeting.weekday == weekday })
        dayItems.forEach { item ->
            val start = item.meeting.startSection.coerceIn(1, 12)
            val end = item.meeting.endSection.coerceIn(start, 12)
            val isCurrentCourse = item.isCurrentWeek && isToday && currentSection != null && currentSection in start..end
            val top = (start - 1) * m.sectionHeight + 2
            val cardHeight = (end - start + 1) * m.sectionHeight - 4
            val cardWidth = m.dayColumnWidth - m.cellGap

            val shadow = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(cardWidth, cardHeight.coerceAtLeast(36)).apply {
                    topMargin = top + 4
                    leftMargin = m.cellGap / 2 + 2
                }
                background = roundedBackground(if (item.isCurrentWeek) 0x2F000000.toInt() else 0x14000000.toInt(), radius = 10f)
            }
            dayColumn.addView(shadow)

            val card = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(cardWidth, cardHeight.coerceAtLeast(36)).apply {
                    topMargin = top
                    leftMargin = m.cellGap / 2
                }
                text = buildString {
                    if (!item.isCurrentWeek) {
                        append("[非本周]")
                    }
                    append(item.course.courseName)
                    append("\n")
                    append(item.meeting.location.ifBlank { "教室待定" })
                }
                textSize = 10.3f * fontScale
                setTextColor(palette.primaryText)
                setPadding(m.cardPadding, m.cardPadding, m.cardPadding, m.cardPadding)
                if (isCurrentCourse) {
                    setTypeface(typeface, Typeface.BOLD)
                }
                alpha = if (item.isCurrentWeek) 1f else 0.55f
                background = roundedBackground(cardColors[item.courseIndex % cardColors.size])
                elevation = if (isCurrentCourse) 10f else 6f
                setOnClickListener { onCourseClick(item.toCourseMeetingRef()) }
            }
            dayColumn.addView(card)
        }

        return dayColumn
    }

    private fun resolveOverlapForDay(items: List<CourseMeetingDisplayRef>): List<CourseMeetingDisplayRef> {
        val occupied = BooleanArray(13)
        val sorted = items.sortedWith(
            compareByDescending<CourseMeetingDisplayRef> { it.isCurrentWeek }
                .thenBy { it.nextActiveWeek }
                .thenBy { it.meeting.startSection }
                .thenBy { it.courseIndex }
        )
        val selected = mutableListOf<CourseMeetingDisplayRef>()

        sorted.forEach { item ->
            val start = item.meeting.startSection.coerceIn(1, 12)
            val end = item.meeting.endSection.coerceIn(start, 12)
            val overlap = (start..end).any { section -> occupied[section] }
            if (!overlap) {
                selected.add(item)
                (start..end).forEach { section -> occupied[section] = true }
            }
        }

        return selected.sortedBy { it.meeting.startSection }
    }

    private fun roundedBackground(fill: Int, radius: Float = 8f): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
        }
    }

    private fun withAlpha(color: Int, alphaFactor: Float): Int {
        val alpha = (((color ushr 24) and 0xFF) * alphaFactor).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    companion object {
        fun resolveCurrentSection(periodRanges: List<String>, now: LocalTime = LocalTime.now()): Int? {
            periodRanges.forEachIndexed { index, range ->
                val start = runCatching { LocalTime.parse(range.substringBefore('-')) }.getOrNull() ?: return@forEachIndexed
                val end = runCatching { LocalTime.parse(range.substringAfter('-')) }.getOrNull() ?: return@forEachIndexed
                if (!now.isBefore(start) && now.isBefore(end)) {
                    return index + 1
                }
            }
            return null
        }
    }

    private data class ThemePalette(
        val header: Int,
        val headerToday: Int,
        val leftColumn: Int,
        val dayCell: Int,
        val dayToday: Int,
        val primaryText: Int,
        val secondaryText: Int
    )
}

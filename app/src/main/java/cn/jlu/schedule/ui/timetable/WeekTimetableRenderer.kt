package cn.jlu.schedule.ui.timetable

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.domain.CourseMeetingDisplayRef
import cn.jlu.schedule.domain.CourseMeetingRef
import cn.jlu.schedule.model.Weekday
import cn.jlu.schedule.ui.theme.ThemePalette
import cn.jlu.schedule.ui.theme.UiFeedback
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class WeekTimetableRenderer(
    private val periodRanges: List<String>,
    private val weekdayLabels: Map<Weekday, String>,
    private val fontScale: Float,
    private val palette: ThemePalette,
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

    // Keep enough opacity for course text to remain readable over user photos.
    private val panelAlpha = if (hasCustomBackground) 0.94f else 1f
    private val gridAlpha = if (hasCustomBackground) 0.34f else 1f
    private var density = 1f

    fun render(
        headerRow: LinearLayout,
        bodyRow: LinearLayout,
        metrics: TimetableMetrics.Spec,
        items: List<CourseMeetingDisplayRef>,
        onCourseClick: (primary: CourseMeetingRef, allItems: List<CourseMeetingRef>) -> Unit,
        weekStart: LocalDate,
        today: LocalDate,
        currentSection: Int?
    ) {
        density = headerRow.resources.displayMetrics.density
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
        val headerPanel = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, m.headerCellHeight)
            background = roundedBackground(withAlpha(palette.gridLeftColumn, panelAlpha), radius = 18f)
            clipToOutline = true
        }
        headerRow.addView(headerPanel)
        val corner = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(m.leftColumnWidth, m.headerCellHeight)
            text = String.format(Locale.getDefault(), "%d月", weekStart.monthValue)
            gravity = Gravity.CENTER
            textSize = 11f * fontScale
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.textPrimary)
            includeFontPadding = false
        }
        headerPanel.addView(corner)

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
                setTextColor(palette.textPrimary)
                background = if (isToday) {
                    roundedBackground(withAlpha(palette.gridHeaderToday, panelAlpha), radius = 18f)
                } else {
                    null
                }
            }
            headerPanel.addView(dayHeader)
        }
    }

    private fun buildBody(
        bodyRow: LinearLayout,
        m: TimetableMetrics.Spec,
        items: List<CourseMeetingDisplayRef>,
        onCourseClick: (primary: CourseMeetingRef, allItems: List<CourseMeetingRef>) -> Unit,
        weekStart: LocalDate,
        today: LocalDate,
        currentSection: Int?
    ) {
        val context = bodyRow.context
        val leftColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(m.leftColumnWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            background = roundedBackground(withAlpha(palette.gridLeftColumn, panelAlpha), radius = 18f)
        }

        periodRanges.forEachIndexed { index, time ->
            val periodCell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(m.leftColumnWidth, m.sectionHeight)
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
                setTextColor(palette.textPrimary)
            }
            val startTime = time.substringBefore('-')
            val endTime = time.substringAfter('-')
            val timeLabel = TextView(context).apply {
                text = startTime
                textSize = 10.5f * fontScale
                maxLines = 1
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                includeFontPadding = false
                setTextColor(palette.textSecondary)
            }
            val timeLabelEnd = TextView(context).apply {
                text = endTime
                textSize = 10.5f * fontScale
                maxLines = 1
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                includeFontPadding = false
                setTextColor(palette.textSecondary)
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
        onCourseClick: (primary: CourseMeetingRef, allItems: List<CourseMeetingRef>) -> Unit,
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
                roundedBackground(withAlpha(palette.gridDayToday, gridAlpha), radius = 8f)
            } else {
                roundedBackground(withAlpha(palette.gridDayCell, gridAlpha), radius = 8f)
            }
        }

        val daySlots = resolveSlotsForDay(context, weekday, items.filter { it.meeting.weekday == weekday })
        daySlots.forEach { slot ->
            val item = slot.primary
            val start = item.meeting.startSection.coerceIn(1, 12)
            val end = item.meeting.endSection.coerceIn(start, 12)
            val isCurrentCourse = item.isCurrentWeek && isToday && currentSection != null && currentSection in start..end
            val top = (start - 1) * m.sectionHeight + 2
            val cardHeight = (end - start + 1) * m.sectionHeight - 4
            val cardWidth = m.dayColumnWidth - m.cellGap

            // 1. 底层微阴影
            val shadow = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(cardWidth, cardHeight.coerceAtLeast(36)).apply {
                    topMargin = top + 4
                    leftMargin = m.cellGap / 2 + 2
                }
                background = roundedBackground(if (item.isCurrentWeek) 0x2F000000.toInt() else 0x14000000.toInt(), radius = 10f)
            }
            dayColumn.addView(shadow)

            // 2. 冲突层叠便签效果：如果存在真实重叠课程，在主卡片底层绘制微偏移的第二张卡片
            if (slot.hasConflict) {
                val secondaryItem = slot.allCourses.firstOrNull { it.course.courseName != slot.primary.course.courseName }
                    ?: slot.allCourses.getOrNull(1)
                    ?: slot.primary
                val secondaryColor = CourseCardColors.forCourse(secondaryItem.courseIndex, palette.isDark)
                val stackedCard = View(context).apply {
                    layoutParams = FrameLayout.LayoutParams(cardWidth, cardHeight.coerceAtLeast(36)).apply {
                        topMargin = top + dpToPx(context, 3f)
                        leftMargin = (m.cellGap / 2) + dpToPx(context, 3f)
                    }
                    background = roundedBackground(secondaryColor, radius = 10f)
                    UiFeedback.addPressFeedback(this, CourseCardColors.textColorFor(secondaryItem.courseIndex, palette.isDark), 10f)
                    alpha = if (secondaryItem.isCurrentWeek) 0.82f else 0.42f
                    elevation = 5f
                    setOnClickListener {
                        onCourseClick(
                            slot.primary.toCourseMeetingRef(),
                            slot.allCourses.map { it.toCourseMeetingRef() }
                        )
                    }
                }
                dayColumn.addView(stackedCard)
            }

            // 3. 主课程卡片
            val card = LinearLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(cardWidth, cardHeight.coerceAtLeast(36)).apply {
                    topMargin = top
                    leftMargin = m.cellGap / 2
                }
                orientation = LinearLayout.VERTICAL
                setPadding(m.cardPadding, m.cardPadding, m.cardPadding, m.cardPadding)
                alpha = if (item.isCurrentWeek || hasCustomBackground) 1f else 0.55f
                val courseColor = CourseCardColors.forCourse(item.courseIndex, palette.isDark)
                background = roundedBackground(
                    if (hasCustomBackground && !item.isCurrentWeek) {
                        ColorUtils.blendARGB(courseColor, palette.panelAltBackground, 0.55f)
                    } else courseColor
                )
                UiFeedback.addPressFeedback(this, CourseCardColors.textColorFor(item.courseIndex, palette.isDark), 8f)
                elevation = if (isCurrentCourse) 10f else if (slot.hasConflict) 7f else 6f
                setOnClickListener {
                    onCourseClick(
                        slot.primary.toCourseMeetingRef(),
                        slot.allCourses.map { it.toCourseMeetingRef() }
                    )
                }

                val spanCount = (end - start + 1).coerceAtLeast(1)

                // 冲突微角标：顶部紧凑胶囊标签，防止覆盖课程文字
                if (slot.hasConflict) {
                    val conflictCount = slot.allCourses.size
                    addView(TextView(context).apply {
                        text = if (conflictCount > 2) "重叠·${conflictCount}" else "重叠·2"
                        textSize = 9f * fontScale
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(CourseCardColors.textColorFor(item.courseIndex, palette.isDark))
                        background = roundedBackground(
                            if (palette.isDark) 0x33FFFFFF else 0x28000000,
                            radius = 4f
                        )
                        setPadding(dpToPx(context, 3f), dpToPx(context, 1f), dpToPx(context, 3f), dpToPx(context, 1f))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            bottomMargin = dpToPx(context, 2f)
                        }
                        includeFontPadding = false
                    })
                }

                if (!item.isCurrentWeek) {
                    addView(TextView(context).apply {
                        text = "[非本周] 第${item.nextActiveWeek}周"
                        textSize = 9f * fontScale
                        setTextColor(CourseCardColors.textColorFor(item.courseIndex, palette.isDark))
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        includeFontPadding = false
                    })
                }

                addCourseText(this, cardWidth, cardHeight.coerceAtLeast(36),
                    item.course.courseName, item.meeting.location,
                    CourseCardColors.textColorFor(item.courseIndex, palette.isDark),
                    if (spanCount >= 2) 3 else 2)
            }
            dayColumn.addView(card)

            // 封面课程比同组其他课程短时，仍要显示剩余节次的实际课程。
            slot.uncoveredSegments().forEach { segment ->
                val segmentTop = (segment.startSection - 1) * m.sectionHeight + 2
                val segmentHeight = (segment.endSection - segment.startSection + 1) * m.sectionHeight - 4
                val continuation = LinearLayout(context).apply {
                    layoutParams = FrameLayout.LayoutParams(cardWidth, segmentHeight.coerceAtLeast(36)).apply {
                        topMargin = segmentTop
                        leftMargin = m.cellGap / 2
                    }
                    orientation = LinearLayout.VERTICAL
                    setPadding(m.cardPadding, m.cardPadding, m.cardPadding, m.cardPadding)
                    background = roundedBackground(CourseCardColors.forCourse(segment.course.courseIndex, palette.isDark))
                    UiFeedback.addPressFeedback(this, CourseCardColors.textColorFor(segment.course.courseIndex, palette.isDark), 8f)
                    elevation = 6f
                    setOnClickListener {
                        onCourseClick(
                            segment.course.toCourseMeetingRef(),
                            slot.allCourses.map { it.toCourseMeetingRef() }
                        )
                    }
                    addCourseText(this, cardWidth, segmentHeight.coerceAtLeast(36),
                        segment.course.course.courseName, segment.course.meeting.location,
                        CourseCardColors.textColorFor(segment.course.courseIndex, palette.isDark), 2)
                }
                dayColumn.addView(continuation)
            }
        }

        return dayColumn
    }

    data class DayCourseSlot(
        val primary: CourseMeetingDisplayRef,
        val allCourses: List<CourseMeetingDisplayRef>,
        val hasConflict: Boolean = allCourses.map { it.course.courseName }.distinct().size > 1 && allCourses.all { it.isCurrentWeek }
    ) {
        data class Segment(val course: CourseMeetingDisplayRef, val startSection: Int, val endSection: Int)

        fun uncoveredSegments(): List<Segment> {
            if (!hasConflict) return emptyList()
            val primaryStart = primary.meeting.startSection.coerceIn(1, 12)
            val primaryEnd = primary.meeting.endSection.coerceIn(primaryStart, 12)
            val first = allCourses.minOf { it.meeting.startSection }.coerceIn(1, 12)
            val last = allCourses.maxOf { it.meeting.endSection }.coerceIn(first, 12)
            val segments = mutableListOf<Segment>()
            var section = first
            while (section <= last) {
                if (section in primaryStart..primaryEnd) {
                    section++
                    continue
                }
                val course = allCourses.asSequence()
                    .filter { section in it.meeting.startSection..it.meeting.endSection }
                    .maxWithOrNull(compareBy<CourseMeetingDisplayRef> { it.meeting.endSection - it.meeting.startSection }
                        .thenByDescending { it.courseIndex })
                if (course == null) {
                    section++
                    continue
                }
                val start = section
                while (section <= last && section !in primaryStart..primaryEnd &&
                    section in course.meeting.startSection..course.meeting.endSection
                ) {
                    section++
                }
                segments += Segment(course, start, section - 1)
            }
            return segments
        }
    }

    internal fun resolveSlotsForDay(
        context: Context?,
        weekday: Weekday,
        items: List<CourseMeetingDisplayRef>,
        pinnedCourseProvider: ((Weekday, Int) -> String?)? = null
    ): List<DayCourseSlot> {
        if (items.isEmpty()) return emptyList()

        val comparator = Comparator<CourseMeetingDisplayRef> { a, b ->
            // 1. 用户置顶封面优先
            val minSec = minOf(a.meeting.startSection, b.meeting.startSection)
            val maxSec = maxOf(a.meeting.endSection, b.meeting.endSection)
            val pinned = (minSec..maxSec).mapNotNull { sec ->
                pinnedCourseProvider?.invoke(weekday, sec)
                    ?: context?.let { AppPreferences.getPinnedCourse(it, weekday, sec) }
            }.firstOrNull()

            if (pinned != null) {
                val aMatch = a.course.courseName == pinned
                val bMatch = b.course.courseName == pinned
                if (aMatch && !bMatch) return@Comparator -1
                if (!aMatch && bMatch) return@Comparator 1
            }

            // 2. 本周有课优先
            if (a.isCurrentWeek != b.isCurrentWeek) {
                return@Comparator if (a.isCurrentWeek) -1 else 1
            }

            // 3. 距离当前周更近优先
            if (a.nextActiveWeek != b.nextActiveWeek) {
                return@Comparator a.nextActiveWeek.compareTo(b.nextActiveWeek)
            }

            // 4. 节次跨度长优先（如 1-4 节长课覆盖 1-2 节，避免下半截留白）
            val spanA = a.meeting.endSection - a.meeting.startSection
            val spanB = b.meeting.endSection - b.meeting.startSection
            if (spanA != spanB) {
                return@Comparator spanB.compareTo(spanA)
            }

            // 5. 稳定顺序
            a.courseIndex.compareTo(b.courseIndex)
        }

        // 步骤 1：严格区分当前周有效课程与非当前周课程
        val currentWeekItems = items.filter { it.isCurrentWeek }
        val nonCurrentWeekItems = items.filter { !it.isCurrentWeek }

        // 步骤 2：对当前周课程进行连通簇聚类（仅当前周真实重叠的不同课程才作为冲突）
        val currentClusters = mutableListOf<MutableList<CourseMeetingDisplayRef>>()
        val remainingCurrent = currentWeekItems.toMutableList()

        while (remainingCurrent.isNotEmpty()) {
            val current = remainingCurrent.removeAt(0)
            val cluster = mutableListOf(current)

            var addedAny = true
            while (addedAny) {
                addedAny = false
                val iterator = remainingCurrent.iterator()
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    val overlaps = cluster.any { member ->
                        val aStart = member.meeting.startSection.coerceIn(1, 12)
                        val aEnd = member.meeting.endSection.coerceIn(aStart, 12)
                        val bStart = candidate.meeting.startSection.coerceIn(1, 12)
                        val bEnd = candidate.meeting.endSection.coerceIn(bStart, 12)
                        maxOf(aStart, bStart) <= minOf(aEnd, bEnd)
                    }
                    if (overlaps) {
                        cluster.add(candidate)
                        iterator.remove()
                        addedAny = true
                    }
                }
            }
            currentClusters.add(cluster)
        }

        // 生成当前周课程的 Slots，并记录已被当前周课程占用的节次
        val occupiedByCurrent = BooleanArray(13) // index 1..12
        val currentSlots = currentClusters.map { cluster ->
            val sortedCluster = cluster.sortedWith(comparator)
            // 同名课程仅保留最优项，不同名课程才是真正冲突
            val distinctCourses = sortedCluster.distinctBy { it.course.courseName }
            val primary = distinctCourses.first()

            cluster.forEach { item ->
                val s = item.meeting.startSection.coerceIn(1, 12)
                val e = item.meeting.endSection.coerceIn(s, 12)
                for (sec in s..e) {
                    occupiedByCurrent[sec] = true
                }
            }

            val hasConflict = distinctCourses.size > 1
            DayCourseSlot(
                primary = primary,
                allCourses = distinctCourses,
                hasConflict = hasConflict
            )
        }

        // 步骤 3：处理非当前周课程
        // 凡是节次已被当前周课程占用的，一律遮蔽（当前周已有课，绝不与非本周课产生冲突或重叠）
        val freeNonCurrentItems = nonCurrentWeekItems.filter { item ->
            val s = item.meeting.startSection.coerceIn(1, 12)
            val e = item.meeting.endSection.coerceIn(s, 12)
            (s..e).none { occupiedByCurrent[it] }
        }

        // 对空闲节次的非当前周课程进行连通簇聚类
        val nonCurrentClusters = mutableListOf<MutableList<CourseMeetingDisplayRef>>()
        val remainingNonCurrent = freeNonCurrentItems.toMutableList()

        while (remainingNonCurrent.isNotEmpty()) {
            val current = remainingNonCurrent.removeAt(0)
            val cluster = mutableListOf(current)

            var addedAny = true
            while (addedAny) {
                addedAny = false
                val iterator = remainingNonCurrent.iterator()
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    val overlaps = cluster.any { member ->
                        val aStart = member.meeting.startSection.coerceIn(1, 12)
                        val aEnd = member.meeting.endSection.coerceIn(aStart, 12)
                        val bStart = candidate.meeting.startSection.coerceIn(1, 12)
                        val bEnd = candidate.meeting.endSection.coerceIn(bStart, 12)
                        maxOf(aStart, bStart) <= minOf(aEnd, bEnd)
                    }
                    if (overlaps) {
                        cluster.add(candidate)
                        iterator.remove()
                        addedAny = true
                    }
                }
            }
            nonCurrentClusters.add(cluster)
        }

        val nonCurrentSlots = nonCurrentClusters.map { cluster ->
            val sorted = cluster.sortedWith(comparator)
            val best = sorted.first()
            DayCourseSlot(
                primary = best,
                allCourses = listOf(best),
                hasConflict = false
            )
        }

        return (currentSlots + nonCurrentSlots).sortedBy { it.primary.meeting.startSection }
    }

    private fun dpToPx(context: Context, dp: Float): Int =
        (dp * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun roundedBackground(fill: Int, radius: Float = 8f): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius * density
            setColor(fill)
            if ((fill ushr 24) > 0x40) {
                setStroke(1, ColorUtils.setAlphaComponent(palette.glassHighlight, if (palette.isDark) 0x35 else 0x50))
            }
        }
    }

    /** Allocate lines from actual card space instead of making long names increasingly tiny. */
    private fun addCourseText(
        card: LinearLayout, width: Int, height: Int,
        name: String, location: String, color: Int, preferredNameLines: Int
    ) {
        val contentWidth = (width - card.paddingLeft - card.paddingRight).coerceAtLeast(1)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.AT_MOST)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        var availableHeight = height - card.paddingTop - card.paddingBottom
        for (index in 0 until card.childCount) {
            val badge = card.getChildAt(index)
            badge.measure(widthSpec, heightSpec)
            val margins = badge.layoutParams as? ViewGroup.MarginLayoutParams
            availableHeight -= badge.measuredHeight + (margins?.topMargin ?: 0) + (margins?.bottomMargin ?: 0)
        }
        fun textView(value: String, size: Float) = TextView(card.context).apply {
            text = value
            textSize = size * fontScale
            setTextColor(color)
            includeFontPadding = false
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val title = textView(name, 12f).apply { setTypeface(typeface, Typeface.BOLD) }
        val place = textView(location.ifBlank { "教室待定" }, 10.5f)
        val gap = dpToPx(card.context, 2f)
        title.maxLines = ((availableHeight - place.lineHeight - gap) / title.lineHeight.coerceAtLeast(1))
            .coerceIn(1, preferredNameLines)
        title.measure(View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY), heightSpec)
        card.addView(title)
        val locationLines = ((availableHeight - title.measuredHeight - gap) / place.lineHeight.coerceAtLeast(1)).coerceIn(0, 2)
        if (locationLines > 0) {
            place.maxLines = locationLines
            (place.layoutParams as LinearLayout.LayoutParams).topMargin = gap
            card.addView(place)
        }
        // Full values remain available through accessibility and the existing course-detail sheet.
        card.contentDescription = listOfNotNull(card.contentDescription?.toString(), name,
            location.ifBlank { "教室待定" }).joinToString("，")
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
}

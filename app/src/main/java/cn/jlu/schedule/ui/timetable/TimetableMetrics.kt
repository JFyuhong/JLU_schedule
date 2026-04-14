package cn.jlu.schedule.ui.timetable

import android.content.Context

object TimetableMetrics {
    data class Spec(
        val leftColumnWidth: Int,
        val dayColumnWidth: Int,
        val headerCellHeight: Int,
        val sectionHeight: Int,
        val cellGap: Int,
        val cardPadding: Int,
        val outerPadding: Int
    )

    fun create(context: Context): Spec {
        val density = context.resources.displayMetrics.density
        val screenWidthPx = context.resources.displayMetrics.widthPixels
        val screenHeightPx = context.resources.displayMetrics.heightPixels

        val baseWidthDp = 411f
        val scale = (screenWidthPx / (baseWidthDp * density)).coerceIn(0.82f, 1.18f)

        val outerPadding = (8f * density).toInt()
        val gap = (4f * density).toInt()
        val leftWidth = (50f * density * scale).toInt()
        val available = screenWidthPx - outerPadding * 2 - leftWidth - gap * 7
        val dayWidth = (available / 7).coerceAtLeast((38f * density).toInt())

        val sectionHeightByWidth = (62f * density * scale).toInt()
        val sectionHeightByHeight = ((screenHeightPx * 0.78f) / 12f).toInt()
        val sectionHeight = minOf(sectionHeightByWidth, sectionHeightByHeight).coerceAtLeast((44f * density).toInt())

        return Spec(
            leftColumnWidth = leftWidth,
            dayColumnWidth = dayWidth,
            headerCellHeight = (42f * density * scale).toInt(),
            sectionHeight = sectionHeight,
            cellGap = gap,
            cardPadding = (4f * density * scale).toInt().coerceAtLeast((2f * density).toInt()),
            outerPadding = outerPadding
        )
    }
}

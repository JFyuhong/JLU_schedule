package cn.jlu.schedule.ui.timetable

import android.content.Context
import cn.jlu.schedule.data.AppPreferences

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
        val textScale = context.resources.configuration.fontScale * AppPreferences.getTimetableFontScale(context)

        val baseWidthDp = 411f
        val scale = (screenWidthPx / (baseWidthDp * density)).coerceIn(0.82f, 1.18f)

        val outerPadding = (8f * density).toInt()
        val gap = (4f * density).toInt()
        val leftWidth = (50f * density * scale).toInt()
        val available = screenWidthPx - outerPadding * 2 - leftWidth - gap * 7
        // All seven days must stay inside the viewport, including narrow windows.
        val dayWidth = (available / 7).coerceAtLeast(1)

        val sectionHeightByWidth = (62f * density * scale).toInt()
        val sectionHeightByHeight = ((screenHeightPx * 0.78f) / 12f).toInt()
        // A section contains its number plus two time labels. Let the body scroll
        // instead of clipping these three lines in landscape or at larger font sizes.
        val minimumSectionHeight = (maxOf(44f, 42f * textScale + 6f) * density).toInt()
        val sectionHeight = minOf(sectionHeightByWidth, sectionHeightByHeight).coerceAtLeast(minimumSectionHeight)

        return Spec(
            leftColumnWidth = leftWidth,
            dayColumnWidth = dayWidth,
            headerCellHeight = (maxOf(42f * scale, 30f * textScale + 8f) * density).toInt(),
            sectionHeight = sectionHeight,
            cellGap = gap,
            cardPadding = (4f * density * scale).toInt().coerceAtLeast((2f * density).toInt()),
            outerPadding = outerPadding
        )
    }
}

package cn.jlu.schedule.data

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeParseException

object AppPreferences {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_DEFAULT_OPEN_PAGE = "default_open_page"
    private const val KEY_CUSTOM_BACKGROUND_URI = "custom_background_uri"
    private const val KEY_SEMESTER_START_DATE = "semester_start_date"
    private const val KEY_THEME_COLOR = "theme_color"
    private const val KEY_TIMETABLE_FONT_SCALE = "timetable_font_scale"
    private const val KEY_SHOW_NON_CURRENT_COURSES = "show_non_current_courses"

    const val PAGE_TIMETABLE = "timetable"
    const val PAGE_TODAY = "today"
    const val THEME_WARM = "warm"
    const val THEME_OCEAN = "ocean"
    const val THEME_MINT = "mint"

    fun getDefaultOpenPage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEFAULT_OPEN_PAGE, PAGE_TIMETABLE) ?: PAGE_TIMETABLE
    }

    fun setDefaultOpenPage(context: Context, page: String) {
        val safeValue = when (page) {
            PAGE_TODAY -> PAGE_TODAY
            else -> PAGE_TIMETABLE
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEFAULT_OPEN_PAGE, safeValue)
            .apply()
    }

    fun getCustomBackgroundUri(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_BACKGROUND_URI, null)
    }

    fun setCustomBackgroundUri(context: Context, uri: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_BACKGROUND_URI, uri)
            .apply()
    }

    fun getSemesterStartDate(context: Context): LocalDate {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SEMESTER_START_DATE, null)
        if (raw.isNullOrBlank()) {
            return defaultSemesterStart()
        }
        return try {
            LocalDate.parse(raw)
        } catch (_: DateTimeParseException) {
            defaultSemesterStart()
        }
    }

    fun setSemesterStartDate(context: Context, date: LocalDate) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SEMESTER_START_DATE, date.toString())
            .apply()
    }

    fun getThemeColor(context: Context): String {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_COLOR, THEME_WARM)
        return when (raw) {
            THEME_OCEAN, THEME_MINT -> raw
            else -> THEME_WARM
        }
    }

    fun setThemeColor(context: Context, theme: String) {
        val safe = when (theme) {
            THEME_OCEAN, THEME_MINT -> theme
            else -> THEME_WARM
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_COLOR, safe)
            .apply()
    }

    fun getTimetableFontScale(context: Context): Float {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_TIMETABLE_FONT_SCALE, 0.88f)
        return when {
            saved < 0.95f -> 0.88f
            saved > 1.08f -> 1.14f
            else -> 1.0f
        }
    }

    fun setTimetableFontScale(context: Context, scale: Float) {
        val safe = when {
            scale < 0.95f -> 0.88f
            scale > 1.08f -> 1.14f
            else -> 1.0f
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_TIMETABLE_FONT_SCALE, safe)
            .apply()
    }

    fun isShowNonCurrentCourses(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_NON_CURRENT_COURSES, true)
    }

    fun setShowNonCurrentCourses(context: Context, show: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_NON_CURRENT_COURSES, show)
            .apply()
    }

    private fun defaultSemesterStart(): LocalDate {
        return LocalDate.now().withMonth(2).withDayOfMonth(24)
    }
}

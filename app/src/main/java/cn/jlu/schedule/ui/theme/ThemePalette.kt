package cn.jlu.schedule.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import cn.jlu.schedule.R
import cn.jlu.schedule.data.AppPreferences

data class ThemePalette(
    val pageBackground: Int,
    val navBackground: Int,
    val panelBackground: Int,
    val panelAltBackground: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val iconTint: Int,
    val buttonBackground: Int,
    val buttonText: Int,
    val gridHeader: Int,
    val gridHeaderToday: Int,
    val gridLeftColumn: Int,
    val gridDayCell: Int,
    val gridDayToday: Int,
    val detailCard: Int,
    val detailTitle: Int,
    val detailBody: Int,
    val detailMeta: Int,
    val isDark: Boolean
)

object ThemePaletteProvider {
    fun fromContext(context: Context): ThemePalette {
        return fromTheme(context, AppPreferences.getThemeColor(context))
    }

    fun fromTheme(context: Context, theme: String): ThemePalette {
        val prefix = when (theme) {
            AppPreferences.THEME_OCEAN -> "ocean"
            AppPreferences.THEME_MINT -> "mint"
            else -> "warm"
        }
        fun color(suffix: String): Int {
            val id = context.resources.getIdentifier(
                "${prefix}_$suffix", "color", context.packageName
            )
            return context.getColor(if (id != 0) id else R.color.warm_page_background)
        }
        val isDark = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val panel = color("panel_background")
        val panelAlt = color("panel_alt_background")
        val icon = color("icon_tint")
        return ThemePalette(
            pageBackground = color("page_background"),
            navBackground = color("nav_background"),
            panelBackground = panel,
            panelAltBackground = panelAlt,
            textPrimary = color("text_primary"),
            textSecondary = color("text_secondary"),
            iconTint = icon,
            buttonBackground = color("button_background"),
            buttonText = color("button_text"),
            gridHeader = color("grid_header"),
            gridHeaderToday = color("grid_header_today"),
            gridLeftColumn = color("grid_left_column"),
            gridDayCell = color("grid_day_cell"),
            gridDayToday = color("grid_day_today"),
            detailCard = color("detail_card"),
            detailTitle = color("detail_title"),
            detailBody = color("detail_body"),
            detailMeta = color("detail_meta"),
            isDark = isDark
        )
    }

    fun themeStyleFor(theme: String): Int {
        return when (theme) {
            AppPreferences.THEME_OCEAN -> R.style.Theme_JLU_Ocean
            AppPreferences.THEME_MINT -> R.style.Theme_JLU_Mint
            else -> R.style.Theme_JLU_Warm
        }
    }

    /** 按用户偏好（跟随系统/浅色/深色）应用全局夜间模式，配置变化时 Activity 自动重建 */
    fun applyNightMode(context: Context) {
        val mode = when (AppPreferences.getDarkMode(context)) {
            AppPreferences.DARK_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppPreferences.DARK_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}

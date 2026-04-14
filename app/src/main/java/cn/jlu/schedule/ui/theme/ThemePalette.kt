package cn.jlu.schedule.ui.theme

import android.content.Context
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
    val buttonText: Int
)

object ThemePaletteProvider {
    fun fromContext(context: Context): ThemePalette {
        return fromTheme(AppPreferences.getThemeColor(context))
    }

    fun fromTheme(theme: String): ThemePalette {
        return when (theme) {
            AppPreferences.THEME_OCEAN -> ThemePalette(
                pageBackground = 0xFFEAF5FF.toInt(),
                navBackground = 0xD9DCEEFF.toInt(),
                panelBackground = 0xFFE1F1FF.toInt(),
                panelAltBackground = 0xFFF0F8FF.toInt(),
                textPrimary = 0xFF1F3A56.toInt(),
                textSecondary = 0xFF3E5876.toInt(),
                iconTint = 0xFF2E4E72.toInt(),
                buttonBackground = 0xFFD6E7FA.toInt(),
                buttonText = 0xFF1F3A56.toInt()
            )

            AppPreferences.THEME_MINT -> ThemePalette(
                pageBackground = 0xFFE8FAF2.toInt(),
                navBackground = 0xD9D6F5E5.toInt(),
                panelBackground = 0xFFDCF4E8.toInt(),
                panelAltBackground = 0xFFECF9F2.toInt(),
                textPrimary = 0xFF1D4A3A.toInt(),
                textSecondary = 0xFF396458.toInt(),
                iconTint = 0xFF2D5A4E.toInt(),
                buttonBackground = 0xFFCFEADF.toInt(),
                buttonText = 0xFF1D4A3A.toInt()
            )

            else -> ThemePalette(
                pageBackground = 0xFFF7E7CC.toInt(),
                navBackground = 0xD9FFF2DA.toInt(),
                panelBackground = 0xFFFFE2BF.toInt(),
                panelAltBackground = 0xFFFFEED8.toInt(),
                textPrimary = 0xFF5C3C1E.toInt(),
                textSecondary = 0xFF7A5A37.toInt(),
                iconTint = 0xFF5A3D1F.toInt(),
                buttonBackground = 0xFFEFD7B1.toInt(),
                buttonText = 0xFF3A2A1A.toInt()
            )
        }
    }
}

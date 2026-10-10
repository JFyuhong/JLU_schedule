package cn.jlu.schedule.ui.theme

import android.graphics.drawable.GradientDrawable
import android.view.View
import cn.jlu.schedule.data.AppPreferences

/** Keep text on a stable theme surface while leaving the surrounding photo visible. */
object BackgroundReadability {
    fun protectPanel(view: View, palette: ThemePalette, radiusDp: Float = 16f) {
        val enabled = !AppPreferences.getCustomBackgroundUri(view.context).isNullOrBlank()
        view.background = if (enabled) {
            GradientDrawable().apply {
                cornerRadius = radiusDp * view.resources.displayMetrics.density
                setColor(palette.panelAltBackground)
            }
        } else null
    }
}

package cn.jlu.schedule.ui.theme

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.graphics.ColorUtils

/** Opaque reading surfaces keep theme contrast predictable, including over custom photos. */
object SurfaceStyle {
    enum class Variant { Panel, Strong, Control }

    fun drawable(
        context: Context,
        palette: ThemePalette,
        variant: Variant = Variant.Panel,
        radiusDp: Float = if (variant == Variant.Control) 12f else 16f
    ): GradientDrawable {
        val fill = when (variant) {
            Variant.Panel -> palette.panelBackground
            Variant.Strong -> palette.panelAltBackground
            Variant.Control -> palette.buttonBackground
        }
        return GradientDrawable().apply {
            cornerRadius = radiusDp * context.resources.displayMetrics.density
            setColor(ColorUtils.setAlphaComponent(fill, 255))
            if (variant != Variant.Control) {
                setStroke(1, ColorUtils.blendARGB(fill, palette.textPrimary, 0.08f))
            }
        }
    }

    fun apply(view: View, palette: ThemePalette, variant: Variant = Variant.Panel, radiusDp: Float = 16f) {
        view.background = drawable(view.context, palette, variant, radiusDp)
    }
}

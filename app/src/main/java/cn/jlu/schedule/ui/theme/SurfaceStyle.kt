package cn.jlu.schedule.ui.theme

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            // Seed the fill alpha before the first outline is read; setColors alone
            // leaves a newly created drawable's paint transparent until its first draw.
            setColor(ColorUtils.setAlphaComponent(fill, 255))
            colors = intArrayOf(
                ColorUtils.blendARGB(fill, Color.WHITE, if (palette.isDark) 0.035f else 0.14f),
                ColorUtils.setAlphaComponent(fill, 255)
            )
            setStroke(1, ColorUtils.blendARGB(fill, Color.WHITE, if (palette.isDark) 0.10f else 0.42f))
        }
    }

    fun apply(view: View, palette: ThemePalette, variant: Variant = Variant.Panel, radiusDp: Float = 16f) {
        view.background = drawable(view.context, palette, variant, radiusDp)
        addDepth(view, palette, if (variant == Variant.Control) 1.5f else 2f)
    }

    /** A small native shadow separates surfaces without making the photo translucent. */
    fun addDepth(view: View, palette: ThemePalette, elevationDp: Float = 2f) {
        view.elevation = elevationDp * view.resources.displayMetrics.density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val shadow = if (palette.isDark) Color.BLACK else palette.textPrimary
            // Android already attenuates native shadows with the theme shadow alpha.
            view.outlineAmbientShadowColor = ColorUtils.setAlphaComponent(shadow, 0xAA)
            view.outlineSpotShadowColor = ColorUtils.setAlphaComponent(shadow, 0xCC)
        }
    }
}

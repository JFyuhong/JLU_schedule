package cn.jlu.schedule.ui.theme

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import cn.jlu.schedule.data.AppPreferences
import androidx.core.graphics.ColorUtils

/** Shared drawable factory for the app's restrained liquid-glass surfaces. */
object GlassSurface {
    enum class Variant { Panel, Strong, Control, Navigation }

    fun drawable(
        context: Context,
        palette: ThemePalette,
        variant: Variant = Variant.Panel,
        radiusDp: Float = when (variant) {
            Variant.Navigation -> 32f
            Variant.Control -> 18f
            else -> 24f
        }
    ): GradientDrawable {
        val base = when (variant) {
            Variant.Strong -> palette.glassSurfaceStrong
            Variant.Control -> palette.buttonBackground
            else -> palette.glassSurface
        }
        val hasPhoto = !AppPreferences.getCustomBackgroundUri(context).isNullOrBlank()
        val alpha = if (hasPhoto) 0xF0 else when (variant) {
            Variant.Strong -> if (palette.isDark) 0xE8 else 0xD9
            Variant.Control -> if (palette.isDark) 0xD8 else 0xC9
            Variant.Navigation -> if (palette.isDark) 0xE0 else 0xD0
            Variant.Panel -> if (palette.isDark) 0xD0 else 0xB8
        }
        val fill = ColorUtils.setAlphaComponent(base, alpha)
        val stroke = ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(palette.glassStroke, palette.glassHighlight, 0.35f),
            if (palette.isDark) 0x88 else 0x70
        )
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * context.resources.displayMetrics.density
            setColor(fill)
            setStroke(context.resources.displayMetrics.density.coerceAtLeast(1f).toInt(), stroke)
            if (variant == Variant.Navigation) {
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(
                    ColorUtils.setAlphaComponent(
                        ColorUtils.blendARGB(base, Color.WHITE, if (palette.isDark) 0.07f else 0.55f), alpha
                    ),
                    fill
                )
                setStroke(context.resources.displayMetrics.density.coerceAtLeast(1f).toInt(),
                    ColorUtils.setAlphaComponent(palette.glassHighlight, if (palette.isDark) 0x48 else 0xB0))
            }
        }
    }

    /** Highlight the whole tab (icon and label) inside the floating capsule. */
    fun dockItem(context: Context, palette: ThemePalette): InsetDrawable {
        val density = context.resources.displayMetrics.density
        val selected = drawable(context, palette, Variant.Control, 28f).apply {
            setColor(ColorUtils.setAlphaComponent(palette.gridHeaderToday, if (palette.isDark) 0xE0 else 0xC0))
            setStroke(density.coerceAtLeast(1f).toInt(),
                ColorUtils.setAlphaComponent(palette.glassHighlight, if (palette.isDark) 0x40 else 0xA0))
        }
        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), selected)
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
        val mask = GradientDrawable().apply {
            cornerRadius = 28f * density
            setColor(Color.WHITE)
        }
        val ripple = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.iconTint, 0x24)), states, mask
        )
        return InsetDrawable(ripple, (2f * density).toInt(), (4f * density).toInt(),
            (2f * density).toInt(), (4f * density).toInt())
    }

    fun apply(view: View, palette: ThemePalette, variant: Variant = Variant.Panel, radiusDp: Float? = null) {
        view.background = drawable(view.context, palette, variant, radiusDp ?: when (variant) {
            Variant.Navigation -> 32f
            Variant.Control -> 18f
            else -> 24f
        })
    }
}

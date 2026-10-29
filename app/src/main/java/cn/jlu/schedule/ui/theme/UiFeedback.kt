package cn.jlu.schedule.ui.theme

import android.view.View
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import com.google.android.material.snackbar.Snackbar

object UiFeedback {
    /** Keep touch feedback above the content and within the rounded surface. */
    fun addPressFeedback(view: View, color: Int, radiusDp: Float) {
        val mask = GradientDrawable().apply {
            cornerRadius = radiusDp * view.resources.displayMetrics.density
            setColor(Color.WHITE)
        }
        view.foreground = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x28)), null, mask
        )
        view.stateListAnimator = StateListAnimator().apply {
            addState(intArrayOf(android.R.attr.state_pressed),
                ObjectAnimator.ofFloat(view, "translationZ", -view.resources.displayMetrics.density).setDuration(100))
            addState(intArrayOf(), ObjectAnimator.ofFloat(view, "translationZ", 0f).setDuration(140))
        }
        view.isFocusable = true
    }

    fun showMessage(anchor: View?, message: String, palette: ThemePalette) {
        if (anchor == null) return
        Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(palette.panelBackground)
            .setTextColor(palette.textPrimary)
            .setActionTextColor(palette.iconTint)
            .setAction("知道了") { }
            .show()
    }

    fun stylePrimaryButton(button: Button, palette: ThemePalette) {
        button.backgroundTintList = null
        button.background = SurfaceStyle.drawable(button.context, palette, SurfaceStyle.Variant.Control, 12f)
        button.setTextColor(palette.buttonText)
        button.isAllCaps = false
        finishButton(button, palette)
    }

    fun styleSecondaryButton(button: Button, palette: ThemePalette) {
        button.backgroundTintList = null
        button.background = SurfaceStyle.drawable(button.context, palette, SurfaceStyle.Variant.Panel, 12f)
        button.setTextColor(palette.textSecondary)
        button.isAllCaps = false
        finishButton(button, palette)
    }

    fun styleDangerButton(button: Button, palette: ThemePalette) {
        button.backgroundTintList = null
        val danger = if (palette.isDark) {
            ColorUtils.blendARGB(0xFF8B2525.toInt(), palette.panelBackground, 0.45f)
        } else {
            ColorUtils.blendARGB(0xFFD35454.toInt(), palette.buttonBackground, 0.45f)
        }
        val textColor = if (palette.isDark) 0xFFEF9A9A.toInt() else 0xFFFFFFFF.toInt()
        button.background = SurfaceStyle.drawable(button.context, palette, SurfaceStyle.Variant.Control, 12f).apply {
            setColor(danger)
            setStroke(1, ColorUtils.setAlphaComponent(palette.textPrimary, 0x55))
        }
        button.setTextColor(textColor)
        button.isAllCaps = false
        finishButton(button, palette)
    }

    private fun finishButton(button: Button, palette: ThemePalette) {
        val density = button.resources.displayMetrics.density
        SurfaceStyle.addDepth(button, palette, 1.5f)
        button.minHeight = (48 * density).toInt()
        button.minimumHeight = button.minHeight
        button.setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())
        addPressFeedback(button, palette.iconTint, 12f)
    }

    fun styleInput(input: android.widget.EditText, palette: ThemePalette) {
        input.background = SurfaceStyle.drawable(input.context, palette, SurfaceStyle.Variant.Control, 12f)
        input.setTextColor(palette.textPrimary)
        input.setHintTextColor(palette.textSecondary)
    }

    fun styleDialogSurface(dialog: AlertDialog, palette: ThemePalette) {
        dialog.window?.setBackgroundDrawable(
            SurfaceStyle.drawable(dialog.context, palette, SurfaceStyle.Variant.Strong, 16f)
        )
    }
}

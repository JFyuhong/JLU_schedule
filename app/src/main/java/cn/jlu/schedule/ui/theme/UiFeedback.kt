package cn.jlu.schedule.ui.theme

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import com.google.android.material.snackbar.Snackbar

object UiFeedback {
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
        button.background = roundedDrawable(
            fillColor = palette.buttonBackground,
            strokeColor = ColorUtils.blendARGB(palette.buttonBackground, palette.iconTint, 0.25f)
        )
        button.setTextColor(palette.buttonText)
        button.isAllCaps = false
        button.minHeight = 0
        button.minimumHeight = 0
        button.setPadding(30, 18, 30, 18)
    }

    fun styleSecondaryButton(button: Button, palette: ThemePalette) {
        button.background = roundedDrawable(
            fillColor = palette.panelAltBackground,
            strokeColor = ColorUtils.blendARGB(palette.panelAltBackground, palette.iconTint, 0.35f)
        )
        button.setTextColor(palette.textSecondary)
        button.isAllCaps = false
        button.minHeight = 0
        button.minimumHeight = 0
        button.setPadding(28, 16, 28, 16)
    }

    fun styleDangerButton(button: Button, palette: ThemePalette) {
        val danger = ColorUtils.blendARGB(0xFFD35454.toInt(), palette.buttonBackground, 0.45f)
        button.background = roundedDrawable(
            fillColor = danger,
            strokeColor = ColorUtils.blendARGB(danger, palette.textPrimary, 0.25f)
        )
        button.setTextColor(0xFFFFFFFF.toInt())
        button.isAllCaps = false
        button.minHeight = 0
        button.minimumHeight = 0
        button.setPadding(28, 16, 28, 16)
    }

    fun styleDialogSurface(dialog: AlertDialog, palette: ThemePalette) {
        dialog.window?.setBackgroundDrawable(
            roundedDrawable(
                fillColor = palette.panelAltBackground,
                strokeColor = ColorUtils.blendARGB(palette.panelAltBackground, palette.iconTint, 0.22f)
            )
        )
    }

    private fun roundedDrawable(fillColor: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18f
            setColor(fillColor)
            setStroke(2, strokeColor)
        }
    }
}

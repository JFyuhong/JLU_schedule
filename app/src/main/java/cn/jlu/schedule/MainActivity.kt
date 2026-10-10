package cn.jlu.schedule

import android.graphics.ImageDecoder
import android.graphics.BitmapFactory
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import cn.jlu.schedule.update.model.UpdateCheckResult
import cn.jlu.schedule.update.repository.UpdateRepository
import cn.jlu.schedule.update.ui.UpdateDialogHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.data.ScheduleRepository
import cn.jlu.schedule.ui.settings.SettingsFragment
import cn.jlu.schedule.ui.timetable.TimetableFragment
import cn.jlu.schedule.ui.theme.ThemePaletteProvider
import cn.jlu.schedule.ui.today.TodayScheduleFragment
import cn.jlu.schedule.ui.tools.ToolsFragment

class MainActivity : AppCompatActivity() {
    private lateinit var rootContainer: FrameLayout
    private lateinit var backgroundImage: ImageView
    private lateinit var backgroundScrim: View
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePaletteProvider.applyNightMode(this)
        setTheme(ThemePaletteProvider.themeStyleFor(AppPreferences.getThemeColor(this)))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootContainer = findViewById(R.id.rootContainer)
        backgroundImage = findViewById(R.id.backgroundImage)
        backgroundScrim = findViewById(R.id.backgroundScrim)
        bottomNav = findViewById(R.id.bottomNav)
        applyUserAppearance()
        refreshCustomBackground()
        ScheduleRepository.refresh(this)

        lifecycleScope.launch {
            delay(1500)
            if (isFinishing || isDestroyed) return@launch
            val updateRepo = UpdateRepository()
            val result = updateRepo.checkAutoUpdate(this@MainActivity)
            if (isFinishing || isDestroyed) return@launch
            if (result is UpdateCheckResult.UpdateAvailable) {
                UpdateDialogHelper.showUpdateDialog(
                    activity = this@MainActivity,
                    payload = result.payload,
                    preferredMirror = result.preferredMirror
                )
            }
        }

        if (savedInstanceState == null) {
            val defaultPage = AppPreferences.getDefaultOpenPage(this)
            if (defaultPage == AppPreferences.PAGE_TODAY) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.mainContainer, TodayScheduleFragment())
                    .commit()
                bottomNav.selectedItemId = R.id.nav_today
            } else {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.mainContainer, TimetableFragment())
                    .commit()
                bottomNav.selectedItemId = R.id.nav_timetable
            }
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_timetable -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.mainContainer, TimetableFragment())
                        .commit()
                    true
                }

                R.id.nav_today -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.mainContainer, TodayScheduleFragment())
                        .commit()
                    true
                }

                R.id.nav_tools -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.mainContainer, ToolsFragment())
                        .commit()
                    true
                }

                R.id.nav_settings -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.mainContainer, SettingsFragment())
                        .commit()
                    true
                }

                else -> false
            }
        }
    }

    fun refreshCustomBackground() {
        val uri = AppPreferences.getCustomBackgroundUri(this)?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (uri == null) {
            backgroundImage.setImageDrawable(null)
            backgroundImage.visibility = ImageView.GONE
            backgroundScrim.visibility = View.GONE
            applyUserAppearance()
            return
        }

        val bitmap = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val targetW = resources.displayMetrics.widthPixels.coerceAtLeast(1)
                    val targetH = resources.displayMetrics.heightPixels.coerceAtLeast(1)
                    val srcW = info.size.width.coerceAtLeast(1)
                    val srcH = info.size.height.coerceAtLeast(1)
                    val sample = maxOf(srcW / targetW, srcH / targetH).coerceAtLeast(1)
                    decoder.setTargetSampleSize(sample)
                }
            } else {
                @Suppress("DEPRECATION")
                contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeStream(stream, null, options)
                }
            }
        }.getOrNull()

        if (bitmap == null) {
            backgroundImage.setImageDrawable(null)
            backgroundImage.visibility = ImageView.GONE
            backgroundScrim.visibility = View.GONE
            applyUserAppearance()
            return
        }

        backgroundImage.setImageBitmap(bitmap)
        backgroundImage.visibility = ImageView.VISIBLE
        backgroundScrim.visibility = View.VISIBLE
        applyUserAppearance()
    }

    fun applyUserAppearance() {
        val palette = ThemePaletteProvider.fromContext(this)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !palette.isDark
            isAppearanceLightNavigationBars = !palette.isDark
        }
        rootContainer.setBackgroundColor(palette.pageBackground)
        // A light tint reduces photo detail in gaps; text itself has opaque reading panels.
        backgroundScrim.setBackgroundColor(ColorUtils.setAlphaComponent(palette.pageBackground, 72))
        bottomNav.setBackgroundColor(
            if (backgroundImage.isVisible) ColorUtils.setAlphaComponent(palette.navBackground, 255)
            else palette.navBackground
        )
        val itemColors = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(palette.buttonText, palette.textSecondary)
        )
        bottomNav.itemIconTintList = itemColors
        bottomNav.itemTextColor = itemColors
    }
}

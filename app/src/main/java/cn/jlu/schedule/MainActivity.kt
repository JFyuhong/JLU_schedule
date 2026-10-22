package cn.jlu.schedule

import android.graphics.ImageDecoder
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import cn.jlu.schedule.update.model.UpdateCheckResult
import cn.jlu.schedule.update.repository.UpdateRepository
import cn.jlu.schedule.update.ui.UpdateDialogHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.data.ScheduleRepository
import cn.jlu.schedule.ui.settings.SettingsFragment
import cn.jlu.schedule.ui.timetable.TimetableFragment
import cn.jlu.schedule.ui.theme.ThemePaletteProvider
import cn.jlu.schedule.ui.theme.GlassSurface
import cn.jlu.schedule.ui.today.TodayScheduleFragment
import cn.jlu.schedule.ui.tools.ToolsFragment

class MainActivity : AppCompatActivity() {
    private lateinit var rootContainer: FrameLayout
    private lateinit var backgroundImage: ImageView
    private lateinit var backgroundScrim: View
    private lateinit var bottomNav: BottomNavigationView
    private var backgroundLoadToken = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePaletteProvider.applyNightMode(this)
        setTheme(ThemePaletteProvider.themeStyleFor(AppPreferences.getThemeColor(this)))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootContainer = findViewById(R.id.rootContainer)
        backgroundImage = findViewById(R.id.backgroundImage)
        backgroundScrim = findViewById(R.id.backgroundScrim)
        bottomNav = findViewById(R.id.bottomNav)
        // Four destinations in a compact, centered dock; shrink safely on narrow windows.
        findViewById<View>(R.id.dockContainer).addOnLayoutChangeListener { dock, _, _, _, _, _, _, _, _ ->
            val availableWidth = (dock.width - dock.paddingLeft - dock.paddingRight).coerceAtLeast(0)
            val dockWidth = minOf(availableWidth, (320f * resources.displayMetrics.density).toInt())
            if (dockWidth > 0 && bottomNav.layoutParams.width != dockWidth) {
                bottomNav.layoutParams = bottomNav.layoutParams.apply { width = dockWidth }
            }
        }
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
        val loadToken = ++backgroundLoadToken
        val uri = AppPreferences.getCustomBackgroundUri(this)?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (uri == null) {
            clearCustomBackground()
            return
        }

        lifecycleScope.launch {
            val targetW = resources.displayMetrics.widthPixels.coerceAtLeast(1)
            val targetH = resources.displayMetrics.heightPixels.coerceAtLeast(1)
            val bitmap = withContext(Dispatchers.IO) { decodeCustomBackground(uri, targetW, targetH) }
            if (isFinishing || isDestroyed || loadToken != backgroundLoadToken) return@launch
            if (bitmap == null) {
                AppPreferences.setCustomBackgroundUri(this@MainActivity, null)
                clearCustomBackground()
                return@launch
            }
            backgroundImage.setImageBitmap(bitmap)
            backgroundImage.alpha = 0.78f
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                backgroundImage.setRenderEffect(RenderEffect.createBlurEffect(18f, 18f, Shader.TileMode.CLAMP))
            }
            backgroundImage.visibility = ImageView.VISIBLE
            backgroundScrim.visibility = View.VISIBLE
            applyUserAppearance()
        }
    }

    private fun decodeCustomBackground(uri: Uri, targetW: Int, targetH: Int): android.graphics.Bitmap? {
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val srcW = info.size.width.coerceAtLeast(1)
                    val srcH = info.size.height.coerceAtLeast(1)
                    val sample = maxOf(srcW / targetW, srcH / targetH).coerceAtLeast(1)
                    decoder.setTargetSampleSize(sample)
                    decoder.isMutableRequired = false
                }
            } else {
                @Suppress("DEPRECATION")
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, bounds)
                    val sample = calculateSample(bounds.outWidth, bounds.outHeight, targetW, targetH)
                    contentResolver.openInputStream(uri)?.use { secondStream ->
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                        }
                        BitmapFactory.decodeStream(secondStream, null, options)
                    }
                }
            }
        }.getOrNull()

    }

    private fun calculateSample(srcW: Int, srcH: Int, targetW: Int, targetH: Int): Int {
        if (srcW <= 0 || srcH <= 0) return 1
        var sample = 1
        while (srcW / (sample * 2) >= targetW && srcH / (sample * 2) >= targetH) sample *= 2
        return sample.coerceAtLeast(1)
    }

    private fun clearCustomBackground() {
        backgroundLoadToken++
        backgroundImage.setImageDrawable(null)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) backgroundImage.setRenderEffect(null)
        backgroundImage.visibility = ImageView.GONE
        backgroundScrim.visibility = View.GONE
        applyUserAppearance()
    }

    fun applyUserAppearance() {
        val palette = ThemePaletteProvider.fromContext(this)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !palette.isDark
            isAppearanceLightNavigationBars = !palette.isDark
        }
        rootContainer.setBackgroundColor(palette.pageBackground)
        bottomNav.background = GlassSurface.drawable(this, palette, GlassSurface.Variant.Navigation)
        bottomNav.elevation = resources.displayMetrics.density * 5f
        bottomNav.isItemActiveIndicatorEnabled = false
        bottomNav.itemBackground = GlassSurface.dockItem(this, palette)
        backgroundScrim.setBackgroundColor(
            androidx.core.graphics.ColorUtils.setAlphaComponent(palette.pageBackground, 72)
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

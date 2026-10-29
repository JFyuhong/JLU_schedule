package cn.jlu.schedule.update.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import cn.jlu.schedule.R
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.ui.theme.ThemePaletteProvider
import cn.jlu.schedule.ui.theme.SurfaceStyle
import cn.jlu.schedule.ui.theme.UiFeedback
import cn.jlu.schedule.update.download.ApkDownloader
import cn.jlu.schedule.update.installer.UpdateInstaller
import cn.jlu.schedule.update.model.UpdatePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object UpdateDialogHelper {

    @SuppressLint("SetTextI18n")
    fun showUpdateDialog(
        activity: Activity,
        payload: UpdatePayload,
        preferredMirror: String? = null,
        downloader: ApkDownloader = ApkDownloader(),
        onDismiss: () -> Unit = {}
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_app_update, null)
        val palette = ThemePaletteProvider.fromContext(activity)

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(true)
            .create()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        dialog.setOnDismissListener {
            scope.cancel()
            onDismiss()
        }

        val root = view.findViewById<LinearLayout>(R.id.updateDialogRoot)
        val title = view.findViewById<TextView>(R.id.updateDialogTitle)
        val badge = view.findViewById<TextView>(R.id.updateVersionBadge)
        val pubDate = view.findViewById<TextView>(R.id.updatePublishDate)
        val cellularWarning = view.findViewById<TextView>(R.id.updateCellularWarning)
        val notesContainer = view.findViewById<LinearLayout>(R.id.updateNotesContainer)

        val progressContainer = view.findViewById<LinearLayout>(R.id.updateProgressContainer)
        val progressBar = view.findViewById<ProgressBar>(R.id.updateProgressBar)
        val progressText = view.findViewById<TextView>(R.id.updateProgressText)
        val progressPercent = view.findViewById<TextView>(R.id.updateProgressPercent)
        val errorText = view.findViewById<TextView>(R.id.updateErrorText)

        val btnIgnore = view.findViewById<TextView>(R.id.updateBtnIgnore)
        val btnSecondary = view.findViewById<Button>(R.id.updateBtnSecondary)
        val btnPrimary = view.findViewById<Button>(R.id.updateBtnPrimary)

        // 主题色配置
        root.background = SurfaceStyle.drawable(activity, palette, SurfaceStyle.Variant.Strong, 16f)
        title.setTextColor(palette.textPrimary)
        badge.setTextColor(palette.textPrimary)
        pubDate.setTextColor(palette.textSecondary)
        cellularWarning.setTextColor(palette.textSecondary)

        val sizeMb = String.format(Locale.getDefault(), "%.1f MB", payload.apk.size / (1024.0 * 1024.0))
        badge.text = "v${payload.versionName} · $sizeMb"
        val dateText = payload.publishedAt.substringBefore("T")
        pubDate.text = "发布日期：$dateText"

        // 移动网络预警
        if (isCellularNetwork(activity)) {
            cellularWarning.visibility = View.VISIBLE
            cellularWarning.text = activity.getString(R.string.update_cellular_warning, sizeMb)
        } else {
            cellularWarning.visibility = View.GONE
        }

        // 渲染更新日志
        notesContainer.removeAllViews()
        payload.notes.forEach { note ->
            val noteTv = TextView(activity).apply {
                text = "•  $note"
                textSize = 13f
                setTextColor(palette.textPrimary)
                setPadding(0, 4, 0, 4)
            }
            notesContainer.addView(noteTv)
        }

        var downloadJob: Job? = null
        var downloadedApkFile: File? = null

        btnIgnore.setOnClickListener {
            AppPreferences.setIgnoredUpdateVersion(activity, payload.versionCode)
            Toast.makeText(activity, "已忽略 v${payload.versionName} 更新", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        btnSecondary.setOnClickListener {
            if (downloadJob != null && downloadJob?.isActive == true) {
                // 取消下载
                downloadJob?.cancel()
                downloadJob = null
                progressContainer.visibility = View.GONE
                btnIgnore.visibility = View.VISIBLE
                btnSecondary.text = activity.getString(R.string.update_action_later)
                btnPrimary.isEnabled = true
                btnPrimary.text = activity.getString(R.string.update_action_download_and_install)
                Toast.makeText(activity, "已取消下载", Toast.LENGTH_SHORT).show()
            } else {
                dialog.dismiss()
            }
        }

        fun executeInstall(apkFile: File) {
            if (UpdateInstaller.canInstallApks(activity)) {
                UpdateInstaller.startInstall(activity, apkFile)
                dialog.dismiss()
            } else {
                AlertDialog.Builder(activity)
                    .setTitle("需要安装应用权限")
                    .setMessage(R.string.update_permission_required)
                    .setPositiveButton(R.string.update_permission_go_settings) { _, _ ->
                        UpdateInstaller.openInstallPermissionSettings(activity)
                    }
                    .setNegativeButton(R.string.close, null)
                    .show()
            }
        }

        btnPrimary.setOnClickListener {
            if (downloadedApkFile != null && downloadedApkFile?.exists() == true) {
                executeInstall(downloadedApkFile!!)
                return@setOnClickListener
            }

            // 切换为下载中状态
            errorText.visibility = View.GONE
            progressContainer.visibility = View.VISIBLE
            btnIgnore.visibility = View.GONE
            btnPrimary.isEnabled = false
            btnPrimary.text = activity.getString(R.string.update_downloading)
            btnSecondary.text = activity.getString(R.string.update_action_cancel)

            downloadJob = scope.launch {
                val result = downloader.downloadApk(
                    context = activity,
                    payload = payload,
                    preferredMirror = preferredMirror,
                    onProgress = { progress ->
                        scope.launch(Dispatchers.Main) {
                            progressBar.progress = progress.percent
                            progressPercent.text = "${progress.percent}%"
                            val currentMb = String.format(Locale.getDefault(), "%.1f", progress.bytesDownloaded / (1024.0 * 1024.0))
                            val speedText = formatSpeed(progress.speedBps)
                            progressText.text = "$currentMb MB / $sizeMb · $speedText"
                        }
                    }
                )

                if (result.isSuccess) {
                    val apk = result.getOrThrow()
                    downloadedApkFile = apk
                    progressBar.progress = 100
                    progressPercent.text = "100%"
                    progressText.text = activity.getString(R.string.update_download_success)

                    btnPrimary.isEnabled = true
                    btnPrimary.text = activity.getString(R.string.update_action_install_now)
                    btnSecondary.text = activity.getString(R.string.action_close)

                    executeInstall(apk)
                } else {
                    val err = result.exceptionOrNull()?.message ?: "下载失败"
                    errorText.visibility = View.VISIBLE
                    errorText.text = "下载中断: $err"
                    btnPrimary.isEnabled = true
                    btnPrimary.text = activity.getString(R.string.update_action_retry)
                    btnSecondary.text = activity.getString(R.string.update_action_later)
                    btnIgnore.visibility = View.VISIBLE
                }
            }
        }

        dialog.show()
        UiFeedback.styleDialogSurface(dialog, palette)
        UiFeedback.stylePrimaryButton(btnPrimary, palette)
        UiFeedback.styleSecondaryButton(btnSecondary, palette)
    }

    private fun isCellularNetwork(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } catch (_: Exception) {
            false
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> String.format(Locale.getDefault(), "%d KB/s", bytesPerSec / 1024)
            else -> "$bytesPerSec B/s"
        }
    }
}

package cn.jlu.schedule.ui.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.graphics.ColorUtils
import cn.jlu.schedule.R
import cn.jlu.schedule.data.ImportedScheduleStorage
import cn.jlu.schedule.ui.theme.ThemePalette
import cn.jlu.schedule.ui.theme.ThemePaletteProvider
import cn.jlu.schedule.ui.theme.UiFeedback
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class TimetableManageActivity : AppCompatActivity() {
    private lateinit var listView: ListView
    private lateinit var createButton: Button
    private lateinit var titleView: TextView
    private lateinit var subTitleView: TextView
    private lateinit var adapter: ProfileAdapter
    private lateinit var palette: ThemePalette
    private var hasChanged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timetable_manage)

        title = "课表管理"
        titleView = findViewById(R.id.manageTitle)
        subTitleView = findViewById(R.id.manageSubTitle)
        listView = findViewById(R.id.profileList)
        createButton = findViewById(R.id.createProfileButton)

        adapter = ProfileAdapter()
        listView.adapter = adapter
        listView.divider = null
        listView.dividerHeight = 0

        applySystemBarInsets()
        applyTheme()
        bindEvents()
        refreshProfiles()
    }

    override fun finish() {
        if (hasChanged) {
            setResult(Activity.RESULT_OK)
        }
        super.finish()
    }

    private fun applyTheme() {
        palette = ThemePaletteProvider.fromContext(this)
        findViewById<View>(R.id.manageRoot).setBackgroundColor(palette.pageBackground)
        titleView.setTextColor(palette.textPrimary)
        subTitleView.setTextColor(palette.textSecondary)
        UiFeedback.stylePrimaryButton(createButton, palette)
    }

    private fun applySystemBarInsets() {
        val root = findViewById<View>(R.id.manageRoot)
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = baseLeft + bars.left,
                top = baseTop + bars.top,
                right = baseRight + bars.right,
                bottom = baseBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun bindEvents() {
        createButton.setOnClickListener {
            @SuppressLint("SetTextI18n")
            val input = EditText(this).apply {
                hint = "新课表名称（可留空）"
                setText("课表${System.currentTimeMillis() % 100000}")
            }
            AlertDialog.Builder(this)
                .setTitle("新建课表")
                .setView(input)
                .setPositiveButton("新建") { _, _ ->
                    ImportedScheduleStorage.createEmptyProfile(
                        this,
                        input.text?.toString()?.trim().orEmpty()
                    )
                    hasChanged = true
                    UiFeedback.showMessage(findViewById(android.R.id.content), "已新建并切换到新课表", palette)
                    refreshProfiles()
                }
                .setNegativeButton("取消", null)
                .create()
                .also { dialog ->
                    dialog.show()
                    styleDialogButtons(dialog)
                }
        }
    }

    private fun refreshProfiles() {
        adapter.submit(ImportedScheduleStorage.listProfiles(this))
        subTitleView.text = String.format(Locale.getDefault(), "共 %d 个课表，点按可切换当前课表", adapter.count)
    }

    private inner class ProfileAdapter : BaseAdapter() {
        private val profiles = mutableListOf<ImportedScheduleStorage.TimetableProfile>()
        private val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

        fun submit(newData: List<ImportedScheduleStorage.TimetableProfile>) {
            profiles.clear()
            profiles.addAll(newData.sortedByDescending { it.updatedAt })
            notifyDataSetChanged()
        }

        override fun getCount(): Int = profiles.size

        override fun getItem(position: Int): Any = profiles[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_timetable_profile, parent, false)
            val nameView = view.findViewById<TextView>(R.id.profileName)
            val subView = view.findViewById<TextView>(R.id.profileSub)
            val currentBadge = view.findViewById<TextView>(R.id.profileCurrentBadge)
            val overflow = view.findViewById<ImageButton>(R.id.profileOverflow)

            val item = profiles[position]
            val localTime = Instant.ofEpochMilli(item.updatedAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
            nameView.text = item.name
            subView.text = String.format(Locale.getDefault(), "最近更新：%s", localTime.format(dateFormatter))
            currentBadge.visibility = if (item.isActive) View.VISIBLE else View.GONE

            val palette = ThemePaletteProvider.fromContext(this@TimetableManageActivity)
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f
                setColor(ColorUtils.setAlphaComponent(palette.panelAltBackground, 150))
            }
            nameView.setTextColor(palette.textPrimary)
            subView.setTextColor(palette.textSecondary)
            currentBadge.setTextColor(palette.buttonText)
            currentBadge.background = ColorDrawable(palette.buttonBackground)
            overflow.imageTintList = android.content.res.ColorStateList.valueOf(palette.iconTint)

            view.setOnClickListener {
                if (item.isActive) {
                    UiFeedback.showMessage(findViewById(android.R.id.content), "已是当前课表", palette)
                    return@setOnClickListener
                }
                ImportedScheduleStorage.setActiveProfile(this@TimetableManageActivity, item.id)
                hasChanged = true
                UiFeedback.showMessage(findViewById(android.R.id.content), "已切换到：${item.name}", palette)
                refreshProfiles()
            }

            overflow.setOnClickListener { anchor ->
                showActions(anchor, item)
            }
            return view
        }

        private fun showActions(anchor: View, item: ImportedScheduleStorage.TimetableProfile) {
            val panel = LinearLayout(this@TimetableManageActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(34, 28, 34, 20)
            }
            val renameButton = Button(this@TimetableManageActivity).apply {
                text = "重命名"
            }
            val deleteButton = Button(this@TimetableManageActivity).apply {
                text = "删除"
            }
            val cancelButton = Button(this@TimetableManageActivity).apply {
                text = "取消"
            }
            UiFeedback.styleSecondaryButton(renameButton, palette)
            UiFeedback.styleSecondaryButton(deleteButton, palette)
            UiFeedback.styleSecondaryButton(cancelButton, palette)

            panel.addView(renameButton)
            panel.addView(deleteButton, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10 })
            panel.addView(cancelButton, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10 })

            val actionDialog = AlertDialog.Builder(this@TimetableManageActivity)
                .setTitle(item.name)
                .setView(panel)
                .create()
            actionDialog.show()
            styleDialogButtons(actionDialog)

            renameButton.setOnClickListener {
                actionDialog.dismiss()
                renameProfile(item)
            }
            deleteButton.setOnClickListener {
                actionDialog.dismiss()
                deleteProfile(item)
            }
            cancelButton.setOnClickListener {
                actionDialog.dismiss()
            }
        }

        private fun renameProfile(item: ImportedScheduleStorage.TimetableProfile) {
            val input = EditText(this@TimetableManageActivity).apply {
                setText(item.name)
                setSelection(item.name.length)
                hint = "课表名称"
            }
            AlertDialog.Builder(this@TimetableManageActivity)
                .setTitle("重命名课表")
                .setView(input)
                .setPositiveButton("保存") { _, _ ->
                    val newName = input.text?.toString()?.trim().orEmpty()
                    if (newName.isBlank()) {
                        UiFeedback.showMessage(findViewById(android.R.id.content), "名称不能为空", palette)
                        return@setPositiveButton
                    }
                    val ok = ImportedScheduleStorage.renameProfile(this@TimetableManageActivity, item.id, newName)
                    if (!ok) {
                        UiFeedback.showMessage(findViewById(android.R.id.content), "重命名失败", palette)
                        return@setPositiveButton
                    }
                    hasChanged = true
                    UiFeedback.showMessage(findViewById(android.R.id.content), "已重命名", palette)
                    refreshProfiles()
                }
                .setNegativeButton("取消", null)
                .create()
                .also { dialog ->
                    dialog.show()
                    styleDialogButtons(dialog)
                }
        }

        private fun deleteProfile(item: ImportedScheduleStorage.TimetableProfile) {
            AlertDialog.Builder(this@TimetableManageActivity)
                .setTitle("删除课表")
                .setMessage("确认删除「${item.name}」吗？")
                .setPositiveButton("删除") { _, _ ->
                    val deleted = ImportedScheduleStorage.deleteProfile(this@TimetableManageActivity, item.id)
                    if (!deleted) {
                        UiFeedback.showMessage(findViewById(android.R.id.content), "至少保留一个课表，无法删除", palette)
                        return@setPositiveButton
                    }
                    hasChanged = true
                    UiFeedback.showMessage(findViewById(android.R.id.content), "已删除课表", palette)
                    refreshProfiles()
                }
                .setNegativeButton("取消", null)
                .create()
                .also { dialog ->
                    dialog.show()
                    styleDialogButtons(dialog)
                }
        }
    }

    private fun styleDialogButtons(dialog: AlertDialog) {
        UiFeedback.styleDialogSurface(dialog, palette)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { UiFeedback.stylePrimaryButton(it, palette) }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { UiFeedback.styleSecondaryButton(it, palette) }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.let { UiFeedback.styleSecondaryButton(it, palette) }
    }
}

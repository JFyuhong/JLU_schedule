package cn.jlu.schedule.ui.settings

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import cn.jlu.schedule.MainActivity
import cn.jlu.schedule.R
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.data.ImportedScheduleStorage
import cn.jlu.schedule.ui.theme.ThemePaletteProvider
import cn.jlu.schedule.ui.theme.UiFeedback
import com.yalantis.ucrop.UCrop
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SettingsFragment : Fragment() {
    private var pendingSourceUri: Uri? = null

    private val manageProfilesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            activity?.recreate()
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            pendingSourceUri = uri
            startCrop(uri)
        }
    }

    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            val fallback = pendingSourceUri
            if (fallback != null) {
                AppPreferences.setCustomBackgroundUri(requireContext(), fallback.toString())
                (activity as? MainActivity)?.refreshCustomBackground()
                pendingSourceUri = null
            }
            return@registerForActivityResult
        }
        val outputUri = result.data?.let { UCrop.getOutput(it) } ?: return@registerForActivityResult
        AppPreferences.setCustomBackgroundUri(requireContext(), outputUri.toString())
        (activity as? MainActivity)?.refreshCustomBackground()
        pendingSourceUri = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val group = view.findViewById<RadioGroup>(R.id.defaultOpenGroup)
        val semesterStartDateText = view.findViewById<TextView>(R.id.semesterStartDateText)
        val changeSemesterStartButton = view.findViewById<Button>(R.id.changeSemesterStartButton)
        val themeGroup = view.findViewById<RadioGroup>(R.id.themeGroup)
        val fontScaleGroup = view.findViewById<RadioGroup>(R.id.fontScaleGroup)
        val showNonCurrentSwitch = view.findViewById<SwitchCompat>(R.id.showNonCurrentSwitch)
        val chooseBackgroundButton = view.findViewById<Button>(R.id.chooseBackgroundButton)
        val clearBackgroundButton = view.findViewById<Button>(R.id.clearBackgroundButton)
        val manageTimetableButton = view.findViewById<Button>(R.id.manageTimetableButton)

        applyButtonStyles(
            changeSemesterStartButton,
            chooseBackgroundButton,
            clearBackgroundButton,
            manageTimetableButton
        )

        val current = AppPreferences.getDefaultOpenPage(requireContext())
        if (current == AppPreferences.PAGE_TODAY) {
            group.check(R.id.defaultOpenToday)
        } else {
            group.check(R.id.defaultOpenTimetable)
        }

        group.setOnCheckedChangeListener { _, checkedId ->
            val page = if (checkedId == R.id.defaultOpenToday) {
                AppPreferences.PAGE_TODAY
            } else {
                AppPreferences.PAGE_TIMETABLE
            }
            AppPreferences.setDefaultOpenPage(requireContext(), page)
        }

        val semesterStartDate = ImportedScheduleStorage.getActiveSemesterStartDate(requireContext())
        semesterStartDateText.text = semesterStartDate.format(DateTimeFormatter.ofPattern("yyyy/M/d"))
        changeSemesterStartButton.setOnClickListener {
            showSemesterDatePicker(semesterStartDateText)
        }

        when (AppPreferences.getThemeColor(requireContext())) {
            AppPreferences.THEME_OCEAN -> themeGroup.check(R.id.themeOcean)
            AppPreferences.THEME_MINT -> themeGroup.check(R.id.themeMint)
            else -> themeGroup.check(R.id.themeWarm)
        }
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.themeOcean -> AppPreferences.THEME_OCEAN
                R.id.themeMint -> AppPreferences.THEME_MINT
                else -> AppPreferences.THEME_WARM
            }
            AppPreferences.setThemeColor(requireContext(), value)
            (activity as? MainActivity)?.applyUserAppearance()
            applyButtonStyles(
                changeSemesterStartButton,
                chooseBackgroundButton,
                clearBackgroundButton,
                manageTimetableButton
            )
        }

        val fontScale = AppPreferences.getTimetableFontScale(requireContext())
        when {
            fontScale < 0.95f -> fontScaleGroup.check(R.id.fontSmall)
            fontScale > 1.08f -> fontScaleGroup.check(R.id.fontLarge)
            else -> fontScaleGroup.check(R.id.fontNormal)
        }
        fontScaleGroup.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.fontSmall -> 0.88f
                R.id.fontLarge -> 1.14f
                else -> 1.0f
            }
            AppPreferences.setTimetableFontScale(requireContext(), value)
            activity?.recreate()
        }

        showNonCurrentSwitch.isChecked = AppPreferences.isShowNonCurrentCourses(requireContext())
        showNonCurrentSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppPreferences.setShowNonCurrentCourses(requireContext(), isChecked)
            activity?.recreate()
        }

        chooseBackgroundButton.setOnClickListener {
            pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        clearBackgroundButton.setOnClickListener {
            AppPreferences.setCustomBackgroundUri(requireContext(), null)
            (activity as? MainActivity)?.refreshCustomBackground()
        }

        manageTimetableButton.setOnClickListener {
            val intent = Intent(requireContext(), TimetableManageActivity::class.java)
            manageProfilesLauncher.launch(intent)
        }
    }

    private fun showSemesterDatePicker(dateTextView: TextView) {
        val current = ImportedScheduleStorage.getActiveSemesterStartDate(requireContext())
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selected = LocalDate.of(year, month + 1, dayOfMonth)
                ImportedScheduleStorage.setActiveSemesterStartDate(requireContext(), selected)
                val normalized = ImportedScheduleStorage.getActiveSemesterStartDate(requireContext())
                dateTextView.text = normalized.format(DateTimeFormatter.ofPattern("yyyy/M/d"))
                activity?.recreate()
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth
        ).show()
    }

    private fun startCrop(sourceUri: Uri) {
        val outputFile = File(requireContext().filesDir, "custom_background.jpg")
        val destinationUri = Uri.fromFile(outputFile)

        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(true)
            setHideBottomControls(false)
        }

        val cropIntent = UCrop.of(sourceUri, destinationUri)
            .withOptions(options)
            .getIntent(requireContext())
        runCatching {
            cropLauncher.launch(cropIntent)
        }.onFailure {
            AppPreferences.setCustomBackgroundUri(requireContext(), sourceUri.toString())
            (activity as? MainActivity)?.refreshCustomBackground()
            pendingSourceUri = null
        }
    }

    private fun applyButtonStyles(
        changeSemesterStartButton: Button,
        chooseBackgroundButton: Button,
        clearBackgroundButton: Button,
        manageTimetableButton: Button
    ) {
        val palette = ThemePaletteProvider.fromContext(requireContext())
        UiFeedback.styleSecondaryButton(changeSemesterStartButton, palette)
        UiFeedback.styleSecondaryButton(chooseBackgroundButton, palette)
        UiFeedback.styleSecondaryButton(clearBackgroundButton, palette)
        UiFeedback.styleSecondaryButton(manageTimetableButton, palette)
    }
}

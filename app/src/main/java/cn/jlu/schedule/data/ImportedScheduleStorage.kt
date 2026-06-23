package cn.jlu.schedule.data

import android.content.Context
import android.util.Log
import cn.jlu.schedule.model.CourseSchedule
import cn.jlu.schedule.model.MeetingTime
import cn.jlu.schedule.model.WeekParity
import cn.jlu.schedule.model.WeekRule
import cn.jlu.schedule.model.Weekday
import cn.jlu.schedule.parser.DoScheduleParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

object ImportedScheduleStorage {
    private const val TAG = "ImportedScheduleStorage"
    private const val LEGACY_FILE_NAME = "imported_schedule.do"
    private const val STORAGE_DIR = "timetables"
    private const val META_FILE_NAME = "meta.json"
    private const val DEFAULT_PROFILE_NAME = "默认课表"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    data class TimetableProfile(
        val id: String,
        val name: String,
        val isActive: Boolean,
        val updatedAt: Long,
        val semesterStartDate: LocalDate
    )

    enum class ImportMode {
        OVERWRITE_ACTIVE,
        CREATE_NEW
    }

    data class ImportResult(
        val profileId: String,
        val profileName: String,
        val courseCount: Int,
        val isNewProfile: Boolean,
        val semesterStartDate: LocalDate?
    )

    data class ManualCourseInput(
        val courseName: String,
        val teacher: String,
        val location: String,
        val weekday: Weekday,
        val startSection: Int,
        val endSection: Int,
        val startWeek: Int,
        val endWeek: Int,
        val parity: WeekParity = WeekParity.ALL,
        val semester: String = "手动添加"
    )

    fun listProfiles(context: Context, sampleAssetName: String = "sample_schedule.do"): List<TimetableProfile> {
        val meta = ensureInitialized(context, sampleAssetName)
        return meta.profiles.map {
            TimetableProfile(
                id = it.id,
                name = it.name,
                isActive = it.id == meta.activeId,
                updatedAt = it.updatedAt,
                semesterStartDate = semesterStartDateForProfile(it)
            )
        }
    }

    fun setActiveProfile(
        context: Context,
        profileId: String,
        sampleAssetName: String = "sample_schedule.do"
    ): Boolean {
        val meta = ensureInitialized(context, sampleAssetName)
        if (meta.profiles.none { it.id == profileId }) {
            return false
        }
        saveMeta(context, meta.copy(activeId = profileId))
        return true
    }

    fun createEmptyProfile(
        context: Context,
        name: String,
        sampleAssetName: String = "sample_schedule.do"
    ): TimetableProfile {
        val meta = ensureInitialized(context, sampleAssetName)
        val now = System.currentTimeMillis()
        val profileId = UUID.randomUUID().toString()
        val profileName = name.ifBlank { "课表${timestampLabel()}" }
        val fileName = "courses_${profileId}.json"
        writeCoursesFile(context, fileName, emptyList())
        val semesterStartDate = SemesterStartDatePolicy.defaultForToday()

        val newProfile = StoredProfileMeta(
            id = profileId,
            name = profileName,
            coursesFile = fileName,
            createdAt = now,
            updatedAt = now,
            semesterStartDate = semesterStartDate.toString()
        )
        val newMeta = meta.copy(
            activeId = profileId,
            profiles = meta.profiles + newProfile
        )
        saveMeta(context, newMeta)

        return TimetableProfile(profileId, profileName, true, now, semesterStartDate)
    }

    fun renameProfile(
        context: Context,
        profileId: String,
        newName: String,
        sampleAssetName: String = "sample_schedule.do"
    ): Boolean {
        val targetName = newName.trim()
        if (targetName.isBlank()) {
            return false
        }
        val meta = ensureInitialized(context, sampleAssetName)
        if (meta.profiles.none { it.id == profileId }) {
            return false
        }
        val now = System.currentTimeMillis()
        val updatedProfiles = meta.profiles.map {
            if (it.id == profileId) {
                it.copy(name = targetName, updatedAt = now)
            } else {
                it
            }
        }
        saveMeta(context, meta.copy(profiles = updatedProfiles))
        return true
    }

    fun deleteProfile(
        context: Context,
        profileId: String,
        sampleAssetName: String = "sample_schedule.do"
    ): Boolean {
        val meta = ensureInitialized(context, sampleAssetName)
        val target = meta.profiles.firstOrNull { it.id == profileId } ?: return false
        if (meta.profiles.size <= 1) {
            return false
        }

        coursesFile(context, target.coursesFile).takeIf { it.exists() }?.delete()
        val remained = meta.profiles.filterNot { it.id == profileId }
        val nextActive = if (meta.activeId == profileId) remained.first().id else meta.activeId
        saveMeta(context, meta.copy(activeId = nextActive, profiles = remained))
        return true
    }

    fun importParsedCourses(
        context: Context,
        courses: List<CourseSchedule>,
        mode: ImportMode,
        newProfileName: String? = null,
        semesterStartDate: LocalDate? = null,
        sampleAssetName: String = "sample_schedule.do"
    ): ImportResult {
        val merged = mergeDuplicateCourses(courses)
        val meta = ensureInitialized(context, sampleAssetName)
        val now = System.currentTimeMillis()
        val profileSemesterStart = semesterStartDate
            ?: SemesterStartDatePolicy.inferFromCourses(merged, AppPreferences.getSemesterStartDate(context))

        return when (mode) {
            ImportMode.OVERWRITE_ACTIVE -> {
                val active = meta.profiles.firstOrNull { it.id == meta.activeId } ?: meta.profiles.first()
                writeCoursesFile(context, active.coursesFile, merged)
                val updatedProfiles = meta.profiles.map {
                    if (it.id == active.id) {
                        it.copy(updatedAt = now, semesterStartDate = profileSemesterStart.toString())
                    } else {
                        it
                    }
                }
                saveMeta(context, meta.copy(activeId = active.id, profiles = updatedProfiles))
                ImportResult(active.id, active.name, merged.size, false, profileSemesterStart)
            }

            ImportMode.CREATE_NEW -> {
                val profileId = UUID.randomUUID().toString()
                val profileName = newProfileName?.ifBlank { null } ?: "课表${timestampLabel()}"
                val coursesFile = "courses_${profileId}.json"
                writeCoursesFile(context, coursesFile, merged)
                val created = StoredProfileMeta(
                    id = profileId,
                    name = profileName,
                    coursesFile = coursesFile,
                    createdAt = now,
                    updatedAt = now,
                    semesterStartDate = profileSemesterStart.toString()
                )
                saveMeta(context, meta.copy(activeId = profileId, profiles = meta.profiles + created))
                ImportResult(profileId, profileName, merged.size, true, profileSemesterStart)
            }
        }
    }

    fun getActiveSemesterStartDate(
        context: Context,
        sampleAssetName: String = "sample_schedule.do"
    ): LocalDate {
        val meta = ensureInitialized(context, sampleAssetName)
        val active = meta.profiles.firstOrNull { it.id == meta.activeId } ?: meta.profiles.first()
        return semesterStartDateForProfile(active)
    }

    fun setActiveSemesterStartDate(
        context: Context,
        date: LocalDate,
        sampleAssetName: String = "sample_schedule.do"
    ) {
        val meta = ensureInitialized(context, sampleAssetName)
        val active = meta.profiles.firstOrNull { it.id == meta.activeId } ?: meta.profiles.first()
        val normalized = SemesterStartDatePolicy.normalizeToWeekStart(date)
        val updatedProfiles = meta.profiles.map {
            if (it.id == active.id) {
                it.copy(updatedAt = System.currentTimeMillis(), semesterStartDate = normalized.toString())
            } else {
                it
            }
        }
        saveMeta(context, meta.copy(activeId = active.id, profiles = updatedProfiles))
    }

    fun addManualCourseToActive(
        context: Context,
        input: ManualCourseInput,
        sampleAssetName: String = "sample_schedule.do"
    ) {
        require(input.courseName.isNotBlank()) { "课程名不能为空" }
        require(input.startSection in 1..12) { "开始节必须在 1-12 之间" }
        require(input.endSection in 1..12) { "结束节必须在 1-12 之间" }
        require(input.endSection >= input.startSection) { "结束节不能小于开始节" }
        require(input.startWeek in 1..30) { "开始周必须在 1-30 之间" }
        require(input.endWeek in 1..30) { "结束周必须在 1-30 之间" }
        require(input.endWeek >= input.startWeek) { "结束周不能小于开始周" }
        val meta = ensureInitialized(context, sampleAssetName)
        val active = meta.profiles.firstOrNull { it.id == meta.activeId } ?: meta.profiles.first()
        val currentCourses = readCoursesFile(context, active.coursesFile)
        val appended = currentCourses + input.toCourseSchedule()
        writeCoursesFile(context, active.coursesFile, appended)
        val now = System.currentTimeMillis()
        saveMeta(
            context,
            meta.copy(
                activeId = active.id,
                profiles = meta.profiles.map {
                    if (it.id == active.id) it.copy(updatedAt = now) else it
                }
            )
        )
    }

    fun getImportedFile(context: Context): File {
        val meta = ensureInitialized(context, "sample_schedule.do")
        val active = meta.profiles.firstOrNull { it.id == meta.activeId } ?: meta.profiles.first()
        return coursesFile(context, active.coursesFile)
    }

    fun saveImportedContent(context: Context, content: String) {
        val parsed = DoScheduleParser.parse(content)
        importParsedCourses(context, parsed, ImportMode.OVERWRITE_ACTIVE)
    }

    fun hasImportedSchedule(context: Context): Boolean {
        val meta = ensureInitialized(context, "sample_schedule.do")
        return meta.profiles.isNotEmpty()
    }

    fun loadCoursesOrSampleAsset(context: Context, sampleAssetName: String): List<CourseSchedule> {
        val meta = ensureInitialized(context, sampleAssetName)
        val active = meta.profiles.firstOrNull { it.id == meta.activeId } ?: meta.profiles.first()
        return readCoursesFile(context, active.coursesFile)
    }

    fun debugSnapshot(context: Context, sampleAssetName: String = "sample_schedule.do"): String {
        val meta = ensureInitialized(context, sampleAssetName)
        val lines = mutableListOf<String>()
        lines += "activeId=${meta.activeId}"
        lines += "profiles=${meta.profiles.size}"
        meta.profiles.forEachIndexed { index, profile ->
            val file = coursesFile(context, profile.coursesFile)
            val count = runCatching { readCoursesFile(context, profile.coursesFile).size }.getOrDefault(-1)
            lines += buildString {
                append("[")
                append(index)
                append("] id=")
                append(profile.id)
                append(" name=")
                append(profile.name)
                append(" active=")
                append(profile.id == meta.activeId)
                append(" file=")
                append(profile.coursesFile)
                append(" exists=")
                append(file.exists())
                append(" bytes=")
                append(file.length())
                append(" courses=")
                append(count)
            }
        }
        return lines.joinToString("\n")
    }

    private fun ensureInitialized(context: Context, sampleAssetName: String): StoredMeta {
        val storageDir = storageDir(context)
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val metaFile = metaFile(context)
        if (metaFile.exists() && metaFile.length() > 0L) {
            val existing = runCatching { json.decodeFromString<StoredMeta>(metaFile.readText()) }
                .onFailure { Log.w(TAG, "Failed to read timetable meta", it) }
                .getOrElse { StoredMeta() }
            if (existing.profiles.isNotEmpty()) {
                val migrated = fillMissingSemesterStartDates(context, existing)
                if (migrated != existing) {
                    saveMeta(context, migrated)
                }
                return migrated
            }
        }

        val now = System.currentTimeMillis()
        val defaultId = UUID.randomUUID().toString()
        val defaultFile = "courses_${defaultId}.json"
        val initialCourses = loadInitialCourses(context, sampleAssetName)
        writeCoursesFile(context, defaultFile, initialCourses)
        val semesterStartDate = SemesterStartDatePolicy.inferFromCourses(
            initialCourses,
            AppPreferences.getSemesterStartDate(context)
        )

        val initialized = StoredMeta(
            activeId = defaultId,
            profiles = listOf(
                StoredProfileMeta(
                    id = defaultId,
                    name = DEFAULT_PROFILE_NAME,
                    coursesFile = defaultFile,
                    createdAt = now,
                    updatedAt = now,
                    semesterStartDate = semesterStartDate.toString()
                )
            )
        )
        saveMeta(context, initialized)
        return initialized
    }

    private fun loadInitialCourses(context: Context, sampleAssetName: String): List<CourseSchedule> {
        val legacy = File(context.filesDir, LEGACY_FILE_NAME)
        if (legacy.exists() && legacy.length() > 0) {
            runCatching {
                return DoScheduleParser.parse(legacy.readText())
            }.onFailure {
                Log.w(TAG, "Failed to parse legacy imported schedule", it)
            }
            legacy.delete()
        }

        val sample = runCatching {
            context.assets.open(sampleAssetName).bufferedReader().use { it.readText() }
        }.getOrElse {
            Log.w(TAG, "Failed to read sample schedule asset $sampleAssetName", it)
            EMPTY_SCHEDULE_JSON
        }
        return runCatching { DoScheduleParser.parse(sample) }
            .onFailure { Log.w(TAG, "Failed to parse initial schedule", it) }
            .getOrDefault(emptyList())
    }

    private fun storageDir(context: Context): File = File(context.filesDir, STORAGE_DIR)

    private fun metaFile(context: Context): File = File(storageDir(context), META_FILE_NAME)

    private fun coursesFile(context: Context, fileName: String): File = File(storageDir(context), fileName)

    private fun saveMeta(context: Context, meta: StoredMeta) {
        writeTextAtomically(metaFile(context), json.encodeToString(meta))
    }

    private fun readCoursesFile(context: Context, fileName: String): List<CourseSchedule> {
        val file = coursesFile(context, fileName)
        if (!file.exists() || file.length() == 0L) {
            return emptyList()
        }
        val persisted = runCatching {
            json.decodeFromString<List<PersistedCourse>>(file.readText())
        }.onFailure {
            Log.w(TAG, "Failed to read courses file $fileName", it)
        }.getOrDefault(emptyList())
        return persisted.map { it.toCourseSchedule() }
    }

    private fun writeCoursesFile(context: Context, fileName: String, courses: List<CourseSchedule>) {
        val persisted = courses.map { PersistedCourse.fromCourseSchedule(it) }
        writeTextAtomically(coursesFile(context, fileName), json.encodeToString(persisted))
    }

    private fun writeTextAtomically(target: File, content: String) {
        val parent = target.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IOException("Cannot create directory ${parent.absolutePath}")
        }
        val tempParent = parent ?: throw IOException("Cannot resolve parent for ${target.absolutePath}")
        val temp = File(tempParent, "${target.name}.tmp")
        temp.writeText(content, Charsets.UTF_8)
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    private fun fillMissingSemesterStartDates(context: Context, meta: StoredMeta): StoredMeta {
        var changed = false
        val migratedProfiles = meta.profiles.map { profile ->
            if (!profile.semesterStartDate.isNullOrBlank()) {
                profile
            } else {
                changed = true
                val courses = readCoursesFile(context, profile.coursesFile)
                val inferred = SemesterStartDatePolicy.inferFromCourses(
                    courses,
                    AppPreferences.getSemesterStartDate(context)
                )
                profile.copy(semesterStartDate = inferred.toString())
            }
        }
        return if (changed) meta.copy(profiles = migratedProfiles) else meta
    }

    private fun semesterStartDateForProfile(profile: StoredProfileMeta): LocalDate {
        val raw = profile.semesterStartDate
        if (raw.isNullOrBlank()) {
            return SemesterStartDatePolicy.defaultForToday()
        }
        return try {
            SemesterStartDatePolicy.normalizeToWeekStart(LocalDate.parse(raw))
        } catch (error: DateTimeParseException) {
            Log.w(TAG, "Invalid semester start date in profile ${profile.id}: $raw", error)
            SemesterStartDatePolicy.defaultForToday()
        }
    }

    private fun mergeDuplicateCourses(courses: List<CourseSchedule>): List<CourseSchedule> {
        return courses.distinctBy { course ->
            buildString {
                append(course.courseName)
                append("|")
                append(course.teacher)
                append("|")
                append(course.semester)
                append("|")
                append(course.credit ?: -1)
                append("|")
                append(course.rawWeekText)
                append("|")
                course.meetings.sortedBy { it.weekday.ordinal * 100 + it.startSection }.forEach { meeting ->
                    append(meeting.weekday.name)
                    append(":")
                    append(meeting.startSection)
                    append("-")
                    append(meeting.endSection)
                    append("@")
                    append(meeting.location)
                    append("#")
                    meeting.weekRules.forEach { rule ->
                        append(rule.startWeek)
                        append("-")
                        append(rule.endWeek)
                        append("(")
                        append(rule.parity.name)
                        append(")")
                    }
                    append(";")
                }
            }
        }
    }

    private fun ManualCourseInput.toCourseSchedule(): CourseSchedule {
        return CourseSchedule(
            courseName = courseName.trim(),
            teacher = teacher,
            semester = semester,
            credit = null,
            rawWeekText = "${startWeek}-${endWeek}周",
            meetings = listOf(
                MeetingTime(
                    weekday = weekday,
                    startSection = startSection,
                    endSection = endSection,
                    weekRules = listOf(WeekRule(startWeek, endWeek, parity)),
                    location = location
                )
            )
        )
    }

    private fun timestampLabel(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd_HHmm"))
    }

    @Serializable
    private data class StoredMeta(
        val activeId: String = "",
        val profiles: List<StoredProfileMeta> = emptyList()
    )

    @Serializable
    private data class StoredProfileMeta(
        val id: String,
        val name: String,
        val coursesFile: String,
        val createdAt: Long,
        val updatedAt: Long,
        val semesterStartDate: String? = null
    )

    @Serializable
    private data class PersistedCourse(
        val courseName: String,
        val teacher: String,
        val semester: String,
        val credit: Double?,
        val rawWeekText: String,
        val meetings: List<PersistedMeeting>
    ) {
        fun toCourseSchedule(): CourseSchedule {
            return CourseSchedule(
                courseName = courseName,
                teacher = teacher,
                semester = semester,
                credit = credit,
                rawWeekText = rawWeekText,
                meetings = meetings.map { it.toMeetingTime() }
            )
        }

        companion object {
            fun fromCourseSchedule(course: CourseSchedule): PersistedCourse {
                return PersistedCourse(
                    courseName = course.courseName,
                    teacher = course.teacher,
                    semester = course.semester,
                    credit = course.credit,
                    rawWeekText = course.rawWeekText,
                    meetings = course.meetings.map { PersistedMeeting.fromMeetingTime(it) }
                )
            }
        }
    }

    @Serializable
    private data class PersistedMeeting(
        val weekday: String,
        val startSection: Int,
        val endSection: Int,
        val weekRules: List<PersistedWeekRule>,
        val location: String
    ) {
        fun toMeetingTime(): MeetingTime {
            return MeetingTime(
                weekday = runCatching { Weekday.valueOf(weekday) }.getOrDefault(Weekday.MONDAY),
                startSection = startSection,
                endSection = endSection,
                weekRules = weekRules.map { it.toWeekRule() },
                location = location
            )
        }

        companion object {
            fun fromMeetingTime(meeting: MeetingTime): PersistedMeeting {
                return PersistedMeeting(
                    weekday = meeting.weekday.name,
                    startSection = meeting.startSection,
                    endSection = meeting.endSection,
                    weekRules = meeting.weekRules.map { PersistedWeekRule.fromWeekRule(it) },
                    location = meeting.location
                )
            }
        }
    }

    @Serializable
    private data class PersistedWeekRule(
        val startWeek: Int,
        val endWeek: Int,
        val parity: String
    ) {
        fun toWeekRule(): WeekRule {
            return WeekRule(
                startWeek = startWeek,
                endWeek = endWeek,
                parity = runCatching { WeekParity.valueOf(parity) }.getOrDefault(WeekParity.ALL)
            )
        }

        companion object {
            fun fromWeekRule(rule: WeekRule): PersistedWeekRule {
                return PersistedWeekRule(rule.startWeek, rule.endWeek, rule.parity.name)
            }
        }
    }

    private const val EMPTY_SCHEDULE_JSON = "{\"datas\":{\"x\":{\"rows\":[]}}}"
}

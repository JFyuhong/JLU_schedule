package cn.jlu.schedule.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RawScheduleResponse(
    val datas: Map<String, RawScheduleTable> = emptyMap()
)

@Serializable
data class RawScheduleTable(
    val rows: List<RawCourseRow> = emptyList()
)

@Serializable
data class RawCourseRow(
    @SerialName("KCM") val courseName: String? = null,
    @SerialName("SKJS") val teacher: String? = null,
    @SerialName("XNXQDM") val semesterCode: String? = null,
    @SerialName("XNXQDM_DISPLAY") val semesterDisplay: String? = null,
    @SerialName("XF") val credit: JsonElement? = null,
    @SerialName("YPSJDD") val arrangedTimeText: String? = null,
    @SerialName("ZCMC") val weekText: String? = null,
    @SerialName("SKXQ") val weekdayNumber: JsonElement? = null,
    @SerialName("KSJC") val startSection: JsonElement? = null,
    @SerialName("JSJC") val endSection: JsonElement? = null,
    @SerialName("JASMC") val classroomName: String? = null,
    @SerialName("JXLDM_DISPLAY") val buildingName: String? = null
)

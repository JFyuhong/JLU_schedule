package cn.jlu.schedule.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    @SerialName("XF") val credit: Double? = null,
    @SerialName("YPSJDD") val arrangedTimeText: String? = null,
    @SerialName("ZCMC") val weekText: String? = null,
    @SerialName("SKXQ") val weekdayNumber: Int? = null,
    @SerialName("KSJC") val startSection: Int? = null,
    @SerialName("JSJC") val endSection: Int? = null,
    @SerialName("JASMC") val classroomName: String? = null,
    @SerialName("JXLDM_DISPLAY") val buildingName: String? = null
)

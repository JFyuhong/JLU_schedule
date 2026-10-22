package cn.jlu.schedule.ui.tools

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import cn.jlu.schedule.R
import cn.jlu.schedule.auth.TpassConfig
import cn.jlu.schedule.remote.JwEndpoints
import cn.jlu.schedule.ui.theme.ThemePaletteProvider
import cn.jlu.schedule.ui.theme.GlassSurface
import cn.jlu.schedule.ui.theme.BackgroundReadability

/** 工具页：汇聚成绩查询、绩点计算、考试安排与学业完成等教务工具（底部导航「工具」Tab） */
class ToolsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_tools, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        val palette = ThemePaletteProvider.fromContext(ctx)
        BackgroundReadability.protectPanel(view.findViewById(R.id.toolsHeader), palette)

        fun styleCard(cardId: Int, iconId: Int) {
            view.findViewById<View>(cardId)?.let { GlassSurface.apply(it, palette, GlassSurface.Variant.Panel, 24f) }
            view.findViewById<ImageView>(iconId)?.imageTintList = ColorStateList.valueOf(palette.iconTint)
        }

        styleCard(R.id.toolsCardAcademics, R.id.iconToolAcademics)
        styleCard(R.id.toolsCardExams, R.id.iconToolExams)
        styleCard(R.id.toolsCardWeb, R.id.iconToolWeb)

        // 学业与成绩卡片
        view.findViewById<View>(R.id.rowGradeInquiry)?.setOnClickListener {
            startActivity(Intent(ctx, GradeInquiryActivity::class.java))
        }
        view.findViewById<View>(R.id.rowGpaCalculator)?.setOnClickListener {
            startActivity(Intent(ctx, GpaCalculatorActivity::class.java))
        }
        view.findViewById<View>(R.id.rowAcademicProgress)?.setOnClickListener {
            startActivity(Intent(ctx, AcademicProgressActivity::class.java))
        }

        // 考务与日程卡片
        view.findViewById<View>(R.id.rowExamSchedule)?.setOnClickListener {
            startActivity(Intent(ctx, ExamScheduleActivity::class.java))
        }

        // 智慧教育平台直达卡片
        view.findViewById<View>(R.id.rowWebPortal)?.setOnClickListener {
            CampusWebActivity.start(ctx, TpassConfig.IEDU_PORTAL_URL, getString(R.string.tools_web_portal))
        }
        view.findViewById<View>(R.id.rowWebGrade)?.setOnClickListener {
            CampusWebActivity.start(ctx, "https://iedu.jlu.edu.cn/jwapp/sys/cjcx/*default/index.do", getString(R.string.tools_web_grade))
        }
        view.findViewById<View>(R.id.rowWebExam)?.setOnClickListener {
            CampusWebActivity.start(ctx, JwEndpoints.EXAM_PAGE_URL, getString(R.string.tools_web_exam))
        }
        view.findViewById<View>(R.id.rowWebAcademic)?.setOnClickListener {
            CampusWebActivity.start(ctx, JwEndpoints.ACADEMIC_PAGE_URL, getString(R.string.tools_web_academic))
        }
    }
}

import React, { useState } from 'react';
import { CourseSchedule } from '../core/types';
import { GpaStore } from '../core/GpaStore';
import { calculateGpa } from '../core/GpaCalculator';
import { ExamStore } from '../core/ExamStore';
import { GpaCalculatorView } from './tools/GpaCalculatorView';
import { ExamScheduleView } from './tools/ExamScheduleView';
import { AcademicProgressView } from './tools/AcademicProgressView';
import { TextScheduleModal } from './tools/TextScheduleModal';
import {
  Calculator,
  Calendar,
  GraduationCap,
  FileText,
  ChevronRight,
  ExternalLink,
  ShieldCheck,
  Globe,
} from 'lucide-react';

interface ToolsPageProps {
  courses: CourseSchedule[];
}

type SubView = 'main' | 'gpa' | 'exams' | 'progress';

export const ToolsPage: React.FC<ToolsPageProps> = ({ courses }) => {
  const [subView, setSubView] = useState<SubView>('main');
  const [isTextExportOpen, setIsTextExportOpen] = useState(false);

  if (subView === 'gpa') {
    return <GpaCalculatorView onBack={() => setSubView('main')} />;
  }
  if (subView === 'exams') {
    return <ExamScheduleView onBack={() => setSubView('main')} />;
  }
  if (subView === 'progress') {
    return <AcademicProgressView onBack={() => setSubView('main')} />;
  }

  // Previews
  const gpaSummary = calculateGpa(GpaStore.getInstance().getCourses());
  const exams = ExamStore.getInstance().getExams();
  const nextExam = exams.find((e) => !e.timestamp || e.timestamp >= Date.now());

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        backgroundColor: 'var(--bg-color)',
        paddingTop: 'var(--sat)',
        paddingBottom: 'calc(50px + var(--sab))',
        overflowY: 'auto',
      }}
    >
      <header
        className="ios-glass"
        style={{
          padding: '16px 20px 12px 20px',
          borderBottom: '0.5px solid var(--border-color)',
        }}
      >
        <h1 style={{ fontSize: '26px', fontWeight: 700, letterSpacing: '-0.5px' }}>
          学习工具
        </h1>
        <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '2px' }}>
          成绩绩点 · 考试倒计时 · 方案完成 · 便民入口
        </p>
      </header>

      <div style={{ padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
        {/* Tool Cards */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {/* Card 1: GPA Calculator */}
          <div
            onClick={() => setSubView('gpa')}
            className="ios-button"
            style={{
              backgroundColor: 'var(--card-bg)',
              border: '0.5px solid var(--border-color)',
              borderRadius: '16px',
              padding: '16px 18px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              boxShadow: '0 2px 8px rgba(0, 0, 0, 0.04)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
              <div
                style={{
                  width: '40px',
                  height: '40px',
                  borderRadius: '12px',
                  backgroundColor: 'var(--accent-light)',
                  color: 'var(--accent-color)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Calculator size={22} />
              </div>
              <div>
                <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>
                  绩点计算器
                </div>
                <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                  {gpaSummary.recommendationGpa !== null ? (
                    <span style={{ color: 'var(--accent-color)', fontWeight: 600 }}>
                      估算 GPA: {gpaSummary.recommendationGpa.toFixed(3)} · 加权均分: {gpaSummary.weightedAverage?.toFixed(1)}
                    </span>
                  ) : (
                    '保研 4.0 绩点、加权均分、重修取高分'
                  )}
                </div>
              </div>
            </div>
            <ChevronRight size={18} color="var(--text-tertiary)" />
          </div>

          {/* Card 2: Exam Schedule */}
          <div
            onClick={() => setSubView('exams')}
            className="ios-button"
            style={{
              backgroundColor: 'var(--card-bg)',
              border: '0.5px solid var(--border-color)',
              borderRadius: '16px',
              padding: '16px 18px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              boxShadow: '0 2px 8px rgba(0, 0, 0, 0.04)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
              <div
                style={{
                  width: '40px',
                  height: '40px',
                  borderRadius: '12px',
                  backgroundColor: 'var(--accent-light)',
                  color: 'var(--accent-color)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Calendar size={22} />
              </div>
              <div>
                <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>
                  考试安排与倒计时
                </div>
                <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                  {nextExam ? (
                    <span style={{ color: 'var(--accent-color)', fontWeight: 600 }}>
                      最近考试: {nextExam.courseName} · {nextExam.location || '考场待定'}
                    </span>
                  ) : (
                    '期末考场、座位号、倒计时提醒'
                  )}
                </div>
              </div>
            </div>
            <ChevronRight size={18} color="var(--text-tertiary)" />
          </div>

          {/* Card 3: Academic Progress */}
          <div
            onClick={() => setSubView('progress')}
            className="ios-button"
            style={{
              backgroundColor: 'var(--card-bg)',
              border: '0.5px solid var(--border-color)',
              borderRadius: '16px',
              padding: '16px 18px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              boxShadow: '0 2px 8px rgba(0, 0, 0, 0.04)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
              <div
                style={{
                  width: '40px',
                  height: '40px',
                  borderRadius: '12px',
                  backgroundColor: 'var(--accent-light)',
                  color: 'var(--accent-color)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <GraduationCap size={22} />
              </div>
              <div>
                <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>
                  学业完成情况查询
                </div>
                <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                  培养方案总学分要求与通识/专业必修修读进度
                </div>
              </div>
            </div>
            <ChevronRight size={18} color="var(--text-tertiary)" />
          </div>

          {/* Card 4: Text Timetable Export */}
          <div
            onClick={() => setIsTextExportOpen(true)}
            className="ios-button"
            style={{
              backgroundColor: 'var(--card-bg)',
              border: '0.5px solid var(--border-color)',
              borderRadius: '16px',
              padding: '16px 18px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              boxShadow: '0 2px 8px rgba(0, 0, 0, 0.04)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
              <div
                style={{
                  width: '40px',
                  height: '40px',
                  borderRadius: '12px',
                  backgroundColor: 'var(--accent-light)',
                  color: 'var(--accent-color)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <FileText size={22} />
              </div>
              <div>
                <div style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>
                  课表文本导出
                </div>
                <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                  一键生成对齐的纯文本课表，方便微信复制或打印
                </div>
              </div>
            </div>
            <ChevronRight size={18} color="var(--text-tertiary)" />
          </div>
        </div>

        {/* Section 2: Campus Quick Navigation */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <div style={{ fontSize: '13px', color: 'var(--text-secondary)', padding: '0 4px', fontWeight: 500 }}>
            校园便民直达
          </div>

          <div
            style={{
              backgroundColor: 'var(--card-bg)',
              border: '0.5px solid var(--border-color)',
              borderRadius: '16px',
              overflow: 'hidden',
            }}
          >
            <a
              href="https://jw.jlu.edu.cn"
              target="_blank"
              rel="noreferrer"
              className="ios-button"
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '14px 16px',
                borderBottom: '0.5px solid var(--divider-color)',
                textDecoration: 'none',
                color: 'var(--text-primary)',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Globe size={18} color="var(--accent-color)" />
                <span style={{ fontSize: '15px', fontWeight: 500 }}>吉大教务管理系统 (校内)</span>
              </div>
              <ExternalLink size={15} color="var(--text-tertiary)" />
            </a>

            <a
              href="https://vpn.jlu.edu.cn"
              target="_blank"
              rel="noreferrer"
              className="ios-button"
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '14px 16px',
                borderBottom: '0.5px solid var(--divider-color)',
                textDecoration: 'none',
                color: 'var(--text-primary)',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <ShieldCheck size={18} color="var(--accent-color)" />
                <span style={{ fontSize: '15px', fontWeight: 500 }}>吉大 WebVPN (校外免客户端)</span>
              </div>
              <ExternalLink size={15} color="var(--text-tertiary)" />
            </a>

            <a
              href="https://cas.jlu.edu.cn"
              target="_blank"
              rel="noreferrer"
              className="ios-button"
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '14px 16px',
                textDecoration: 'none',
                color: 'var(--text-primary)',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Globe size={18} color="var(--accent-color)" />
                <span style={{ fontSize: '15px', fontWeight: 500 }}>吉大统一身份认证 (CAS)</span>
              </div>
              <ExternalLink size={15} color="var(--text-tertiary)" />
            </a>
          </div>
        </div>
      </div>

      <TextScheduleModal
        isOpen={isTextExportOpen}
        onClose={() => setIsTextExportOpen(false)}
        courses={courses}
      />
    </div>
  );
};

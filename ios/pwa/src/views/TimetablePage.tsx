import React, { useState } from 'react';
import {
  CourseMeetingRef,
  CourseSchedule,
  FontSize,
  SECTION_RANGES,
  WEEKDAY_NAMES,
} from '../core/types';
import { SimpleDate } from '../core/SimpleDate';
import {
  displayMeetings,
  guessCurrentWeek,
  totalWeeks,
} from '../core/WeekScheduleCalculator';
import { getCourseColor } from '../core/colorPalette';
import { CourseDetailModal } from '../components/CourseDetailModal';
import { AddCourseModal } from '../components/AddCourseModal';
import { TextScheduleModal } from './tools/TextScheduleModal';
import { ChevronLeft, ChevronRight, Plus, Layers, FileText } from 'lucide-react';

interface TimetablePageProps {
  courses: CourseSchedule[];
  semesterStartDate: string | null;
  showNonCurrent: boolean;
  fontSize?: FontSize;
  isDark: boolean;
  profileName: string;
  onAddCourse: (course: CourseSchedule) => void;
  onUpdateCourse: (index: number, course: CourseSchedule) => void;
  onDeleteCourse: (index: number) => void;
  onSwitchProfileClick: () => void;
}

const SECTION_HEIGHT = 56; // px per section

export const TimetablePage: React.FC<TimetablePageProps> = ({
  courses,
  semesterStartDate,
  showNonCurrent,
  fontSize = 'normal',
  isDark,
  profileName,
  onAddCourse,
  onUpdateCourse,
  onDeleteCourse,
  onSwitchProfileClick,
}) => {
  const today = SimpleDate.today();
  const startDate = semesterStartDate
    ? SimpleDate.parse(semesterStartDate) ?? today.previousOrSameMonday()
    : today.previousOrSameMonday();

  const maxWeeks = Math.max(totalWeeks(courses), 20);
  const actualCurrentWeek = guessCurrentWeek(startDate, today, maxWeeks);

  const [selectedWeek, setSelectedWeek] = useState(actualCurrentWeek);
  const [activeMeetingRef, setActiveMeetingRef] = useState<CourseMeetingRef | null>(null);
  const [isAddOpen, setIsAddOpen] = useState(false);
  const [isTextExportOpen, setIsTextExportOpen] = useState(false);
  const [editingRef, setEditingRef] = useState<CourseMeetingRef | null>(null);
  const [showWeekPicker, setShowWeekPicker] = useState(false);

  const cardTitleFontSize =
    fontSize === 'compact' ? '9.5px' : fontSize === 'large' ? '12px' : '10.5px';

  // Compute dates for Monday - Sunday of selected week
  // week 1 Monday = startDate
  const weekStartMonday = startDate.adding((selectedWeek - 1) * 7);
  const weekDates: SimpleDate[] = Array.from({ length: 7 }, (_, i) =>
    weekStartMonday.adding(i)
  );

  const isCurrentWeekSelected = selectedWeek === actualCurrentWeek;
  const todayWeekday = today.weekdayMonBased(); // 1..7

  // Get displayed meetings for selected week
  const allMeetings = displayMeetings(courses, selectedWeek, showNonCurrent);

  // Group by weekday (1..7)
  const meetingsByDay: Record<number, CourseMeetingRef[]> = {
    1: [], 2: [], 3: [], 4: [], 5: [], 6: [], 7: [],
  };

  allMeetings.forEach((m) => {
    if (m.meeting.weekday >= 1 && m.meeting.weekday <= 7) {
      meetingsByDay[m.meeting.weekday].push(m);
    }
  });

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        backgroundColor: 'var(--bg-color)',
        paddingTop: 'var(--sat)',
        paddingBottom: 'calc(50px + var(--sab))',
        position: 'relative',
      }}
    >
      {/* Top Header */}
      <header
        className="ios-glass"
        style={{
          padding: '8px 16px 6px 16px',
          borderBottom: '0.5px solid var(--border-color)',
          zIndex: 30,
          display: 'flex',
          flexDirection: 'column',
          gap: '6px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <h1 style={{ fontSize: '20px', fontWeight: 700, letterSpacing: '-0.3px' }}>
              吉大课表
            </h1>
            <button
              onClick={onSwitchProfileClick}
              className="ios-button"
              style={{
                background: 'var(--card-bg)',
                border: '0.5px solid var(--border-color)',
                borderRadius: '12px',
                padding: '3px 8px',
                fontSize: '11px',
                fontWeight: 500,
                color: 'var(--text-secondary)',
                display: 'flex',
                alignItems: 'center',
                gap: '3px',
              }}
            >
              <Layers size={11} />
              {profileName}
            </button>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <button
              onClick={() => setIsTextExportOpen(true)}
              className="ios-button"
              style={{
                width: '32px',
                height: '32px',
                borderRadius: '50%',
                backgroundColor: 'var(--card-bg)',
                color: 'var(--text-secondary)',
                border: '0.5px solid var(--border-color)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
              title="导出文本课表"
            >
              <FileText size={16} />
            </button>
            <button
              onClick={() => {
                setEditingRef(null);
                setIsAddOpen(true);
              }}
              className="ios-button"
              style={{
                width: '32px',
                height: '32px',
                borderRadius: '50%',
                backgroundColor: 'var(--accent-color)',
                color: 'var(--accent-contrast)',
                border: 'none',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
              title="添加课程"
            >
              <Plus size={18} strokeWidth={2.5} />
            </button>
          </div>
        </div>

        {/* Week Switcher Bar */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '12px' }}>
          <button
            onClick={() => setSelectedWeek((w: number) => Math.max(1, w - 1))}
            disabled={selectedWeek <= 1}
            className="ios-button"
            style={{
              background: 'none',
              border: 'none',
              color: selectedWeek <= 1 ? 'var(--text-tertiary)' : 'var(--text-primary)',
              padding: '4px',
              display: 'flex',
              alignItems: 'center',
            }}
          >
            <ChevronLeft size={20} />
          </button>

          <button
            onClick={() => setShowWeekPicker(!showWeekPicker)}
            className="ios-button"
            style={{
              backgroundColor: isCurrentWeekSelected ? 'var(--accent-light)' : 'var(--card-bg)',
              color: isCurrentWeekSelected ? 'var(--accent-color)' : 'var(--text-primary)',
              border: '0.5px solid var(--border-color)',
              borderRadius: '14px',
              padding: '4px 14px',
              fontSize: '13px',
              fontWeight: 600,
              display: 'flex',
              alignItems: 'center',
              gap: '4px',
            }}
          >
            第 {selectedWeek} 周 {isCurrentWeekSelected ? '(本周)' : ''}
          </button>

          <button
            onClick={() => setSelectedWeek((w: number) => Math.min(maxWeeks, w + 1))}
            disabled={selectedWeek >= maxWeeks}
            className="ios-button"
            style={{
              background: 'none',
              border: 'none',
              color: selectedWeek >= maxWeeks ? 'var(--text-tertiary)' : 'var(--text-primary)',
              padding: '4px',
              display: 'flex',
              alignItems: 'center',
            }}
          >
            <ChevronRight size={20} />
          </button>
        </div>
      </header>

      {/* Week Picker Dropdown Sheet */}
      {showWeekPicker && (
        <div
          style={{
            position: 'absolute',
            top: 'calc(var(--sat) + 80px)',
            left: '16px',
            right: '16px',
            zIndex: 40,
            backgroundColor: 'var(--card-bg)',
            borderRadius: '16px',
            boxShadow: '0 8px 30px rgba(0, 0, 0, 0.2)',
            border: '0.5px solid var(--border-color)',
            padding: '14px',
            animation: 'fadeIn 0.15s ease',
          }}
        >
          <div style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '10px', textAlign: 'center' }}>
            选择跳转周次（本学期共 {maxWeeks} 周）
          </div>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(5, 1fr)',
              gap: '6px',
              maxHeight: '180px',
              overflowY: 'auto',
            }}
          >
            {Array.from({ length: maxWeeks }, (_, i) => i + 1).map((w) => {
              const isSelected = w === selectedWeek;
              const isCurrent = w === actualCurrentWeek;
              return (
                <button
                  key={w}
                  onClick={() => {
                    setSelectedWeek(w);
                    setShowWeekPicker(false);
                  }}
                  className="ios-button"
                  style={{
                    height: '36px',
                    borderRadius: '8px',
                    border: 'none',
                    backgroundColor: isSelected
                      ? 'var(--accent-color)'
                      : isCurrent
                      ? 'var(--accent-light)'
                      : 'var(--bg-color)',
                    color: isSelected
                      ? 'var(--accent-contrast)'
                      : isCurrent
                      ? 'var(--accent-color)'
                      : 'var(--text-primary)',
                    fontSize: '12px',
                    fontWeight: isSelected || isCurrent ? 600 : 400,
                  }}
                >
                  第 {w} 周
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* Day Headers (Sticky) */}
      <div
        className="ios-glass"
        style={{
          display: 'grid',
          gridTemplateColumns: '36px repeat(7, 1fr)',
          borderBottom: '0.5px solid var(--border-color)',
          textAlign: 'center',
          padding: '4px 0',
          fontSize: '11px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-tertiary)', fontSize: '10px' }}>
          {weekDates[0]?.month}月
        </div>
        {WEEKDAY_NAMES.map((name, i) => {
          const dayNum = i + 1;
          const isToday = isCurrentWeekSelected && dayNum === todayWeekday;
          const dateObj = weekDates[i];
          return (
            <div
              key={name}
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                padding: '2px 0',
                color: isToday ? 'var(--accent-color)' : 'var(--text-primary)',
                fontWeight: isToday ? 700 : 400,
              }}
            >
              <span>{name.replace('周', '')}</span>
              <span
                style={{
                  fontSize: '10px',
                  color: isToday ? 'var(--accent-color)' : 'var(--text-secondary)',
                  marginTop: '1px',
                }}
              >
                {dateObj ? `${dateObj.month}.${dateObj.day}` : ''}
              </span>
              {isToday && (
                <div
                  style={{
                    width: '4px',
                    height: '4px',
                    borderRadius: '50%',
                    backgroundColor: 'var(--accent-color)',
                    marginTop: '2px',
                  }}
                />
              )}
            </div>
          );
        })}
      </div>

      {/* Main Timetable Scroll Grid */}
      <div style={{ flex: 1, overflowY: 'auto', position: 'relative' }}>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '36px repeat(7, 1fr)',
            minHeight: `${12 * SECTION_HEIGHT}px`,
            position: 'relative',
          }}
        >
          {/* Section Indicator Column (1..12) */}
          <div
            style={{
              borderRight: '0.5px solid var(--divider-color)',
              display: 'flex',
              flexDirection: 'column',
            }}
          >
            {Array.from({ length: 12 }, (_, i) => {
              const sec = i + 1;
              const timeStr = SECTION_RANGES[i] || '';
              const startTime = timeStr.split('-')[0] || '';
              return (
                <div
                  key={sec}
                  style={{
                    height: `${SECTION_HEIGHT}px`,
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    borderBottom: '0.5px solid var(--divider-color)',
                    color: 'var(--text-secondary)',
                    fontSize: '11px',
                    fontWeight: 600,
                  }}
                >
                  <span>{sec}</span>
                  <span style={{ fontSize: '8px', color: 'var(--text-tertiary)', transform: 'scale(0.85)' }}>
                    {startTime}
                  </span>
                </div>
              );
            })}
          </div>

          {/* 7 Day Columns */}
          {Array.from({ length: 7 }, (_, dIndex) => {
            const dayNum = dIndex + 1;
            const dayMeetings = meetingsByDay[dayNum] || [];

            // Detect conflicts
            const sectionCount: Record<number, number> = {};
            dayMeetings.forEach((m) => {
              for (let s = m.meeting.startSection; s <= m.meeting.endSection; s++) {
                sectionCount[s] = (sectionCount[s] || 0) + 1;
              }
            });

            return (
              <div
                key={dayNum}
                style={{
                  position: 'relative',
                  borderRight: dIndex < 6 ? '0.5px solid var(--divider-color)' : 'none',
                }}
              >
                {/* Horizontal section grid lines */}
                {Array.from({ length: 12 }, (_, s) => (
                  <div
                    key={s}
                    style={{
                      height: `${SECTION_HEIGHT}px`,
                      borderBottom: '0.5px solid var(--divider-color)',
                    }}
                  />
                ))}

                {/* Course Cards */}
                {dayMeetings.map((item, mIdx) => {
                  const { meeting, course, isCurrentWeek } = item;
                  const startSec = Math.min(Math.max(meeting.startSection, 1), 12);
                  const endSec = Math.min(Math.max(meeting.endSection, 1), 12);
                  const span = endSec - startSec + 1;

                  const top = (startSec - 1) * SECTION_HEIGHT + 2;
                  const height = span * SECTION_HEIGHT - 4;

                  const colors = getCourseColor(course.courseName, isCurrentWeek, isDark);
                  const hasConflict = Object.keys(sectionCount).some(
                    (s) => Number(s) >= startSec && Number(s) <= endSec && (sectionCount[Number(s)] ?? 0) > 1
                  );

                  return (
                    <div
                      key={`${item.courseIndex}-${mIdx}`}
                      onClick={() => setActiveMeetingRef(item)}
                      className="ios-button"
                      style={{
                        position: 'absolute',
                        top: `${top}px`,
                        left: '2px',
                        right: '2px',
                        height: `${height}px`,
                        borderRadius: '8px',
                        backgroundColor: colors.backgroundColor,
                        color: colors.color,
                        border: colors.border || 'none',
                        padding: '4px 3px',
                        fontSize: '11px',
                        lineHeight: '1.25',
                        overflow: 'hidden',
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'flex-start',
                        boxShadow: isCurrentWeek ? '0 1px 3px rgba(0, 0, 0, 0.08)' : 'none',
                        zIndex: isCurrentWeek ? 10 : 5,
                      }}
                    >
                      <div
                        style={{
                          fontWeight: 600,
                          fontSize: cardTitleFontSize,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          display: '-webkit-box',
                          WebkitLineClamp: span > 2 ? 3 : 2,
                          WebkitBoxOrient: 'vertical',
                        }}
                      >
                        {course.courseName}
                      </div>
                      {meeting.location && (
                        <div
                          style={{
                            fontSize: fontSize === 'compact' ? '8px' : '9px',
                            opacity: 0.85,
                            marginTop: '2px',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          @{meeting.location}
                        </div>
                      )}
                      {hasConflict && (
                        <div
                          style={{
                            position: 'absolute',
                            top: '2px',
                            right: '2px',
                            width: '6px',
                            height: '6px',
                            borderRadius: '50%',
                            backgroundColor: '#FF3B30',
                          }}
                          title="课程时间冲突"
                        />
                      )}
                    </div>
                  );
                })}
              </div>
            );
          })}
        </div>
      </div>

      {/* Course Detail Modal */}
      <CourseDetailModal
        item={activeMeetingRef}
        onClose={() => setActiveMeetingRef(null)}
        onDelete={(idx) => onDeleteCourse(idx)}
        onEdit={(item) => {
          setEditingRef(item);
          setIsAddOpen(true);
        }}
      />

      {/* Add / Edit Course Modal */}
      <AddCourseModal
        isOpen={isAddOpen}
        onClose={() => {
          setIsAddOpen(false);
          setEditingRef(null);
        }}
        onSave={(c, editIdx) => {
          if (editIdx !== undefined) {
            onUpdateCourse(editIdx, c);
          } else {
            onAddCourse(c);
          }
        }}
        editItem={editingRef}
      />

      {/* Text Timetable Export Modal */}
      <TextScheduleModal
        isOpen={isTextExportOpen}
        onClose={() => setIsTextExportOpen(false)}
        courses={courses}
      />
    </div>
  );
};

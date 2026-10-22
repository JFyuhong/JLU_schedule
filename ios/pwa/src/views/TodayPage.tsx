import React, { useState, useEffect } from 'react';
import {
  CourseMeetingRef,
  CourseSchedule,
  SECTION_RANGES,
  WEEKDAY_NAMES,
} from '../core/types';
import { SimpleDate } from '../core/SimpleDate';
import {
  displayMeetings,
  guessCurrentWeek,
  resolveOverlaps,
  totalWeeks,
} from '../core/WeekScheduleCalculator';
import { CourseDetailModal } from '../components/CourseDetailModal';
import { Clock, MapPin, User, CheckCircle2, AlertCircle } from 'lucide-react';

interface TodayPageProps {
  courses: CourseSchedule[];
  semesterStartDate: string | null;
  showNonCurrent: boolean;
  onDeleteCourse: (index: number) => void;
}

export const TodayPage: React.FC<TodayPageProps> = ({
  courses,
  semesterStartDate,
  showNonCurrent,
  onDeleteCourse,
}) => {
  const [activeItem, setActiveItem] = useState<CourseMeetingRef | null>(null);
  const [currentTimeStr, setCurrentTimeStr] = useState<string>('');

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      const hh = String(now.getHours()).padStart(2, '0');
      const mm = String(now.getMinutes()).padStart(2, '0');
      setCurrentTimeStr(`${hh}:${mm}`);
    };
    updateTime();
    const timer = setInterval(updateTime, 30000);
    return () => clearInterval(timer);
  }, []);

  const today = SimpleDate.today();
  const startDate = semesterStartDate
    ? SimpleDate.parse(semesterStartDate) ?? today.previousOrSameMonday()
    : today.previousOrSameMonday();

  const maxWeeks = Math.max(totalWeeks(courses), 20);
  const currentWeek = guessCurrentWeek(startDate, today, maxWeeks);
  const weekday = today.weekdayMonBased(); // 1..7

  // Filter meetings for today
  const allMeetings = displayMeetings(courses, currentWeek, showNonCurrent);
  const todayMeetings = allMeetings.filter((m) => m.meeting.weekday === weekday);
  const resolvedToday = resolveOverlaps(todayMeetings);

  // Status computation based on wall clock time
  const getMeetingStatus = (startSec: number, endSec: number) => {
    const startTimeStr = SECTION_RANGES[startSec - 1]?.split('-')[0] || '00:00';
    const endTimeStr = SECTION_RANGES[endSec - 1]?.split('-')[1] || '23:59';

    if (currentTimeStr < startTimeStr) return 'UPCOMING';
    if (currentTimeStr > endTimeStr) return 'FINISHED';
    return 'ONGOING';
  };

  const earliestTime =
    resolvedToday.length > 0
      ? SECTION_RANGES[resolvedToday[0].meeting.startSection - 1]?.split('-')[0]
      : null;
  const latestTime =
    resolvedToday.length > 0
      ? SECTION_RANGES[resolvedToday[resolvedToday.length - 1].meeting.endSection - 1]?.split('-')[1]
      : null;

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
      {/* iOS Header */}
      <header
        className="ios-glass"
        style={{
          padding: '16px 20px 12px 20px',
          borderBottom: '0.5px solid var(--border-color)',
          display: 'flex',
          flexDirection: 'column',
          gap: '2px',
        }}
      >
        <div style={{ fontSize: '13px', fontWeight: 600, color: 'var(--accent-color)' }}>
          第 {currentWeek} 周 · {WEEKDAY_NAMES[weekday - 1]}
        </div>
        <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between' }}>
          <h1 style={{ fontSize: '26px', fontWeight: 700, letterSpacing: '-0.5px' }}>
            今日课程
          </h1>
          <span style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>
            {today.year}年{today.month}月{today.day}日
          </span>
        </div>
      </header>

      <div style={{ padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
        {/* Today Summary Card */}
        <div
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
          <div>
            <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '3px' }}>
              课程统计
            </div>
            <div style={{ fontSize: '18px', fontWeight: 700, color: 'var(--text-primary)' }}>
              今日共 {resolvedToday.length} 门课程
            </div>
          </div>
          {earliestTime && latestTime && (
            <div style={{ textAlign: 'right' }}>
              <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '3px' }}>
                上课时段
              </div>
              <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--accent-color)' }}>
                {earliestTime} - {latestTime}
              </div>
            </div>
          )}
        </div>

        {/* Course List / Timeline */}
        {resolvedToday.length === 0 ? (
          <div
            style={{
              padding: '60px 20px',
              textAlign: 'center',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: '12px',
              color: 'var(--text-secondary)',
            }}
          >
            <CheckCircle2 size={48} color="var(--accent-color)" opacity={0.7} />
            <div style={{ fontSize: '17px', fontWeight: 600, color: 'var(--text-primary)' }}>
              今天没有课程安排
            </div>
            <div style={{ fontSize: '13px', maxWidth: '240px' }}>
              享受自由的校园生活吧，也可以提前查看本周其他安排。
            </div>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {resolvedToday.map((item, idx) => {
              const { meeting, course, isCurrentWeek } = item;
              const status = getMeetingStatus(meeting.startSection, meeting.endSection);
              const startTime = SECTION_RANGES[meeting.startSection - 1]?.split('-')[0] || '';
              const endTime = SECTION_RANGES[meeting.endSection - 1]?.split('-')[1] || '';

              const isOngoing = status === 'ONGOING';
              const isFinished = status === 'FINISHED';

              return (
                <div
                  key={idx}
                  onClick={() => setActiveItem(item)}
                  className="ios-button"
                  style={{
                    backgroundColor: 'var(--card-bg)',
                    border: isOngoing
                      ? '1.5px solid var(--accent-color)'
                      : '0.5px solid var(--border-color)',
                    borderRadius: '16px',
                    padding: '16px',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '10px',
                    opacity: isFinished ? 0.65 : 1,
                    boxShadow: isOngoing ? '0 4px 14px rgba(250, 90, 58, 0.15)' : 'none',
                    position: 'relative',
                  }}
                >
                  {/* Top Bar: Time and Status Badge */}
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <Clock size={15} color="var(--text-secondary)" />
                      <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-primary)' }}>
                        {startTime} - {endTime}
                      </span>
                      <span style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                        (第 {meeting.startSection} - {meeting.endSection} 节)
                      </span>
                    </div>

                    <div>
                      {isOngoing && (
                        <span
                          style={{
                            fontSize: '11px',
                            fontWeight: 600,
                            backgroundColor: 'var(--accent-color)',
                            color: 'var(--accent-contrast)',
                            padding: '3px 8px',
                            borderRadius: '10px',
                          }}
                        >
                          进行中
                        </span>
                      )}
                      {status === 'UPCOMING' && (
                        <span
                          style={{
                            fontSize: '11px',
                            fontWeight: 500,
                            backgroundColor: 'var(--accent-light)',
                            color: 'var(--accent-color)',
                            padding: '3px 8px',
                            borderRadius: '10px',
                          }}
                        >
                          未开始
                        </span>
                      )}
                      {isFinished && (
                        <span
                          style={{
                            fontSize: '11px',
                            fontWeight: 500,
                            backgroundColor: 'var(--bg-color)',
                            color: 'var(--text-secondary)',
                            padding: '3px 8px',
                            borderRadius: '10px',
                          }}
                        >
                          已结束
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Course Title */}
                  <div style={{ fontSize: '17px', fontWeight: 700, color: 'var(--text-primary)' }}>
                    {course.courseName}
                  </div>

                  {/* Location & Teacher */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: '16px', fontSize: '13px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '4px', color: 'var(--text-secondary)' }}>
                      <MapPin size={14} color="var(--accent-color)" />
                      <span style={{ color: 'var(--text-primary)', fontWeight: 500 }}>
                        {meeting.location || '未指定教室'}
                      </span>
                    </div>
                    {course.teacher && (
                      <div style={{ display: 'flex', alignItems: 'center', gap: '4px', color: 'var(--text-secondary)' }}>
                        <User size={14} />
                        <span>{course.teacher}</span>
                      </div>
                    )}
                  </div>

                  {!isCurrentWeek && (
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '4px',
                        fontSize: '11px',
                        color: 'var(--text-secondary)',
                      }}
                    >
                      <AlertCircle size={13} />
                      非本周课程（下次上课：第 {item.nextActiveWeek} 周）
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      <CourseDetailModal
        item={activeItem}
        onClose={() => setActiveItem(null)}
        onDelete={onDeleteCourse}
        onEdit={() => {}}
      />
    </div>
  );
};

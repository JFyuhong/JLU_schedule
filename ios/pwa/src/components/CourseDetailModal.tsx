import React, { useState } from 'react';
import { CourseMeetingRef, SECTION_RANGES, WEEKDAY_NAMES } from '../core/types';
import { MapPin, User, Clock, Calendar, Award, Copy, Check, Trash2, Edit3, X } from 'lucide-react';

interface CourseDetailModalProps {
  item: CourseMeetingRef | null;
  onClose: () => void;
  onDelete: (courseIndex: number) => void;
  onEdit: (item: CourseMeetingRef) => void;
}

export const CourseDetailModal: React.FC<CourseDetailModalProps> = ({
  item,
  onClose,
  onDelete,
  onEdit,
}) => {
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  if (!item) return null;

  const { course, meeting, courseIndex } = item;

  const copyText = (text: string, key: string) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 1800);
  };

  const timeRange = `${SECTION_RANGES[meeting.startSection - 1]?.split('-')[0] || ''} - ${
    SECTION_RANGES[meeting.endSection - 1]?.split('-')[1] || ''
  }`;

  const formatWeekRules = () => {
    if (meeting.weekRules.length === 0) return course.rawWeekText || '全周';
    return meeting.weekRules
      .map((r) => {
        const parity = r.parity === 'ODD' ? ' (单周)' : r.parity === 'EVEN' ? ' (双周)' : '';
        return r.startWeek === r.endWeek ? `第${r.startWeek}周${parity}` : `${r.startWeek}-${r.endWeek}周${parity}`;
      })
      .join(', ');
  };

  return (
    <div
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.45)',
        backdropFilter: 'blur(4px)',
        WebkitBackdropFilter: 'blur(4px)',
        zIndex: 100,
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'flex-end',
        animation: 'fadeIn 0.18s ease',
      }}
      onClick={onClose}
    >
      <div
        style={{
          backgroundColor: 'var(--sheet-bg)',
          borderTopLeftRadius: '20px',
          borderTopRightRadius: '20px',
          maxHeight: '85vh',
          paddingBottom: 'calc(var(--sab) + 16px)',
          overflowY: 'auto',
          boxShadow: '0 -8px 30px rgba(0, 0, 0, 0.25)',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* iOS Pull Handle */}
        <div
          style={{
            width: '36px',
            height: '4px',
            borderRadius: '2px',
            backgroundColor: 'var(--text-tertiary)',
            margin: '10px auto 14px auto',
            opacity: 0.6,
          }}
        />

        {/* Header */}
        <div
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
            padding: '0 20px 14px 20px',
            borderBottom: '0.5px solid var(--divider-color)',
          }}
        >
          <div>
            <h2
              style={{
                fontSize: '20px',
                fontWeight: 700,
                color: 'var(--text-primary)',
                letterSpacing: '-0.4px',
                marginBottom: '4px',
              }}
            >
              {course.courseName}
            </h2>
            <div
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                fontSize: '12px',
                color: item.isCurrentWeek ? 'var(--accent-color)' : 'var(--text-secondary)',
                fontWeight: 500,
              }}
            >
              <span
                style={{
                  width: '6px',
                  height: '6px',
                  borderRadius: '50%',
                  backgroundColor: item.isCurrentWeek ? 'var(--accent-color)' : 'var(--text-tertiary)',
                }}
              />
              {item.isCurrentWeek ? '本周有课' : `非本周 (第 ${item.nextActiveWeek} 周有课)`}
            </div>
          </div>
          <button
            onClick={onClose}
            className="ios-button"
            style={{
              background: 'var(--bg-color)',
              border: 'none',
              borderRadius: '50%',
              width: '32px',
              height: '32px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--text-secondary)',
            }}
          >
            <X size={18} />
          </button>
        </div>

        {/* Info Rows */}
        <div style={{ padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {/* Location */}
          <div
            onClick={() => copyText(meeting.location || '未指定教室', 'loc')}
            className="ios-button"
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              backgroundColor: 'var(--card-bg)',
              border: '0.5px solid var(--border-color)',
              borderRadius: '12px',
              padding: '12px 14px',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div
                style={{
                  width: '32px',
                  height: '32px',
                  borderRadius: '8px',
                  backgroundColor: 'var(--accent-light)',
                  color: 'var(--accent-color)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <MapPin size={17} />
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>上课教室</div>
                <div style={{ fontSize: '15px', fontWeight: 600, color: 'var(--text-primary)' }}>
                  {meeting.location || '未指定教室'}
                </div>
              </div>
            </div>
            <span style={{ fontSize: '12px', color: 'var(--text-tertiary)', display: 'flex', alignItems: 'center', gap: '3px' }}>
              {copiedKey === 'loc' ? <Check size={14} color="var(--accent-color)" /> : <Copy size={14} />}
              {copiedKey === 'loc' ? '已复制' : '复制'}
            </span>
          </div>

          {/* Teacher */}
          <div
            onClick={() => copyText(course.teacher || '未知教师', 'teacher')}
            className="ios-button"
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              backgroundColor: 'var(--card-bg)',
              border: '0.5px solid var(--border-color)',
              borderRadius: '12px',
              padding: '12px 14px',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div
                style={{
                  width: '32px',
                  height: '32px',
                  borderRadius: '8px',
                  backgroundColor: 'var(--accent-light)',
                  color: 'var(--accent-color)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <User size={17} />
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>授课教师</div>
                <div style={{ fontSize: '15px', fontWeight: 600, color: 'var(--text-primary)' }}>
                  {course.teacher || '未知教师'}
                </div>
              </div>
            </div>
            <span style={{ fontSize: '12px', color: 'var(--text-tertiary)', display: 'flex', alignItems: 'center', gap: '3px' }}>
              {copiedKey === 'teacher' ? <Check size={14} color="var(--accent-color)" /> : <Copy size={14} />}
              {copiedKey === 'teacher' ? '已复制' : '复制'}
            </span>
          </div>

          {/* Time & Section */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              backgroundColor: 'var(--card-bg)',
              border: '0.5px solid var(--border-color)',
              borderRadius: '12px',
              padding: '12px 14px',
              gap: '10px',
            }}
          >
            <div
              style={{
                width: '32px',
                height: '32px',
                borderRadius: '8px',
                backgroundColor: 'var(--accent-light)',
                color: 'var(--accent-color)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Clock size={17} />
            </div>
            <div>
              <div style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>上课时间</div>
              <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--text-primary)' }}>
                {WEEKDAY_NAMES[meeting.weekday - 1]} 第 {meeting.startSection} - {meeting.endSection} 节
                <span style={{ marginLeft: '6px', fontSize: '12px', color: 'var(--text-secondary)', fontWeight: 400 }}>
                  ({timeRange})
                </span>
              </div>
            </div>
          </div>

          {/* Weeks */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              backgroundColor: 'var(--card-bg)',
              border: '0.5px solid var(--border-color)',
              borderRadius: '12px',
              padding: '12px 14px',
              gap: '10px',
            }}
          >
            <div
              style={{
                width: '32px',
                height: '32px',
                borderRadius: '8px',
                backgroundColor: 'var(--accent-light)',
                color: 'var(--accent-color)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Calendar size={17} />
            </div>
            <div>
              <div style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>周次安排</div>
              <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--text-primary)' }}>
                {formatWeekRules()}
              </div>
            </div>
          </div>

          {/* Credits & Semester */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              backgroundColor: 'var(--card-bg)',
              border: '0.5px solid var(--border-color)',
              borderRadius: '12px',
              padding: '12px 14px',
              gap: '10px',
            }}
          >
            <div
              style={{
                width: '32px',
                height: '32px',
                borderRadius: '8px',
                backgroundColor: 'var(--accent-light)',
                color: 'var(--accent-color)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Award size={17} />
            </div>
            <div>
              <div style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>学分与学期</div>
              <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--text-primary)' }}>
                {course.credit !== null ? `${course.credit} 学分` : '暂无学分信息'}
                {course.semester && (
                  <span style={{ marginLeft: '6px', fontSize: '12px', color: 'var(--text-secondary)', fontWeight: 400 }}>
                    · {course.semester}
                  </span>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* Action Buttons */}
        <div
          style={{
            padding: '4px 20px 0 20px',
            display: 'flex',
            gap: '12px',
          }}
        >
          <button
            onClick={() => {
              onClose();
              onEdit(item);
            }}
            className="ios-button"
            style={{
              flex: 1,
              height: '46px',
              borderRadius: '12px',
              border: '1px solid var(--border-color)',
              backgroundColor: 'var(--card-bg)',
              color: 'var(--text-primary)',
              fontSize: '15px',
              fontWeight: 600,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '6px',
            }}
          >
            <Edit3 size={16} />
            编辑
          </button>
          <button
            onClick={() => {
              if (window.confirm(`确定要从当前课表中删除「${course.courseName}」吗？`)) {
                onDelete(courseIndex);
                onClose();
              }
            }}
            className="ios-button"
            style={{
              flex: 1,
              height: '46px',
              borderRadius: '12px',
              border: 'none',
              backgroundColor: 'rgba(255, 59, 48, 0.1)',
              color: '#FF3B30',
              fontSize: '15px',
              fontWeight: 600,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '6px',
            }}
          >
            <Trash2 size={16} />
            删除
          </button>
        </div>
      </div>
    </div>
  );
};

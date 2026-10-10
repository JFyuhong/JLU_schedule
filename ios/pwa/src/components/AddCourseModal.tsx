import React, { useState, useEffect } from 'react';
import { CourseMeetingRef, CourseSchedule, WEEKDAY_NAMES } from '../core/types';
import { X } from 'lucide-react';

interface AddCourseModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (course: CourseSchedule, editIndex?: number) => void;
  editItem?: CourseMeetingRef | null;
}

export const AddCourseModal: React.FC<AddCourseModalProps> = ({
  isOpen,
  onClose,
  onSave,
  editItem,
}) => {
  const [courseName, setCourseName] = useState('');
  const [teacher, setTeacher] = useState('');
  const [location, setLocation] = useState('');
  const [weekday, setWeekday] = useState(1);
  const [startSection, setStartSection] = useState(1);
  const [endSection, setEndSection] = useState(2);
  const [startWeek, setStartWeek] = useState(1);
  const [endWeek, setEndWeek] = useState(16);
  const [parity, setParity] = useState('ALL');
  const [credit, setCredit] = useState('');

  useEffect(() => {
    if (editItem) {
      const { course, meeting } = editItem;
      setCourseName(course.courseName);
      setTeacher(course.teacher);
      setLocation(meeting.location);
      setWeekday(meeting.weekday);
      setStartSection(meeting.startSection);
      setEndSection(meeting.endSection);
      if (meeting.weekRules.length > 0) {
        setStartWeek(meeting.weekRules[0].startWeek);
        setEndWeek(meeting.weekRules[0].endWeek);
        setParity(meeting.weekRules[0].parity);
      } else {
        setStartWeek(1);
        setEndWeek(16);
        setParity('ALL');
      }
      setCredit(course.credit !== null ? String(course.credit) : '');
    } else {
      setCourseName('');
      setTeacher('');
      setLocation('');
      setWeekday(1);
      setStartSection(1);
      setEndSection(2);
      setStartWeek(1);
      setEndWeek(16);
      setParity('ALL');
      setCredit('');
    }
  }, [editItem, isOpen]);

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!courseName.trim()) {
      alert('请输入课程名称');
      return;
    }
    const parsedCredit = credit.trim() ? parseFloat(credit.trim()) : null;

    const newCourse: CourseSchedule = {
      courseName: courseName.trim(),
      teacher: teacher.trim(),
      semester: editItem ? editItem.course.semester : '手动添加',
      credit: Number.isFinite(parsedCredit) ? parsedCredit : null,
      rawWeekText: `${startWeek}-${endWeek}周`,
      meetings: [
        {
          weekday,
          startSection: Math.min(startSection, endSection),
          endSection: Math.max(startSection, endSection),
          weekRules: [
            {
              startWeek: Math.min(startWeek, endWeek),
              endWeek: Math.max(startWeek, endWeek),
              parity,
            },
          ],
          location: location.trim(),
        },
      ],
    };

    onSave(newCourse, editItem ? editItem.courseIndex : undefined);
    onClose();
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
        zIndex: 110,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '20px',
        animation: 'fadeIn 0.18s ease',
      }}
      onClick={onClose}
    >
      <div
        style={{
          backgroundColor: 'var(--card-bg)',
          borderRadius: '16px',
          width: '100%',
          maxWidth: '420px',
          maxHeight: '90vh',
          overflowY: 'auto',
          boxShadow: '0 12px 36px rgba(0, 0, 0, 0.25)',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '16px 20px',
            borderBottom: '0.5px solid var(--divider-color)',
          }}
        >
          <h3 style={{ fontSize: '17px', fontWeight: 600 }}>
            {editItem ? '编辑课程' : '添加课程'}
          </h3>
          <button
            onClick={onClose}
            className="ios-button"
            style={{
              background: 'none',
              border: 'none',
              color: 'var(--text-secondary)',
            }}
          >
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} style={{ padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <div>
            <label style={{ fontSize: '13px', color: 'var(--text-secondary)', display: 'block', marginBottom: '6px' }}>
              课程名称 *
            </label>
            <input
              type="text"
              value={courseName}
              onChange={(e) => setCourseName(e.target.value)}
              placeholder="例如：高等数学"
              required
              style={{
                width: '100%',
                height: '42px',
                borderRadius: '10px',
                border: '1px solid var(--border-color)',
                backgroundColor: 'var(--bg-color)',
                color: 'var(--text-primary)',
                padding: '0 12px',
                fontSize: '15px',
                outline: 'none',
              }}
            />
          </div>

          <div style={{ display: 'flex', gap: '10px' }}>
            <div style={{ flex: 1 }}>
              <label style={{ fontSize: '13px', color: 'var(--text-secondary)', display: 'block', marginBottom: '6px' }}>
                授课教师
              </label>
              <input
                type="text"
                value={teacher}
                onChange={(e) => setTeacher(e.target.value)}
                placeholder="例如：张老师"
                style={{
                  width: '100%',
                  height: '42px',
                  borderRadius: '10px',
                  border: '1px solid var(--border-color)',
                  backgroundColor: 'var(--bg-color)',
                  color: 'var(--text-primary)',
                  padding: '0 12px',
                  fontSize: '15px',
                  outline: 'none',
                }}
              />
            </div>
            <div style={{ flex: 1 }}>
              <label style={{ fontSize: '13px', color: 'var(--text-secondary)', display: 'block', marginBottom: '6px' }}>
                上课地点
              </label>
              <input
                type="text"
                value={location}
                onChange={(e) => setLocation(e.target.value)}
                placeholder="例如：逸夫楼A101"
                style={{
                  width: '100%',
                  height: '42px',
                  borderRadius: '10px',
                  border: '1px solid var(--border-color)',
                  backgroundColor: 'var(--bg-color)',
                  color: 'var(--text-primary)',
                  padding: '0 12px',
                  fontSize: '15px',
                  outline: 'none',
                }}
              />
            </div>
          </div>

          <div>
            <label style={{ fontSize: '13px', color: 'var(--text-secondary)', display: 'block', marginBottom: '6px' }}>
              星期
            </label>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: '4px' }}>
              {WEEKDAY_NAMES.map((name, i) => {
                const dayVal = i + 1;
                const isSelected = weekday === dayVal;
                return (
                  <button
                    type="button"
                    key={name}
                    onClick={() => setWeekday(dayVal)}
                    style={{
                      height: '36px',
                      borderRadius: '8px',
                      border: 'none',
                      backgroundColor: isSelected ? 'var(--accent-color)' : 'var(--bg-color)',
                      color: isSelected ? 'var(--accent-contrast)' : 'var(--text-primary)',
                      fontSize: '13px',
                      fontWeight: isSelected ? 600 : 400,
                      cursor: 'pointer',
                    }}
                  >
                    {name.replace('周', '')}
                  </button>
                );
              })}
            </div>
          </div>

          <div style={{ display: 'flex', gap: '10px' }}>
            <div style={{ flex: 1 }}>
              <label style={{ fontSize: '13px', color: 'var(--text-secondary)', display: 'block', marginBottom: '6px' }}>
                起始节次
              </label>
              <select
                value={startSection}
                onChange={(e) => setStartSection(Number(e.target.value))}
                style={{
                  width: '100%',
                  height: '42px',
                  borderRadius: '10px',
                  border: '1px solid var(--border-color)',
                  backgroundColor: 'var(--bg-color)',
                  color: 'var(--text-primary)',
                  padding: '0 10px',
                  fontSize: '14px',
                  outline: 'none',
                }}
              >
                {Array.from({ length: 12 }, (_, i) => i + 1).map((s) => (
                  <option key={s} value={s}>
                    第 {s} 节
                  </option>
                ))}
              </select>
            </div>
            <div style={{ flex: 1 }}>
              <label style={{ fontSize: '13px', color: 'var(--text-secondary)', display: 'block', marginBottom: '6px' }}>
                结束节次
              </label>
              <select
                value={endSection}
                onChange={(e) => setEndSection(Number(e.target.value))}
                style={{
                  width: '100%',
                  height: '42px',
                  borderRadius: '10px',
                  border: '1px solid var(--border-color)',
                  backgroundColor: 'var(--bg-color)',
                  color: 'var(--text-primary)',
                  padding: '0 10px',
                  fontSize: '14px',
                  outline: 'none',
                }}
              >
                {Array.from({ length: 12 }, (_, i) => i + 1).map((s) => (
                  <option key={s} value={s}>
                    第 {s} 节
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '10px' }}>
            <div style={{ flex: 1 }}>
              <label style={{ fontSize: '13px', color: 'var(--text-secondary)', display: 'block', marginBottom: '6px' }}>
                起始周
              </label>
              <input
                type="number"
                min="1"
                max="30"
                value={startWeek}
                onChange={(e) => setStartWeek(Number(e.target.value))}
                style={{
                  width: '100%',
                  height: '42px',
                  borderRadius: '10px',
                  border: '1px solid var(--border-color)',
                  backgroundColor: 'var(--bg-color)',
                  color: 'var(--text-primary)',
                  padding: '0 12px',
                  fontSize: '15px',
                  outline: 'none',
                }}
              />
            </div>
            <div style={{ flex: 1 }}>
              <label style={{ fontSize: '13px', color: 'var(--text-secondary)', display: 'block', marginBottom: '6px' }}>
                结束周
              </label>
              <input
                type="number"
                min="1"
                max="30"
                value={endWeek}
                onChange={(e) => setEndWeek(Number(e.target.value))}
                style={{
                  width: '100%',
                  height: '42px',
                  borderRadius: '10px',
                  border: '1px solid var(--border-color)',
                  backgroundColor: 'var(--bg-color)',
                  color: 'var(--text-primary)',
                  padding: '0 12px',
                  fontSize: '15px',
                  outline: 'none',
                }}
              />
            </div>
          </div>

          <div>
            <label style={{ fontSize: '13px', color: 'var(--text-secondary)', display: 'block', marginBottom: '6px' }}>
              单双周
            </label>
            <div style={{ display: 'flex', gap: '8px' }}>
              {[
                { key: 'ALL', label: '全部周' },
                { key: 'ODD', label: '单周' },
                { key: 'EVEN', label: '双周' },
              ].map((p) => {
                const isSelected = parity === p.key;
                return (
                  <button
                    type="button"
                    key={p.key}
                    onClick={() => setParity(p.key)}
                    style={{
                      flex: 1,
                      height: '36px',
                      borderRadius: '8px',
                      border: 'none',
                      backgroundColor: isSelected ? 'var(--accent-color)' : 'var(--bg-color)',
                      color: isSelected ? 'var(--accent-contrast)' : 'var(--text-primary)',
                      fontSize: '13px',
                      fontWeight: isSelected ? 600 : 400,
                      cursor: 'pointer',
                    }}
                  >
                    {p.label}
                  </button>
                );
              })}
            </div>
          </div>

          <div>
            <label style={{ fontSize: '13px', color: 'var(--text-secondary)', display: 'block', marginBottom: '6px' }}>
              学分 (可选)
            </label>
            <input
              type="number"
              step="0.5"
              value={credit}
              onChange={(e) => setCredit(e.target.value)}
              placeholder="例如：3.0"
              style={{
                width: '100%',
                height: '42px',
                borderRadius: '10px',
                border: '1px solid var(--border-color)',
                backgroundColor: 'var(--bg-color)',
                color: 'var(--text-primary)',
                padding: '0 12px',
                fontSize: '15px',
                outline: 'none',
              }}
            />
          </div>

          <button
            type="submit"
            className="ios-button"
            style={{
              marginTop: '8px',
              height: '46px',
              borderRadius: '12px',
              border: 'none',
              backgroundColor: 'var(--accent-color)',
              color: 'var(--accent-contrast)',
              fontSize: '16px',
              fontWeight: 600,
            }}
          >
            保存
          </button>
        </form>
      </div>
    </div>
  );
};

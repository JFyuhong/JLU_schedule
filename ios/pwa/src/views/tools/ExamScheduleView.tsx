import React, { useState, useEffect } from 'react';
import { ExamItem, parseExamSchedule } from '../../core/ExamScheduleParser';
import { ExamStore } from '../../core/ExamStore';
import {
  Calendar,
  Clock,
  MapPin,
  ChevronLeft,
  Plus,
  Trash2,
  Edit2,
  X,
  Sparkles,
} from 'lucide-react';

interface ExamScheduleViewProps {
  onBack: () => void;
}

export const ExamScheduleView: React.FC<ExamScheduleViewProps> = ({ onBack }) => {
  const store = ExamStore.getInstance();
  const [exams, setExams] = useState<ExamItem[]>([]);
  const [isAddOpen, setIsAddOpen] = useState(false);
  const [editingExam, setEditingExam] = useState<ExamItem | null>(null);

  // Form states
  const [formCourse, setFormCourse] = useState('');
  const [formTime, setFormTime] = useState('');
  const [formLocation, setFormLocation] = useState('');
  const [formSeat, setFormSeat] = useState('');
  const [formType, setFormType] = useState('期末考试');

  // Import modal
  const [isImportOpen, setIsImportOpen] = useState(false);
  const [importText, setImportText] = useState('');

  const refreshExams = () => {
    setExams(store.getExams());
  };

  useEffect(() => {
    refreshExams();
  }, []);

  const handleOpenAdd = (exam?: ExamItem) => {
    if (exam) {
      setEditingExam(exam);
      setFormCourse(exam.courseName);
      setFormTime(exam.examTimeText);
      setFormLocation(exam.location);
      setFormSeat(exam.seatNumber);
      setFormType(exam.examType);
    } else {
      setEditingExam(null);
      setFormCourse('');
      setFormTime('');
      setFormLocation('');
      setFormSeat('');
      setFormType('期末考试');
    }
    setIsAddOpen(true);
  };

  const handleSaveExam = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formCourse.trim()) {
      alert('请输入课程名称');
      return;
    }

    const match = /(\d{4})-(\d{2})-(\d{2})(?:\s+(\d{2}):(\d{2}))?/.exec(formTime.trim());
    let ts = 0;
    if (match) {
      const year = Number(match[1]);
      const month = Number(match[2]) - 1;
      const day = Number(match[3]);
      const hour = match[4] ? Number(match[4]) : 9;
      const min = match[5] ? Number(match[5]) : 0;
      ts = new Date(year, month, day, hour, min).getTime();
    }

    if (editingExam) {
      store.updateExam(editingExam.id, {
        courseName: formCourse.trim(),
        examTimeText: formTime.trim(),
        location: formLocation.trim(),
        seatNumber: formSeat.trim(),
        examType: formType.trim(),
        timestamp: ts,
      });
    } else {
      store.addExam({
        id: `custom-exam-${Date.now()}`,
        courseName: formCourse.trim(),
        courseCode: '',
        examTimeText: formTime.trim(),
        location: formLocation.trim(),
        seatNumber: formSeat.trim(),
        examType: formType.trim(),
        timestamp: ts,
        isCustom: true,
      });
    }

    setIsAddOpen(false);
    refreshExams();
  };

  const handleDelete = (id: string, name: string) => {
    if (window.confirm(`确定删除「${name}」考试安排吗？`)) {
      store.deleteExam(id);
      refreshExams();
    }
  };

  const handleImportExams = () => {
    if (!importText.trim()) return;
    const parsed = parseExamSchedule(importText);
    if (parsed.length === 0) {
      alert('未识别到考试安排数据，请确保粘贴的是教务系统考试接口返回的 JSON');
      return;
    }
    store.saveExams(parsed);
    setIsImportOpen(false);
    setImportText('');
    refreshExams();
    alert(`成功导入 ${parsed.length} 门考试安排！`);
  };

  const nowTs = Date.now();
  const upcomingExams = exams.filter((e) => !e.timestamp || e.timestamp >= nowTs);
  const nextExam = upcomingExams[0];

  const getCountdownText = (ts: number) => {
    if (!ts) return '未设置时间';
    const diff = ts - nowTs;
    if (diff < 0) return '已结束';
    const days = Math.floor(diff / (24 * 3600 * 1000));
    if (days === 0) return '今天考试';
    if (days === 1) return '明天考试';
    return `还有 ${days} 天`;
  };

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
      {/* Header */}
      <header
        className="ios-glass"
        style={{
          padding: '12px 16px',
          borderBottom: '0.5px solid var(--border-color)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <button
          onClick={onBack}
          className="ios-button"
          style={{
            background: 'none',
            border: 'none',
            color: 'var(--accent-color)',
            display: 'flex',
            alignItems: 'center',
            gap: '2px',
            fontSize: '16px',
            fontWeight: 500,
          }}
        >
          <ChevronLeft size={22} />
          返回
        </button>

        <h2 style={{ fontSize: '17px', fontWeight: 600 }}>考试安排与倒计时</h2>

        <button
          onClick={() => handleOpenAdd()}
          className="ios-button"
          style={{
            background: 'none',
            border: 'none',
            color: 'var(--accent-color)',
            display: 'flex',
            alignItems: 'center',
            fontSize: '15px',
            fontWeight: 600,
          }}
        >
          <Plus size={18} />
          添加
        </button>
      </header>

      <div style={{ padding: '16px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
        {/* Next Exam Hero Card */}
        {nextExam && (
          <div
            style={{
              backgroundColor: 'var(--card-bg)',
              border: '0.5px solid var(--border-color)',
              borderRadius: '16px',
              padding: '18px',
              display: 'flex',
              flexDirection: 'column',
              gap: '10px',
              boxShadow: '0 4px 16px rgba(0, 0, 0, 0.05)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <span
                style={{
                  fontSize: '11px',
                  fontWeight: 600,
                  backgroundColor: 'var(--accent-light)',
                  color: 'var(--accent-color)',
                  padding: '3px 8px',
                  borderRadius: '8px',
                }}
              >
                距离下一场考试
              </span>
              <span style={{ fontSize: '15px', fontWeight: 700, color: 'var(--accent-color)' }}>
                {getCountdownText(nextExam.timestamp)}
              </span>
            </div>

            <div style={{ fontSize: '20px', fontWeight: 700, color: 'var(--text-primary)' }}>
              {nextExam.courseName}
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '13px', color: 'var(--text-secondary)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <Clock size={14} color="var(--accent-color)" />
                <span style={{ color: 'var(--text-primary)', fontWeight: 500 }}>
                  {nextExam.examTimeText || '时间待定'}
                </span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <MapPin size={14} color="var(--accent-color)" />
                <span style={{ color: 'var(--text-primary)', fontWeight: 500 }}>
                  {nextExam.location || '考场待定'}
                </span>
                {nextExam.seatNumber && <span>· 座位：{nextExam.seatNumber}</span>}
              </div>
            </div>
          </div>
        )}

        {/* Action Controls */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 4px' }}>
          <button
            onClick={() => setIsImportOpen(true)}
            className="ios-button"
            style={{
              background: 'var(--card-bg)',
              border: '0.5px solid var(--border-color)',
              borderRadius: '8px',
              padding: '4px 10px',
              fontSize: '12px',
              color: 'var(--accent-color)',
              display: 'flex',
              alignItems: 'center',
              gap: '4px',
            }}
          >
            <Sparkles size={13} />
            导入教务考试安排
          </button>

          {exams.length > 0 && (
            <button
              onClick={() => {
                if (window.confirm('确定清空全部考试安排吗？')) {
                  store.clearExams();
                  refreshExams();
                }
              }}
              style={{
                background: 'none',
                border: 'none',
                fontSize: '12px',
                color: 'var(--text-tertiary)',
                cursor: 'pointer',
              }}
            >
              清空
            </button>
          )}
        </div>

        {/* Exam List */}
        {exams.length === 0 ? (
          <div
            style={{
              padding: '60px 20px',
              textAlign: 'center',
              color: 'var(--text-secondary)',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: '10px',
            }}
          >
            <Calendar size={40} color="var(--accent-color)" opacity={0.6} />
            <div style={{ fontSize: '15px', fontWeight: 600, color: 'var(--text-primary)' }}>
              暂无考试安排
            </div>
            <div style={{ fontSize: '12px', maxWidth: '240px' }}>
              点击右上角「添加」手动添加考试日程，或点击「导入教务考试安排」从教务网同步。
            </div>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {exams.map((exam) => {
              const countdown = getCountdownText(exam.timestamp);
              const isFinished = countdown === '已结束';
              const isToday = countdown === '今天考试';

              return (
                <div
                  key={exam.id}
                  style={{
                    backgroundColor: 'var(--card-bg)',
                    border: isToday
                      ? '1.5px solid var(--accent-color)'
                      : '0.5px solid var(--border-color)',
                    borderRadius: '14px',
                    padding: '14px 16px',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '8px',
                    opacity: isFinished ? 0.6 : 1,
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <span
                        style={{
                          fontSize: '11px',
                          padding: '2px 6px',
                          borderRadius: '6px',
                          backgroundColor: 'var(--bg-color)',
                          color: 'var(--text-secondary)',
                        }}
                      >
                        {exam.examType || '考试'}
                      </span>
                      <h4 style={{ fontSize: '16px', fontWeight: 700, color: 'var(--text-primary)' }}>
                        {exam.courseName}
                      </h4>
                    </div>

                    <span
                      style={{
                        fontSize: '12px',
                        fontWeight: 600,
                        color: isToday ? 'var(--accent-color)' : isFinished ? 'var(--text-tertiary)' : 'var(--accent-color)',
                      }}
                    >
                      {countdown}
                    </span>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '14px', fontSize: '13px', color: 'var(--text-secondary)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                      <Clock size={13} />
                      <span>{exam.examTimeText || '时间待定'}</span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                      <MapPin size={13} />
                      <span>{exam.location || '考场待定'}</span>
                      {exam.seatNumber && <span>({exam.seatNumber})</span>}
                    </div>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', borderTop: '0.5px solid var(--divider-color)', paddingTop: '6px' }}>
                    <button
                      onClick={() => handleOpenAdd(exam)}
                      className="ios-button"
                      style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', padding: '4px', fontSize: '12px', display: 'flex', alignItems: 'center', gap: '2px' }}
                    >
                      <Edit2 size={13} />
                      编辑
                    </button>
                    <button
                      onClick={() => handleDelete(exam.id, exam.courseName)}
                      className="ios-button"
                      style={{ background: 'none', border: 'none', color: '#FF3B30', padding: '4px', fontSize: '12px', display: 'flex', alignItems: 'center', gap: '2px' }}
                    >
                      <Trash2 size={13} />
                      删除
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Add / Edit Modal */}
      {isAddOpen && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.45)',
            backdropFilter: 'blur(4px)',
            zIndex: 100,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '20px',
          }}
          onClick={() => setIsAddOpen(false)}
        >
          <div
            style={{
              backgroundColor: 'var(--card-bg)',
              borderRadius: '16px',
              width: '100%',
              maxWidth: '380px',
              padding: '18px',
              boxShadow: '0 12px 30px rgba(0,0,0,0.2)',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '14px' }}>
              <h3 style={{ fontSize: '17px', fontWeight: 600 }}>
                {editingExam ? '编辑考试安排' : '添加考试安排'}
              </h3>
              <button
                onClick={() => setIsAddOpen(false)}
                className="ios-button"
                style={{ background: 'none', border: 'none', color: 'var(--text-secondary)' }}
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSaveExam} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <div>
                <label style={{ fontSize: '12px', color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                  课程名称 *
                </label>
                <input
                  type="text"
                  value={formCourse}
                  onChange={(e) => setFormCourse(e.target.value)}
                  placeholder="例如：大学英语"
                  required
                  style={{
                    width: '100%',
                    height: '38px',
                    borderRadius: '8px',
                    border: '1px solid var(--border-color)',
                    backgroundColor: 'var(--bg-color)',
                    color: 'var(--text-primary)',
                    padding: '0 10px',
                    fontSize: '14px',
                    outline: 'none',
                  }}
                />
              </div>

              <div>
                <label style={{ fontSize: '12px', color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                  考试时间 (格式: YYYY-MM-DD HH:mm)
                </label>
                <input
                  type="text"
                  value={formTime}
                  onChange={(e) => setFormTime(e.target.value)}
                  placeholder="2026-10-15 08:30-10:30"
                  style={{
                    width: '100%',
                    height: '38px',
                    borderRadius: '8px',
                    border: '1px solid var(--border-color)',
                    backgroundColor: 'var(--bg-color)',
                    color: 'var(--text-primary)',
                    padding: '0 10px',
                    fontSize: '14px',
                    outline: 'none',
                  }}
                />
              </div>

              <div style={{ display: 'flex', gap: '8px' }}>
                <div style={{ flex: 1 }}>
                  <label style={{ fontSize: '12px', color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                    考场地点
                  </label>
                  <input
                    type="text"
                    value={formLocation}
                    onChange={(e) => setFormLocation(e.target.value)}
                    placeholder="逸夫楼A101"
                    style={{
                      width: '100%',
                      height: '38px',
                      borderRadius: '8px',
                      border: '1px solid var(--border-color)',
                      backgroundColor: 'var(--bg-color)',
                      color: 'var(--text-primary)',
                      padding: '0 10px',
                      fontSize: '14px',
                      outline: 'none',
                    }}
                  />
                </div>
                <div style={{ width: '90px' }}>
                  <label style={{ fontSize: '12px', color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                    座位号
                  </label>
                  <input
                    type="text"
                    value={formSeat}
                    onChange={(e) => setFormSeat(e.target.value)}
                    placeholder="25号"
                    style={{
                      width: '100%',
                      height: '38px',
                      borderRadius: '8px',
                      border: '1px solid var(--border-color)',
                      backgroundColor: 'var(--bg-color)',
                      color: 'var(--text-primary)',
                      padding: '0 10px',
                      fontSize: '14px',
                      outline: 'none',
                    }}
                  />
                </div>
              </div>

              <button
                type="submit"
                className="ios-button"
                style={{
                  marginTop: '8px',
                  height: '42px',
                  borderRadius: '10px',
                  border: 'none',
                  backgroundColor: 'var(--accent-color)',
                  color: 'var(--accent-contrast)',
                  fontSize: '15px',
                  fontWeight: 600,
                }}
              >
                保存
              </button>
            </form>
          </div>
        </div>
      )}

      {/* Import Modal */}
      {isImportOpen && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.45)',
            backdropFilter: 'blur(4px)',
            zIndex: 100,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '20px',
          }}
          onClick={() => setIsImportOpen(false)}
        >
          <div
            style={{
              backgroundColor: 'var(--card-bg)',
              borderRadius: '16px',
              width: '100%',
              maxWidth: '380px',
              padding: '18px',
              boxShadow: '0 12px 30px rgba(0,0,0,0.2)',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '12px' }}>
              <h3 style={{ fontSize: '17px', fontWeight: 600 }}>导入考试安排</h3>
              <button
                onClick={() => setIsImportOpen(false)}
                className="ios-button"
                style={{ background: 'none', border: 'none', color: 'var(--text-secondary)' }}
              >
                <X size={18} />
              </button>
            </div>

            <p style={{ fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '10px' }}>
              粘贴教务系统考试安排接口（<code>cxxsapxx.do</code> / <code>studentWdksapApp</code>）返回的 JSON 数据：
            </p>

            <textarea
              value={importText}
              onChange={(e) => setImportText(e.target.value)}
              placeholder="粘贴考试安排 JSON 数据..."
              rows={6}
              style={{
                width: '100%',
                borderRadius: '8px',
                border: '1px solid var(--border-color)',
                backgroundColor: 'var(--bg-color)',
                color: 'var(--text-primary)',
                padding: '8px',
                fontSize: '12px',
                fontFamily: 'monospace',
                outline: 'none',
                resize: 'none',
                marginBottom: '12px',
              }}
            />

            <button
              onClick={handleImportExams}
              className="ios-button"
              style={{
                width: '100%',
                height: '42px',
                borderRadius: '10px',
                border: 'none',
                backgroundColor: 'var(--accent-color)',
                color: 'var(--accent-contrast)',
                fontSize: '15px',
                fontWeight: 600,
              }}
            >
              解析并导入考试
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

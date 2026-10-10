import React, { useState, useEffect } from 'react';
import {
  GpaCourse,
  GpaGradeType,
  calculateGpa,
  LEVEL_ORDER,
  LEVEL_SCORES,
  mergeImportedGrades,
} from '../../core/GpaCalculator';
import { GpaStore } from '../../core/GpaStore';
import { parseGradeTranscript } from '../../core/GradeTranscriptParser';
import {
  Calculator,
  Plus,
  Trash2,
  Edit2,
  Check,
  Award,
  ChevronLeft,
  X,
  Sparkles,
} from 'lucide-react';

interface GpaCalculatorViewProps {
  onBack: () => void;
}

export const GpaCalculatorView: React.FC<GpaCalculatorViewProps> = ({ onBack }) => {
  const store = GpaStore.getInstance();
  const [courses, setCourses] = useState<GpaCourse[]>([]);
  const [isAddOpen, setIsAddOpen] = useState(false);
  const [editingCourse, setEditingCourse] = useState<GpaCourse | null>(null);

  // Form states
  const [formName, setFormName] = useState('');
  const [formCredit, setFormCredit] = useState('2.0');
  const [formGradeType, setFormGradeType] = useState<GpaGradeType>('PERCENT');
  const [formScore, setFormScore] = useState('85');
  const [formLevel, setFormLevel] = useState('优秀');

  // Import modal
  const [isImportOpen, setIsImportOpen] = useState(false);
  const [importText, setImportText] = useState('');

  const refreshCourses = () => {
    setCourses(store.getCourses());
  };

  useEffect(() => {
    refreshCourses();
  }, []);

  const summary = calculateGpa(courses);

  const handleOpenAdd = (course?: GpaCourse) => {
    if (course) {
      setEditingCourse(course);
      setFormName(course.name);
      setFormCredit(String(course.credit));
      setFormGradeType(course.gradeType);
      setFormScore(String(course.score));
      setFormLevel(course.level || '优秀');
    } else {
      setEditingCourse(null);
      setFormName('');
      setFormCredit('2.0');
      setFormGradeType('PERCENT');
      setFormScore('85');
      setFormLevel('优秀');
    }
    setIsAddOpen(true);
  };

  const handleSaveCourse = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formName.trim()) {
      alert('请输入课程名称');
      return;
    }
    const creditNum = parseFloat(formCredit);
    if (!Number.isFinite(creditNum) || creditNum <= 0) {
      alert('请输入有效学分');
      return;
    }

    let scoreNum = 0;
    if (formGradeType === 'PERCENT') {
      scoreNum = parseFloat(formScore);
      if (!Number.isFinite(scoreNum) || scoreNum < 0 || scoreNum > 100) {
        alert('请输入 0 - 100 之间的百分制分数');
        return;
      }
    }

    if (editingCourse) {
      store.updateCourse(editingCourse.id, {
        name: formName.trim(),
        credit: creditNum,
        gradeType: formGradeType,
        score: scoreNum,
        level: formGradeType === 'LEVEL5' ? formLevel : '',
      });
    } else {
      store.addCourse({
        id: `custom-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
        name: formName.trim(),
        credit: creditNum,
        gradeType: formGradeType,
        score: scoreNum,
        level: formGradeType === 'LEVEL5' ? formLevel : '',
        included: true,
      });
    }

    setIsAddOpen(false);
    refreshCourses();
  };

  const handleToggleInclude = (id: string, current: boolean) => {
    store.updateCourse(id, { included: !current });
    refreshCourses();
  };

  const handleDelete = (id: string, name: string) => {
    if (window.confirm(`确定要删除课程「${name}」吗？`)) {
      store.deleteCourse(id);
      refreshCourses();
    }
  };

  const handleImportGrades = () => {
    if (!importText.trim()) return;
    const imported = parseGradeTranscript(importText);
    if (imported.length === 0) {
      alert('未识别到成绩数据，请确保粘贴的是教务系统 xscjcx.do 返回的 JSON 数据');
      return;
    }
    const merged = mergeImportedGrades(imported);
    store.saveCourses(merged);
    setIsImportOpen(false);
    setImportText('');
    refreshCourses();
    alert(`成功导入并合并 ${merged.length} 门有效课程成绩（重修同课程号已自动取最高分）！`);
  };

  const allIncluded = courses.length > 0 && courses.every((c) => c.included);

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
      {/* Navigation Bar */}
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

        <h2 style={{ fontSize: '17px', fontWeight: 600 }}>绩点计算器</h2>

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
          录入
        </button>
      </header>

      <div style={{ padding: '16px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
        {/* GPA Summary Card */}
        <div
          style={{
            backgroundColor: 'var(--card-bg)',
            border: '0.5px solid var(--border-color)',
            borderRadius: '16px',
            padding: '18px',
            display: 'flex',
            flexDirection: 'column',
            gap: '14px',
            boxShadow: '0 4px 16px rgba(0, 0, 0, 0.05)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
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
                <Award size={18} />
              </div>
              <span style={{ fontSize: '13px', color: 'var(--text-secondary)', fontWeight: 500 }}>
                保研绩点估算 (4.0 满绩)
              </span>
            </div>
            <span style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>
              以学院最终文件为准
            </span>
          </div>

          <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px' }}>
            <div
              style={{
                fontSize: '40px',
                fontWeight: 800,
                color: 'var(--accent-color)',
                letterSpacing: '-1px',
                lineHeight: 1,
              }}
            >
              {summary.recommendationGpa !== null ? summary.recommendationGpa.toFixed(3) : '--'}
            </div>
            <span style={{ fontSize: '15px', color: 'var(--text-secondary)' }}>/ 4.000</span>
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(3, 1fr)',
              gap: '8px',
              borderTop: '0.5px solid var(--divider-color)',
              paddingTop: '12px',
              textAlign: 'center',
            }}
          >
            <div>
              <div style={{ fontSize: '11px', color: 'var(--text-secondary)', marginBottom: '3px' }}>
                加权平均分
              </div>
              <div style={{ fontSize: '16px', fontWeight: 700, color: 'var(--text-primary)' }}>
                {summary.weightedAverage !== null ? summary.weightedAverage.toFixed(2) : '--'}
              </div>
            </div>
            <div>
              <div style={{ fontSize: '11px', color: 'var(--text-secondary)', marginBottom: '3px' }}>
                算术平均分
              </div>
              <div style={{ fontSize: '16px', fontWeight: 700, color: 'var(--text-primary)' }}>
                {summary.arithmeticAverage !== null ? summary.arithmeticAverage.toFixed(2) : '--'}
              </div>
            </div>
            <div>
              <div style={{ fontSize: '11px', color: 'var(--text-secondary)', marginBottom: '3px' }}>
                已纳入学分
              </div>
              <div style={{ fontSize: '16px', fontWeight: 700, color: 'var(--text-primary)' }}>
                {summary.includedCredits}
                <span style={{ fontSize: '11px', fontWeight: 400, color: 'var(--text-secondary)' }}>
                  {' '}({summary.includedCount}门)
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Action Controls */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 4px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <button
              onClick={() => {
                store.toggleAll(!allIncluded);
                refreshCourses();
              }}
              className="ios-button"
              style={{
                background: 'var(--card-bg)',
                border: '0.5px solid var(--border-color)',
                borderRadius: '8px',
                padding: '4px 10px',
                fontSize: '12px',
                color: 'var(--text-primary)',
              }}
            >
              {allIncluded ? '全不选' : '全选'}
            </button>
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
                gap: '3px',
              }}
            >
              <Sparkles size={13} />
              教务一键导入
            </button>
          </div>

          {courses.length > 0 && (
            <button
              onClick={() => {
                if (window.confirm('确定要清空全部已录入成绩吗？')) {
                  store.clearCourses();
                  refreshCourses();
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

        {/* Course List */}
        {courses.length === 0 ? (
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
            <Calculator size={40} color="var(--accent-color)" opacity={0.6} />
            <div style={{ fontSize: '15px', fontWeight: 600, color: 'var(--text-primary)' }}>
              暂无成绩数据
            </div>
            <div style={{ fontSize: '12px', maxWidth: '240px' }}>
              点击右上角「录入」手动添加课程成绩，或点击「教务一键导入」批量导入。
            </div>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {summary.breakdowns.map(({ course, effectiveScore: score, gradePoint, included }) => {
              return (
                <div
                  key={course.id}
                  style={{
                    backgroundColor: 'var(--card-bg)',
                    border: '0.5px solid var(--border-color)',
                    borderRadius: '12px',
                    padding: '12px 14px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    opacity: included ? 1 : 0.5,
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flex: 1 }}>
                    <input
                      type="checkbox"
                      checked={course.included}
                      onChange={() => handleToggleInclude(course.id, course.included)}
                      style={{
                        width: '18px',
                        height: '18px',
                        accentColor: 'var(--accent-color)',
                        cursor: 'pointer',
                      }}
                    />

                    <div>
                      <div style={{ fontSize: '15px', fontWeight: 600, color: 'var(--text-primary)' }}>
                        {course.name}
                      </div>
                      <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                        {course.credit} 学分 · {course.gradeType === 'LEVEL5' ? `${course.level} (${score}分)` : `${course.score} 分`}
                        {' · '}
                        <span style={{ color: 'var(--accent-color)', fontWeight: 600 }}>
                          绩点 {gradePoint.toFixed(1)}
                        </span>
                      </div>
                    </div>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <button
                      onClick={() => handleOpenAdd(course)}
                      className="ios-button"
                      style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', padding: '6px' }}
                    >
                      <Edit2 size={15} />
                    </button>
                    <button
                      onClick={() => handleDelete(course.id, course.name)}
                      className="ios-button"
                      style={{ background: 'none', border: 'none', color: '#FF3B30', padding: '6px' }}
                    >
                      <Trash2 size={15} />
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
                {editingCourse ? '编辑课程成绩' : '录入课程成绩'}
              </h3>
              <button
                onClick={() => setIsAddOpen(false)}
                className="ios-button"
                style={{ background: 'none', border: 'none', color: 'var(--text-secondary)' }}
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSaveCourse} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <div>
                <label style={{ fontSize: '12px', color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                  课程名称 *
                </label>
                <input
                  type="text"
                  value={formName}
                  onChange={(e) => setFormName(e.target.value)}
                  placeholder="例如：概率论与数理统计"
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
                  学分 *
                </label>
                <input
                  type="number"
                  step="0.5"
                  min="0.5"
                  value={formCredit}
                  onChange={(e) => setFormCredit(e.target.value)}
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
                  成绩类型
                </label>
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button
                    type="button"
                    onClick={() => setFormGradeType('PERCENT')}
                    style={{
                      flex: 1,
                      height: '34px',
                      borderRadius: '8px',
                      border: 'none',
                      backgroundColor: formGradeType === 'PERCENT' ? 'var(--accent-color)' : 'var(--bg-color)',
                      color: formGradeType === 'PERCENT' ? 'var(--accent-contrast)' : 'var(--text-primary)',
                      fontSize: '13px',
                      fontWeight: formGradeType === 'PERCENT' ? 600 : 400,
                      cursor: 'pointer',
                    }}
                  >
                    百分制 (0-100)
                  </button>
                  <button
                    type="button"
                    onClick={() => setFormGradeType('LEVEL5')}
                    style={{
                      flex: 1,
                      height: '34px',
                      borderRadius: '8px',
                      border: 'none',
                      backgroundColor: formGradeType === 'LEVEL5' ? 'var(--accent-color)' : 'var(--bg-color)',
                      color: formGradeType === 'LEVEL5' ? 'var(--accent-contrast)' : 'var(--text-primary)',
                      fontSize: '13px',
                      fontWeight: formGradeType === 'LEVEL5' ? 600 : 400,
                      cursor: 'pointer',
                    }}
                  >
                    五级制等级
                  </button>
                </div>
              </div>

              {formGradeType === 'PERCENT' ? (
                <div>
                  <label style={{ fontSize: '12px', color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                    成绩分数 *
                  </label>
                  <input
                    type="number"
                    min="0"
                    max="100"
                    step="0.5"
                    value={formScore}
                    onChange={(e) => setFormScore(e.target.value)}
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
              ) : (
                <div>
                  <label style={{ fontSize: '12px', color: 'var(--text-secondary)', display: 'block', marginBottom: '4px' }}>
                    等级选择 *
                  </label>
                  <select
                    value={formLevel}
                    onChange={(e) => setFormLevel(e.target.value)}
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
                  >
                    {LEVEL_ORDER.map((l) => (
                      <option key={l} value={l}>
                        {l} (折算 {LEVEL_SCORES[l]}分)
                      </option>
                    ))}
                  </select>
                </div>
              )}

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
                保存成绩
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
              <h3 style={{ fontSize: '17px', fontWeight: 600 }}>导入全部成绩</h3>
              <button
                onClick={() => setIsImportOpen(false)}
                className="ios-button"
                style={{ background: 'none', border: 'none', color: 'var(--text-secondary)' }}
              >
                <X size={18} />
              </button>
            </div>

            <p style={{ fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '10px', lineHeight: 1.4 }}>
              粘贴教务系统成绩查询接口（<code>xscjcx.do</code>）返回的完整 JSON 数据，或使用 Safari 书签脚本在成绩页面直接抓取。
            </p>

            <textarea
              value={importText}
              onChange={(e) => setImportText(e.target.value)}
              placeholder="粘贴 xscjcx.do JSON 响应数据..."
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
              onClick={handleImportGrades}
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
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '6px',
              }}
            >
              <Check size={16} />
              解析并导入成绩
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

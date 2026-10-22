import React, { useState } from 'react';
import { AcademicProgressPlan, parseAcademicProgress } from '../../core/PyfaTranscriptParser';
import { ChevronLeft, GraduationCap, Sparkles, X, CheckCircle2 } from 'lucide-react';

const PROGRESS_STORAGE_KEY = 'jlu_schedule_academic_progress';

interface AcademicProgressViewProps {
  onBack: () => void;
}

export const AcademicProgressView: React.FC<AcademicProgressViewProps> = ({ onBack }) => {
  const [plan, setPlan] = useState<AcademicProgressPlan | null>(() => {
    try {
      const raw = localStorage.getItem(PROGRESS_STORAGE_KEY);
      return raw ? (JSON.parse(raw) as AcademicProgressPlan) : null;
    } catch {
      return null;
    }
  });

  const [isImportOpen, setIsImportOpen] = useState(false);
  const [importText, setImportText] = useState('');

  const savePlan = (newPlan: AcademicProgressPlan | null) => {
    setPlan(newPlan);
    if (newPlan) {
      localStorage.setItem(PROGRESS_STORAGE_KEY, JSON.stringify(newPlan));
    } else {
      localStorage.removeItem(PROGRESS_STORAGE_KEY);
    }
  };

  const handleImport = () => {
    if (!importText.trim()) return;
    const parsed = parseAcademicProgress(importText);
    if (!parsed) {
      alert('未识别到学业完成情况数据，请确保粘贴的是学业完成查询接口返回的 JSON');
      return;
    }
    savePlan(parsed);
    setIsImportOpen(false);
    setImportText('');
    alert(`成功导入培养方案学业进度（共 ${parsed.requirements.length} 个分类模块）！`);
  };

  const percent =
    plan && plan.totalRequiredCredits > 0
      ? Math.min(100, Math.round((plan.totalEarnedCredits / plan.totalRequiredCredits) * 100))
      : 0;

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

        <h2 style={{ fontSize: '17px', fontWeight: 600 }}>学业完成情况</h2>

        <button
          onClick={() => setIsImportOpen(true)}
          className="ios-button"
          style={{
            background: 'none',
            border: 'none',
            color: 'var(--accent-color)',
            display: 'flex',
            alignItems: 'center',
            gap: '2px',
            fontSize: '15px',
            fontWeight: 600,
          }}
        >
          <Sparkles size={16} />
          导入
        </button>
      </header>

      <div style={{ padding: '16px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
        {plan ? (
          <>
            {/* Overview Card */}
            <div
              style={{
                backgroundColor: 'var(--card-bg)',
                border: '0.5px solid var(--border-color)',
                borderRadius: '16px',
                padding: '18px',
                display: 'flex',
                flexDirection: 'column',
                gap: '12px',
                boxShadow: '0 4px 16px rgba(0, 0, 0, 0.05)',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <span style={{ fontSize: '13px', color: 'var(--text-secondary)', fontWeight: 500 }}>
                  总学分修读进度
                </span>
                <span style={{ fontSize: '15px', fontWeight: 700, color: 'var(--accent-color)' }}>
                  {percent}%
                </span>
              </div>

              {/* Progress Bar */}
              <div
                style={{
                  width: '100%',
                  height: '10px',
                  borderRadius: '5px',
                  backgroundColor: 'var(--bg-color)',
                  overflow: 'hidden',
                }}
              >
                <div
                  style={{
                    width: `${percent}%`,
                    height: '100%',
                    backgroundColor: 'var(--accent-color)',
                    borderRadius: '5px',
                    transition: 'width 0.3s ease',
                  }}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
                <div>
                  已获学分：
                  <strong style={{ color: 'var(--accent-color)', fontSize: '16px' }}>
                    {plan.totalEarnedCredits}
                  </strong>
                </div>
                <div style={{ color: 'var(--text-secondary)' }}>
                  毕业要求：<strong>{plan.totalRequiredCredits}</strong> 学分
                </div>
              </div>
            </div>

            {/* Modules List */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <div style={{ fontSize: '13px', color: 'var(--text-secondary)', padding: '0 4px', fontWeight: 500 }}>
                各模块完成明细
              </div>
              {plan.requirements.map((req, i) => {
                const isCompleted = req.earnedCredits >= req.requiredCredits;
                const modPercent =
                  req.requiredCredits > 0
                    ? Math.min(100, Math.round((req.earnedCredits / req.requiredCredits) * 100))
                    : 100;

                return (
                  <div
                    key={i}
                    style={{
                      backgroundColor: 'var(--card-bg)',
                      border: '0.5px solid var(--border-color)',
                      borderRadius: '12px',
                      padding: '12px 14px',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '8px',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        {isCompleted && <CheckCircle2 size={16} color="var(--accent-color)" />}
                        <span style={{ fontSize: '15px', fontWeight: 600, color: 'var(--text-primary)' }}>
                          {req.categoryName}
                        </span>
                      </div>
                      <span
                        style={{
                          fontSize: '13px',
                          fontWeight: 600,
                          color: isCompleted ? 'var(--accent-color)' : 'var(--text-secondary)',
                        }}
                      >
                        {req.earnedCredits} / {req.requiredCredits} 学分
                      </span>
                    </div>

                    <div
                      style={{
                        width: '100%',
                        height: '6px',
                        borderRadius: '3px',
                        backgroundColor: 'var(--bg-color)',
                        overflow: 'hidden',
                      }}
                    >
                      <div
                        style={{
                          width: `${modPercent}%`,
                          height: '100%',
                          backgroundColor: isCompleted ? 'var(--accent-color)' : 'var(--text-tertiary)',
                          borderRadius: '3px',
                        }}
                      />
                    </div>
                  </div>
                );
              })}
            </div>

            <button
              onClick={() => {
                if (window.confirm('确定要清除已保存的学业完成情况吗？')) {
                  savePlan(null);
                }
              }}
              style={{
                alignSelf: 'center',
                background: 'none',
                border: 'none',
                fontSize: '12px',
                color: 'var(--text-tertiary)',
                padding: '8px',
                cursor: 'pointer',
              }}
            >
              清除此记录
            </button>
          </>
        ) : (
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
            <GraduationCap size={44} color="var(--accent-color)" opacity={0.6} />
            <div style={{ fontSize: '15px', fontWeight: 600, color: 'var(--text-primary)' }}>
              暂未导入学业完成情况
            </div>
            <div style={{ fontSize: '12px', maxWidth: '260px', lineHeight: 1.4 }}>
              在吉大教务系统打开「学业完成情况」页面，点击右上角「导入」即可一键同步各模块学分修读进度。
            </div>
            <button
              onClick={() => setIsImportOpen(true)}
              className="ios-button"
              style={{
                marginTop: '10px',
                height: '40px',
                padding: '0 20px',
                borderRadius: '10px',
                border: 'none',
                backgroundColor: 'var(--accent-color)',
                color: 'var(--accent-contrast)',
                fontSize: '14px',
                fontWeight: 600,
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
              }}
            >
              <Sparkles size={16} />
              导入学业进度
            </button>
          </div>
        )}
      </div>

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
              <h3 style={{ fontSize: '17px', fontWeight: 600 }}>导入学业完成情况</h3>
              <button
                onClick={() => setIsImportOpen(false)}
                className="ios-button"
                style={{ background: 'none', border: 'none', color: 'var(--text-secondary)' }}
              >
                <X size={18} />
              </button>
            </div>

            <p style={{ fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '10px', lineHeight: 1.4 }}>
              粘贴教务系统「学生学业完成情况」接口返回的 JSON 数据：
            </p>

            <textarea
              value={importText}
              onChange={(e) => setImportText(e.target.value)}
              placeholder="粘贴学业完成情况 JSON 数据..."
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
              onClick={handleImport}
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
              解析并导入
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

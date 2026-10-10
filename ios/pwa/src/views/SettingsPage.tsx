import React, { useState } from 'react';
import { ProfileStore } from '../core/ProfileStore';
import {
  AppPreferences,
  CourseSchedule,
  DarkModeType,
  DefaultTab,
  FontSize,
  ProfileInfo,
  ThemeType,
} from '../core/types';
import { SimpleDate } from '../core/SimpleDate';
import { inferFromCourses, normalizeToWeekStart } from '../core/SemesterStartDatePolicy';
import { TextScheduleModal } from './tools/TextScheduleModal';
import {
  Layers,
  Calendar,
  Palette,
  Moon,
  Sun,
  Smartphone,
  Download,
  Upload,
  Plus,
  Trash2,
  Edit2,
  Check,
  Share2,
  FileText,
} from 'lucide-react';

interface SettingsPageProps {
  preferences: AppPreferences;
  onUpdatePreferences: (prefs: Partial<AppPreferences>) => void;
  activeProfile: { id: string; name: string; semesterStartDate: string | null };
  courses: CourseSchedule[];
  onProfileChanged: () => void;
}

export const SettingsPage: React.FC<SettingsPageProps> = ({
  preferences,
  onUpdatePreferences,
  activeProfile,
  courses,
  onProfileChanged,
}) => {
  const store = ProfileStore.getInstance();
  const profiles: ProfileInfo[] = store.listProfiles();

  const [editingProfileId, setEditingProfileId] = useState<string | null>(null);
  const [editingName, setEditingName] = useState('');
  const [isCreatingNew, setIsCreatingNew] = useState(false);
  const [newProfileName, setNewProfileName] = useState('');
  const [isTextExportOpen, setIsTextExportOpen] = useState(false);

  const currentStartDate = activeProfile.semesterStartDate || SimpleDate.today().previousOrSameMonday().isoString();

  const handleStartDateChange = (dateStr: string) => {
    const parsed = SimpleDate.parse(dateStr);
    if (parsed) {
      const monday = normalizeToWeekStart(parsed);
      store.setSemesterStartDate(activeProfile.id, monday.isoString());
      onProfileChanged();
    }
  };

  const handleAutoInferStartDate = () => {
    const inferred = inferFromCourses(courses);
    store.setSemesterStartDate(activeProfile.id, inferred.isoString());
    onProfileChanged();
  };

  const handleCreateProfile = () => {
    if (!newProfileName.trim()) return;
    store.createProfile(newProfileName.trim());
    setNewProfileName('');
    setIsCreatingNew(false);
    onProfileChanged();
  };

  const handleRenameProfile = (id: string) => {
    if (!editingName.trim()) return;
    store.renameProfile(id, editingName.trim());
    setEditingProfileId(null);
    onProfileChanged();
  };

  const handleDeleteProfile = (id: string, name: string) => {
    if (profiles.length <= 1) {
      alert('请至少保留一份课表');
      return;
    }
    if (window.confirm(`确定要删除课表「${name}」吗？此操作不可逆。`)) {
      store.deleteProfile(id);
      onProfileChanged();
    }
  };

  const handleExportBackup = () => {
    const json = store.exportBackup();
    const blob = new Blob([json], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `jlu_schedule_backup_${new Date().toISOString().split('T')[0]}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const handleImportBackup = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const text = event.target?.result as string;
        const count = store.importBackup(text);
        alert(`成功恢复 ${count} 份课表！`);
        onProfileChanged();
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        alert(`导入备份失败: ${msg}`);
      }
    };
    reader.readAsText(file);
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
      <header
        className="ios-glass"
        style={{
          padding: '16px 20px 12px 20px',
          borderBottom: '0.5px solid var(--border-color)',
        }}
      >
        <h1 style={{ fontSize: '26px', fontWeight: 700, letterSpacing: '-0.5px' }}>
          设置
        </h1>
      </header>

      <div style={{ padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
        {/* Section 1: Profile Management */}
        <div
          style={{
            backgroundColor: 'var(--card-bg)',
            border: '0.5px solid var(--border-color)',
            borderRadius: '16px',
            padding: '16px',
            display: 'flex',
            flexDirection: 'column',
            gap: '12px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Layers size={18} color="var(--accent-color)" />
              <h3 style={{ fontSize: '16px', fontWeight: 600 }}>多课表管理</h3>
            </div>
            <button
              onClick={() => setIsCreatingNew(!isCreatingNew)}
              className="ios-button"
              style={{
                background: 'var(--bg-color)',
                border: '0.5px solid var(--border-color)',
                borderRadius: '8px',
                padding: '4px 8px',
                fontSize: '12px',
                fontWeight: 500,
                display: 'flex',
                alignItems: 'center',
                gap: '4px',
                color: 'var(--text-primary)',
              }}
            >
              <Plus size={14} />
              新建课表
            </button>
          </div>

          {isCreatingNew && (
            <div style={{ display: 'flex', gap: '8px', padding: '6px 0' }}>
              <input
                type="text"
                value={newProfileName}
                onChange={(e) => setNewProfileName(e.target.value)}
                placeholder="课表名称..."
                style={{
                  flex: 1,
                  height: '36px',
                  borderRadius: '8px',
                  border: '1px solid var(--border-color)',
                  backgroundColor: 'var(--bg-color)',
                  color: 'var(--text-primary)',
                  padding: '0 10px',
                  fontSize: '13px',
                  outline: 'none',
                }}
              />
              <button
                onClick={handleCreateProfile}
                className="ios-button"
                style={{
                  height: '36px',
                  padding: '0 14px',
                  borderRadius: '8px',
                  border: 'none',
                  backgroundColor: 'var(--accent-color)',
                  color: 'var(--accent-contrast)',
                  fontSize: '13px',
                  fontWeight: 600,
                }}
              >
                创建
              </button>
            </div>
          )}

          {/* Profile list */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {profiles.map((p) => {
              const isCurrent = p.isActive;
              const isEditing = editingProfileId === p.id;

              return (
                <div
                  key={p.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '10px 12px',
                    borderRadius: '12px',
                    backgroundColor: isCurrent ? 'var(--accent-light)' : 'var(--bg-color)',
                    border: isCurrent ? '1px solid var(--accent-color)' : '0.5px solid var(--border-color)',
                  }}
                >
                  {isEditing ? (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flex: 1 }}>
                      <input
                        type="text"
                        value={editingName}
                        onChange={(e) => setEditingName(e.target.value)}
                        style={{
                          flex: 1,
                          height: '32px',
                          borderRadius: '6px',
                          border: '1px solid var(--border-color)',
                          backgroundColor: 'var(--card-bg)',
                          color: 'var(--text-primary)',
                          padding: '0 8px',
                          fontSize: '13px',
                          outline: 'none',
                        }}
                      />
                      <button
                        onClick={() => handleRenameProfile(p.id)}
                        className="ios-button"
                        style={{
                          height: '32px',
                          padding: '0 10px',
                          borderRadius: '6px',
                          border: 'none',
                          backgroundColor: 'var(--accent-color)',
                          color: 'var(--accent-contrast)',
                          fontSize: '12px',
                          fontWeight: 600,
                        }}
                      >
                        <Check size={14} />
                      </button>
                    </div>
                  ) : (
                    <div
                      onClick={() => {
                        store.switchProfile(p.id);
                        onProfileChanged();
                      }}
                      style={{ cursor: 'pointer', flex: 1 }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <span style={{ fontSize: '15px', fontWeight: isCurrent ? 700 : 500, color: 'var(--text-primary)' }}>
                          {p.name}
                        </span>
                        {isCurrent && (
                          <span
                            style={{
                              fontSize: '10px',
                              backgroundColor: 'var(--accent-color)',
                              color: 'var(--accent-contrast)',
                              padding: '2px 6px',
                              borderRadius: '6px',
                              fontWeight: 600,
                            }}
                          >
                            当前
                          </span>
                        )}
                      </div>
                      <div style={{ fontSize: '11px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                        {p.courseCount} 门课程 · 开学周一: {p.semesterStartDate || '未设置'}
                      </div>
                    </div>
                  )}

                  {!isEditing && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <button
                        onClick={() => {
                          setEditingProfileId(p.id);
                          setEditingName(p.name);
                        }}
                        className="ios-button"
                        style={{
                          background: 'none',
                          border: 'none',
                          color: 'var(--text-secondary)',
                          padding: '4px',
                        }}
                        title="重命名"
                      >
                        <Edit2 size={15} />
                      </button>
                      {profiles.length > 1 && (
                        <button
                          onClick={() => handleDeleteProfile(p.id, p.name)}
                          className="ios-button"
                          style={{
                            background: 'none',
                            border: 'none',
                            color: '#FF3B30',
                            padding: '4px',
                          }}
                          title="删除课表"
                        >
                          <Trash2 size={15} />
                        </button>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        {/* Section 2: Semester Start Date */}
        <div
          style={{
            backgroundColor: 'var(--card-bg)',
            border: '0.5px solid var(--border-color)',
            borderRadius: '16px',
            padding: '16px',
            display: 'flex',
            flexDirection: 'column',
            gap: '12px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Calendar size={18} color="var(--accent-color)" />
            <h3 style={{ fontSize: '16px', fontWeight: 600 }}>学期第一周周一</h3>
          </div>
          <p style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
            用于精准推算当前位于第几周。修改任意日期后，系统会自动将其归整为该周周一。
          </p>

          <div style={{ display: 'flex', gap: '10px' }}>
            <input
              type="date"
              value={currentStartDate}
              onChange={(e) => handleStartDateChange(e.target.value)}
              style={{
                flex: 1,
                height: '40px',
                borderRadius: '10px',
                border: '1px solid var(--border-color)',
                backgroundColor: 'var(--bg-color)',
                color: 'var(--text-primary)',
                padding: '0 12px',
                fontSize: '14px',
                outline: 'none',
              }}
            />
            <button
              onClick={handleAutoInferStartDate}
              className="ios-button"
              style={{
                height: '40px',
                padding: '0 12px',
                borderRadius: '10px',
                border: '0.5px solid var(--border-color)',
                backgroundColor: 'var(--bg-color)',
                color: 'var(--text-primary)',
                fontSize: '13px',
                fontWeight: 500,
              }}
            >
              自动推算
            </button>
          </div>
        </div>

        {/* Section 3: Appearance & Themes */}
        <div
          style={{
            backgroundColor: 'var(--card-bg)',
            border: '0.5px solid var(--border-color)',
            borderRadius: '16px',
            padding: '16px',
            display: 'flex',
            flexDirection: 'column',
            gap: '14px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Palette size={18} color="var(--accent-color)" />
            <h3 style={{ fontSize: '16px', fontWeight: 600 }}>外观与主题</h3>
          </div>

          {/* Theme Colors */}
          <div>
            <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '8px' }}>
              主题配色
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '8px' }}>
              {[
                { key: 'warm' as ThemeType, label: '暖色', color: '#FA5A3A' },
                { key: 'blue' as ThemeType, label: '海蓝', color: '#007AFF' },
                { key: 'mint' as ThemeType, label: '薄荷', color: '#34C759' },
              ].map((t) => {
                const isSelected = preferences.theme === t.key;
                return (
                  <button
                    key={t.key}
                    onClick={() => onUpdatePreferences({ theme: t.key })}
                    className="ios-button"
                    style={{
                      height: '42px',
                      borderRadius: '10px',
                      border: isSelected ? `2px solid ${t.color}` : '1px solid var(--border-color)',
                      backgroundColor: 'var(--bg-color)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: '8px',
                      color: 'var(--text-primary)',
                      fontSize: '13px',
                      fontWeight: isSelected ? 600 : 400,
                    }}
                  >
                    <span
                      style={{
                        width: '12px',
                        height: '12px',
                        borderRadius: '50%',
                        backgroundColor: t.color,
                      }}
                    />
                    {t.label}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Dark Mode */}
          <div>
            <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '8px' }}>
              深色模式
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '8px' }}>
              {[
                { key: 'system' as DarkModeType, label: '跟随系统', icon: Smartphone },
                { key: 'light' as DarkModeType, label: '浅色', icon: Sun },
                { key: 'dark' as DarkModeType, label: '深色', icon: Moon },
              ].map((m) => {
                const Icon = m.icon;
                const isSelected = preferences.darkMode === m.key;
                return (
                  <button
                    key={m.key}
                    onClick={() => onUpdatePreferences({ darkMode: m.key })}
                    className="ios-button"
                    style={{
                      height: '42px',
                      borderRadius: '10px',
                      border: isSelected ? '2px solid var(--accent-color)' : '1px solid var(--border-color)',
                      backgroundColor: isSelected ? 'var(--accent-light)' : 'var(--bg-color)',
                      color: isSelected ? 'var(--accent-color)' : 'var(--text-primary)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: '6px',
                      fontSize: '13px',
                      fontWeight: isSelected ? 600 : 400,
                    }}
                  >
                    <Icon size={14} />
                    {m.label}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Font Size */}
          <div>
            <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '8px' }}>
              课表字号
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '8px' }}>
              {[
                { key: 'compact' as FontSize, label: '紧凑 (小)' },
                { key: 'normal' as FontSize, label: '标准' },
                { key: 'large' as FontSize, label: '清晰 (大)' },
              ].map((f) => {
                const isSelected = preferences.fontSize === f.key;
                return (
                  <button
                    key={f.key}
                    onClick={() => onUpdatePreferences({ fontSize: f.key })}
                    className="ios-button"
                    style={{
                      height: '40px',
                      borderRadius: '10px',
                      border: isSelected ? '2px solid var(--accent-color)' : '1px solid var(--border-color)',
                      backgroundColor: isSelected ? 'var(--accent-light)' : 'var(--bg-color)',
                      color: isSelected ? 'var(--accent-color)' : 'var(--text-primary)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: '13px',
                      fontWeight: isSelected ? 600 : 400,
                    }}
                  >
                    {f.label}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Default Start Tab */}
          <div>
            <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '8px' }}>
              默认启动页面
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '8px' }}>
              {[
                { key: 'timetable' as DefaultTab, label: '完整课表' },
                { key: 'today' as DefaultTab, label: '今日课程' },
              ].map((tab) => {
                const isSelected = preferences.defaultTab === tab.key;
                return (
                  <button
                    key={tab.key}
                    onClick={() => onUpdatePreferences({ defaultTab: tab.key })}
                    className="ios-button"
                    style={{
                      height: '40px',
                      borderRadius: '10px',
                      border: isSelected ? '2px solid var(--accent-color)' : '1px solid var(--border-color)',
                      backgroundColor: isSelected ? 'var(--accent-light)' : 'var(--bg-color)',
                      color: isSelected ? 'var(--accent-color)' : 'var(--text-primary)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: '13px',
                      fontWeight: isSelected ? 600 : 400,
                    }}
                  >
                    {tab.label}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Show non-current toggle */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              paddingTop: '6px',
            }}
          >
            <div>
              <div style={{ fontSize: '14px', fontWeight: 500, color: 'var(--text-primary)' }}>
                显示非本周课程
              </div>
              <div style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>
                在课表网格中以虚线灰显展示非本周课程
              </div>
            </div>
            <input
              type="checkbox"
              checked={preferences.showNonCurrentWeek}
              onChange={(e) => onUpdatePreferences({ showNonCurrentWeek: e.target.checked })}
              style={{
                width: '20px',
                height: '20px',
                accentColor: 'var(--accent-color)',
                cursor: 'pointer',
              }}
            />
          </div>
        </div>

        {/* Section: Text Schedule Export */}
        <div
          style={{
            backgroundColor: 'var(--card-bg)',
            border: '0.5px solid var(--border-color)',
            borderRadius: '16px',
            padding: '16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <FileText size={18} color="var(--accent-color)" />
            <div>
              <h3 style={{ fontSize: '15px', fontWeight: 600 }}>课表纯文本导出</h3>
              <p style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                一键生成纯文本课表，便于分享至微信或便签备忘录
              </p>
            </div>
          </div>
          <button
            onClick={() => setIsTextExportOpen(true)}
            className="ios-button"
            style={{
              padding: '8px 14px',
              borderRadius: '10px',
              border: '0.5px solid var(--border-color)',
              backgroundColor: 'var(--bg-color)',
              color: 'var(--text-primary)',
              fontSize: '13px',
              fontWeight: 500,
              whiteSpace: 'nowrap',
            }}
          >
            导出文本
          </button>
        </div>

        {/* Section 4: Backup & Cross-platform Interoperability */}
        <div
          style={{
            backgroundColor: 'var(--card-bg)',
            border: '0.5px solid var(--border-color)',
            borderRadius: '16px',
            padding: '16px',
            display: 'flex',
            flexDirection: 'column',
            gap: '12px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Download size={18} color="var(--accent-color)" />
            <h3 style={{ fontSize: '16px', fontWeight: 600 }}>备份与多端同步</h3>
          </div>
          <p style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
            采用全端统一的 version 1 JSON 格式，可与 Android / 鸿蒙版互导互通。
          </p>

          <div style={{ display: 'flex', gap: '10px' }}>
            <button
              onClick={handleExportBackup}
              className="ios-button"
              style={{
                flex: 1,
                height: '40px',
                borderRadius: '10px',
                border: '0.5px solid var(--border-color)',
                backgroundColor: 'var(--bg-color)',
                color: 'var(--text-primary)',
                fontSize: '13px',
                fontWeight: 500,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '6px',
              }}
            >
              <Download size={15} />
              导出备份文件
            </button>

            <label
              className="ios-button"
              style={{
                flex: 1,
                height: '40px',
                borderRadius: '10px',
                border: '0.5px solid var(--border-color)',
                backgroundColor: 'var(--bg-color)',
                color: 'var(--text-primary)',
                fontSize: '13px',
                fontWeight: 500,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '6px',
                cursor: 'pointer',
              }}
            >
              <Upload size={15} />
              恢复备份文件
              <input
                type="file"
                accept=".json,application/json"
                onChange={handleImportBackup}
                style={{ display: 'none' }}
              />
            </label>
          </div>
        </div>

        {/* Section 5: Add to iOS Home Screen Tutorial */}
        <div
          style={{
            backgroundColor: 'var(--accent-light)',
            borderRadius: '16px',
            padding: '16px',
            display: 'flex',
            flexDirection: 'column',
            gap: '8px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--accent-color)', fontWeight: 600 }}>
            <Share2 size={16} />
            添加到 iPhone 主屏幕获得原生 App 体验
          </div>
          <div style={{ fontSize: '12px', color: 'var(--text-primary)', lineHeight: '1.5' }}>
            1. 在 Safari 打开本页面，轻点底部的分享按钮 <strong>⎋</strong><br />
            2. 在弹出的菜单中向下滚动，选择「<strong>添加到主屏幕</strong>」<br />
            3. 点右上角「添加」，即可像原生 App 一样全屏沉浸使用，免去浏览器地址栏与 99$ 证书限制！
          </div>
        </div>

        <div style={{ textAlign: 'center', fontSize: '12px', color: 'var(--text-tertiary)', padding: '10px 0' }}>
          JLU_schedule v2.2.2 PWA · 吉林大学课表应用 · 开源无广告
        </div>
      </div>

      <TextScheduleModal
        courses={courses}
        isOpen={isTextExportOpen}
        onClose={() => setIsTextExportOpen(false)}
      />
    </div>
  );
};


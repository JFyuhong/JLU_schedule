import React, { useState, useEffect, useCallback } from 'react';
import { ProfileStore } from './core/ProfileStore';
import { AppPreferences, CourseSchedule } from './core/types';
import { TabBar, TabType } from './components/TabBar';
import { TimetablePage } from './views/TimetablePage';
import { TodayPage } from './views/TodayPage';
import { ToolsPage } from './views/ToolsPage';
import { ImportPage } from './views/ImportPage';
import { SettingsPage } from './views/SettingsPage';
import { Layers, X } from 'lucide-react';

export const App: React.FC = () => {
  const store = ProfileStore.getInstance();

  const [currentTab, setCurrentTab] = useState<TabType>(() => store.getPreferences().defaultTab || 'timetable');
  const [preferences, setPreferences] = useState<AppPreferences>(store.getPreferences());
  const [activeProfile, setActiveProfile] = useState(store.getActiveProfile());
  const [isSystemDark, setIsSystemDark] = useState<boolean>(() => {
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
  });
  const [showProfileSwitcher, setShowProfileSwitcher] = useState(false);

  // System dark mode listener
  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = (e: MediaQueryListEvent) => setIsSystemDark(e.matches);
    media.addEventListener('change', handler);
    return () => media.removeEventListener('change', handler);
  }, []);

  const isDark =
    preferences.darkMode === 'dark' ||
    (preferences.darkMode === 'system' && isSystemDark);

  // Apply theme & dark mode attributes to <html>
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', preferences.theme);
    document.documentElement.setAttribute('data-mode', isDark ? 'dark' : 'light');

    // Update meta theme-color for iOS Safari status bar
    const metaThemeColor = document.querySelector('meta[name="theme-color"]');
    if (metaThemeColor) {
      if (isDark) {
        metaThemeColor.setAttribute('content', '#000000');
      } else {
        const themeColors = { warm: '#FA5A3A', blue: '#007AFF', mint: '#34C759' };
        metaThemeColor.setAttribute('content', themeColors[preferences.theme] || '#FA5A3A');
      }
    }
  }, [preferences.theme, isDark]);

  const refreshProfileData = useCallback(() => {
    setActiveProfile(store.getActiveProfile());
  }, [store]);

  const handleUpdatePreferences = (newPrefs: Partial<AppPreferences>) => {
    const updated = store.savePreferences(newPrefs);
    setPreferences(updated);
  };

  const handleAddCourse = (course: CourseSchedule) => {
    store.addCourse(activeProfile.meta.id, course);
    refreshProfileData();
  };

  const handleUpdateCourse = (index: number, course: CourseSchedule) => {
    store.updateCourse(activeProfile.meta.id, index, course);
    refreshProfileData();
  };

  const handleDeleteCourse = (index: number) => {
    store.deleteCourse(activeProfile.meta.id, index);
    refreshProfileData();
  };

  return (
    <div style={{ height: '100%', width: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* Main Views */}
      <main style={{ flex: 1, position: 'relative', overflow: 'hidden' }}>
        {currentTab === 'timetable' && (
          <TimetablePage
            courses={activeProfile.courses}
            semesterStartDate={activeProfile.meta.semesterStartDate ?? null}
            showNonCurrent={preferences.showNonCurrentWeek}
            fontSize={preferences.fontSize}
            isDark={isDark}
            profileName={activeProfile.meta.name}
            onAddCourse={handleAddCourse}
            onUpdateCourse={handleUpdateCourse}
            onDeleteCourse={handleDeleteCourse}
            onSwitchProfileClick={() => setShowProfileSwitcher(true)}
          />
        )}

        {currentTab === 'today' && (
          <TodayPage
            courses={activeProfile.courses}
            semesterStartDate={activeProfile.meta.semesterStartDate ?? null}
            showNonCurrent={preferences.showNonCurrentWeek}
            onDeleteCourse={handleDeleteCourse}
          />
        )}

        {currentTab === 'tools' && (
          <ToolsPage
            courses={activeProfile.courses}
          />
        )}

        {currentTab === 'import' && (
          <ImportPage
            onImportSuccess={() => {
              refreshProfileData();
              setCurrentTab('timetable');
            }}
          />
        )}

        {currentTab === 'settings' && (
          <SettingsPage
            preferences={preferences}
            onUpdatePreferences={handleUpdatePreferences}
            activeProfile={{
              id: activeProfile.meta.id,
              name: activeProfile.meta.name,
              semesterStartDate: activeProfile.meta.semesterStartDate ?? null,
            }}
            courses={activeProfile.courses}
            onProfileChanged={refreshProfileData}
          />
        )}
      </main>

      {/* Profile Switcher Quick Sheet */}
      {showProfileSwitcher && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.4)',
            backdropFilter: 'blur(4px)',
            WebkitBackdropFilter: 'blur(4px)',
            zIndex: 90,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '20px',
            animation: 'fadeIn 0.15s ease',
          }}
          onClick={() => setShowProfileSwitcher(false)}
        >
          <div
            style={{
              backgroundColor: 'var(--card-bg)',
              borderRadius: '16px',
              width: '100%',
              maxWidth: '360px',
              padding: '16px',
              boxShadow: '0 10px 30px rgba(0, 0, 0, 0.2)',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: '12px',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 600 }}>
                <Layers size={17} color="var(--accent-color)" />
                切换课表
              </div>
              <button
                onClick={() => setShowProfileSwitcher(false)}
                className="ios-button"
                style={{ background: 'none', border: 'none', color: 'var(--text-secondary)' }}
              >
                <X size={18} />
              </button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {store.listProfiles().map((p) => {
                const isSelected = p.id === activeProfile.meta.id;
                return (
                  <button
                    key={p.id}
                    onClick={() => {
                      store.switchProfile(p.id);
                      refreshProfileData();
                      setShowProfileSwitcher(false);
                    }}
                    className="ios-button"
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '12px',
                      borderRadius: '10px',
                      border: isSelected ? '1px solid var(--accent-color)' : '0.5px solid var(--border-color)',
                      backgroundColor: isSelected ? 'var(--accent-light)' : 'var(--bg-color)',
                      color: isSelected ? 'var(--accent-color)' : 'var(--text-primary)',
                      textAlign: 'left',
                      fontSize: '14px',
                      fontWeight: isSelected ? 600 : 400,
                    }}
                  >
                    <span>{p.name}</span>
                    <span style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>
                      {p.courseCount} 门课
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* iOS Bottom Tab Bar */}
      <TabBar currentTab={currentTab} onTabChange={setCurrentTab} />
    </div>
  );
};

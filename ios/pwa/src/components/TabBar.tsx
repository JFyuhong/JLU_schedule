import React from 'react';
import { Calendar, Clock, Briefcase, Download, Settings } from 'lucide-react';

export type TabType = 'timetable' | 'today' | 'tools' | 'import' | 'settings';

interface TabBarProps {
  currentTab: TabType;
  onTabChange: (tab: TabType) => void;
}

export const TabBar: React.FC<TabBarProps> = ({ currentTab, onTabChange }) => {
  const tabs = [
    { key: 'timetable' as TabType, label: '课表', icon: Calendar },
    { key: 'today' as TabType, label: '今日', icon: Clock },
    { key: 'tools' as TabType, label: '工具', icon: Briefcase },
    { key: 'import' as TabType, label: '导入', icon: Download },
    { key: 'settings' as TabType, label: '设置', icon: Settings },
  ];

  return (
    <nav
      style={{
        position: 'fixed',
        bottom: 0,
        left: 0,
        right: 0,
        height: 'calc(50px + var(--sab))',
        paddingBottom: 'var(--sab)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-around',
        borderTop: '0.5px solid var(--tab-bar-border)',
        zIndex: 50,
      }}
      className="ios-glass"
    >
      {tabs.map((tab) => {
        const Icon = tab.icon;
        const isActive = currentTab === tab.key;
        return (
          <button
            key={tab.key}
            onClick={() => onTabChange(tab.key)}
            className="ios-button"
            style={{
              background: 'none',
              border: 'none',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              width: '20%',
              height: '50px',
              color: isActive ? 'var(--accent-color)' : 'var(--text-secondary)',
              transition: 'color 0.15s ease',
            }}
          >
            <Icon size={21} strokeWidth={isActive ? 2.5 : 1.8} />
            <span
              style={{
                fontSize: '10px',
                fontWeight: isActive ? 600 : 400,
                marginTop: '3px',
                letterSpacing: '-0.2px',
              }}
            >
              {tab.label}
            </span>
          </button>
        );
      })}
    </nav>
  );
};

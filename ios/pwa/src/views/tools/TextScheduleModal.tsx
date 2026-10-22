import React, { useState } from 'react';
import { CourseSchedule } from '../../core/types';
import { formatTimetableToText } from '../../core/TimetableFormatter';
import { Copy, Check, Download, X } from 'lucide-react';

interface TextScheduleModalProps {
  isOpen: boolean;
  onClose: () => void;
  courses: CourseSchedule[];
}

export const TextScheduleModal: React.FC<TextScheduleModalProps> = ({
  isOpen,
  onClose,
  courses,
}) => {
  const [copied, setCopied] = useState(false);

  if (!isOpen) return null;

  const textContent = formatTimetableToText(courses);

  const handleCopy = () => {
    navigator.clipboard.writeText(textContent);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownload = () => {
    const blob = new Blob([textContent], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `吉大课表文本_${new Date().toISOString().split('T')[0]}.txt`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
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
        zIndex: 110,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '20px',
      }}
      onClick={onClose}
    >
      <div
        style={{
          backgroundColor: 'var(--card-bg)',
          borderRadius: '16px',
          width: '100%',
          maxWidth: '440px',
          maxHeight: '85vh',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '0 12px 30px rgba(0,0,0,0.2)',
          overflow: 'hidden',
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
          <h3 style={{ fontSize: '17px', fontWeight: 600 }}>课表文本导出</h3>
          <button
            onClick={onClose}
            className="ios-button"
            style={{ background: 'none', border: 'none', color: 'var(--text-secondary)' }}
          >
            <X size={18} />
          </button>
        </div>

        <div style={{ flex: 1, padding: '16px 20px', overflowY: 'auto' }}>
          <p style={{ fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '8px' }}>
            已格式化为纯文本对齐排版，方便复制到微信、QQ、备忘录或打印：
          </p>
          <textarea
            readOnly
            value={textContent}
            rows={12}
            style={{
              width: '100%',
              borderRadius: '10px',
              border: '1px solid var(--border-color)',
              backgroundColor: 'var(--bg-color)',
              color: 'var(--text-primary)',
              padding: '12px',
              fontSize: '12px',
              fontFamily: 'monospace',
              lineHeight: 1.5,
              outline: 'none',
              resize: 'none',
            }}
          />
        </div>

        <div
          style={{
            padding: '12px 20px 16px 20px',
            borderTop: '0.5px solid var(--divider-color)',
            display: 'flex',
            gap: '10px',
          }}
        >
          <button
            onClick={handleDownload}
            className="ios-button"
            style={{
              flex: 1,
              height: '42px',
              borderRadius: '10px',
              border: '1px solid var(--border-color)',
              backgroundColor: 'var(--bg-color)',
              color: 'var(--text-primary)',
              fontSize: '14px',
              fontWeight: 500,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '6px',
            }}
          >
            <Download size={15} />
            保存为 .txt
          </button>

          <button
            onClick={handleCopy}
            className="ios-button"
            style={{
              flex: 1,
              height: '42px',
              borderRadius: '10px',
              border: 'none',
              backgroundColor: 'var(--accent-color)',
              color: 'var(--accent-contrast)',
              fontSize: '14px',
              fontWeight: 600,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '6px',
            }}
          >
            {copied ? <Check size={16} /> : <Copy size={16} />}
            {copied ? '已复制到剪贴板！' : '一键复制文本'}
          </button>
        </div>
      </div>
    </div>
  );
};

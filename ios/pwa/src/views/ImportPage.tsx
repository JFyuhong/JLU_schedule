import React, { useState, useEffect } from 'react';
import { ProfileStore } from '../core/ProfileStore';
import { isLikelyGradePayload } from '../core/GradeTranscriptParser';
import { isLikelyExamPayload } from '../core/ExamScheduleParser';
import { isLikelyPyfaPayload, parseAcademicProgress } from '../core/PyfaTranscriptParser';
import { GpaStore } from '../core/GpaStore';
import { ExamStore } from '../core/ExamStore';
import {
  Bookmark,
  Copy,
  Check,
  FileText,
  Upload,
  ExternalLink,
  HelpCircle,
  Sparkles,
} from 'lucide-react';

interface ImportPageProps {
  onImportSuccess: () => void;
}

export const ImportPage: React.FC<ImportPageProps> = ({ onImportSuccess }) => {
  const [copiedBookmarklet, setCopiedBookmarklet] = useState(false);
  const [jsonText, setJsonText] = useState('');
  const [importMode, setImportMode] = useState<'overwrite' | 'new'>('new');
  const [newProfileName, setNewProfileName] = useState('');
  const [statusMessage, setStatusMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Dynamic PWA origin for the Bookmarklet script
  const pwaBaseUrl = window.location.href.split('#')[0];

  // The Safari Bookmarklet code supporting schedule, grades, exams, and pyfa
  const bookmarkletCode = `javascript:(function(){
  var targetUrl = '${pwaBaseUrl}';
  function sendData(jsonStr){
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(jsonStr);
      }
    } catch(e){}
    var encoded = encodeURIComponent(jsonStr);
    var hashType = '#import_data=';
    if (jsonStr.indexOf('xscjcx') !== -1) hashType = '#import_grades=';
    else if (jsonStr.indexOf('cxxsapxx') !== -1 || jsonStr.indexOf('KSSJ') !== -1 || jsonStr.indexOf('xskscx') !== -1) hashType = '#import_exams=';
    else if (jsonStr.indexOf('FAJDMC') !== -1 || jsonStr.indexOf('YQJDXF') !== -1) hashType = '#import_progress=';
    window.location.href = targetUrl + hashType + encoded;
  }
  
  if (document.body && document.body.innerText && document.body.innerText.indexOf('"datas"') !== -1 && document.body.innerText.indexOf('"rows"') !== -1) {
    sendData(document.body.innerText.trim());
    return;
  }
  
  var currentUrl = window.location.href;
  var fetchUrl = '/resService/jwxt/teacher/studentquery/xskcb.do?responseType=json';
  if (currentUrl.indexOf('cjcx') !== -1 || currentUrl.indexOf('score') !== -1) {
    fetchUrl = '/resService/jwxt/cjcx/modules/cjcx/xscjcx.do?responseType=json';
  } else if (currentUrl.indexOf('ks') !== -1 || currentUrl.indexOf('exam') !== -1) {
    fetchUrl = '/resService/jwxt/cxxsap/modules/cxxsapxx/cxxsapxx.do?responseType=json';
  }
  
  fetch(fetchUrl, {
    headers: { 'Accept': 'application/json, text/plain, */*' }
  }).then(function(res){
    return res.text();
  }).then(function(text){
    if (text.indexOf('"datas"') !== -1) {
      sendData(text);
    } else {
      alert('未捕获到有效数据，请确保已登录教务系统！');
    }
  }).catch(function(err){
    alert('请求教务数据失败：' + err.message);
  });
})();`.replace(/\n\s*/g, '');

  const copyBookmarklet = () => {
    navigator.clipboard.writeText(bookmarkletCode);
    setCopiedBookmarklet(true);
    setTimeout(() => setCopiedBookmarklet(false), 2500);
  };

  const processImportPayload = (raw: string, mode: 'overwrite' | 'new', newName?: string) => {
    // 1. Academic Progress / Pyfa
    if (isLikelyPyfaPayload(raw)) {
      const plan = parseAcademicProgress(raw);
      if (plan && plan.requirements.length > 0) {
        localStorage.setItem('jlu_schedule_academic_progress', JSON.stringify(plan));
        setStatusMessage({
          type: 'success',
          text: `成功导入培养方案学业进度（共 ${plan.requirements.length} 个培养模块，已获 ${plan.totalEarnedCredits} / 计划 ${plan.totalRequiredCredits} 学分）！`,
        });
        return;
      }
    }

    // 2. Grades / Transcript
    if (isLikelyGradePayload(raw)) {
      const count = GpaStore.getInstance().importFromTranscript(raw);
      if (count > 0) {
        setStatusMessage({
          type: 'success',
          text: `成功导入 ${count} 门课程成绩并同步至绩点计算器！`,
        });
        return;
      }
    }

    // 3. Exam Schedule
    if (isLikelyExamPayload(raw)) {
      const count = ExamStore.getInstance().importFromJson(raw);
      if (count > 0) {
        setStatusMessage({
          type: 'success',
          text: `成功导入 ${count} 门考试安排并已同步至考试倒计时！`,
        });
        return;
      }
    }

    // 4. Backup JSON (v1)
    if (raw.includes('"version"') && raw.includes('"profiles"')) {
      const store = ProfileStore.getInstance();
      const count = store.importBackup(raw);
      setStatusMessage({ type: 'success', text: `成功从跨平台备份恢复 ${count} 份课表！` });
      onImportSuccess();
      return;
    }

    // 5. Timetable Schedule (default)
    const store = ProfileStore.getInstance();
    const res = store.importSchedule(raw, mode, newName);
    setStatusMessage({
      type: 'success',
      text: `成功导入课表，共 ${res.courseCount} 门课程！`,
    });
    onImportSuccess();
  };

  // Check URL hash for incoming import payloads
  useEffect(() => {
    const handleHash = () => {
      const hash = window.location.hash;
      const prefixes = ['#import_data=', '#import_grades=', '#import_exams=', '#import_progress='];
      const matched = prefixes.find((p) => hash.startsWith(p));
      if (matched) {
        try {
          const raw = decodeURIComponent(hash.substring(matched.length));
          processImportPayload(raw, 'new');
          window.location.hash = '';
        } catch (err: unknown) {
          const msg = err instanceof Error ? err.message : String(err);
          setStatusMessage({
            type: 'error',
            text: `导入失败: ${msg}`,
          });
        }
      }
    };
    handleHash();
    window.addEventListener('hashchange', handleHash);
    return () => window.removeEventListener('hashchange', handleHash);
  }, [onImportSuccess]);

  const handleManualImport = () => {
    if (!jsonText.trim()) {
      setStatusMessage({ type: 'error', text: '请先粘贴课表、成绩单、考试或备份 JSON 数据' });
      return;
    }
    try {
      processImportPayload(jsonText, importMode, newProfileName);
      setJsonText('');
      setNewProfileName('');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setStatusMessage({ type: 'error', text: `解析失败: ${msg}` });
    }
  };

  const handleClipboardImport = async () => {
    try {
      const text = await navigator.clipboard.readText();
      if (!text || !text.includes('{')) {
        setStatusMessage({ type: 'error', text: '剪贴板中未发现有效的数据 JSON' });
        return;
      }
      setJsonText(text);
      setStatusMessage({ type: 'success', text: '已从剪贴板读取数据，请核对后点击下方「开始导入」' });
    } catch {
      setStatusMessage({ type: 'error', text: '读取剪贴板受阻，请直接在下方文本框长按粘贴' });
    }
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (event) => {
      const content = event.target?.result as string;
      if (content) {
        setJsonText(content);
        setStatusMessage({ type: 'success', text: `已加载文件「${file.name}」，点击下方开始导入` });
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
          课表导入
        </h1>
        <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '2px' }}>
          专为 iOS Safari 设计的一键书签导入与备份同步
        </p>
      </header>

      <div style={{ padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
        {/* Status Toast */}
        {statusMessage && (
          <div
            style={{
              padding: '12px 14px',
              borderRadius: '12px',
              backgroundColor: statusMessage.type === 'success' ? 'var(--accent-light)' : 'rgba(255, 59, 48, 0.1)',
              color: statusMessage.type === 'success' ? 'var(--accent-color)' : '#FF3B30',
              fontSize: '13px',
              fontWeight: 500,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}
          >
            <span>{statusMessage.text}</span>
            <button
              onClick={() => setStatusMessage(null)}
              style={{ background: 'none', border: 'none', color: 'inherit', fontSize: '13px', cursor: 'pointer' }}
            >
              ✕
            </button>
          </div>
        )}

        {/* Section 1: Safari Bookmarklet (Primary Recommended Way for iOS) */}
        <div
          style={{
            backgroundColor: 'var(--card-bg)',
            border: '0.5px solid var(--border-color)',
            borderRadius: '16px',
            padding: '18px',
            display: 'flex',
            flexDirection: 'column',
            gap: '12px',
            boxShadow: '0 2px 10px rgba(0, 0, 0, 0.04)',
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
                <Bookmark size={18} />
              </div>
              <div>
                <h3 style={{ fontSize: '16px', fontWeight: 700 }}>Safari 一键导入书签</h3>
                <span style={{ fontSize: '11px', color: 'var(--accent-color)', fontWeight: 600 }}>
                  ✨ 推荐 iOS 手机用户使用
                </span>
              </div>
            </div>
            <span
              style={{
                fontSize: '11px',
                padding: '2px 8px',
                borderRadius: '8px',
                backgroundColor: 'var(--bg-color)',
                color: 'var(--text-secondary)',
              }}
            >
              免签名 · 跨域直通
            </span>
          </div>

          <p style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: '1.4' }}>
            在 Safari 登录吉大教务后，进入「学生课表」、「成绩查询」或「考试安排」页面，轻点一下书签即可自动捕获数据并跳转导入，无需手动复制大段文本。
          </p>

          <button
            onClick={copyBookmarklet}
            className="ios-button"
            style={{
              height: '44px',
              borderRadius: '12px',
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
            {copiedBookmarklet ? <Check size={18} /> : <Copy size={18} />}
            {copiedBookmarklet ? '书签脚本已复制到剪贴板！' : '复制 Safari 书签脚本'}
          </button>

          {/* Tutorial steps */}
          <div
            style={{
              backgroundColor: 'var(--bg-color)',
              borderRadius: '12px',
              padding: '12px 14px',
              fontSize: '12px',
              color: 'var(--text-secondary)',
              display: 'flex',
              flexDirection: 'column',
              gap: '8px',
            }}
          >
            <div style={{ fontWeight: 600, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <HelpCircle size={14} color="var(--accent-color)" />
              首次使用设置说明（仅需配置一次）：
            </div>
            <div>
              1. 随意将当前网页添加为 Safari 书签，标题命名为「<strong>导入吉大助手</strong>」。
            </div>
            <div>
              2. 打开 Safari 书签列表，点击「编辑」，将该书签的网址全部删除，<strong>粘贴刚才复制的脚本</strong>并保存。
            </div>
            <div>
              3. 在 Safari 中打开并登录教务系统，进入「学生课表」、「成绩查询」或「考试安排」任一页面。
            </div>
            <div>
              4. 点击书签中的「<strong>导入吉大助手</strong>」，将立即自动识别并同步导入相应模块！
            </div>
          </div>

          {/* Quick links to JLU JW */}
          <div style={{ display: 'flex', gap: '10px', marginTop: '4px' }}>
            <a
              href="https://jw.jlu.edu.cn"
              target="_blank"
              rel="noreferrer"
              className="ios-button"
              style={{
                flex: 1,
                height: '38px',
                borderRadius: '10px',
                border: '0.5px solid var(--border-color)',
                backgroundColor: 'var(--card-bg)',
                color: 'var(--text-primary)',
                fontSize: '13px',
                fontWeight: 500,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '4px',
                textDecoration: 'none',
              }}
            >
              校内入口 (jw.jlu)
              <ExternalLink size={13} color="var(--text-secondary)" />
            </a>
            <a
              href="https://vpn.jlu.edu.cn"
              target="_blank"
              rel="noreferrer"
              className="ios-button"
              style={{
                flex: 1,
                height: '38px',
                borderRadius: '10px',
                border: '0.5px solid var(--border-color)',
                backgroundColor: 'var(--card-bg)',
                color: 'var(--text-primary)',
                fontSize: '13px',
                fontWeight: 500,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '4px',
                textDecoration: 'none',
              }}
            >
              校外入口 (VPN)
              <ExternalLink size={13} color="var(--text-secondary)" />
            </a>
          </div>
        </div>

        {/* Section 2: JSON Paste & Backup File Upload */}
        <div
          style={{
            backgroundColor: 'var(--card-bg)',
            border: '0.5px solid var(--border-color)',
            borderRadius: '16px',
            padding: '18px',
            display: 'flex',
            flexDirection: 'column',
            gap: '12px',
            boxShadow: '0 2px 10px rgba(0, 0, 0, 0.04)',
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
                <FileText size={18} />
              </div>
              <h3 style={{ fontSize: '16px', fontWeight: 700 }}>JSON 粘贴 / 备份文件导入</h3>
            </div>
            <button
              onClick={handleClipboardImport}
              className="ios-button"
              style={{
                background: 'var(--bg-color)',
                border: '0.5px solid var(--border-color)',
                borderRadius: '8px',
                padding: '4px 8px',
                fontSize: '12px',
                color: 'var(--text-primary)',
                display: 'flex',
                alignItems: 'center',
                gap: '4px',
              }}
            >
              <Sparkles size={12} color="var(--accent-color)" />
              读取剪贴板
            </button>
          </div>

          <textarea
            value={jsonText}
            onChange={(e) => setJsonText(e.target.value)}
            placeholder="粘贴教务系统课表、成绩单 (xscjcx)、考试安排或跨端 v1 备份 JSON，系统将自动识别格式..."
            rows={5}
            style={{
              width: '100%',
              borderRadius: '12px',
              border: '1px solid var(--border-color)',
              backgroundColor: 'var(--bg-color)',
              color: 'var(--text-primary)',
              padding: '10px 12px',
              fontSize: '13px',
              fontFamily: 'monospace',
              outline: 'none',
              resize: 'vertical',
            }}
          />

          {/* Import Mode selection */}
          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              type="button"
              onClick={() => setImportMode('new')}
              style={{
                flex: 1,
                height: '34px',
                borderRadius: '8px',
                border: 'none',
                backgroundColor: importMode === 'new' ? 'var(--accent-color)' : 'var(--bg-color)',
                color: importMode === 'new' ? 'var(--accent-contrast)' : 'var(--text-primary)',
                fontSize: '13px',
                fontWeight: importMode === 'new' ? 600 : 400,
                cursor: 'pointer',
              }}
            >
              新建独立课表
            </button>
            <button
              type="button"
              onClick={() => setImportMode('overwrite')}
              style={{
                flex: 1,
                height: '34px',
                borderRadius: '8px',
                border: 'none',
                backgroundColor: importMode === 'overwrite' ? 'var(--accent-color)' : 'var(--bg-color)',
                color: importMode === 'overwrite' ? 'var(--accent-contrast)' : 'var(--text-primary)',
                fontSize: '13px',
                fontWeight: importMode === 'overwrite' ? 600 : 400,
                cursor: 'pointer',
              }}
            >
              覆盖当前课表
            </button>
          </div>

          {importMode === 'new' && (
            <input
              type="text"
              value={newProfileName}
              onChange={(e) => setNewProfileName(e.target.value)}
              placeholder="新课表名称（留空默认按学期命名）"
              style={{
                width: '100%',
                height: '38px',
                borderRadius: '10px',
                border: '1px solid var(--border-color)',
                backgroundColor: 'var(--bg-color)',
                color: 'var(--text-primary)',
                padding: '0 12px',
                fontSize: '13px',
                outline: 'none',
              }}
            />
          )}

          <div style={{ display: 'flex', gap: '10px' }}>
            <label
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
                cursor: 'pointer',
              }}
            >
              <Upload size={16} />
              选择备份文件
              <input
                type="file"
                accept=".json,application/json"
                onChange={handleFileUpload}
                style={{ display: 'none' }}
              />
            </label>

            <button
              onClick={handleManualImport}
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
              }}
            >
              开始导入
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

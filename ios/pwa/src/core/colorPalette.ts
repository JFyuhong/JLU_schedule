export interface CourseCardStyle {
  bg: string;
  text: string;
  darkBg: string;
  darkText: string;
  border?: string;
}

const PALETTES: CourseCardStyle[] = [
  { bg: '#FFE5DE', text: '#8C2A1E', darkBg: '#441812', darkText: '#FF9E90' },
  { bg: '#FEF3D6', text: '#7A5306', darkBg: '#3B2904', darkText: '#F7D075' },
  { bg: '#E1F1FD', text: '#165287', darkBg: '#0F2C46', darkText: '#85C7FD' },
  { bg: '#E2F6ED', text: '#196142', darkBg: '#103525', darkText: '#7FDCB0' },
  { bg: '#F2E8FD', text: '#5D2994', darkBg: '#31164E', darkText: '#CFACFB' },
  { bg: '#FFE8F2', text: '#8C1C52', darkBg: '#440D26', darkText: '#FFA4CE' },
  { bg: '#E0F7F6', text: '#156360', darkBg: '#0E3634', darkText: '#7BE1DC' },
  { bg: '#E8EDFD', text: '#244199', darkBg: '#162657', darkText: '#9CB6FD' },
];

export function getCourseColor(courseName: string, isCurrentWeek = true, isDark = false): {
  backgroundColor: string;
  color: string;
  border?: string;
} {
  if (!isCurrentWeek) {
    return {
      backgroundColor: isDark ? 'rgba(255, 255, 255, 0.06)' : 'rgba(0, 0, 0, 0.04)',
      color: isDark ? '#8E8E93' : '#8E8E93',
      border: isDark ? '1px dashed rgba(255, 255, 255, 0.16)' : '1px dashed rgba(60, 60, 67, 0.25)',
    };
  }

  let hash = 0;
  for (let i = 0; i < courseName.length; i++) {
    hash = (hash << 5) - hash + courseName.charCodeAt(i);
    hash |= 0;
  }
  const index = Math.abs(hash) % PALETTES.length;
  const p = PALETTES[index];

  return {
    backgroundColor: isDark ? p.darkBg : p.bg,
    color: isDark ? p.darkText : p.text,
  };
}

# 三端同步开发指南（Android / iOS / HarmonyOS）

本项目采用「双原生轻量移植」架构：**Android 是参考实现（source of truth）**，iOS（SwiftUI）与鸿蒙（ArkUI）各自移植同一套核心逻辑。核心逻辑量约 600 行，三端逐函数对齐，均有单元测试保障一致性。

## 目录结构

```
app/                          # Android 主版本（Kotlin + WebView 导入）
  src/main/java/cn/jlu/schedule/
    parser/DoScheduleParser.kt        # 教务 .do JSON 解析
    parser/ScheduleImportCacheParser.kt
    domain/WeekScheduleCalculator.kt  # 周次计算
    data/SemesterStartDatePolicy.kt
    data/ScheduleBackupCodec.kt
    data/ImportedScheduleStorage.kt   # 多课表存储
ios/                          # iOS 版
  pwa/                          # iOS 原生质感 PWA（React + Vite + TypeScript，目标 iPhone Safari / 全屏运行）
    src/core/                   # 与 Android/鸿蒙 一一对应的纯 TS 逻辑
    src/views/                  # 课表 / 今日 / 导入 / 设置
    test/                       # Vitest 自动化单元测试
  JLUSchedule/Core/             # SwiftUI 原生核心移植
  JLUSchedule/App/              # SwiftUI 界面
  JLUScheduleTests/             # XCTest（用例与 Android 对齐）
  JLUSchedule.xcodeproj
harmonyos/                    # 鸿蒙版（ArkUI，API 12+）
  entry/src/main/ets/core/      # Core.ets / ImportParser.ets（纯逻辑，可 Node 验证）
  entry/src/main/ets/pages/     # ArkUI 界面
  entry/src/ohosTest/           # hypium 单元测试
  verify/run.mjs                # Windows/Node 上直接验证核心逻辑
docs/PORTING.md               # 本文件
```

## 核心逻辑对应表

| 逻辑 | Android | iOS (SwiftUI) | iOS (PWA TS) | 鸿蒙 |
|---|---|---|---|---|
| 数据模型 | `model/ScheduleModels.kt` | `Core/Models.swift` | `pwa/src/core/types.ts` | `core/Core.ets` |
| 教务解析 | `parser/DoScheduleParser.kt` | `Core/ScheduleParser.swift` | `pwa/src/core/DoScheduleParser.ts` | `Core.ets: parseSchedule/parseWeekRules` |
| 周次计算 | `domain/WeekScheduleCalculator.kt` | `Core/Models.swift: WeekScheduleCalculator` | `pwa/src/core/WeekScheduleCalculator.ts` | `Core.ets: guessCurrentWeek/displayMeetings/...` |
| 学期日期 | `data/SemesterStartDatePolicy.kt` | `Core/SemesterStartDatePolicy.swift` | `pwa/src/core/SemesterStartDatePolicy.ts` | `Core.ets: inferFromSemesterLabelOrNull/...` |
| 备份格式 | `data/ScheduleBackupCodec.kt`（version 1） | `Core/ImportParser.swift: ScheduleBackupCodec` | `pwa/src/core/ScheduleBackupCodec.ts` | `Core.ets: encodeBackup/decodeBackup` |
| 多课表存储 | `data/ImportedScheduleStorage.kt` | `Core/ProfileStore.swift` | `pwa/src/core/ProfileStore.ts` | `core/ProfileStore.ets` |

所有平台的备份文件格式（version 1 JSON）与 meta.json + courses_*.json 存储布局完全一致，可以互相恢复对方导出的备份。

## 修改核心逻辑时的同步流程

1. 先在 Android 端实现并补单元测试（`gradlew testDebugUnitTest`）。
2. 同步移植到 iOS PWA（`ios/pwa/src/core/` + `ios/pwa/test/`），运行 `cd ios/pwa && npm test` 验证。
3. 同步移植到 iOS SwiftUI（`ios/JLUSchedule/Core/` + `ios/JLUScheduleTests/`）与鸿蒙
   （`harmonyos/entry/src/main/ets/core/` + `harmonyos/entry/src/ohosTest/`）。
4. 鸿蒙端在本机即可回归：`cd harmonyos && node verify/run.mjs`（无需 DevEco）。
5. iOS 端在 Xcode 按 Cmd+U 运行 XCTest。

## 构建与运行

### Android
```bash
./gradlew assembleDebug          # 输出 app/build/outputs/apk/debug
```

### iOS PWA（推荐，跨平台极速开发调试）
```bash
cd ios/pwa
npm install                      # 安装依赖
npm test                         # 运行 Vitest 核心逻辑单元测试
npm run dev                      # 启动本地开发服务（支持手机局域网预览）
npm run build                    # 打包生产静态资源（dist/ 目录，可直接托管或部署）
```
- 专为 iOS Safari 设计：具备 `apple-mobile-web-app-capable`、安全区域（Safe Area Insets）、毛玻璃（Frosted Glass）质感。
- 配备 Safari 一键书签导入脚本，智能识别课表、成绩查询、考试安排与培养方案数据，解决 iOS 浏览器跨域无法直接请求教务网关的痛点。
- 全量对齐核心功能：课表网格、今日课程、学习工具箱（JLU 4.0 保研绩点计算器、考试倒计时与考场信息、培养方案学业完成度、课表纯文本导出）、多课表管理与 v1 备份跨端互通、课表字号缩放与默认启动页个性化设置。

### iOS 原生（需要 macOS + Xcode 16+）
```bash
open ios/JLUSchedule.xcodeproj   # 选择个人签名 Team 后运行
# 若工程文件打开异常，可用 XcodeGen 重建：
# brew install xcodegen && cd ios && xcodegen
```
- Bundle ID：`cn.jlu.schedule.ios`；最低 iOS 16。
- 导入页使用 WKWebView + XHR/fetch 钩子捕获课表响应（同 Android 的 JS 桥方案）。
- 每日提醒用 UNCalendarNotificationTrigger 的 7 天滚动窗口（每次启动重排）。

### 鸿蒙（需要 DevEco Studio 5+ / API 12）
```bash
# DevEco 打开 harmonyos/ 目录，签名后运行到真机/模拟器
# 核心逻辑回归（任何平台）：
cd harmonyos && node verify/run.mjs
```
- Bundle Name：`cn.jlu.schedule.harmony`。
- 导入页使用 Web 组件 + `javaScriptOnDocumentStart` 注入同一套钩子，`onPageEnd` 拉取捕获结果。
- 每日提醒用 `reminderAgentManager` 日历提醒的 7 天滚动窗口。

## 已知的端差异（有意为之）

- **导入镜像缓存**：Android 会镜像 GET 请求到本地缓存作为兜底；iOS/鸿蒙只依赖 JS 钩子捕获（现代教务系统都走 XHR/fetch，够用且省流量）。
- **鸿蒙端"新建课表导入"**目前使用时间戳命名（系统对话框不支持带输入）；Android/iOS PWA 可自定义名称。
- **自定义背景图**：仅 Android 支持（UCrop 依赖）；iOS/鸿蒙暂未实现。
- **小组件**：iOS 版暂未实现 WidgetKit 扩展；鸿蒙端暂未实现卡片（JS 卡片/ArkTS 卡片可后续加）。

## 版本策略

三端版本号保持一致（当前 1.2.0）。发版时同步更新：
- Android：`app/build.gradle.kts` 的 versionCode/versionName
- iOS：`project.pbxproj` 中 MARKETING_VERSION / CURRENT_PROJECT_VERSION
- 鸿蒙：`AppScope/app.json5` 的 versionCode/versionName

# JLU_schedule

吉林大学课表应用，开源、无广告。

## 项目简介

JLU_schedule 是一个面向吉大学生的 Android 课表工具，支持通过教务网页导入课表、管理多份课表、手动补课、查看今日课程，并提供可切换的主题外观。

## 主要功能

- 教务课表解析
	- 支持解析教务系统 `.do` 返回的 `datas.*.rows` 数据
	- 支持从 `YPSJDD` 解析周次、星期、节次、地点
- 网页导入课表
	- 支持校内入口与校外 VPN 入口
	- 支持导入到当前课表或新建课表
- 多课表管理
	- 课表管理页可查看所有课表
	- 支持切换当前课表、重命名、删除、新建空课表
- 手动添加课程
	- 可设置课程名、教师、地点、星期、节次、周次、单双周
- 今日课程视图
	- 展示今日课程列表、课程数量、最早/最晚上课时间
- 个性化设置
	- 主题色切换（暖色、海蓝、薄荷）
	- 默认打开页面
	- 课表字号
	- 是否显示非本周课程
	- 自定义背景（选择与裁剪）

## 技术栈

- Android (Kotlin)
- Gradle (KTS)
- Kotlinx Serialization (JSON)
- WebView (网页导入)
- Material Components

## 项目结构

- `app/src/main/java/cn/jlu/schedule/ui/`
	- `timetable/`：课表页、手动加课、导入入口
	- `today/`：今日课程页
	- `settings/`：设置页与课表管理页
	- `importer/`：网页导入 Activity
	- `theme/`：主题调色板与统一 UI 反馈样式
- `app/src/main/java/cn/jlu/schedule/data/`
	- 偏好设置、课表持久化、多课表元数据
- `app/src/main/java/cn/jlu/schedule/parser/`
	- 教务 `.do` 数据解析器
- `app/src/main/assets/`
	- `sample_schedule.do`：示例课表
	- `target.url`：校内导入入口
	- `VPN.url`：校外导入入口

## 快速开始

1. 使用 Android Studio 打开仓库根目录。
2. 等待 Gradle Sync 完成。
3. 运行 `app` 模块到模拟器或真机。

命令行构建：

```bash
./gradlew assembleDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug
```

## 使用说明

1. 打开课表页，点击导入按钮。
2. 选择“我在校内”或“我在校外”。
3. 在网页中进入到课表页面后，点击“从此处导入”。
4. 选择覆盖当前课表或新建课表导入。
5. 可在设置页进入“课表管理页”切换或整理课表。

## 开发说明

- 仓库已配置 `.gitignore`，默认忽略构建产物与本地环境文件。
- 提交前建议执行：

```bash
./gradlew assembleDebug
```

## 免责声明

本项目仅用于学习与个人效率提升，请遵守学校相关系统使用规范与法律法规。

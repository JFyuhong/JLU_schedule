# JLU_schedule v2.2.4（Android）

Android 修复版，`versionCode=10`，最低支持 Android 8.0（API 26）。

## 自定义背景可读性修复

- 为课表顶部周次与日期、月份、星期和节次时间添加稳定底色，避免文字直接叠在照片上。
- 对齐月份格与星期日期格的高度和上下位置，修复“10月”格高出相邻日期格的问题。
- 日程摘要、统计与左侧时间列增加底色保护；设置、工具页的标题和设置页底部版本号同样受到保护。
- 非本周课程使用淡化底色区分，文字保持清晰，保留“非本周”标记。
- 添加随浅色、深色主题变化的背景遮罩，底部导航使用不透明底色；照片仍可从课表空白区域和卡片间隙显示。
- 更换或清除背景时即时更新保护层，不需要重启应用。

## 下载与升级

- [GitHub 下载 APK](https://github.com/JFyuhong/JLU_schedule/releases/download/v2.2.4/JLU_schedule.apk)
- [服务器 IP 备用下载（HTTP）](http://123.57.205.13/updates/android/versions/10/JLU_schedule.apk)

沿用旧正式版签名，可覆盖安装保留数据。应用内更新使用签名清单与 GitHub HTTPS 下载；国内服务器的清单、APK 和下载页同步更新，域名 HTTPS 入口仍无法从本机稳定访问。

本版包含 [v2.2.3 的课程周次、背景裁剪和账号隔离修复](https://github.com/JFyuhong/JLU_schedule/releases/tag/v2.2.3)。从更早版本升级时，已有错误周次的课表请重新导入并覆盖；成绩、考试等工具数据请重新同步。

## 验证

- Android 16 模拟器检查暖色、海蓝、薄荷三种主题的浅色和深色显示，覆盖纯黑、纯白、高对比花纹背景以及清除背景。
- Android 单元测试、Debug / Release 构建与 Lint 检查通过。

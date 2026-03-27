# PaintBoard — 会议白板 Android 应用

<p align="center">
  <img src="icon_512.png" width="128" alt="PaintBoard Icon"/>
</p>

<p align="center">
  专为大屏（55"–86"）与平板设计的实时协同会议白板应用
</p>

---

## 功能亮点

- **自由书写** — 多粗细画笔、压感支持、贝塞尔曲线插值，书写延迟 < 16ms
- **橡皮擦** — 分段擦除；大屏模式自动识别触摸面积切换书写/擦除，平板模式按输入设备区分
- **形状工具** — 直线、箭头、矩形、圆形、三角形、菱形，支持空心/填充和自动校正
- **颜色选择** — 工具栏快捷 4 色 + 20 预设色 + 最近使用 + HSV 自定义取色器 + Hex 输入
- **撤销/重做** — 每页独立 50 步撤销栈
- **多页白板** — 最多 20 页，支持空白/网格/横线背景，缩略图预览与拖拽排序
- **局域网协同** — WebSocket 实时同步，NSD 自动发现 + 手动 IP 连接，支持断线重连
- **二维码分享** — 内置 HTTP 服务，扫码即可在手机浏览器查看、保存 PNG 或下载 PDF
- **文件管理** — `.pb` 格式（JSON），SAF 存储，2 秒自动存盘，崩溃自动恢复

## 技术栈

| 层次 | 技术 |
|------|------|
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Clean Architecture |
| DI | Hilt |
| 网络 | Ktor (WebSocket Server/Client, HTTP Server) |
| 序列化 | kotlinx.serialization |
| 二维码 | ZXing |
| 低延迟渲染 | AndroidX Graphics Core (FrontBufferedRenderer) |
| 语言 | Kotlin 2.0 / Java 17 |

## 系统要求

- Android 8.0 (API 26) 及以上
- 推荐设备：会议大屏 (55"–86") 或 Android 平板
- 协同功能需设备处于同一局域网

## 构建

```bash
git clone https://github.com/h3c-hexin/Android_WriteBoard.git
cd Android_WriteBoard
./gradlew assembleRelease
```

## 项目结构

```
com.h3c.writeboard/
├── domain/model/       # 数据模型（Stroke, DrawingPage, Whiteboard…）
├── domain/usecase/     # 业务逻辑
├── data/file/          # 文件存储（SAF + 自动存盘）
├── data/collab/        # 协同（WebSocket + NSD）
├── data/share/         # 二维码分享（HTTP 服务 + 页面渲染）
├── ui/canvas/          # 画布引擎 + ViewModel
├── ui/toolbar/         # 工具栏组件
├── ui/pages/           # 多页管理
├── ui/collab/          # 协同面板
├── ui/share/           # 分享面板
└── ui/theme/           # 设计 token
```

## 设计要点

- **归一化坐标系 [0.0, 1.0]** — 大屏与平板之间内容无缝同步
- **直接 Canvas 渲染** — 无 Bitmap 缓存，消除闪烁
- **FrontBufferedRenderer** — 活跃笔画 < 5ms 延迟
- **触摸预测** — 速度自适应预测 0.5–1 帧
- **每页独立撤销栈** — 多页协作互不干扰

## 许可证

本项目为内部项目，版权归 H3C 所有。

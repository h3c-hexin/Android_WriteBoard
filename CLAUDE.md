# PaintBoard — Claude 工作约定

## 项目当前状态
模块 1~11 ✅ + 书写流畅度优化 ✅，v1.0.0 已发布

## 架构约定
- MVVM + Hilt；ViewModel：`ui/canvas/CanvasViewModel.kt`
- 坐标系：归一化 [0.0, 1.0]，x/y 均为画布宽度比例
- 文件格式：`.pb` = JSON（kotlinx.serialization），内部自动存盘路径 `filesDir/autosave.pb`

## 关键包路径
- `domain/model/`    数据模型（DrawingPage, Stroke, StrokeTool, ShapeType…）
- `ui/canvas/`       CanvasViewModel, DrawingCanvas
- `ui/toolbar/`      所有工具栏组件和子面板 Popup
- `ui/pages/`        PageManagerPopup
- `data/file/`       BoardRepository（SAF 存盘 + 自动存盘）
- `data/share/`      ShareRepository（Ktor HTTP 服务）、PageBitmapRenderer
- `ui/share/`        SharePopup、FullscreenQROverlay
- `ui/theme/`        颜色 token（ToolbarBackground, ActiveIconBackground…）
- `data/collab/`     CollabRepository（WebSocket HOST/CLIENT、NSD）
- `ui/collab/`       CollabPopup

## 文档导航
- 需求 / 功能边界   → `docs/requirements.md`
- 待办 / Backlog   → `docs/develop/backlog.md`
- 模块实现详情     → `docs/develop/progress.md`
- 架构说明         → `docs/develop/architecture.md`
- UX 设计规范      → `docs/designs/`（按功能名找对应文件）

## 文档约定
- 实现与设计的偏差记录在 `docs/develop/progress.md` 对应模块节（一句话即可）
- 本文件（CLAUDE.md）每个模块完成后只改"当前状态"一行
- 模块验收后只更新两个文件：`progress.md`（新增模块详情节）+ `CLAUDE.md`（改当前状态）

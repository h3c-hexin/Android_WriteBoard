# PaintBoard 文件管理 UX 规范

> 文档版本：v0.2
> 日期：2026-03-24
> 作者：小徐（UI/UX 设计）
> 对应大纲版本：ui-ux-outline.md v0.5
> 对应 PRD 版本：v0.2
> 状态：初稿，待评审

---

## 目录

1. ["更多"菜单内容](#1-更多菜单内容)
2. [保存白板流程](#2-保存白板流程)
3. [打开白板流程](#3-打开白板流程)
4. [新建白板流程](#4-新建白板流程)
5. [崩溃恢复提示 UI](#5-崩溃恢复提示-ui)
6. [导出功能](#6-导出功能)

---

## 1. "更多"菜单内容

### 1.1 触发入口

工具栏 E 区"更多"按钮（⋯ 三点图标），点击后弹出菜单型 **Popup**（遵循 toolbar-component.md §0 统一规范，底边对齐工具栏顶边，水平居中锚点按钮）。

### 1.2 菜单 Popup 布局（当前已实现）

```
┌────────────────────┐
│  ＋  新建白板       │
│  ─────────────────  │
│  📂  打开白板       │
│  💾  保存白板       │
└────────────────────┘
  宽度由内容决定（IntrinsicSize.Max），最小 160dp
  整行可点击（fillMaxWidth + clickable），无截断热区
```

### 1.3 菜单项规范（v1.0 已实现）

| 菜单项 | 图标 | 行为 |
|--------|------|------|
| 新建白板 | `Icons.Default.Add` | 切换到内联确认视图（见第 4 节） |
| 打开白板 | `Icons.Default.FolderOpen` | 调用 SAF `ACTION_OPEN_DOCUMENT`，选择 `.pb` 文件 |
| 保存白板 | `Icons.Default.Save` | 调用 SAF `ACTION_CREATE_DOCUMENT`，默认文件名 `白板.pb` |

- 行高：`padding(horizontal = 20dp, vertical = 14dp)`
- 图标尺寸：`20dp`，颜色 `IconDefault`（`#9E9E9E`）
- 图标与文字间距：`14dp`
- 文字：`16sp`，白色
- 分隔线：`#3A3A3A`，`0.5dp`
- 整行 `fillMaxWidth()`，确保 clickable 覆盖完整宽度

> **待实现（v1.x）**：导出当前页（PNG）、导出全部页（PDF）、图形自动矫正开关、设置子面板

---

## 2. 保存白板流程

### 2.1 触发与流程

```
用户点击"更多"→"保存白板"
  │
  ▼
  调用 Android SAF 系统文件选择器（ACTION_CREATE_DOCUMENT）
  MIME type："application/octet-stream"，默认文件名："白板.pb"
  │
  ▼
  用户在系统文件选择器中选择保存位置并确认
  返回 URI
  │
  ▼
  序列化当前所有页面 → JSON 写入 URI 对应文件
  │
  ├─ 写入成功：Popup 关闭，更新 currentFileName
  └─ 写入失败：静默失败（v1.0，错误处理待完善）
```

> **待实现（v1.x）**：保存前弹出命名对话框，支持自定义文件名；写入失败时显示 Error Toast。

### 2.2 .pb 文件格式说明（面向开发参考）

- 文件扩展名：`.pb`（PaintBoard 自定义格式）
- 编码：**JSON**（`kotlinx.serialization`），UTF-8
- 格式：`{ "version": 1, "pages": [ { "id", "background", "strokes": [ { "id", "tool", "color", "width", "shapeType", "points": [{x, y, pressure}] } ] } ] }`
- 跨设备兼容：任何安装了 PaintBoard App 的 Android 设备均可打开

---

## 3. 打开白板流程

### 3.1 触发与流程

```
用户点击"更多"→"打开白板"
  │
  ▼
  检查当前画布是否有未保存内容（isDirty == true）
  │
  ├─ isDirty == false（无未保存内容）：
  │    直接调用系统文件选择器 → 进入步骤 ③
  │
  └─ isDirty == true（有未保存内容）：
       弹出"未保存内容提示" Bottom Sheet（见 3.2 节）
       │
       ├─ 用户点击【保存】：
       │    先执行保存流程（见第 2 节）
       │    保存成功后继续打开流程
       │
       ├─ 用户点击【不保存】：
       │    直接进入 ③
       │
       └─ 用户点击【取消】：
            关闭 Bottom Sheet，回到白板（不打开任何文件）
  │
  ③ 调用 SAF 系统文件选择器（ACTION_OPEN_DOCUMENT）
     MIME type："application/octet-stream"，过滤 .pb 文件
  │
  ④ 用户选择 .pb 文件，返回 URI
  │
  ⑤ 读取并反序列化 .pb 文件 → PageDataList
     执行画布切换动画（全页面淡入淡出，300ms）
     加载新内容，切换至第 1 页
  │
  ├─ 读取成功：Toast "已打开文件名"（2s）
  │
  └─ 读取失败（格式损坏/版本不兼容）：
       Bottom Sheet Error（见 error-states-ux.md）
       画布保持原内容不变
```

### 3.2 未保存内容提示 Bottom Sheet

```
┌──────────────────────────────────────────────────────────────┐
│  ╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌（拖拽条）                             │
│                                                              │
│  ⚠️  当前白板有未保存的内容                                    │
│                                                              │
│  打开新文件前，是否要保存当前内容？                              │
│                                                              │
│  [ 取消 ]    [ 不保存，直接打开 ]    [ 保存 ]                   │
│                （color.semantic.warning 文字）                 │
└──────────────────────────────────────────────────────────────┘
```

| 按钮 | 样式 | 行为 |
|------|------|------|
| 取消 | 文字按钮，`color.icon.default` | 关闭提示，回到白板 |
| 不保存，直接打开 | 文字按钮，`color.semantic.warning`（`#FFCC80`） | 直接进入文件选择 |
| 保存 | 填充按钮，`color.brand.primary` 背景 | 先保存再打开 |

---

## 4. 新建白板流程

### 4.1 触发与流程

```
用户点击"更多"→"新建白板"
  │
  ▼
  更多菜单 Popup 切换为内联确认视图（见 4.2 节）
  Popup 保持显示，菜单列表替换为确认内容
  │
  用户确认 →
  │
  ▼
  执行新建操作：
    清空所有页面
    创建 1 个空白 DrawingPage（BLANK 背景）
    清空所有 UndoStack / RedoStack
    立即同步写入自动存盘（取消防抖，直接落盘空白板）
    currentFileName = null
  │
  ▼
  关闭 Popup
```

**重要**：新建操作清空全部内容，此操作**不纳入 UndoStack**（清空后不可撤销，区别于"清除当前页"）。新建后立即落盘自动存盘，确保强杀 App 后重启不会恢复旧内容。

### 4.2 内联确认视图（在更多菜单 Popup 内部切换）

```
┌──────────────────────────────┐
│  新建将清空当前内容            │
│                              │
│   [ 取消 ]   [ 确认新建 ]     │
└──────────────────────────────┘
  取消（IconDefault 色）：返回正常菜单视图
  确认新建（#FF6B6B 红色）：执行 newBoard()，关闭 Popup
```

- 确认视图复用同一 Popup 容器，无需额外弹窗层级
- 宽度：`widthIn(min = 200.dp)`（确认文字略宽于菜单项）

> **待实现（v1.x）**：有未保存内容时增加"保存后再新建"选项（当前 v1.0 未实现 isDirty 检测）。

---

## 5. 自动存盘与启动恢复

### 5.1 设计原则

**静默自动恢复**：App 强杀后重启，上次的白板内容自动还原，**无任何弹窗提示**，用户无感知。

### 5.2 自动存盘机制

| 属性 | 规范 |
|------|------|
| 存储位置 | 内部存储 `context.filesDir/autosave.pb`（对用户不可见，不占用存储配额提示） |
| 文件格式 | 与手动保存相同（JSON，`kotlinx.serialization`） |
| 触发时机 | 每次画布内容变化（笔画提交、页面增删、背景切换等） |
| 防抖策略 | 2 秒防抖（`delay(2000L)` + `Job` cancel/relaunch on `Dispatchers.IO`），避免高频写入 |
| 写入线程 | IO 线程（`viewModelScope.launch(Dispatchers.IO)`），不阻塞 UI |

**触发 `scheduleAutosave()` 的操作**：

- 笔画提交（`updateCurrentPage` 被调用）
- 添加页面（`addPage`）
- 删除页面（`deletePage`）
- 页面排序（`movePageUp` / `movePageDown`）
- 背景切换（`setPageBackground`）

**新建白板的特殊处理**：取消防抖 Job，立即同步写入空白板（确保下次启动恢复到空白，而非旧内容）。

### 5.3 启动恢复

```
App 冷启动（CanvasViewModel.init{}）：
  │
  ▼
  同步读取 filesDir/autosave.pb
  （主线程同步，< 30ms，避免画布显示空白后再跳变）
  │
  ├─ 文件存在且解析成功：
  │    _pages.value = saved
  │    _page.value = saved[0]
  │    updateUndoRedoState()
  │    → 画布直接显示上次内容，无任何提示
  │
  └─ 文件不存在 / 解析失败：
       → 保持默认空白页，正常启动
```

### 5.4 新建白板后的恢复行为

用户新建白板 → App 立即将空白板写入自动存盘 → 即使随后强杀 → 下次启动显示空白板（不恢复旧内容）。

> **待实现（v1.x）**：若用户需要区分"自动恢复内容"与"正常启动"，可在恢复时显示轻量级 Snackbar 提示（非阻塞式）。

---

## 6. 导出功能

### 6.1 导出当前页（PNG）

```
触发：更多菜单 → "导出当前页（PNG）"
  │
  ▼
  截图当前页画布（包含背景 + 所有笔画）
  渲染为 PNG（后台线程，约 200ms~500ms）
  │
  ▼
  调用 Android 系统分享（Intent.ACTION_SEND）
  弹出系统分享面板（存储到相册 / 发送给联系人 / 等）
  │
  ├─ 用户通过系统分享发送 → 完成
  └─ 用户取消分享 → 临时 PNG 文件删除
```

### 6.2 导出全部页（PDF）

```
触发：更多菜单 → "导出全部页（PDF）"
  │
  ▼
  显示 Loading Toast："正在生成 PDF..."
  后台逐页渲染 PNG，汇集为 PDF（Android PdfDocument API）
  典型耗时：5 页约 1~3s，20 页约 5~10s
  │
  ├─ 生成成功：
  │    调用 Intent.ACTION_SEND 弹出系统分享面板
  │    Loading Toast 消失
  │
  └─ 生成失败（内存不足）：
       Toast："PDF 生成失败，请关闭其他应用后重试"（3s）
```

### 6.3 导出文件命名规则

| 类型 | 默认文件名 |
|------|---------|
| PNG（当前页） | `PaintBoard_P{页码}_{YYYYMMDD_HHmm}.png` |
| PNG（全部页，单独导出每页时） | `PaintBoard_P{页码}_{YYYYMMDD_HHmm}.png` |
| PDF（全部页） | `PaintBoard_{YYYYMMDD_HHmm}.pdf` |

---

*文档结束*
*下一份文档：error-states-ux.md*

# PaintBoard Design Tokens

> 文档版本：v0.1
> 日期：2026-03-23
> 作者：小徐（UI/UX 设计）
> 对应大纲版本：ui-ux-outline.md v0.5
> 对应 PRD 版本：v0.3
> 状态：初稿，待评审

---

## 目录

1. [颜色系统](#1-颜色系统)
2. [字体排版](#2-字体排版)
3. [间距体系](#3-间距体系)
4. [圆角规范](#4-圆角规范)
5. [阴影与层级](#5-阴影与层级)
6. [动画规范](#6-动画规范)

---

## 1. 颜色系统

### 1.1 品牌主色

| Token 名称 | 亮色模式值 | 深色模式值 | 用途 |
|-----------|-----------|-----------|------|
| `color.brand.primary` | `#1976D2` | `#90CAF9` | 工具激活态背景、主按钮、强调色 |
| `color.brand.primary.container` | `#E3F2FD` | `#1565C0` | 轻量强调容器背景（如 Bottom Sheet 标题栏） |
| `color.brand.on-primary` | `#FFFFFF` | `#003C8F` | 主色上的前景文字/图标色 |
| `color.brand.primary.variant` | `#1565C0` | `#BBDEFB` | 主色深色变体，用于按下态、聚焦态边框 |

### 1.2 语义色

#### Primary（主操作色）

| Token 名称 | 亮色模式值 | 深色模式值 | 用途 |
|-----------|-----------|-----------|------|
| `color.semantic.primary` | `#1976D2` | `#90CAF9` | 同品牌主色 |
| `color.semantic.primary.hover` | `#1565C0` | `#BBDEFB` | 悬停/按压态 |

#### Secondary（次要操作色）

| Token 名称 | 亮色模式值 | 深色模式值 | 用途 |
|-----------|-----------|-----------|------|
| `color.semantic.secondary` | `#455A64` | `#B0BEC5` | 次要按钮、非核心标签 |
| `color.semantic.secondary.container` | `#ECEFF1` | `#37474F` | 次要容器背景 |

#### Error（错误/危险）

| Token 名称 | 亮色模式值 | 深色模式值 | 用途 |
|-----------|-----------|-----------|------|
| `color.semantic.error` | `#D32F2F` | `#EF9A9A` | 删除确认按钮、错误提示 |
| `color.semantic.error.container` | `#FFEBEE` | `#B71C1C` | 错误提示背景 |
| `color.semantic.on-error` | `#FFFFFF` | `#7F0000` | 错误色上的文字/图标 |

#### Warning（警告）

| Token 名称 | 亮色模式值 | 深色模式值 | 用途 |
|-----------|-----------|-----------|------|
| `color.semantic.warning` | `#F57C00` | `#FFCC80` | 操作不可逆提示、注意事项 |
| `color.semantic.warning.container` | `#FFF8E1` | `#E65100` | 警告提示背景 |

#### Success（成功）

| Token 名称 | 亮色模式值 | 深色模式值 | 用途 |
|-----------|-----------|-----------|------|
| `color.semantic.success` | `#388E3C` | `#A5D6A7` | 保存成功、协同连接成功 |
| `color.semantic.success.container` | `#E8F5E9` | `#1B5E20` | 成功提示背景 |

### 1.3 画布背景色

画布背景是整个 App 的视觉主体，亮色/深色模式各提供三种页面背景选项，用户可在页面管理 Bottom Sheet 中切换。

| Token 名称 | 亮色模式值 | 深色模式值 | 说明 |
|-----------|-----------|-----------|------|
| `color.canvas.background.solid` | `#FFFFFF` | `#1E1E1E` | 纯色白板（默认） |
| `color.canvas.background.grid.line` | `#E0E0E0` | `#424242` | 网格线颜色 |
| `color.canvas.background.rule.line` | `#BDBDBD` | `#616161` | 横线背景线颜色 |
| `color.canvas.background.grid.bg` | `#FAFAFA` | `#212121` | 网格背景底色 |
| `color.canvas.background.rule.bg` | `#FAFAFA` | `#212121` | 横线背景底色 |

> **设计说明**：深色模式下画布背景采用 `#1E1E1E`（Material 3 深色规范推荐值）而非纯黑，降低长时间使用的视觉疲劳。亮色模式下优先使用纯白 `#FFFFFF`，保证书写色彩还原准确。

### 1.4 工具栏背景色

工具栏以浮层形式叠加在画布上方，需兼顾与画布背景的对比度及透明度。

| Token 名称 | 亮色模式值 | 深色模式值 | 说明 |
|-----------|-----------|-----------|------|
| `color.toolbar.background` | `#F5F5F5` / `rgba(245,245,245,0.96)` | `#2C2C2C` / `rgba(44,44,44,0.96)` | 工具栏主背景（略透明，保持与画布视觉联系） |
| `color.toolbar.background.solid` | `#EFEFEF` | `#262626` | 不透明版本，用于截图/导出场景 |
| `color.toolbar.border` | `#E0E0E0` | `#424242` | 工具栏顶部分隔线 |
| `color.toolbar.divider` | `#D6D6D6` | `#3A3A3A` | 区域分隔线（竖线，各分区之间） |

### 1.5 图标色

三种交互状态下的图标颜色，适用于工具栏所有图标。

| Token 名称 | 亮色模式值 | 深色模式值 | 适用场景 |
|-----------|-----------|-----------|---------|
| `color.icon.default` | `#757575` | `#9E9E9E` | 普通未选中状态 |
| `color.icon.active` | `#003C8F` | `#003C8F` | 激活/选中状态（配合浅蓝 `#90CAF9` 激活背景，深蓝保持对比度） |
| `color.icon.disabled` | `#BDBDBD` | `#616161` | 禁用状态（如不可撤销时的撤销按钮） |
| `color.icon.on-surface` | `#424242` | `#EEEEEE` | 非工具栏场景（Bottom Sheet 内图标） |

### 1.6 快捷色与二级色板

#### 工具栏 4 快捷色

| 槽位 | Token 名称 | 色值 | 说明 |
|------|-----------|------|------|
| 槽位 1 — 黑色 | `color.quickpick.black` | `#212121` | 固定，主要书写色，深色背景下需白色描边 |
| 槽位 2 — 红色 | `color.quickpick.red` | `#E53935` | 固定，重点标注、警示 |
| 槽位 3 — 蓝色 | `color.quickpick.blue` | `#1E88E5` | 固定，次要书写、图示 |
| 槽位 4 — 自定义 | `color.quickpick.custom` | 动态（默认 `#43A047`）| 记忆最近一次从色板选取的颜色；App 首次安装默认绿色 |

> **自定义槽位说明**：用户从二级色板（预设 20 色或 HSV 自定义）选色后，所选色自动填入槽位 4 并更新当前书写颜色。App 重启后恢复用户上次设定的颜色，不重置为默认绿色。

#### 二级色板 — 预设 20 色

按 5 列 × 4 行宫格排列，色值如下：

| 行 | 色名 | Token 名称 | 色值 |
|----|------|-----------|------|
| 第 1 行 | 黑色 | `color.palette.black` | `#212121` |
| | 深灰 | `color.palette.dark-gray` | `#616161` |
| | 中灰 | `color.palette.gray` | `#9E9E9E` |
| | 浅灰 | `color.palette.light-gray` | `#E0E0E0` |
| | 白色 | `color.palette.white` | `#FFFFFF` |
| 第 2 行 | 红色 | `color.palette.red` | `#E53935` |
| | 橙色 | `color.palette.orange` | `#FB8C00` |
| | 黄色 | `color.palette.yellow` | `#FDD835` |
| | 黄绿 | `color.palette.yellow-green` | `#9CCC65` |
| | 绿色 | `color.palette.green` | `#43A047` |
| 第 3 行 | 青色 | `color.palette.cyan` | `#00ACC1` |
| | 蓝色 | `color.palette.blue` | `#1E88E5` |
| | 靛蓝 | `color.palette.indigo` | `#3949AB` |
| | 紫色 | `color.palette.purple` | `#8E24AA` |
| | 粉色 | `color.palette.pink` | `#EC407A` |
| 第 4 行 | 棕色 | `color.palette.brown` | `#6D4C41` |
| | 深棕 | `color.palette.dark-brown` | `#4E342E` |
| | 金色 | `color.palette.gold` | `#FFB300` |
| | 银色 | `color.palette.silver` | `#B0BEC5` |
| | 透明 | `color.palette.transparent` | `#00000000` |

> **透明色说明**：透明色用于填充图形时"仅描边、无填充"效果。选中透明色后工具栏第 4 槽显示棋盘格图案以指示透明状态。

### 1.7 分隔线与阴影色

| Token 名称 | 亮色模式值 | 深色模式值 | 用途 |
|-----------|-----------|-----------|------|
| `color.divider` | `#E0E0E0` | `#3A3A3A` | 通用分隔线 |
| `color.divider.strong` | `#BDBDBD` | `#4A4A4A` | 加重分隔线（Bottom Sheet 拖拽条上方） |
| `color.shadow` | `rgba(0,0,0,0.15)` | `rgba(0,0,0,0.40)` | 通用阴影色（深色模式阴影需加深以保持辨识） |
| `color.shadow.strong` | `rgba(0,0,0,0.25)` | `rgba(0,0,0,0.60)` | 强阴影（工具栏悬浮、Bottom Sheet） |
| `color.overlay.scrim` | `rgba(0,0,0,0.32)` | `rgba(0,0,0,0.50)` | Bottom Sheet 遮罩层 |

---

## 2. 字体排版

PaintBoard 面向站立用户在 1 米左右距离操作，字号规范相比手机 App 整体上调，确保 1 米外仍可清晰辨读。

### 2.1 字号规范

所有字号单位为 `sp`（Scale-independent Pixels），随系统字体大小设置等比缩放。

| Token 名称 | 大屏模式值 | PAD 模式值 | 最小可用值 | 适用场景 |
|-----------|-----------|-----------|-----------|---------|
| `typography.size.display` | `28sp` | `24sp` | `24sp` | 页面管理 Bottom Sheet 标题、协同人数大字 |
| `typography.size.headline` | `22sp` | `18sp` | `18sp` | Bottom Sheet 各区块标题 |
| `typography.size.title` | `18sp` | `16sp` | `16sp` | 弹出面板列表项文字、设置项标签 |
| `typography.size.body` | `18sp` | `14sp` | `14sp` | 正文说明文字、提示文字 |
| `typography.size.label` | `16sp` | `13sp` | `13sp` | 按钮标签、工具标注 |
| `typography.size.caption` | `14sp` | `12sp` | `12sp` | 状态注释（页码显示 `2/5`、协同人数徽章） |

> **1 米外可读原则**：
> - 大屏模式所有正文最小 `18sp`，状态标注最小 `14sp`（符合 ui-ux-outline.md 1.1 节规范）
> - PAD 模式遵循 Material 3 标准最小字号，用户距屏幕约 30~40cm
> - 系统字体大小调整（无障碍放大）时，所有 `sp` 值等比响应，不设字号上限

### 2.2 字重

| Token 名称 | 值 | 适用场景 |
|-----------|-----|---------|
| `typography.weight.regular` | `400` | 正文、说明文字 |
| `typography.weight.medium` | `500` | 列表项、标签 |
| `typography.weight.semibold` | `600` | 标题、激活状态标注 |
| `typography.weight.bold` | `700` | 强调文字、错误警告 |

### 2.3 行高

| Token 名称 | 值 | 计算方式 | 适用场景 |
|-----------|-----|---------|---------|
| `typography.lineheight.tight` | `1.2` | 字号 × 1.2 | 单行标题、按钮文字 |
| `typography.lineheight.normal` | `1.4` | 字号 × 1.4 | 正文段落（Bottom Sheet 说明文字） |
| `typography.lineheight.loose` | `1.6` | 字号 × 1.6 | 多行提示文字（二次确认弹窗内容） |

### 2.4 字体族

| Token 名称 | 值 | 说明 |
|-----------|-----|------|
| `typography.family.default` | `"Roboto", sans-serif` | Android 系统默认，支持中文时回落系统中文字体 |
| `typography.family.monospace` | `"Roboto Mono", monospace` | 仅用于颜色 Hex 值显示（HSV 调色盘输入框） |

---

## 3. 间距体系

### 3.1 基础网格

所有间距值均为 **4dp 的整数倍**，确保像素对齐及视觉节奏一致性。

| Token 名称 | 值 | 说明 |
|-----------|-----|------|
| `spacing.base` | `4dp` | 基础单元 |
| `spacing.xs` | `4dp` | 极小间距（色块内图标与边框） |
| `spacing.sm` | `8dp` | 小间距（相邻工具图标间距、列表项垂直内边距） |
| `spacing.md` | `12dp` | 中等间距（图标触控热区内边距缓冲） |
| `spacing.lg` | `16dp` | 大间距（工具栏上下内边距、Bottom Sheet 内容边距） |
| `spacing.xl` | `24dp` | 超大间距（Bottom Sheet 顶部圆角区到内容的距离） |
| `spacing.2xl` | `32dp` | 特大间距（确认弹窗内部主要元素垂直间距） |

### 3.2 工具栏触控热区

| Token 名称 | 大屏模式值 | PAD 模式值 | 说明 |
|-----------|-----------|-----------|------|
| `toolbar.icon.touch-area.width` | `64dp` | `48dp` | 触控热区宽度（含四周缓冲） |
| `toolbar.icon.touch-area.height` | `64dp` | `48dp` | 触控热区高度（含四周缓冲） |
| `toolbar.icon.visual.size` | `40dp` | `32dp` | 图标视觉尺寸（SVG 实际渲染尺寸） |
| `toolbar.icon.visual.size.min` | `32dp` | `24dp` | 图标最小视觉尺寸下限 |
| `toolbar.color-chip.size` | `36dp` | `28dp` | 快捷色色块直径 |
| `toolbar.color-chip.touch-area` | `64dp` | `48dp` | 色块触控热区（正方形） |

> **说明**：触控热区比图标视觉尺寸大，多出部分为透明缓冲区，响应触控事件但不渲染任何视觉元素，以满足大屏站立操作精度偏低的场景（参考 ui-ux-outline.md 1.1 节）。

### 3.3 工具栏内间距

| Token 名称 | 大屏模式值 | PAD 模式值 | 说明 |
|-----------|-----------|-----------|------|
| `toolbar.height` | `72dp` | `60dp` | 工具栏总高度 |
| `toolbar.padding.top` | `16dp` | `14dp` | 工具栏上内边距 |
| `toolbar.padding.bottom` | `16dp` | `14dp` | 工具栏下内边距（含系统导航条高度时动态增加） |
| `toolbar.padding.horizontal` | `16dp` | `12dp` | 工具栏左右端内边距 |
| `toolbar.icon.gap` | `8dp` | `6dp` | 同一区域内相邻图标的间距 |
| `toolbar.section.gap` | `8dp` | `6dp` | 分隔线左右到相邻图标的间距 |
| `toolbar.divider.width` | `1dp` | `1dp` | 区域分隔线厚度 |
| `toolbar.divider.height` | `32dp` | `24dp` | 分隔线可见高度（居中于工具栏，不占满全高） |

### 3.4 Bottom Sheet 内间距

| Token 名称 | 值 | 说明 |
|-----------|-----|------|
| `bottomsheet.padding.horizontal` | `24dp` | 水平内边距 |
| `bottomsheet.padding.top` | `16dp` | 拖拽条下方到内容区的距离 |
| `bottomsheet.padding.bottom` | `24dp` | 内容区下方到底边的距离 |
| `bottomsheet.drag-handle.width` | `40dp` | 拖拽条宽度 |
| `bottomsheet.drag-handle.height` | `4dp` | 拖拽条高度 |
| `bottomsheet.drag-handle.margin-top` | `12dp` | 拖拽条距 Bottom Sheet 顶边距离 |
| `bottomsheet.item.height` | `56dp` | 列表项行高（"更多"菜单中每项） |
| `bottomsheet.item.icon-size` | `24dp` | 列表项图标尺寸 |
| `bottomsheet.item.icon-margin` | `16dp` | 图标与文字间距 |

---

## 4. 圆角规范

### 4.1 圆角值定义

| Token 名称 | 值 | 说明 |
|-----------|-----|------|
| `radius.none` | `0dp` | 无圆角 |
| `radius.xs` | `4dp` | 极小圆角（工具栏分隔线端点） |
| `radius.sm` | `8dp` | 小圆角（激活态按钮背景、快捷色色块选中环） |
| `radius.md` | `12dp` | 中等圆角（图形工具子面板、橡皮子面板浮层） |
| `radius.lg` | `16dp` | 大圆角（工具栏背景两端、Bottom Sheet 顶部） |
| `radius.xl` | `24dp` | 超大圆角（协同管理浮层顶部圆角） |
| `radius.full` | `50%` | 全圆（色块、人数徽章、橡皮光标圆圈） |

### 4.2 各组件圆角规范

| 组件 | Token | 值 | 说明 |
|------|-------|-----|------|
| 工具栏背景 | `radius.lg` | `16dp` | 仅顶部两角应用圆角，底部贴近屏幕边缘（值为 0） |
| 激活态按钮背景 | `radius.sm` | `8dp` | 圆角矩形高亮块，使图标视觉居中 |
| 工具子面板（图形/橡皮） | `radius.md` | `12dp` | 四角均匀圆角，向上弹出的浮层 |
| Bottom Sheet 顶部圆角 | `radius.lg` | `16dp` | 顶部左右两角，底部 0（贴底） |
| 颜色 Bottom Sheet 顶部圆角 | `radius.xl` | `24dp` | 色板面板视觉上更"托盘"化，圆角稍大 |
| 便利贴卡片 | `radius.md` | `12dp` | 四角均匀，模拟真实便利贴纸张质感 |
| 二次确认弹窗 | `radius.lg` | `16dp` | 虽为 Bottom Sheet，顶部圆角与通用一致 |
| 颜色色块（非圆形） | `radius.xs` | `4dp` | 若色块为方形（非圆形变体），四角小圆角 |
| 人数徽章 | `radius.full` | `50%` | 协同状态徽章为圆形 |
| 滑动解锁轨道（清除当前页） | `radius.full` | `50%` | 胶囊形轨道，端部全圆角 |

---

## 5. 阴影与层级

### 5.1 层级体系

PaintBoard 浮层层级从低到高依次为：

| 层级编号 | 层级名称 | Elevation | 包含组件 |
|---------|---------|-----------|---------|
| z-0 | 画布层 | `0dp` | 画布书写区域（最底层） |
| z-1 | 工具栏层 | `4dp` | 底部工具栏、子面板向上弹出的浮层 |
| z-2 | Bottom Sheet 遮罩层 | — | 半透明遮罩 `rgba(0,0,0,0.32)` |
| z-3 | Bottom Sheet 层 | `8dp` | 颜色面板、页面管理、更多菜单等 |
| z-4 | 弹窗层 | `16dp` | 二次确认弹窗（也以 Bottom Sheet 形式呈现） |
| z-5 | 便利贴层（v1.5） | `2dp` | 便利贴卡片（轻微阴影，区分于笔画层） |

### 5.2 工具栏阴影

```
Token: shadow.toolbar
值（亮色）：
  box-shadow: 0dp -2dp 8dp rgba(0,0,0,0.15),
              0dp -1dp 3dp rgba(0,0,0,0.10)
值（深色）：
  box-shadow: 0dp -2dp 8dp rgba(0,0,0,0.40),
              0dp -1dp 3dp rgba(0,0,0,0.25)
```

- 阴影方向向上（-Y），因工具栏固定底部，阴影投向画布侧
- 采用双层阴影：外层大模糊扩散，内层小模糊加锐度，模拟自然光源

### 5.3 Bottom Sheet 阴影

```
Token: shadow.bottomsheet
值（亮色）：
  box-shadow: 0dp -4dp 16dp rgba(0,0,0,0.20),
              0dp -2dp 6dp  rgba(0,0,0,0.12)
值（深色）：
  box-shadow: 0dp -4dp 16dp rgba(0,0,0,0.55),
              0dp -2dp 6px  rgba(0,0,0,0.35)
```

- Bottom Sheet 阴影比工具栏更强，强调弹出层级感
- 深色模式下阴影需显著加深，否则深色背景下阴影不可见

### 5.4 便利贴卡片阴影（v1.5）

```
Token: shadow.sticky-note
值（亮色）：
  box-shadow: 2dp 2dp 8dp rgba(0,0,0,0.18),
              1dp 1dp 3dp rgba(0,0,0,0.10)
值（深色）：
  box-shadow: 2dp 2dp 8dp rgba(0,0,0,0.45),
              1dp 1dp 3dp rgba(0,0,0,0.25)
```

- 阴影偏右下角，模拟便利贴粘贴在白板上的自然投影效果
- 卡片被拖动时阴影动态增强（hover/drag 态 Elevation 从 `2dp` 升至 `6dp`）

### 5.5 工具子面板阴影（图形/橡皮）

```
Token: shadow.sub-panel
值（亮色）：
  box-shadow: 0dp 4dp 12px rgba(0,0,0,0.15),
              0dp 2dp 4dp  rgba(0,0,0,0.10)
值（深色）：
  box-shadow: 0dp 4dp 12px rgba(0,0,0,0.45),
              0dp 2dp 4dp  rgba(0,0,0,0.28)
```

- 子面板向上弹出，阴影方向向下（+Y），阴影落在工具栏上
- 视觉上子面板"漂浮"于工具栏正上方

---

## 6. 动画规范

### 6.1 基础动画参数

| Token 名称 | 值 | 说明 |
|-----------|-----|------|
| `animation.easing.standard` | `cubic-bezier(0.4, 0.0, 0.2, 1)` | Material 3 标准缓动，适用于常规状态转换 |
| `animation.easing.decelerate` | `cubic-bezier(0.0, 0.0, 0.2, 1)` | 减速进入，适用于元素进入屏幕（Bottom Sheet 弹出） |
| `animation.easing.accelerate` | `cubic-bezier(0.4, 0.0, 1.0, 1)` | 加速离开，适用于元素退出屏幕（Bottom Sheet 收起） |
| `animation.easing.emphasized` | `cubic-bezier(0.2, 0.0, 0.0, 1.0)` | 强调缓动，适用于图形矫正等需要"弹性到位"的动效 |

### 6.2 工具切换动画

工具栏中点击切换激活工具时：

| 动画属性 | 值 | 说明 |
|---------|-----|------|
| 时长 | `150ms` | 快速响应，不阻碍用户下一步操作 |
| 缓动曲线 | `animation.easing.standard` | 激活背景块的缩放和颜色过渡 |
| 激活背景出现 | `scale: 0.85 → 1.0` + `opacity: 0 → 1` | 蓝色圆角矩形从中心略微弹出 |
| 激活背景消失 | `opacity: 1 → 0` | 上一个激活背景淡出，无缩放 |
| 图标颜色变化 | `color: #757575 → #FFFFFF` | 与背景过渡同步，无延迟 |

> 工具切换总时长控制在 150ms 以内，确保用户感知到反馈的同时不产生"等待感"。系统 100ms 内给出视觉反馈的原则（ui-ux-outline.md 1.0 节）在此满足。

### 6.3 Bottom Sheet 弹出/收起动画

| 动画属性 | 弹出时 | 收起时 |
|---------|--------|--------|
| 时长 | `300ms` | `250ms` |
| 缓动曲线 | `animation.easing.decelerate` | `animation.easing.accelerate` |
| 位移轨迹 | `translateY: +200dp → 0` | `translateY: 0 → +200dp` |
| 遮罩透明度 | `opacity: 0 → 0.32` | `opacity: 0.32 → 0` |
| 收起触发方式 | — | 向下滑动拖拽条（速度 > 300dp/s 或位移 > 30%）或点击遮罩 |

> 弹出速度（300ms）比收起速度（250ms）略慢，符合"进入平缓减速、退出快速离开"的 Material Motion 原则，减少用户等待感。

### 6.4 图形自动矫正动效

图形绘制完成（手指抬起）后，若触发自动矫正，执行以下过渡动效：

| 动画属性 | 值 | 说明 |
|---------|-----|------|
| 时长 | `150ms` | 快速矫正，不影响书写节奏 |
| 缓动曲线 | `animation.easing.emphasized` | 略带弹性的到位感，强调"被吸附到标准形状" |
| 矫正过渡方式 | 关键点插值（顶点位置从识别形状平滑移动至标准形状） | 形状顶点坐标从用户绘制值插值到矫正后标准值 |
| 线条颜色 | 不变，全程保持用户选定颜色 | 矫正过程不改变线条颜色或粗细 |

**矫正前后形状视觉过渡细节**：
- 矫正不替换笔画，而是在笔画上层渲染一层"矫正后图形"覆盖层，动画结束后原笔画数据被矫正后数据替换
- 若矫正被撤销，恢复原始笔画数据，反向执行 150ms 动画（矫正后形状 → 原始笔画）

### 6.5 颜色切换

颜色切换**即时生效，无过渡动画**。

| 行为 | 规范 |
|------|------|
| 选中颜色后 | 工具栏当前颜色色块同步更新，0ms 延迟 |
| 后续笔画 | 立即使用新颜色，已存在笔画颜色不变 |
| 底部 Sheet 收起 | 颜色已在点击时生效，收起动画期间颜色已更新 |
| 快捷色槽位 4 更新 | 选色后立即更新槽位 4 色块，无动画 |

> 颜色是用户明确意图的即时操作，无需过渡动画。动画会在视觉上造成"颜色延迟"的错觉，降低信任感。

### 6.6 页面切换动画

| 动画属性 | 值 | 说明 |
|---------|-----|------|
| 时长 | `200ms` | |
| 缓动曲线 | `animation.easing.standard` | |
| 切换方式 | 水平滑动淡入淡出（cross-fade + slight horizontal slide） | 向左/右翻页时画布内容淡出同时新页淡入 |
| 侧移量 | `±40dp`（旧页退出方向） | 轻微位移，避免纯淡入淡出的"闪烁"感 |

### 6.7 禁用状态动画

| 操作 | 规范 |
|------|------|
| 撤销/重做到极限时 | 按钮颜色从 `color.icon.default` 过渡到 `color.icon.disabled`，时长 `100ms` |
| 不可交互的工具（禁用态） | 无点击反馈，触摸后无任何动画（减少误操作的视觉噪声） |
| 便利贴占位按钮（v1.5）| 点击时轻微抖动动画（`translateX: 0→4dp→-4dp→0`，`150ms`），配合 Toast 提示"即将推出" |

# PaintBoard 画布交互规范

> 文档版本：v0.1
> 日期：2026-03-23
> 作者：小徐（UI/UX 设计）
> 对应大纲版本：ui-ux-outline.md v0.5
> 对应 PRD 版本：v0.2
> 状态：初稿，待评审

> ⚠️ **实际实现说明（模块 9 已验收）**
> - **§7 崩溃恢复机制与设计差异较大**，以 `docs/designs/file-management-ux.md §5` 和 `docs/develop/progress.md 模块 9` 为准：
>   - 存盘策略：事件触发防抖（2 秒），非定时任务（2 分钟）
>   - 存储路径：`context.filesDir/autosave.pb`，非 `cacheDir/recovery/recovery_tmp.pb`
>   - 恢复方式：`CanvasViewModel.init{}` 同步静默恢复，**无弹窗提示**，非 Bottom Sheet 确认流程

---

## 目录

1. [输入事件处理优先级](#1-输入事件处理优先级)
2. [笔迹渲染规范](#2-笔迹渲染规范)
3. [橡皮擦逻辑](#3-橡皮擦逻辑)
4. [图形绘制流程](#4-图形绘制流程)
5. [多用户触摸点管理](#5-多用户触摸点管理)
6. [画布坐标系定义](#6-画布坐标系定义)
7. [崩溃恢复机制](#7-崩溃恢复机制)

---

## 1. 输入事件处理优先级

### 1.1 MotionEvent 分发总体架构

画布作为全屏 View，重写 `onTouchEvent()` 自行处理所有触摸事件，不依赖 Android 默认手势检测器（避免与多人书写冲突）。

```
触摸事件到达 CanvasView.onTouchEvent(MotionEvent)
         │
         ▼
  ① 工具栏 / Bottom Sheet 区域判断
         │
         ├─ 触点 Y 坐标落在工具栏区域（屏幕底部 toolbar.height = 72dp / 60dp）？
         │         └─ 是 → 转发给工具栏 View，不进入画布处理流程
         │
         ▼
  ② 设备模式分支（启动时由屏幕对角线确定）
         │
         ├─ 大屏模式 → 进入【触点面积判断流程】（1.2 节）
         └─ PAD 模式 → 进入【输入设备类型判断流程】（1.3 节）
         │
         ▼
  ③ 触点簇分配（1.4 节）
         │
         ▼
  ④ 分工具处理（笔迹 / 橡皮 / 图形）（2 / 3 / 4 节）
```

> **原则**：每个 MotionEvent.ACTION_POINTER_DOWN / MOVE / UP 均独立分析，不做批量合并处理，保证多人同步精度。

### 1.2 大屏模式：触点面积判断

大屏模式不依赖工具栏"橡皮擦"激活态来判断擦除意图，通过 `MotionEvent.getTouchMajor()` / `getTouchMinor()` 实时判断：

| 触点类型 | 判断条件 | 处理方式 |
|---------|---------|---------|
| 指尖书写 | `touchMajor < PALM_THRESHOLD`（默认 40dp，可在设置中调整） | 进入笔迹渲染流程 |
| 手掌擦除 | `touchMajor ≥ PALM_THRESHOLD` | 进入橡皮擦流程，擦除半径 = `touchMajor / 2` |
| 工具栏显式橡皮 | 工具栏橡皮擦按钮处于激活态 | 所有小面积触点也进入橡皮擦流程（覆盖自动判断） |

```
常量定义（可由设置页调整）：
  PALM_THRESHOLD = 40dp（经 DisplayMetrics 转换为 px 后使用）
  存储于 SettingsRepository，支持运行时读取
```

**注意**：两人手掌触点过近导致触点簇合并的极端场景，当前版本不做处理（PRD v0.2 明确标注暂不覆盖）。

### 1.3 PAD 模式：输入设备类型判断

PAD 模式通过 `MotionEvent.getToolType(pointerIndex)` 区分：

| ToolType 值 | 含义 | 处理方式 |
|------------|------|---------|
| `TOOL_TYPE_STYLUS` | 触控笔笔尖 | 进入笔迹渲染流程，读取压感数据 |
| `TOOL_TYPE_ERASER` | 触控笔橡皮端 | 直接进入橡皮擦流程 |
| `TOOL_TYPE_FINGER` | 手指 | 进入橡皮擦流程（"手指擦"） |
| `TOOL_TYPE_MOUSE` | 鼠标 | 进入笔迹渲染流程（无压感，固定粗细） |

> PAD 模式下，"手指 = 擦除"逻辑在工具栏橡皮擦按钮处于任意激活状态时均生效（自适应模式）。若用户明确点击工具栏橡皮擦按钮进入**显式橡皮模式**，则触控笔触点也执行擦除。

### 1.4 事件处理优先级总表

```
优先级（从高到低）：
  P1  系统手势（系统级截取，如导航手势）      — 不可覆盖
  P2  工具栏 / Bottom Sheet 触控热区           — CanvasView 不处理，交由上层 View
  P3  显式橡皮擦模式（工具栏按钮激活态）      — 覆盖 P4 / P5 的自适应判断
  P4  PAD 模式：输入设备类型自适应            — getToolType() 判断
  P5  大屏模式：触点面积自适应               — getTouchMajor() 判断
  P6  默认笔迹渲染（书写）                   — 兜底处理
```

---

## 2. 笔迹渲染规范

### 2.1 渲染流程概览

```
ACTION_DOWN
  └─ 创建新 Stroke 对象
       └─ 记录首个采样点 (x, y, pressure, timestamp)
       └─ 初始化 Path 对象

ACTION_MOVE（每帧可能包含多个历史采样点）
  └─ 读取 MotionEvent.getHistoricalX / Y / Pressure（批量历史点）
       └─ 追加到当前 Stroke.rawPoints 列表
       └─ 执行贝塞尔插值，更新 Path
       └─ invalidate(dirtyRect) → onDraw() 局部重绘当前笔迹

ACTION_UP
  └─ 完成当前 Stroke
       └─ 追加至 PageData.strokeList
       └─ 推入 UndoStack
       └─ 触发协同同步（若在协同会话中）
```

### 2.2 Path 构建与贝塞尔插值

**目标**：将离散触摸采样点连接为平滑曲线，消除锯齿感。

**算法**：Catmull-Rom 样条转换为三阶贝塞尔曲线（适合实时增量绘制）：

```
对于采样点序列 P[0], P[1], P[2], P[3], ...
每 4 个相邻点计算一段贝塞尔曲线：

  控制点计算（Catmull-Rom → Bézier 转换）：
    CP1 = P[1] + (P[2] - P[0]) / 6
    CP2 = P[2] - (P[3] - P[1]) / 6

  path.cubicTo(CP1.x, CP1.y, CP2.x, CP2.y, P[2].x, P[2].y)
```

**增量绘制策略**：
- 已确认曲线段渲染到离屏 Bitmap（`strokeBitmap`），不重复计算
- 当前正在绘制的最后 1 段贝塞尔曲线通过 Canvas 直接绘制（实时刷新）
- `invalidate()` 仅传入笔尖周边脏区（`invalidate(dirtyRect)`），减少 GPU 负担

**平滑强度**：
- 大屏模式（手指）：标准 Catmull-Rom（保留适度手写抖动，会议场景不做过度平滑）
- PAD 触控笔：标准 Catmull-Rom（笔迹精度高，无需额外平滑）
- PAD 手指（未来便利贴内容书写）：额外 1 次移动平均（窗口 3 点），减少手指抖动

### 2.3 压感映射规范

**前提**：`MotionEvent.getPressure()` 返回值范围 `[0.0, 1.0]`，硬件不支持压感时固定返回约 `1.0`。

**笔画宽度计算**：

```
baseWidth = 当前选中粗细档的基准宽度（见下表）
minWidth  = baseWidth × 0.4    （最细，轻触笔尖）
maxWidth  = baseWidth × 1.6    （最粗，用力下压）

strokeWidth = minWidth + (maxWidth - minWidth) × clamp(pressure, 0.0, 1.0)
```

| 粗细档 | Token | 基准宽度（大屏模式） | 基准宽度（PAD 模式） |
|------|-------|-----------------|-----------------|
| 细（Thin）   | `stroke.width.thin`   | `4dp`  | `2dp` |
| 中（Medium） | `stroke.width.medium` | `8dp`  | `4dp` |
| 粗（Thick）  | `stroke.width.thick`  | `16dp` | `8dp` |

**无压感设备兜底**：`pressure` 固定取 `0.7`（视觉适中），`strokeWidth = baseWidth`，不做宽度动态变化。

**硬件压感支持检测**：
```kotlin
val hasPressure = event.device?.motionRanges
    ?.any { it.axis == MotionEvent.AXIS_PRESSURE && it.range.extent > 0.01f }
    ?: false
```

### 2.4 笔画 Paint 配置

```kotlin
Paint().apply {
    isAntiAlias = true
    style       = Paint.Style.STROKE
    strokeCap   = Paint.Cap.ROUND    // 端点圆润，模拟笔尖感
    strokeJoin  = Paint.Join.ROUND   // 转折处圆滑，无锯齿角
    strokeWidth = 压感计算值（见 2.3）
    color       = 当前选中颜色（ARGB，Alpha 默认 255）
    // 不使用 PathEffect，避免性能损耗
}
```

### 2.5 性能保障

- **目标帧率**：60fps，单帧处理时间 < 16ms
- 笔迹渲染在主线程执行（Android View / SurfaceView 限制），通过以下手段降低耗时：

| 手段 | 说明 |
|------|------|
| 离屏 Bitmap 缓存 | 历史已完成 Stroke 光栅化到 `strokeBitmap`，每帧仅 `canvas.drawBitmap()` 一次 |
| 局部 invalidate | `invalidate(dirtyRect)` 仅刷新笔尖周边约 `strokeWidth × 4` 的矩形区域 |
| 采样点压缩 | Stroke.rawPoints 超过 1000 个时，将已确认曲线段光栅化写入离屏 Bitmap，清空内存中原始点列表 |
| 历史点批量处理 | `getHistoricalX / Y / Pressure` 逐帧批量追加，减少 invalidate 调用次数 |

---

## 3. 橡皮擦逻辑

### 3.1 触发条件

| 模式 | 触发橡皮擦的条件 |
|------|--------------|
| 大屏自适应 | 触点 `touchMajor ≥ PALM_THRESHOLD`（手掌接触） |
| PAD 自适应 | `toolType == TOOL_TYPE_FINGER` 或 `TOOL_TYPE_ERASER` |
| 显式橡皮模式 | 工具栏橡皮擦按钮已激活（覆盖上述自适应判断，所有触点执行擦除） |

### 3.2 擦除半径规范

| 来源 | 擦除半径 |
|------|--------|
| 大屏手掌自适应 | `touchMajor / 2`（动态，跟随手掌接触面积变化） |
| PAD 手指自适应 | 固定中等半径（`eraserMediumRadius`，见下表） |
| 显式橡皮擦（子面板档位） | 按选中档位（见下表） |

| 档位 | Token | 大屏模式半径 | PAD 模式半径 |
|------|-------|-----------|-----------|
| 小（Small）  | `eraser.radius.small`  | `20dp` | `12dp` |
| 中（Medium） | `eraser.radius.medium` | `40dp` | `24dp` |
| 大（Large）  | `eraser.radius.large`  | `72dp` | `48dp` |

### 3.3 擦除算法：路径求交

**笔画级擦除（逐 Stroke 遍历）**：

```
对每条已有 Stroke（从最新到最旧遍历，优先擦最上层）：

  步骤 1：快速排除
    Stroke.boundingRect 与橡皮圆形的矩形包围盒无交集 → 跳过，O(1)

  步骤 2：精确检测
    遍历 Stroke.pathSegments（每段三阶贝塞尔曲线）
    检查每段曲线与橡皮圆形的交叉点：
      - 曲线段完全在圆内 → 整段标记删除
      - 曲线段与圆边界相交 → 在交点处将 Stroke 拆分为两段（两个新 Stroke）
      - 曲线段完全在圆外 → 保留

  步骤 3：写入操作记录
    创建 EraseOperation {
      removedStrokes:  List<Stroke>,  // 被完整删除的笔画
      modifiedStrokes: List<StrokePair>  // 被拆分的笔画（原 → 两段）
    }
    推入 UndoStack
```

**实时擦除预览**：橡皮移动过程中，被擦除区域的笔画实时消失（无延迟），不等待 ACTION_UP。

**橡皮光标**：显示圆形光标指示擦除范围（深色主题下为 `color.icon.default` 描边白色圆圈，半径 = 擦除半径）。

### 3.4 清除当前页逻辑

**触发入口**：橡皮擦子面板底部的滑动解锁控件（详细 UI 规范见 toolbar-component.md）。

**执行流程**：

```
用户将滑块从左端滑动至右端（完整滑过轨道）
         │
         ▼
  立即执行：PageData.strokeList.clear()
         │
         ▼
  创建 ClearPageOperation：
    快照当前页全部 Stroke 列表（深拷贝）
    推入 UndoStack
         │
         ▼
  画布刷新（呈现空白页面）
  子面板底部显示 Toast："已清除当前页（可撤销）"，持续 2s
```

**撤销支持**：`ClearPageOperation.undo()` 将全部 Stroke 快照恢复至 `strokeList`，重新渲染画布。

> 清除操作纳入与普通笔迹相同的 UndoStack，不区分优先级，受最大 50 步限制约束。

---

## 4. 图形绘制流程

### 4.1 流程总览

```
┌─────────────────────────────────────────────────────────┐
│  步骤 1：选择图形工具                                      │
│    工具栏图形按钮点击 → 弹出图形子面板（向上弹出浮层）        │
│    点击具体图形图标 → 子面板收起，图形工具激活              │
│    工具栏图形按钮图标更新为当前选中图形                      │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│  步骤 2：画布拖拽绘制                                      │
│    ACTION_DOWN → 记录起点 (startX, startY)               │
│    ACTION_MOVE → 实时渲染预览层（虚线 + 50% 透明度）        │
│    ACTION_UP   → 确认终点，提交图形至数据模型               │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│  步骤 3：自动矫正（可选，由设置开关控制）                    │
│    ML Kit 识别手绘形状类型 → 置信度 ≥ 0.65 → 触发矫正动画  │
│    150ms 插值动画：原始顶点 → 标准图形顶点                  │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│  步骤 4：写入数据模型                                      │
│    创建 ShapeStroke {type, startPoint, endPoint,          │
│      color, strokeWidth, isCorrected}                    │
│    推入 PageData.strokeList + UndoStack                  │
│    触发协同同步（若在协同会话中）                            │
└─────────────────────────────────────────────────────────┘
```

### 4.2 拖拽预览层规范

预览层为独立的 Canvas 绘制层（不写入离屏 Bitmap，每帧重绘，仅在图形拖拽期间存在）：

```
预览渲染规则：
  线条颜色：当前选中颜色（ARGB）
  线条透明度：Alpha = 128（约 50%），与最终图形视觉区分
  线条样式：DashPathEffect（4dp 实线 + 4dp 空白交替）
  填充模式预览（矩形 / 圆形 / 三角形 / 菱形）：
    填充色透明度：Alpha = 64（约 25%），轮廓 Alpha = 128
  抬手后：预览层清除，渲染最终图形
```

**各图形的起点 / 终点语义**：

| 图形 | 起点 | 终点 | 备注 |
|------|------|------|------|
| 直线 | 线段起点 | 线段终点 | 无角度约束 |
| 箭头 | 箭尾 | 箭头指向端 | 箭头大小固定为线段长度的 15%，夹角 30° |
| 矩形 | 对角点 A | 对角点 B | 拖拽对角线确定矩形大小 |
| 圆形 / 椭圆 | 包围盒左上角 | 包围盒右下角 | 拖拽方向偏差 ≤ 15° 时自动锁定正圆 |
| 三角形 | 底边左端 | 包围盒右下角 | 等腰三角形，顶点自动计算居中 |
| 菱形 | 包围盒左上角 | 包围盒右下角 | 中心菱形，对角线与包围盒中轴对齐 |

### 4.3 ML Kit 自动矫正详细流程

**触发时机**：`ACTION_UP` 后，"图形自动矫正"设置开关为开启。

```
ACTION_UP
  │
  ▼
  提取用户绘制的原始坐标点序列（rawPoints）
  异步提交给 ML Kit Digital Ink Recognition
  设置超时：500ms
  │
  ├─ 超时 → 跳过矫正，保留原始笔画，直接进入步骤 4
  │
  └─ 识别返回候选结果
       │
       ├─ 最高置信度 < 0.65 → 跳过矫正，保留原始笔画
       │
       └─ 置信度 ≥ 0.65，按识别类型矫正：
            "line"       → 线性回归最佳拟合直线
            "rectangle"  → 最小外接矩形（最优顶点拟合）
            "circle"     → 最小外接圆（Welzl 算法）
            "triangle"   → 三角形顶点最优化（等腰 / 等边判断）
            "diamond"    → 菱形中心点 + 对角线长度计算
            "arrow"      → 方向向量提取 + 箭头标准化
            其他         → 跳过矫正
         │
         ▼
         执行矫正动画（150ms，emphasized 缓动）
         原始 Path 顶点 → 插值过渡 → 标准图形顶点
         │
         ▼
         动画结束：
           ShapeStroke.rawPoints   ← 矫正后标准坐标
           ShapeStroke.isCorrected = true
           若此 Stroke 被撤销：恢复原始 rawPoints，反向执行 150ms 动画
```

**视觉过渡细节**（参考 design-tokens.md 6.4 节）：
- 矫正动画期间，在原始笔画上层叠加"矫正覆盖层"，颜色与粗细同原始笔画
- 动画结束后，覆盖层融合为最终笔画，原始数据被替换
- 矫正不改变颜色、线宽、填充状态

---

## 5. 多用户触摸点管理

### 5.1 触点簇（Touch Cluster）概念

大屏硬件最多支持 20 个同时触点，多人并发书写时每人触点在 `MotionEvent` 中以不同 `pointerId` 区分。

```
数据结构：
  activePointers: Map<Int, ActiveStroke>
    key   = pointerId（Android 系统分配，0 ~ pointerCount-1）
    value = ActiveStroke {
              stroke:     Stroke,     // 当前正在绘制的笔画对象
              paint:      Paint,      // 渲染配置（颜色、宽度）
              strokeType: StrokeType  // DRAW / ERASE / SHAPE
            }
```

**簇分配规则**：
1. 每个 `ACTION_DOWN` / `ACTION_POINTER_DOWN`（新触点落下）创建一条新的 ActiveStroke
2. 每个 `pointerId` 独立维护自己的笔画轨迹，互不干扰
3. A 的 `ACTION_UP` 不影响 B 正在进行的笔画
4. 没有簇合并逻辑（手掌与指尖的归并由面积判断独立处理，不跨 pointerId 合并）

### 5.2 多指并发书写事件流

```
ACTION_POINTER_DOWN (pointerId = N)
  └─ activePointers[N] = ActiveStroke(当前工具配置)

ACTION_MOVE
  └─ 遍历 event.pointerCount 个触点
       └─ 对每个 pointerId 读取 (x, y, pressure)
       └─ 追加到 activePointers[id].rawPoints
       └─ 更新对应 Path，触发局部 invalidate

ACTION_POINTER_UP (pointerId = N)
  └─ 完成 activePointers[N].stroke → 推入 strokeList + UndoStack
  └─ activePointers.remove(N)

ACTION_UP（最后一个触点抬起）
  └─ 同 ACTION_POINTER_UP，并清空 activePointers
```

### 5.3 协同场景：远端笔迹渲染层级

```
渲染层级（从下到上）：

  z-0  本地历史笔迹（离屏 Bitmap，已完成的全部 Stroke）
  z-1  远端协同笔迹（协同专用 Canvas，其他设备正在绘制的笔画）
  z-2  本地当前正在绘制的笔迹（实时 Path，最上层，优先显示本机输入）
```

**远端用户光标**：
- 每台远端设备显示一个小彩圈（直径 `16dp`），颜色由房间分配（详见 collaboration-ux.md）
- 彩圈位于远端触点当前位置，随同步数据实时更新
- 彩圈不遮挡笔画（始终在笔画层上方，但透明度 70% 避免干扰阅读）

---

## 6. 画布坐标系定义

### 6.1 坐标原点与方向

```
坐标原点：画布左上角（不含工具栏区域）
X 轴：向右为正方向
Y 轴：向下为正方向
单位：px（物理像素）
     存储和网络同步时使用归一化坐标 [0.0, 1.0]（见 6.2 节）

  ┌────────────────────────────────────────┐
  │(0, 0)                    (canvasW, 0)  │ ← 顶部（可书写，顶部 35% 无交互 UI）
  │                                        │
  │                                        │
  │            画布书写区域                 │
  │                                        │
  │                                        │
  │(0, canvasH)          (canvasW, canvasH)│
  ├────────────────────────────────────────┤
  │    底部工具栏（高度 72dp / 60dp）        │ ← 工具栏不属于画布坐标系
  └────────────────────────────────────────┘

  canvasW = 屏幕宽度（px）
  canvasH = 屏幕高度（px）- 工具栏高度（px）
          = screenHeight - toolbar.height × displayDensity
```

### 6.2 无缩放 / 无平移约束

- 画布坐标系始终与屏幕坐标系对齐（`CanvasMatrix = Identity`）
- 不存在 `scrollX`、`scrollY`、`scaleX`、`scaleY` 变换
- 笔画坐标直接对应屏幕物理像素位置，无需任何坐标变换

**协同跨设备坐标归一化**：

不同设备（大屏 vs PAD）画布物理尺寸不同，协同同步协议统一使用归一化坐标：

```
发送方（写入）：
  normX = rawX / senderCanvasWidth
  normY = rawY / senderCanvasHeight

接收方（还原）：
  localX = normX × localCanvasWidth
  localY = normY × localCanvasHeight
```

### 6.3 坐标存储精度

| 场景 | 精度 | 说明 |
|------|------|------|
| 内存中的 rawPoints | `Float`（32 位） | 精度约 0.0001px，满足书写需求 |
| .pb 文件存储 | `Float`（4 字节） | 归一化坐标，值域 [0.0, 1.0] |
| 网络同步（JSON） | 保留 4 位小数 | `"x": 0.1234`，降低数据包体积 |

---

## 7. 崩溃恢复机制

### 7.1 静默自动保存

**策略**：每 2 分钟，若画布有未保存变更，静默写入临时恢复文件，不触发任何 UI 提示。

```
定时任务（Handler.postDelayed 或 CoroutineScope + delay）：
  周期：120,000ms（2 分钟）
  触发条件：
    - App 处于前台（ActivityLifecycleCallbacks 监听）
    - 画布数据自上次写入后有变更（isDirty = true）

  执行步骤：
    1. 序列化当前 PageDataList → 二进制格式（.pb 文件格式）
    2. 写入 {Context.cacheDir}/recovery/recovery_tmp.pb
    3. 写入成功 → 更新 recoveryTimestamp（不重置 isDirty）
    4. 写入失败 → 静默忽略，等待下一周期重试（不弹错误提示）
```

**文件位置**：`{cacheDir}/recovery/recovery_tmp.pb`

- `cacheDir` 为应用私有缓存目录，用户不可见，不经 SAF 授权访问
- 文件格式与用户主动保存的 `.pb` 文件完全一致，可直接加载

### 7.2 启动时恢复检测流程

```
App 冷启动
  │
  ▼
  检查 {cacheDir}/recovery/recovery_tmp.pb 是否存在
  │
  ├─ 不存在 → 正常启动（空白页 / 加载上次用户保存的文件）
  │
  └─ 存在 → 显示崩溃恢复 Bottom Sheet（一次性，阻塞操作）
             │
             ├─ 用户点击【恢复】
             │    ├─ 加载 recovery_tmp.pb 内容到当前画板
             │    ├─ 删除临时文件
             │    └─ 进入白板（画布呈现恢复内容，UndoStack 重置为空）
             │
             └─ 用户点击【放弃】
                  ├─ 删除临时文件
                  └─ 进入白板（空白页）

  恢复提示仅出现一次：无论用户选择恢复还是放弃，
  临时文件均被删除，下次冷启动不再弹出。
```

**Bottom Sheet UI 规范**：见 file-management-ux.md §5。

### 7.3 恢复文件的写入策略

| 场景 | 行为 |
|------|------|
| 每 2 分钟定时写入 | 覆盖同一文件，不累积多版本 |
| App 正常退出（用户主动关闭） | 执行一次最终写入，临时文件保留（下次启动仍可恢复） |
| 用户主动执行"保存" | `isDirty` 重置为 `false`，不删除临时文件（防止保存后继续书写又崩溃丢失） |
| 用户主动执行"新建白板" | 临时文件删除（新白板视为无需恢复的内容） |

### 7.4 恢复后的 UndoStack 处理

- 恢复后的画布内容视为"初始内容"，UndoStack 重置为空
- 用户无法通过撤销回到崩溃前的某个中间状态（简化实现，避免操作序列重放复杂度）
- 恢复 Bottom Sheet 提示文案包含说明："恢复后操作历史已重置，无法撤销到恢复前状态"

---

*文档结束*
*下一份文档：toolbar-component.md*

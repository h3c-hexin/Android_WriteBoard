# Android 手写渲染方案对比

> 创建：2026-03-25
> 适用场景：白板、笔记、手写签名等对书写延迟敏感的 Android 应用

---

## 延迟来源拆解

书写延迟由四段叠加组成：

```
手指/笔触摸屏
   ↓ ① 硬件采样（60Hz=16ms，120Hz=8ms，240Hz触控笔=4ms）
触摸事件到 App
   ↓ ② App 处理（计算路径，通常 < 2ms）
绘制指令提交
   ↓ ③ Vsync 等待（Choreographer 对齐，最坏 16ms @60Hz）
GPU 渲染完成
   ↓ ④ SurfaceFlinger 合成 + 扫描输出（约 8ms）
像素出现在屏幕
```

- **前端缓冲渲染**解决的是 ③④（绕过 Vsync + SurfaceFlinger）
- **历史点 + Ink Stroke Modeler**解决的是 ① 的利用率和 ② 的预测补偿
- **GraphicsLayer 缓存**解决的是 ② 的计算成本（稳帧率）

---

## 方案 A：Compose Canvas + 优化（当前方案）

### 架构

```
pointerInteropFilter（原始 MotionEvent）
  → 历史点 + 压感 → Ink Stroke Modeler
  → predictedPoints → _activeStroke（Compose StateFlow）
  → DrawingCanvas 重组 → StrokeRenderer.buildPath → drawPath
  已提交笔画 → GraphicsLayer（GPU 重放）
```

### 延迟分析

| 来源 | 能否改善 | 方式 |
|------|---------|------|
| ① 硬件采样 | ✅ 利用率提升 | 历史点补全 |
| ② App 处理 | ✅ 预测补偿 | Ink Stroke Modeler |
| ③ Vsync 等待 | ❌ 无法绕过 | — |
| ④ 合成延迟 | ❌ 无法绕过 | — |

### 适用条件

- 全版本（minSdk 无限制）
- 纯 Compose 架构，无 View 混用
- 工作量：~5 天

### 体感延迟

~20ms（预测后视觉延迟 ~10ms，但像素实际输出仍受 Vsync 约束）

---

## 方案 B：双层混合渲染（本项目采用方案）

### 架构

```
pointerInteropFilter（原始 MotionEvent）
  → 历史点 + 压感 → Ink Stroke Modeler
  → predictedPoints
       ├── API 31+：GLFrontBufferedRenderer（SurfaceView，绕过 Vsync）
       └── API < 31：Compose _activeStroke（兜底）
  手势结束
  → confirmedPoints → commitStroke → GraphicsLayer 重录
  → FrontBuffer 清空（SurfaceControl.Transaction 同步，无闪烁）
```

### 延迟分析

| 来源 | 能否改善 | 方式 |
|------|---------|------|
| ① 硬件采样 | ✅ 利用率提升 | 历史点补全 |
| ② App 处理 | ✅ 预测补偿 | Ink Stroke Modeler |
| ③ Vsync 等待 | ✅ API 31+ 绕过 | FrontBuffer SurfaceView |
| ④ 合成延迟 | ✅ API 31+ 绕过 | FrontBuffer 直写 |

### 适用条件

- 推荐 minSdk 31（Android 12），低版本自动降级
- Compose 架构为主，局部引入 AndroidView
- 工作量：~7.5 天

### 体感延迟

- API 31+：< 10ms
- API < 31：~20ms（降级为方案 A）

### 实现文档

详见 `docs/develop/writing-smoothness-plan.md`

---

## 方案 C：androidx.ink（InProgressStrokesView）

### 架构

```
InProgressStrokesView（自定义 View，内部 SurfaceView）
  → 内置触摸处理（MotionEvent 直接接入）
  → 内置 Ink Stroke Modeler 平滑 + 预测
  → 内置 GLFrontBufferedRenderer（前端缓冲）
  → 内置笔触渲染（可配置 Brush）
  手势结束
  → InProgressStrokesView 回调 → Stroke 对象
  → 业务层（存盘 / 协同 / Undo）
```

### 延迟分析

| 来源 | 能否改善 | 方式 |
|------|---------|------|
| ① 硬件采样 | ✅ 最优 | 内置历史点 + 高频采样 |
| ② App 处理 | ✅ 最优 | 内置 Modeler，深度优化 |
| ③ Vsync 等待 | ✅ 完全绕过 | 内置 FrontBuffer |
| ④ 合成延迟 | ✅ 完全绕过 | 内置硬件加速通道 |

### 适用条件

- minSdk 21+，推荐 API 29+
- **需要改造渲染层**：DrawingCanvas 替换为 `InProgressStrokesView + AndroidView`
- 图形绘制（ShapeRenderer）需并行保留，Ink API 不支持预制图形
- PageBitmapRenderer（导出）需重新适配

### 架构改动范围（相对现有项目）

| 模块 | 影响 |
|------|------|
| `DrawingCanvas.kt` | 🔴 完全重写（触摸 + 渲染） |
| `PageBitmapRenderer.kt` | 🔴 较大改动（导出渲染适配）|
| `ShapeRenderer.kt` | 🟡 需并行渲染层 |
| `StrokeRenderer.kt` | 🟢 可保留用于导出 |
| `Stroke` 数据模型 | ✅ 零改动 |
| 协同系统 | ✅ 零改动 |
| 文件存盘 | ✅ 零改动 |
| 橡皮擦 / Undo | ✅ 零改动（仍在 ViewModel）|

### 体感延迟

< 5ms（对标 iPad + Apple Pencil 级别）

### 工作量

~15 天

---

## 三方案对比总览

| 维度 | 方案 A（Compose 优化）| 方案 B（双层混合）| 方案 C（InProgressStrokesView）|
|------|---------------------|-----------------|-------------------------------|
| **体感延迟** | ~20ms | < 10ms（API 31+）| < 5ms |
| **绕过 Vsync** | ❌ | ✅ API 31+ | ✅ 全版本 |
| **历史点** | ✅ | ✅ | ✅（内置）|
| **Modeler 平滑** | ✅ | ✅ | ✅（内置）|
| **压感变宽** | ✅ 可实现 | ✅ 可实现 | ✅（内置 Brush）|
| **架构改动** | 小 | 中 | 大 |
| **图形绘制** | ✅ 无影响 | ✅ 无影响 | ⚠️ 需并行层 |
| **导出功能** | ✅ 无影响 | ✅ 无影响 | ⚠️ 需重新适配 |
| **协同系统** | ✅ 无影响 | ✅ 无影响 | ✅ 无影响 |
| **minSdk 要求** | 无限制 | 推荐 31 | 21+（推荐 29+）|
| **工作量** | ~5 天 | ~7.5 天 | ~15 天 |
| **回退成本** | 低 | 低～中 | 高 |

---

## 决策建议

| 场景 | 推荐方案 |
|------|---------|
| 轻度书写（会议白板、偶尔标注） | 方案 A |
| 中度书写（课堂板书、日常笔记） | 方案 B |
| 重度书写（专业笔记、手写签名、教育批改） | 方案 C |
| 新项目、书写为核心功能、无历史架构包袱 | 方案 C（从一开始设计进去）|

---

## 关键技术参考

- `androidx.graphics:graphics-core` — `GLFrontBufferedRenderer` / `CanvasFrontBufferedRenderer`
- `androidx.ink:ink-strokes` — Ink Stroke Modeler Kotlin 封装
- `androidx.ink:ink-authoring` — `InProgressStrokesView`（方案 C 核心）
- `MotionEvent.getHistoricalX/Y/Pressure()` — 历史点 API
- `Modifier.pointerInteropFilter` — Compose 访问原始 MotionEvent 的入口
- `androidx.compose.ui.graphics.layer.GraphicsLayer` — Compose 1.7+ 的 GPU 渲染缓存
- `SurfaceControl.Transaction` — 多 Surface 同步提交，消除图层切换闪烁

---

## 注意事项

1. **历史点压感**：Compose 的 `PointerInputChange.historical` 只有坐标，无压感。
   获取历史压感必须通过 `pointerInteropFilter` 拿到原始 `MotionEvent`。

2. **前端缓冲闪烁**：笔画交接（FrontBuffer → Compose Layer）需用 `SurfaceControl.Transaction`
   同步提交，否则会有 1 帧空白闪烁。

3. **预测点不可持久化**：Modeler 输出的预测点（extrapolated）不代表用户实际轨迹，
   只能用于当帧视觉渲染。存盘、协同广播、Undo 必须使用 `confirmedPoints`。

4. **方案 C 的图形兼容**：`InProgressStrokesView` 仅处理自由笔画，
   预制图形（箭头、矩形等）需在其上方叠加独立 Compose Canvas 层渲染。

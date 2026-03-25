# 书写流畅度优化方案 B：双层混合渲染

> 创建：2026-03-25

---

## 背景

MVP 完成后对书写延迟进行专项优化。目标：在保持 Compose Canvas 架构不变的前提下，
将手写延迟从 ~30ms 降低到 < 10ms（API 31+ 设备），同时稳定多笔画场景的帧率。

---

## 方案概述

三个独立优化层，互不干扰，可分步实施：

```
触摸入口
pointerInteropFilter（原始 MotionEvent）
         |
    历史点 + 当前点 + 历史压感
         ↓
  StrokeModelerWrapper
  （Ink Stroke Modeler：平滑 + 预测）
         ↓
  predicted points
    /          \
API 31+       API < 31
FrontBuffer   Compose _activeStroke
SurfaceView   （GraphicsLayer 缓存兜底）
（零 Vsync 等待）
    \          /
    手势结束（收敛）
    → commitStroke → GraphicsLayer 重录
    → FrontBuffer 清空
```

---

## 依赖引入

```kotlin
// app/build.gradle.kts
implementation("androidx.graphics:graphics-core:1.0.0-rc01")  // GLFrontBufferedRenderer
implementation("androidx.ink:ink-strokes:1.0.0-alpha01")       // Ink Stroke Modeler
```

---

## Step 1：触摸入口切换

**文件**：`app/src/main/java/com/paintboard/ui/canvas/DrawingCanvas.kt`

将书写手势从 `awaitPointerEvent()` 改为 `pointerInteropFilter`，以访问完整的原始
`MotionEvent`（含历史坐标和历史压感）。

```kotlin
// 改前：
val event = awaitPointerEvent()
val change = event.changes.firstOrNull() ?: break
viewModel.onStrokeMove(change.position.x / w, change.position.y / h, change.pressure)

// 改后：
Modifier.pointerInteropFilter { motionEvent ->
    when (motionEvent.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            modeler.reset()
            modeler.feed(motionEvent.x / w, motionEvent.y / h, motionEvent.pressure, motionEvent.eventTime)
        }
        MotionEvent.ACTION_MOVE -> {
            // 历史点：两帧之间系统缓冲的中间坐标
            for (i in 0 until motionEvent.historySize) {
                modeler.feed(
                    motionEvent.getHistoricalX(i) / w,
                    motionEvent.getHistoricalY(i) / h,
                    motionEvent.getHistoricalPressure(i),
                    motionEvent.getHistoricalEventTime(i)
                )
            }
            // 当前点
            modeler.feed(motionEvent.x / w, motionEvent.y / h, motionEvent.pressure, motionEvent.eventTime)
            frontBufferView.renderActiveStroke(modeler.predictedPoints)
        }
        MotionEvent.ACTION_UP -> {
            viewModel.onStrokeEnd(modeler.confirmedPoints)
            frontBufferView.clear()
        }
    }
    true
}
```

**注意**：橡皮擦、图形手势继续使用原有 `pointerInput`，不受影响。

---

## Step 2：StrokeModelerWrapper

**新建文件**：`app/src/main/java/com/paintboard/ui/canvas/StrokeModelerWrapper.kt`

封装 Ink Stroke Modeler，对外提供简单接口：

```kotlin
class StrokeModelerWrapper {
    private val modeler = InkStrokeModeler(/* 物理参数配置 */)

    /** 喂入一个触摸点（含历史点循环调用） */
    fun feed(x: Float, y: Float, pressure: Float, timestampMs: Long) { ... }

    /** 预测点（含 1-2 帧外推）→ 实时渲染用 */
    val predictedPoints: List<StrokePoint> get() = ...

    /** 已确认点（不含预测）→ commitStroke 用，也是协同广播和存盘的数据 */
    val confirmedPoints: List<StrokePoint> get() = ...

    /** 手势开始时重置 */
    fun reset() { ... }
}
```

**关键约束**：
- 协同广播、文件存盘、Undo 栈全部使用 `confirmedPoints`，不含预测点
- `predictedPoints` 仅用于当帧视觉渲染，不持久化

---

## Step 3：FrontBufferStrokeView

**新建文件**：`app/src/main/java/com/paintboard/ui/canvas/FrontBufferStrokeView.kt`

自定义 View，内部封装 `GLFrontBufferedRenderer`（API 31+）：

```kotlin
@RequiresApi(31)
class FrontBufferStrokeView(context: Context) : SurfaceView(context) {

    private val renderer = GLFrontBufferedRenderer(this, callbacks)

    /** 实时渲染当前活跃笔画（绕过 Vsync，直接写前端缓冲） */
    fun renderActiveStroke(points: List<StrokePoint>) {
        renderer.renderFrontBufferedLayer(points)
    }

    /** 手势结束：清空前端缓冲（Compose 层已接管渲染） */
    fun clear() {
        renderer.renderMultiBufferedLayer(emptyList())
        renderer.commit()
    }
}
```

**在 CanvasScreen 叠加此 View**（`CanvasScreen.kt`）：

```kotlin
Box(Modifier.fillMaxSize()) {
    DrawingCanvas(Modifier.fillMaxSize(), viewModel)         // 已提交笔画层
    if (Build.VERSION.SDK_INT >= 31) {
        AndroidView(
            factory = { FrontBufferStrokeView(it) },
            modifier = Modifier.fillMaxSize()
        ) { view -> frontBufferViewRef = view }
    }
    BottomToolbar(...)
}
```

**低版本兜底（API < 31）**：
- 不创建 FrontBufferStrokeView
- 活跃笔画仍走 Compose `_activeStroke` → GraphicsLayer，延迟约 20ms

---

## Step 4：GraphicsLayer 缓存已提交笔画

**文件**：`DrawingCanvas.kt`，渲染块

```kotlin
val committedLayer = rememberGraphicsLayer()

// 已提交笔画变化时重录（只录 DrawCommand，GPU 直接重放）
LaunchedEffect(page.strokes) {
    committedLayer.record {
        drawIntoCanvas { canvas ->
            page.strokes.forEach { stroke ->
                canvas.nativeCanvas.drawPath(
                    StrokeRenderer.buildPath(stroke.points, w, h),
                    buildPaint(stroke.color, stroke.width * w)
                )
            }
        }
    }
}

// 每帧渲染
Canvas(modifier) {
    drawLayer(committedLayer)        // GPU 重放，零 CPU 开销
    // API < 31 时在 Compose 层绘制活跃笔画
    if (Build.VERSION.SDK_INT < 31 && activeStroke.isNotEmpty()) {
        drawPath(StrokeRenderer.buildPath(activeStroke, w, h), activePaint)
    }
}
```

---

## Step 5：onStrokeEnd 接收 confirmedPoints

**文件**：`CanvasViewModel.kt`

```kotlin
// 改前：
fun onStrokeEnd() {
    commitStroke(_activeStroke.value)
    _activeStroke.value = emptyList()
}

// 改后（接收 Modeler 确认点）：
fun onStrokeEnd(confirmedPoints: List<StrokePoint>) {
    if (confirmedPoints.isEmpty()) return
    commitStroke(confirmedPoints)
    _activeStroke.value = emptyList()
}
```

`commitStroke` 内部逻辑不变，协同广播 / 存盘 / Undo 均使用传入的 `confirmedPoints`。

---

## 笔画交接时序（手势结束，消除闪烁）

```
T+0ms  用户抬手
T+0ms  onStrokeEnd(confirmedPoints) → commitStroke → ViewModel 更新
T+0ms  GraphicsLayer.record() 触发（异步）
T+1帧  Compose 重组，drawLayer(committedLayer) 渲染新笔画
T+1帧  frontBufferView.clear() 在同一帧内执行
       → SurfaceControl.Transaction 同步两层，零闪烁
```

---

## 架构影响范围

| 模块 | 改动 | 风险 |
|------|------|------|
| `DrawingCanvas.kt` | 触摸入口 + 渲染层重构 | 🟡 中，局部可控 |
| `CanvasScreen.kt` | 新增 AndroidView overlay（约 10 行） | 🟢 低 |
| `CanvasViewModel.kt` | `onStrokeEnd` 增加参数 | 🟢 低 |
| 新增 `StrokeModelerWrapper.kt` | 独立封装 | 🟢 低 |
| 新增 `FrontBufferStrokeView.kt` | API 31+ 才生效 | 🟡 中 |
| `Stroke` 数据模型 | **零改动** | ✅ 无 |
| 协同系统 | **零改动** | ✅ 无 |
| 文件存盘 / 自动存盘 | **零改动** | ✅ 无 |
| 橡皮擦 / Undo / Redo | **零改动** | ✅ 无 |
| 图形绘制 | **零改动** | ✅ 无 |
| 分享导出（PageBitmapRenderer） | **零改动** | ✅ 无 |

---

## 预期效果

| 设备 / 场景 | 优化前 | 优化后 |
|------------|--------|--------|
| API 31+，快速书写 | ~30ms，断线 | < 10ms，连续 |
| API 31+，大量笔画 | 帧率下降 | 稳定 60fps |
| API < 31 | ~30ms | ~20ms（预测 + GraphicsLayer）|
| 协同书写 | 无变化 | 无变化（confirmedPoints 不变）|
| 导出分享 | 无变化 | 无变化 |

---

## 工作量估算

| 步骤 | 工作量 |
|------|--------|
| Step 1：触摸入口切换 | 1 天 |
| Step 2：StrokeModelerWrapper | 1.5 天 |
| Step 3：FrontBufferStrokeView | 2 天 |
| Step 4：GraphicsLayer 缓存 | 1 天 |
| Step 5：onStrokeEnd 联调 | 0.5 天 |
| Step 6：协同 + Undo 回归验证 | 1.5 天 |
| **合计** | **~7.5 天** |

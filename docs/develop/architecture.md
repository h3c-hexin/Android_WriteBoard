# PaintBoard — 软件架构设计

> 版本：v1.0 | 日期：2026-03-23

---

## 一、整体架构

采用 **MVVM + Clean Architecture** 分层，UI 层使用 Jetpack Compose。

```
┌─────────────────────────────────────────────────────┐
│                    UI Layer                         │
│   Composable Screens + ViewModels                   │
├─────────────────────────────────────────────────────┤
│                  Domain Layer                       │
│   UseCases + 核心数据模型（纯 Kotlin，无 Android 依赖）│
├─────────────────────────────────────────────────────┤
│                  Data Layer                         │
│   Repositories + 本地存储 + WebSocket + HTTP Server  │
└─────────────────────────────────────────────────────┘
```

各层职责：
- **UI Layer**：渲染画布、工具栏、Bottom Sheet；监听 ViewModel 状态
- **Domain Layer**：笔画逻辑、Undo/Redo 栈、页面管理、设备模式判断
- **Data Layer**：.pb 文件读写、WebSocket 协同、HTTP 分享服务、崩溃恢复

---

## 二、包目录结构

```
com.paintboard/
├── app/
│   └── PaintBoardApplication.kt       # Hilt Application 入口
│
├── ui/
│   ├── canvas/
│   │   ├── CanvasScreen.kt            # 主画布页面（唯一页面）
│   │   ├── CanvasViewModel.kt
│   │   └── DrawingCanvas.kt           # 自定义 Canvas Composable
│   ├── toolbar/
│   │   ├── BottomToolbar.kt           # 底部工具栏
│   │   ├── EraserPanel.kt             # 橡皮擦子面板 Bottom Sheet
│   │   ├── ShapePanel.kt              # 图形工具子面板 Bottom Sheet
│   │   └── ColorPickerPanel.kt        # 颜色选择器 Bottom Sheet
│   ├── pages/
│   │   └── PageManagerPanel.kt        # 页面管理 Bottom Sheet
│   ├── collaboration/
│   │   └── CollaborationPanel.kt      # 协同面板 Bottom Sheet
│   ├── share/
│   │   └── SharePanel.kt              # 扫码分享 Bottom Sheet
│   ├── file/
│   │   └── MoreMenuPanel.kt           # 更多菜单 Bottom Sheet
│   └── theme/
│       ├── Color.kt                   # Design Token 颜色常量
│       ├── Type.kt                    # 字体
│       └── Theme.kt                   # MaterialTheme 配置
│
├── domain/
│   ├── model/                         # 核心数据模型（见第三章）
│   │   ├── Stroke.kt
│   │   ├── DrawingPage.kt
│   │   ├── Whiteboard.kt
│   │   └── DrawingTool.kt
│   ├── usecase/
│   │   ├── DrawStrokeUseCase.kt
│   │   ├── EraseUseCase.kt
│   │   ├── UndoRedoUseCase.kt
│   │   ├── ManagePagesUseCase.kt
│   │   └── ExportWhiteboardUseCase.kt
│   └── repository/                    # 接口定义
│       ├── WhiteboardRepository.kt
│       └── CollaborationRepository.kt
│
├── data/
│   ├── file/
│   │   ├── WhiteboardRepositoryImpl.kt
│   │   └── PbFileFormat.kt            # .pb 文件序列化/反序列化
│   ├── collaboration/
│   │   ├── CollaborationRepositoryImpl.kt
│   │   ├── WebSocketHost.kt           # 大屏 Host 服务
│   │   └── WebSocketClient.kt         # PAD 客户端
│   └── share/
│       └── HttpShareServer.kt         # 本地 HTTP 分享服务
│
└── di/
    └── AppModule.kt                   # Hilt 依赖注入模块
```

---

## 三、核心数据模型

### 3.1 笔画 Stroke

```kotlin
data class Stroke(
    val id: String,              // 格式：{deviceId}_{clusterId}_{sequence}
    val points: List<StrokePoint>,
    val color: Int,              // ARGB
    val width: Float,            // dp，对应细/中/粗三档
    val tool: StrokeTool,        // PEN | SHAPE
    val shapeType: ShapeType? = null,  // 仅 SHAPE 类型使用
    val deviceId: String,        // 协同时标识来源设备
    val timestamp: Long
)

data class StrokePoint(
    val x: Float,    // 归一化坐标 [0.0, 1.0]，文件存储和协同传输使用
    val y: Float,
    val pressure: Float = 1.0f  // 压感，不支持时为 1.0
)

enum class StrokeTool { PEN, SHAPE, ERASER }

enum class ShapeType {
    LINE, ARROW, RECT, CIRCLE, TRIANGLE, DIAMOND
}
```

### 3.2 页面 DrawingPage

```kotlin
data class DrawingPage(
    val id: String,
    val strokes: List<Stroke>,
    val background: PageBackground,
    val undoStack: List<UndoAction>,   // 按页隔离，最多 50 步
    val redoStack: List<UndoAction>
)

enum class PageBackground { BLANK, GRID, LINES }
```

### 3.3 白板 Whiteboard

```kotlin
data class Whiteboard(
    val id: String,
    val title: String,           // 默认：会议_YYYY-MM-DD_HH:mm
    val pages: List<DrawingPage>,
    val currentPageIndex: Int,
    val createdAt: Long,
    val updatedAt: Long
)
```

### 3.4 工具状态 DrawingTool

```kotlin
// ViewModel 中维护的当前工具状态
data class DrawingToolState(
    val activeTool: ActiveTool,
    val strokeWidth: StrokeWidth,
    val currentColor: Int,
    val quickColors: List<Int>,        // 工具栏 4 个快捷色
    val recentColors: List<Int>,       // 最近 5 色
    val selectedShape: ShapeType,
    val eraserSize: EraserSize,
    val autoCorrect: Boolean           // 图形自动矫正开关
)

enum class ActiveTool { PEN, ERASER, SHAPE }
enum class StrokeWidth { THIN, MEDIUM, THICK }
enum class EraserSize { SMALL, MEDIUM, LARGE }
```

### 3.5 Undo 动作 UndoAction

```kotlin
sealed class UndoAction {
    data class AddStroke(val stroke: Stroke) : UndoAction()
    data class RemoveStrokes(val strokes: List<Stroke>) : UndoAction()  // 橡皮擦
    data class ClearPage(val strokes: List<Stroke>) : UndoAction()      // 清页
    data class CorrectShape(
        val original: Stroke,
        val corrected: Stroke
    ) : UndoAction()
}
```

### 3.6 协同消息 SyncMessage

```kotlin
// WebSocket 传输的消息格式（JSON 序列化）
sealed class SyncMessage {
    data class StrokeBegin(val deviceId: String, val strokeId: String, val color: Int, val width: Float) : SyncMessage()
    data class StrokePoint(val strokeId: String, val x: Float, val y: Float) : SyncMessage()
    data class StrokeEnd(val strokeId: String) : SyncMessage()
    data class EraseArea(val strokeIds: List<String>) : SyncMessage()
    data class CursorMove(val deviceId: String, val x: Float, val y: Float) : SyncMessage()
    data class HostLock(val locked: Boolean) : SyncMessage()
    data class PageSwitch(val pageIndex: Int) : SyncMessage()
}
```

---

## 四、设备模式检测

```
启动时检测屏幕对角线尺寸
    │
    ├── > 30 英寸 → 大屏模式（LargeScreen）
    │   └── 输入判断：getTouchMajor() 面积阈值（默认 80dp）
    │
    └── ≤ 30 英寸 → PAD 模式（Pad）
        └── 输入判断：getToolType() — STYLUS=书写，FINGER=擦除
```

两种模式均可在设置中手动切换。

---

## 五、关键技术依赖清单

```toml
# gradle/libs.versions.toml

[versions]
kotlin = "2.0.21"
agp = "8.7.3"
compose-bom = "2024.12.01"
hilt = "2.53"
coroutines = "1.9.0"
okhttp = "4.12.0"         # WebSocket 客户端
ktor = "3.0.3"            # HTTP 分享服务（轻量内嵌服务器）
zxing = "3.5.3"           # 二维码生成
mlkit-ink = "1.0.0-beta1" # 图形自动矫正
kotlinx-serialization = "1.7.3"  # .pb 文件 JSON 序列化

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }

# 架构
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version = "2.8.7" }

# 网络
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
ktor-server-cio = { group = "io.ktor", name = "ktor-server-cio", version.ref = "ktor" }

# 工具
zxing-core = { group = "com.google.zxing", name = "core", version.ref = "zxing" }
mlkit-digitalink = { group = "com.google.mlkit", name = "digital-ink-recognition", version.ref = "mlkit-ink" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
```

---

## 六、画布渲染方案

- 使用 Compose `Canvas` + `drawWithCache`，避免每帧重建 Path 对象
- 笔画渲染：增量绘制（只重绘新增点的脏矩形区域）
- 所有已完成笔画缓存到 `Bitmap`，新笔画绘制在 Bitmap 之上
- 橡皮擦：`PorterDuff.Mode.CLEAR` 实现像素级擦除

```
┌─────────────────────────────┐
│  Bitmap 层（已完成笔画缓存）  │  ← 不频繁重绘
├─────────────────────────────┤
│  Canvas 层（当前正在绘制的笔画）│  ← 每帧更新
└─────────────────────────────┘
```

---

## 七、.pb 文件格式

`.pb` 文件为 **UTF-8 编码的 JSON**，扩展名自定义。

```json
{
  "version": "1.0",
  "title": "会议_2026-03-23_14:30",
  "createdAt": 1742731800000,
  "updatedAt": 1742735400000,
  "pages": [
    {
      "id": "page_001",
      "background": "BLANK",
      "strokes": [
        {
          "id": "dev1_c0_001",
          "tool": "PEN",
          "color": -15921906,
          "width": 8.0,
          "points": [
            { "x": 0.12, "y": 0.34, "pressure": 1.0 }
          ],
          "timestamp": 1742731900000
        }
      ]
    }
  ]
}
```

---

## 八、崩溃恢复机制

```
App 运行中
    │
    ▼
每 2 分钟 → 静默写入 /cache/paintboard_recovery.pb
    │
App 正常退出 → 删除恢复文件
    │
App 异常退出（崩溃/强杀）→ 恢复文件保留
    │
下次启动检测到恢复文件
    │
    └── 弹出 Bottom Sheet（不可点遮罩关闭）
            ├── [恢复] → 加载恢复文件，删除临时文件
            └── [放弃] → 直接删除临时文件，新建空白白板
```

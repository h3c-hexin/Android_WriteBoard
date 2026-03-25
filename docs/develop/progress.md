# PaintBoard 开发进度

> 最后更新：2026-03-25

---

## 当前状态：模块 1~11 全部完成，MVP ✅

```
模块 1  项目脚手架          ██████████  ✅ 已验收
模块 2  画布引擎            ██████████  ✅ 已验收
模块 3  工具栏 UI           ██████████  ✅ 已验收（三轮）
模块 4  颜色选择器          ██████████  ✅ 已验收（二轮，含 Popup 改造）
模块 5  橡皮擦              ██████████  ✅ 已验收（两轮）
模块 6  撤销 / 重做         ██████████  ✅ 随模块 2~5 一并实现，待独立验收
模块 7  图形工具            ██████████  ✅ 随模块 5/6 一并实现，已验收
模块 8  多页管理            ██████████  ✅ 已验收
模块 9  文件保存 / 打开     ██████████  ✅ 已验收
模块 10 扫码分享            ██████████  ✅ 已验收
模块 11 LAN 协同            ██████████  ✅ 已验收
```

---

## 已完成模块详情

### 模块 1：项目脚手架
- Gradle 8.11.1 / AGP 8.7.3 / Compose BOM 2024.12.01
- Hilt 依赖注入初始化
- 包结构：`domain/model`、`ui/canvas`、`ui/toolbar`、`ui/theme`
- 深色主题：画布 `#1E1E1E`，工具栏 `#2C2C2C`
- TLS 修复（`jdk.tls.namedGroups` in gradle.properties）

### 模块 2：画布引擎
- `DrawingCanvas` Composable，Android Canvas 直接渲染（无 Bitmap 缓存，避免笔迹闪烁）
- Catmull-Rom 样条插值（`SMOOTH_FACTOR = 0.35`）
- 归一化坐标系 [0.0, 1.0]，跨设备兼容
- 距离过滤阈值 0.003（归一化），减少锯齿
- PAD 模式检测：对角线 < 20 英寸视为 PAD

### 模块 3：工具栏 UI
- 6 分区横排布局（A 工具 / B 粗细 / C 颜色 / D 操作 / E 功能 / F 页面），高度 72dp
- 按钮三态：激活背景 `#90CAF9`（40×40dp 圆角块）+ 图标 `#003C8F`；默认 `#9E9E9E`；禁用 `#616161`
- 图标视觉尺寸 24dp，触控热区 64dp
- **B 区上下文切换**：画笔/图形 → 显示线条粗细（横线 22dp 宽）；橡皮擦 → 显示橡皮大小（中空圆圈）
- 图形工具图标：空心三角形（`Icons.Outlined.ChangeHistory`）

### 模块 4：颜色选择器
- 工具栏 4 快捷色 + 调色盘入口
- `ColorPickerPanel`：**Compose Popup**（已从 ModalBottomSheet 改造），两栏布局
  - 左：快速色 44dp + 20 色宫格（36×32dp）+ 最近使用 5 色 32dp
  - 右：SV 矩形（双渐变叠加）+ 色相滑块 + Hex 输入框
- HSV ↔ Color：`android.graphics.Color.colorToHSV / HSVToColor`
- 弹出位置：以调色盘按钮为锚点，底边对齐工具栏顶边，最大宽度 560dp

### 模块 5：橡皮擦
- **分段擦除**：仅擦除橡皮圆覆盖区域，两端保留为独立笔画段
- 橡皮圆判断纵横比修正（`dy *= aspectRatio`），确保物理像素空间圆形
- 手势合并为单步 Undo（`SplitEraseStrokes` action）
- 橡皮擦光标：画布上实时渲染白色圆圈
- PAD 模式：`PointerType.Touch` = 擦除，`PointerType.Stylus` = 绘制
- 实际橡皮大小：小 1% / 中 2% / 大 4%（画布宽度归一化）
- **橡皮擦子面板**：Compose `Popup`，锚定在按钮正上方，宽 280dp
  - 触发：橡皮已激活状态下再次点击按钮
  - 内容：标题 + 关闭按钮 + 滑动清除控件
  - 滑动清除：`BoxWithConstraints` 测量轨道宽，Y 偏移固定 6dp（历史 bug 修复）
  - 触发后自动切换画笔模式

### 模块 6：撤销 / 重做
- `UndoStack` / `RedoStack`（ArrayDeque，最多 50 步）
- 支持操作类型：`AddStroke`、`SplitEraseStrokes`、`ClearPage`（含 `EraseStrokes` 备用）
- `canUndo` / `canRedo` StateFlow，工具栏按钮实时联动禁用态
- **待独立验收**（功能随模块 2~5 实现，尚未按模块 6 验收清单走一遍）

### 模块 8：多页管理
- **数据模型**：`DrawingPage`（id、background、strokes）；`PageBackground`（BLANK/GRID/LINES）
- **ViewModel 多页架构**：
  - `_pages: MutableStateFlow<List<DrawingPage>>`（页面列表）
  - `_currentPageIndex: MutableStateFlow<Int>`（当前索引）
  - `_page: MutableStateFlow<DrawingPage>`（同步镜像，供 DrawingCanvas 即时响应）
  - `updateCurrentPage()` 同时更新 `_pages` 和 `_page.value`，避免 combine+stateIn 异步延迟导致撤销闪白
- **页面操作**：switchPage、addPage、deletePage、movePageUp、movePageDown、setPageBackground
- **每页独立 Undo/Redo**：`undoStacks / redoStacks: Map<pageId, ArrayDeque<UndoAction>>`，切换页面自动同步 canUndo/canRedo
- **页面背景渲染**：`DrawingCanvas` 在绘制笔画前先绘制网格/横线背景
- **`PageManagerPopup`**（`ui/pages/PageManagerPanel.kt`）：
  - 以页码区为锚点，Popup 底边对齐工具栏顶边
  - 最大宽度 648dp（约 3.5 张卡片），内容宽度自适应
  - 打开时当前页自动水平居中：`snapshotFlow { layoutInfo.viewportSize.width }.first { it > 0 }` + `scrollToItem(index, centerOffset)`
  - 缩略图 160×90dp，实时渲染笔画（复用 ShapeRenderer / StrokeRenderer）
  - 删除二次确认：内联卡片确认覆盖层（非 AlertDialog）

### 模块 9：文件保存 / 打开
- **文件格式**：JSON（`kotlinx.serialization`），扩展名 `.pb`，UTF-8
  - 格式：`{ version: 1, pages: [DrawingPage] }`
  - `DrawingPage`、`Stroke`、`StrokePoint`、`StrokeTool`、`ShapeType`、`PageBackground` 均加 `@Serializable`
- **手动保存 / 打开**：SAF `ActivityResultContracts.CreateDocument` / `OpenDocument`，BottomToolbar 注册 launcher，ViewModel 的 `saveBoard` / `loadBoard` 执行读写
- **更多菜单**（`ui/toolbar/MoreMenuPopup.kt`）：Compose `Popup`（§0 规范）
  - 3 个菜单项：新建 / 打开 / 保存
  - 宽度 `IntrinsicSize.Max + widthIn(min=160dp)`，自适应内容，无多余留白
  - 菜单项 `fillMaxWidth()`，整行 clickable（修复热区截断 bug）
  - 新建二次确认：内联切换确认视图（`confirmingNew` 状态），无额外弹窗层级
- **自动存盘**（`BoardRepository` + `CanvasViewModel`）：
  - `saveAutosave` / `loadAutosave`：读写 `context.filesDir/autosave.pb`
  - `scheduleAutosave()`：2 秒防抖，`viewModelScope + Dispatchers.IO`，所有页面修改操作后触发
  - `init{}`：同步读取自动存盘（主线程，< 30ms），启动即恢复，无闪白无弹窗
  - 新建白板时：取消防抖 Job，立即写入空白板，确保强杀后不恢复旧内容

---

## 关键技术决策备忘

| 决策 | 结论 | 原因 |
|------|------|------|
| 画布渲染方式 | 直接 Canvas 渲染，无 Bitmap 缓存 | 缓存方案导致笔迹闪烁 |
| 坐标系 | 归一化 [0,1] | 大屏/PAD 同一套数据 |
| 橡皮擦大小 | 用户选定档位（非 touchMajor 自动） | 行为可预期，混用两种来源会让用户困惑 |
| **所有二级子面板形式** | **统一用 Compose Popup**（非 ModalBottomSheet） | BottomSheet 全屏遮罩占画布、位置不靠近触发点、大屏体验差；Popup 锚点准确、无遮罩、尺寸按内容决定 |
| 二级面板弹出位置 | Popup 底边 = 工具栏顶边（`y = windowHeight - 72dp - popupHeight`），水平居中锚点按钮 | 面板紧贴工具栏上方，不遮挡画布，不压住工具栏 |
| 二级面板锚点放置 | Popup 必须放置于触发按钮 Box 内部 | `PopupPositionProvider` 通过 `anchorBounds` 获取锚点坐标，放在按钮内才能拿到正确坐标 |
| B 区切换 | 上下文切换（画笔/橡皮共用 B 区） | 减少界面层级，B 区永远与当前工具联动 |
| 激活态配色 | 浅蓝 `#90CAF9` + 深蓝图标 `#003C8F` | 对照 `01-main-canvas.html` 设计稿精确还原 |
| 文件序列化格式 | JSON（`kotlinx.serialization`） | 项目已有依赖，可读性好，易于调试；`.pb` 扩展名为自定义格式（非 Protocol Buffers） |
| 自动存盘恢复 | 静默恢复（无弹窗），`init{}` 同步读取 | 弹窗打断用户习惯；同步读取避免画布先显示空白再跳变 |
| 新建白板确认 | 更多菜单 Popup 内联切换视图 | 无需额外弹窗层级，符合 §0 "不额外覆盖画布"原则 |

---

### 模块 7：图形工具
- `ShapeRenderer`：6 种图形（直线、箭头、矩形、圆形、三角形、菱形），支持实线 + 虚线预览
- `ShapePanel`：Popup，2×3 宫格，选中后关闭面板
- 画布手势：`onShapeBegin/Move/End`，拖拽实时虚线预览，抬手确认写入
- 图形写入 `UndoAction.AddStroke`（与笔画共用 Undo 机制）
- 工具栏图形按钮：Canvas 实时渲染当前选中图形轮廓（替代固定三角图标）
- Popup 定位已统一为 §0 规范（底边对齐工具栏顶边）

---

### 模块 10：扫码分享 ✅

**新增文件**
- `data/share/ShareRepository.kt`：@Singleton Hilt 组件，管理 Ktor HTTP 服务生命周期、页面 Bitmap、Token 生成、QR 生成、本地 IP 探测
- `data/share/PageBitmapRenderer.kt`：离屏渲染 DrawingPage → Bitmap，复用 StrokeRenderer / ShapeRenderer 逻辑
- `ui/share/SharePopup.kt`：§0 Popup，三态（加载中 / 错误 / 就绪），就绪态显示 200dp 二维码 + URL（可点击复制）+ 放大按钮
- `ui/share/FullscreenQROverlay.kt`：全屏覆盖层，320dp 二维码，点击背景或"关闭"按钮退出

**修改文件**
- `ui/canvas/CanvasViewModel.kt`：注入 `ShareRepository`（改为 `val` 暴露）；新增 `ShareUiState`、`openSharePanel / closeSharePanel / openFullscreenQR / closeFullscreenQR`
- `ui/toolbar/BottomToolbar.kt`：Share 按钮改为 Box 锚点，内嵌 `SharePopup`
- `ui/canvas/CanvasScreen.kt`：在顶层 Box 追加 `FullscreenQROverlay`

**关键实现细节**
- Ktor 3 `embeddedServer(CIO, port=8080)`，路由 `/board?token=` 返回 HTML，`/page/{i}.png?token=` 返回 PNG 字节
- Token：`SecureRandom` 8 字节 hex，16 字符；每次 `prepareShare` 重新生成，旧 Token 自动失效
- QR 生成：ZXing `QRCodeWriter`，Level Q 容错，白底深码，异步 `Dispatchers.Default`
- 成员扩展函数引用限制：`Application::configureShareRouting` 不合法，改为 lambda `{ configureShareRouting() }`

**与设计偏差**
- 实现为 §0 Popup（非设计中的 Bottom Sheet）
- 未实现导出格式选择（PNG 当前页 / 全部页 / PDF），仅实现 PNG 全部页

---

### 模块 11：LAN 协同 ✅

**新增文件**
- `data/collab/CollabRepository.kt`：WebSocket Host（Ktor CIO）+ Client（Ktor CIO）；NSD 服务注册/发现；`doConnect()` 封装带 CancellationException 安全的连接逻辑；`getLocalIp()` 探测本机 IPv4；`registerNetworkCallback()` 监听 HOST 网络断开
- `ui/collab/CollabPopup.kt`：§0 Popup，四态（NONE / CONNECTING / HOST / PARTICIPANT）；NSD 超时时自动展开手动 IP 输入区；HOST 视图展示房间码 + 本机 IP + 在线设备列表

**修改文件**
- `domain/model/CollabMessage.kt`：新增字段 `strokes / removedIds / pageIndex`；新增消息类型 `PAGE_STROKES_SYNC / ERASE_OP / PAGE_CLEAR`
- `ui/canvas/CanvasViewModel.kt`：新增 `CollabUiState`（含 `hostIp / nsdTimeout`）；新增 `startHosting / stopHosting / joinSession / joinSessionDirect / leaveSession`；新增广播方法 `broadcast / broadcastEraseOp / broadcastPageClear / broadcastUndo / broadcastRedo`；新增 `hostBroadcastPageSync()` HOST 权威同步；`applyRemoteMessage()` 处理全部协同消息类型
- `ui/toolbar/BottomToolbar.kt`：协同按钮角标 `lineHeight = 9.sp` 修正居中；传入 `isCollabActive` 至 `MoreMenuPopup`
- `ui/toolbar/MoreMenuPopup.kt`：新增 `isCollabActive` 参数；协同中"新建白板"和"打开白板"置灰并显示"协同中不可用"提示

**关键实现细节**
- 协议：JSON WebSocket，消息类型通过 `type` 字段区分，所有字段可空向后兼容
- NSD 超时 8 秒，超时后 UI 自动展开手动 IP 区并提示网络隔离原因
- `joinSessionDirect(host, roomCode)` 支持跨子网（L3 路由隔离场景）直连
- PAGE_ADD 消息携带 `pageIndex` 实现原子性页面添加+切换（避免两次消息竞态）
- ERASE_OP 接收端 `hadAny` 守卫：仅当 removedIds 中至少一条在本地存在时才追加替换段，消除并发擦除残影
- HOST 在应用任何擦除/清除操作后广播 `PAGE_STROKES_SYNC` 作为权威状态兜底
- 客户端断线检测：`sessionEndedNormally` + `joinSucceeded` 双标志区分主动离开与意外断线；HOST 侧通过 `ConnectivityManager.NetworkCallback` 监听网络切换
- 协同中禁用新建/打开白板（两者直接替换 `_pages` 不通知对端），保存白板不受影响

**与设计偏差**
- NSD 自动发现在 L3 路由隔离场景下不可用（mDNS 为 L2 广播），提供手动 IP 输入作为兜底
- 未实现 HOST 主持人锁定模式（仅 Host 可翻页），所有端均可自由翻页
- 未实现游标显示（不同设备实时光标彩圈），CURSOR_MOVE 消息已预留


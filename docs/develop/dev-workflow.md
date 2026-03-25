# PaintBoard 开发与测试验收流程

> 版本：v1.1 | 日期：2026-03-23

---

## 零、设计参考资料

开发过程中随时可查阅以下文档和效果图：

| 资料 | 路径 | 用途 |
|------|------|------|
| UI 效果图入口 | `docs/mockups/index.html` | 浏览器打开，查看所有页面视觉稿 |
| 主画布效果图 | `docs/mockups/01-main-canvas.html` | 工具栏布局、画布样式参考 |
| 工具栏状态 | `docs/mockups/02-toolbar-states.html` | 各按钮激活/默认/禁用状态参考 |
| Bottom Sheet 汇总 | `docs/mockups/03-bottom-sheets.html` | 所有弹出面板样式参考 |
| 协同状态 | `docs/mockups/04-collaboration-states.html` | 协同 UI 状态参考 |
| 错误状态 | `docs/mockups/05-error-states.html` | Toast/Banner/弹窗样式参考 |
| 设计 Token | `docs/designs/design-tokens.md` | 颜色、间距、字体精确数值 |
| 工具栏规范 | `docs/designs/toolbar-component.md` | 尺寸、间距、分组规范 |
| 画布交互规范 | `docs/designs/canvas-interaction.md` | 事件处理、笔迹渲染、橡皮逻辑 |
| 各功能 UX | `docs/designs/` 目录 | 各模块完整交互规范 |

---

## 一、总体节奏

```
[模块开始]
    │
    ▼
关键架构/接口确认（如有分歧先讨论）
    │
    ▼
Claude 编写代码（主对话）
    │
    ▼
你在 Android Studio 编译运行
    │
    ├── 编译失败 → 贴报错 → Claude 修复 → 重新编译
    │
    ├── 运行异常 → 描述现象 / 贴 logcat → Claude 修复
    │
    └── 功能 OK → 人工验收（对照验收清单）
                    │
                    ├── 验收通过 → 进入下一模块
                    │
                    └── 有问题 → 反馈 → Claude 修复 → 重新验收
```

---

## 二、模块开发顺序与验收标准

### 模块 1：项目脚手架
**设计参考：** `docs/designs/design-tokens.md`（颜色/主题 Token）

**交付内容：**
- Gradle 配置（compileSdk、依赖库版本）
- 包结构（feature 分层）
- Hilt 初始化
- Jetpack Navigation 骨架
- 主题（深色主题，颜色严格对应 design-tokens.md）
- 空的各主要 Screen 占位

**验收标准：**
- [ ] 编译通过，无报错
- [ ] App 能启动，进入空白画布页面
- [ ] 深色主题：画布背景 `#1E1E1E`，工具栏背景 `#2C2C2C`

---

### 模块 2：画布引擎
**设计参考：** `docs/designs/canvas-interaction.md`（笔迹渲染、插值、坐标系）

**交付内容：**
- `DrawingCanvas` Composable（手势捕获 + 渲染）
- Catmull-Rom 贝塞尔曲线插值（SMOOTH_FACTOR=0.35）
- 笔刷三档粗细（细 4dp / 中 8dp / 粗 16dp）
- ViewModel + 笔画数据模型（StrokeId 三段结构）
- 画布坐标系：原点左上角，Y 轴向下，单位 px

**验收标准：**
- [ ] 手指在画布上滑动，笔迹流畅无锯齿
- [ ] 三档粗细切换正常，视觉差异明显
- [ ] 笔迹颜色为默认黑色（`#212121`）
- [ ] 性能：60fps，无明显卡顿

---

### 模块 3：工具栏 UI
**设计参考：** `docs/designs/toolbar-component.md` + `docs/mockups/02-toolbar-states.html`

**交付内容：**
- 底部横排工具栏，高度 72dp，6个分区（A工具区/B粗细区/C颜色区/D撤销区/E协作区/F页面区）
- 各按钮三态：默认（图标 `#9E9E9E`）/ 激活（背景 56dp `#1565C0`，图标白色）/ 禁用（图标 `#616161`）
- 分组分隔线：高 32dp，颜色 `#3A3A3A`
- 触控热区最小 64×64dp
- 画笔/橡皮擦/图形工具切换（功能暂空，UI 先到位）

**验收标准：**
- [ ] 工具栏贴底显示，高度 72dp
- [ ] 各按钮点击后激活态视觉正确（蓝色背景圆角块）
- [ ] 对照 `02-toolbar-states.html` 效果图，视觉一致
- [ ] 在大屏和 PAD 两种分辨率下布局不变形

---

### 模块 4：颜色选择器
**设计参考：** `docs/designs/color-picker-ux.md` + `docs/mockups/03-bottom-sheets.html`（颜色选择器面板）

**交付内容：**
- 工具栏 4 个快捷色按钮（黑 `#212121` / 红 `#E53935` / 蓝 `#1E88E5` / 绿 `#43A047`）
- 🎨 调色盘入口按钮
- 颜色选择器 Bottom Sheet（顶部圆角 24dp）：
  - 4 个快速色高亮区
  - 20 色预设网格（4列×5行，每格 48dp）
  - 最近使用 5 色
  - HSV 自定义选色区

**验收标准：**
- [ ] 点击快捷色立即切换，笔迹颜色变化
- [ ] Bottom Sheet 弹出 300ms / 收起 250ms，动画流畅
- [ ] 选择预设色后，最近使用色自动更新
- [ ] HSV 自定义选色后颜色正确写入
- [ ] 对照 `03-bottom-sheets.html` 颜色面板，视觉一致

---

### 模块 5：橡皮擦
**设计参考：** `docs/designs/canvas-interaction.md`（橡皮擦逻辑）+ `docs/mockups/03-bottom-sheets.html`（橡皮擦面板）

**交付内容：**
- 橡皮擦工具激活（B 区工具切换）
- 大屏模式：`getTouchMajor()` 面积判断，默认阈值 80dp（60~120dp 可调）
- PAD 模式：`getToolType()` 判断，TOOL_TYPE_FINGER = 擦除，TOOL_TYPE_STYLUS = 书写
- 橡皮擦子面板 Bottom Sheet：三档圆形按钮（小 20dp / 中 40dp / 大 80dp）
- 滑动解锁清除当前页控件：90% 触发阈值，松手回弹，清页可撤销

**验收标准：**
- [ ] 橡皮擦能正确擦除笔迹
- [ ] 三档大小橡皮擦效果明显不同
- [ ] 滑动解锁清页：滑到 90% 才触发，中途松手回弹
- [ ] 清页操作可被撤销
- [ ] 大屏模式：掌心（面积 >80dp）自动擦除，指尖正常书写
- [ ] PAD 模式：手指擦除，触控笔书写，互不干扰

---

### 模块 6：撤销 / 重做
**交付内容：**
- Undo 栈实现（按页隔离）
- 工具栏撤销/重做按钮
- 按钮可用/禁用状态联动

**验收标准：**
- [ ] 每步笔画可逐步撤销
- [ ] 清页操作可撤销
- [ ] 无可撤销时按钮显示禁用态
- [ ] 撤销后再绘制，重做栈清空

---

### 模块 7：图形工具
**设计参考：** `docs/designs/canvas-interaction.md`（图形绘制流程）+ `docs/mockups/03-bottom-sheets.html`（图形子面板）

**交付内容：**
- 图形子面板 Bottom Sheet：2×3 宫格，6种图形（直线、箭头、矩形、圆形、三角形、菱形）
- 起点→拖拽预览→抬手确认流程
- ML Kit Digital Ink Recognition 图形自动矫正
- 矫正覆盖层动效（150ms emphasized 缓动）
- 矫正开关（Settings 项）
- 矫正写入独立 Undo 步骤（可单独撤销）

**验收标准：**
- [ ] 六种图形均可绘制
- [ ] 拖拽时有实时预览（虚线轮廓）
- [ ] 自动矫正开启时：歪斜图形被矫正，有 150ms 动效
- [ ] 矫正关闭时：原始形状保留
- [ ] 矫正结果可单独撤销（两步：先撤销矫正，再撤销图形）
- [ ] 对照 `03-bottom-sheets.html` 图形面板，视觉一致

---

### 模块 8：多页管理
**设计参考：** `docs/designs/page-management-ux.md` + `docs/mockups/03-bottom-sheets.html`（页面管理面板）

**交付内容：**
- 页面管理 Bottom Sheet：缩略图网格（160×90dp，16:9 比例）
- 新增页（底部 + 按钮）、删除页（二次确认 Bottom Sheet）
- ▲▼ 箭头按钮调整页面顺序（首页 ▲ 禁用，末页 ▼ 禁用）
- 工具栏 F 区：◀▶ 翻页按钮 + 页码（当前/总页数）+ ＋新增页
- 切换页面动画：±40dp 位移 + cross-fade，200ms

**验收标准：**
- [ ] 新增页正常，最多支持 20 页
- [ ] 删除页弹出二次确认，确认后删除
- [ ] ▲▼ 排序后翻页顺序一致
- [ ] 切换页面时画布内容正确切换，有过渡动画
- [ ] 对照 `03-bottom-sheets.html` 页面管理面板，视觉一致

---

### 模块 9：文件保存 / 打开
**设计参考：** `docs/designs/file-management-ux.md` + `docs/mockups/03-bottom-sheets.html`（更多菜单）+ `docs/mockups/05-error-states.html`（崩溃恢复弹窗）

**交付内容：**
- .pb 文件格式（JSON）读写，使用 SAF 文件选择器
- "更多"菜单 Bottom Sheet：保存白板 / 打开白板 / 新建白板
- 保存：命名 Bottom Sheet（默认名 `会议_YYYY-MM-DD_HH:mm`）
- 新建白板：三按钮确认弹窗（取消 / 不保存 / 保存），标注"不可撤销"
- 崩溃恢复：每 2 分钟静默写临时文件，重启后一次性 Bottom Sheet 提示（不可通过点击遮罩关闭）

**验收标准：**
- [ ] 保存后关闭 App，重新打开文件内容完整
- [ ] 新建白板弹出三按钮确认，选"不保存"则清空
- [ ] 模拟崩溃（强制关闭），重启后弹出恢复提示，用户必须主动选择
- [ ] .pb 文件可通过文件管理器找到
- [ ] 对照 `05-error-states.html` 崩溃恢复弹窗，视觉一致

---

### 模块 10：扫码分享
**设计参考：** `docs/designs/qr-share-ux.md` + `docs/mockups/03-bottom-sheets.html`（扫码分享面板）

**交付内容：**
- 大屏启动本地 HTTP Server（退后台 10 分钟后关闭）
- 生成二维码（320dp，Level Q 容错，含一次性 Token）
- 分享 Bottom Sheet：二维码 + PNG/PDF 选项卡 + 全屏展示入口
- 手机扫码 → 浏览器打开 → 下载 PNG/PDF
- Token 语义：换分享即失效（非扫一次失效）

**验收标准：**
- [ ] 同一 WiFi 下手机扫码能打开页面
- [ ] 图片/PDF 内容与画布一致
- [ ] 重新生成分享时旧 Token 失效
- [ ] 二维码在大屏上清晰可扫（Level Q 容错）
- [ ] 对照 `03-bottom-sheets.html` 扫码分享面板，视觉一致

---

### 模块 11：LAN 协同
**设计参考：** `docs/designs/collaboration-ux.md` + `docs/mockups/04-collaboration-states.html`

**交付内容：**
- 大屏作为 WebSocket Host，生成房间码 + 二维码
- PAD 扫码加入（超时 3s 提示）
- 实时笔画同步（归一化坐标，60Hz 广播）
- 协同状态 Banner（绿色/橙色/红色三态）+ 在线人数徽章
- 他人光标：彩圈 48dp，6色分配，2秒无操作淡出
- 主持人锁定：广播 lock 消息，PAD 显示橙色 Banner + 画布遮罩 + 工具栏禁用
- 断线重连：指数退避（1s/2s/4s/10s）

**验收标准：**
- [ ] PAD 扫码后 3 秒内加入协同
- [ ] 大屏书写，PAD 实时可见（延迟 <200ms）
- [ ] PAD 书写，大屏实时可见
- [ ] 他人光标彩圈正常显示，颜色各异
- [ ] 主持人锁定后，PAD 无法书写，显示橙色 Banner
- [ ] 断网后自动重连，内容一致无丢失
- [ ] 2 台设备同时书写，笔迹不干扰
- [ ] 对照 `04-collaboration-states.html`，四种状态视觉一致

---

## 三、每个模块的启动方式

每个模块开始时，Claude 会：
1. 简要说明本模块的实现思路（关键类/接口）
2. 如有架构决策需要确认，先讨论
3. 确认后开始写代码

---

## 四、问题反馈方式

| 问题类型 | 你提供的信息 | Claude 处理方式 |
|---------|------------|----------------|
| 编译报错 | 贴 Build Output 报错信息 | 直接修复 |
| 运行崩溃 | 贴 logcat crash 堆栈 | 定位 + 修复 |
| 功能不对 | 描述现象（必要时截图）| 复现分析 + 修复 |
| 性能卡顿 | 描述卡顿场景 | 分析瓶颈 + 优化 |

---

## 五、上下文管理

- 每完成 2~3 个模块后执行一次 `/compact`，避免上下文过长
- 重要的架构决策会写入代码注释，不依赖对话记忆
- 设计文档（`docs/designs/`）和效果图（`docs/mockups/`）始终可作为参考依据
- 每个模块验收时对照对应效果图，确保视觉还原度

---

## 六、开发环境

| 项目 | 配置 |
|------|------|
| 构建方式 | 命令行直接编译（无 Android Studio），已有 Java/JDK/NDK 环境 |
| compileSdk | 35 |
| minSdk | 26 |
| 调试设备 | 优先 PAD（ADB 连接），大屏备用 |
| 代码注释 | 中文 |

**常用编译命令：**
```bash
# 编译 debug APK
./gradlew assembleDebug

# 安装到已连接的 ADB 设备
./gradlew installDebug

# 查看已连接设备
adb devices

# 查看 logcat（过滤 PaintBoard tag）
adb logcat -s PaintBoard

# 清除构建缓存
./gradlew clean
```

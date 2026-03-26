package com.h3c.writeboard.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.res.painterResource
import com.h3c.writeboard.R
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Share
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.h3c.writeboard.ui.canvas.ShapeRenderer
import com.h3c.writeboard.domain.model.ActiveTool
import com.h3c.writeboard.domain.model.EraserSize
import com.h3c.writeboard.domain.model.StrokeWidth
import com.h3c.writeboard.ui.canvas.CanvasViewModel
import com.h3c.writeboard.ui.theme.ActiveIconBackground
import com.h3c.writeboard.ui.theme.ActiveIconDark
import com.h3c.writeboard.ui.theme.IconDefault
import com.h3c.writeboard.ui.theme.ToolbarBackground

/**
 * 底部横排工具栏
 *
 * 六分区布局：
 *   A [画笔|橡皮|图形] | B [细|中|粗] | C [色1|色2|色3|色4|调色盘] | D [撤销|重做] | E [协同|分享|更多] | F [◀|页码|▶|＋]
 */
@Composable
fun BottomToolbar(
    modifier: Modifier = Modifier,
    viewModel: CanvasViewModel
) {
    val toolState by viewModel.toolState.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val page by viewModel.page.collectAsState()
    val pages by viewModel.pages.collectAsState()
    val currentPageIndex by viewModel.currentPageIndex.collectAsState()
    val showColorPicker by viewModel.showColorPicker.collectAsState()
    val showEraserPanel by viewModel.showEraserPanel.collectAsState()
    val showShapePanel by viewModel.showShapePanel.collectAsState()
    val showPageManager by viewModel.showPageManager.collectAsState()
    val showSharePanel by viewModel.showSharePanel.collectAsState()
    val shareState by viewModel.shareState.collectAsState()
    val showCollabPanel by viewModel.showCollabPanel.collectAsState()
    val collabState by viewModel.collabState.collectAsState()
    var showMoreMenu by remember { mutableStateOf(false) }

    // 文件选择器：打开
    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.loadBoard(it, it.lastPathSegment ?: "白板") }
    }
    // 文件选择器：保存
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { viewModel.saveBoard(it, it.lastPathSegment ?: "白板") }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(ToolbarBackground)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // ===== A 区：工具选择 =====
        ToolButton(
            icon = Icons.Default.Edit,
            contentDescription = "画笔",
            isActive = toolState.activeTool == ActiveTool.PEN,
            onClick = { viewModel.setActiveTool(ActiveTool.PEN) }
        )
        // 橡皮擦：
        //   · 首次点击（未激活） → 仅切换到橡皮擦模式，不弹菜单
        //   · 再次点击（已激活） → 弹出清除页面 Popup
        val eraserActive = toolState.activeTool == ActiveTool.ERASER
        androidx.compose.foundation.layout.Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clickable {
                    if (eraserActive) {
                        viewModel.setShowEraserPanel(true)   // 已激活 → 打开清除面板
                    } else {
                        viewModel.setActiveTool(ActiveTool.ERASER)  // 首次点击 → 激活
                    }
                }
        ) {
            if (eraserActive) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(ActiveIconBackground)
                )
            }
            androidx.compose.material3.Icon(
                painter = painterResource(R.drawable.ic_eraser),
                contentDescription = "橡皮擦",
                tint = if (eraserActive) ActiveIconDark else IconDefault,
                modifier = Modifier.size(24.dp)
            )
            // 橡皮擦子面板 Popup（锚点为本 Box）
            if (showEraserPanel) {
                EraserPopup(
                    onClearPage = {
                        viewModel.clearPage()
                        viewModel.setActiveTool(ActiveTool.PEN)
                        viewModel.setShowEraserPanel(false)
                    },
                    onDismiss = { viewModel.setShowEraserPanel(false) }
                )
            }
        }
        // 图形工具：未激活时点击 → 激活 + 弹面板；已激活时点击 → 切换面板显示
        val shapeActive = toolState.activeTool == ActiveTool.SHAPE
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(64.dp).clickable {
                if (shapeActive) {
                    viewModel.setShowShapePanel(!showShapePanel)
                } else {
                    viewModel.setActiveTool(ActiveTool.SHAPE)
                    viewModel.setShowShapePanel(true)
                }
            }
        ) {
            if (shapeActive) {
                Box(
                    modifier = Modifier.size(40.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(ActiveIconBackground)
                )
            }
            // 动态渲染当前选中形状轮廓，替代固定三角图标
            Canvas(modifier = Modifier.size(24.dp)) {
                val colorInt = if (shapeActive) ActiveIconDark.toArgb() else IconDefault.toArgb()
                val paint = android.graphics.Paint().apply {
                    color = colorInt
                    strokeWidth = (2.2f * density).coerceAtLeast(2f)
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    isAntiAlias = true
                }
                drawIntoCanvas { c ->
                    val pad = 0.1f
                    ShapeRenderer.drawShape(
                        c.nativeCanvas, toolState.selectedShape,
                        pad, pad, 1f - pad, 1f - pad, size.width, size.height, paint
                    )
                }
            }
            if (showShapePanel) {
                ShapePanel(
                    selectedShape = toolState.selectedShape,
                    onShapeSelected = { viewModel.setSelectedShape(it) },
                    onDismiss = { viewModel.setShowShapePanel(false) }
                )
            }
        }

        ToolbarDivider()

        // ===== B 区：笔刷粗细 / 橡皮擦大小（根据当前工具上下文切换）=====
        if (toolState.activeTool == ActiveTool.ERASER) {
            EraserSizeToolButton(size = EraserSize.SMALL,  isActive = toolState.eraserSize == EraserSize.SMALL,  onClick = { viewModel.setEraserSize(EraserSize.SMALL) })
            EraserSizeToolButton(size = EraserSize.MEDIUM, isActive = toolState.eraserSize == EraserSize.MEDIUM, onClick = { viewModel.setEraserSize(EraserSize.MEDIUM) })
            EraserSizeToolButton(size = EraserSize.LARGE,  isActive = toolState.eraserSize == EraserSize.LARGE,  onClick = { viewModel.setEraserSize(EraserSize.LARGE) })
        } else {
            StrokeWidthButton(lineHeight = 2.dp,  isActive = toolState.strokeWidth == StrokeWidth.THIN,   onClick = { viewModel.setStrokeWidth(StrokeWidth.THIN) })
            StrokeWidthButton(lineHeight = 5.dp,  isActive = toolState.strokeWidth == StrokeWidth.MEDIUM, onClick = { viewModel.setStrokeWidth(StrokeWidth.MEDIUM) })
            StrokeWidthButton(lineHeight = 10.dp, isActive = toolState.strokeWidth == StrokeWidth.THICK,  onClick = { viewModel.setStrokeWidth(StrokeWidth.THICK) })
        }

        ToolbarDivider()

        // ===== C 区：颜色 =====
        toolState.quickColors.forEachIndexed { index, colorInt ->
            ColorButton(
                color = Color(colorInt),
                isSelected = toolState.currentColor == colorInt,
                onClick = { viewModel.setColor(colorInt) }
            )
        }
        // 调色盘入口：以按钮为锚点弹出颜色选择器 Popup
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clickable { viewModel.setShowColorPicker(true) }
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = "更多颜色",
                tint = IconDefault,
                modifier = Modifier.size(24.dp)
            )
            // 当前颜色小圆点（右下角常驻，实时反映当前书写色）
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(-8.dp, 8.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(toolState.currentColor))
                    .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
            )
            if (showColorPicker) {
                ColorPickerPanel(
                    toolState = toolState,
                    onColorSelected = { viewModel.setColor(it) },
                    onDismiss = { viewModel.setShowColorPicker(false) }
                )
            }
        }

        ToolbarDivider()

        // ===== D 区：撤销 / 重做 =====
        ToolButton(
            icon = Icons.AutoMirrored.Filled.Undo,
            contentDescription = "撤销",
            isEnabled = canUndo,
            onClick = { viewModel.undo() }
        )
        ToolButton(
            icon = Icons.AutoMirrored.Filled.Redo,
            contentDescription = "重做",
            isEnabled = canRedo,
            onClick = { viewModel.redo() }
        )

        ToolbarDivider()

        // ===== E 区：协同 / 分享 / 更多 =====
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clickable { viewModel.openCollabPanel() }
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.People,
                contentDescription = "局域网协同",
                tint = if (collabState.role != com.h3c.writeboard.ui.canvas.CanvasViewModel.CollabRole.NONE)
                    Color(0xFF90CAF9) else IconDefault,
                modifier = Modifier.size(24.dp)
            )
            // 在线人数徽章
            if (collabState.devices.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(16.dp)
                        .background(Color(0xFF43A047), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = collabState.devices.size.toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        lineHeight = 9.sp
                    )
                }
            }
            if (showCollabPanel) {
                com.h3c.writeboard.ui.collab.CollabPopup(
                    collabState = collabState,
                    onStartHosting = { viewModel.startHosting() },
                    onStopHosting = { viewModel.stopHosting() },
                    onJoin = { roomCode -> viewModel.joinSession(roomCode) },
                    onJoinDirect = { host, roomCode -> viewModel.joinSessionDirect(host, roomCode) },
                    onRejoin = { viewModel.rejoinLastSession() },
                    onLeave = { viewModel.leaveSession() },
                    onCancelJoin = { viewModel.leaveSession() },
                    onDismiss = { viewModel.closeCollabPanel() }
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clickable { viewModel.openSharePanel() }
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "扫码分享",
                tint = IconDefault,
                modifier = Modifier.size(24.dp)
            )
            if (showSharePanel) {
                com.h3c.writeboard.ui.share.SharePopup(
                    shareState = shareState,
                    shareRepository = viewModel.shareRepository,
                    onEnlargeQR = { viewModel.openFullscreenQR() },
                    onDismiss = { viewModel.closeSharePanel() }
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clickable { showMoreMenu = true }
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "更多",
                tint = IconDefault,
                modifier = Modifier.size(24.dp)
            )
            if (showMoreMenu) {
                MoreMenuPopup(
                    onNewBoard = { viewModel.newBoard() },
                    onOpenBoard = { openLauncher.launch(arrayOf("*/*")) },
                    onSaveBoard = { saveLauncher.launch("白板.pb") },
                    onDismiss = { showMoreMenu = false },
                    isCollabActive = collabState.role == CanvasViewModel.CollabRole.HOST
                        || collabState.role == CanvasViewModel.CollabRole.PARTICIPANT
                )
            }
        }

        ToolbarDivider()

        // ===== F 区：页面管理 =====
        ToolButton(
            icon = Icons.Default.ChevronLeft,
            contentDescription = "上一页",
            isEnabled = currentPageIndex > 0,
            onClick = { viewModel.switchPage(currentPageIndex - 1) }
        )
        // 页码显示（可点击打开页面管理 Popup）
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clickable { viewModel.setShowPageManager(!showPageManager) }
        ) {
            Text(
                text = "${currentPageIndex + 1}/${pages.size}",
                color = IconDefault,
                fontSize = 16.sp
            )
            if (showPageManager) {
                com.h3c.writeboard.ui.pages.PageManagerPopup(
                    pages = pages,
                    currentPageIndex = currentPageIndex,
                    onSwitchPage = { viewModel.switchPage(it); viewModel.setShowPageManager(false) },
                    onAddPage = { viewModel.addPage() },
                    onDeletePage = { viewModel.deletePage(it) },
                    onMovePageUp = { viewModel.movePageUp(it) },
                    onMovePageDown = { viewModel.movePageDown(it) },
                    onSetBackground = { idx, bg -> viewModel.setPageBackground(idx, bg) },
                    onDismiss = { viewModel.setShowPageManager(false) }
                )
            }
        }
        ToolButton(
            icon = Icons.Default.ChevronRight,
            contentDescription = "下一页",
            isEnabled = currentPageIndex < pages.size - 1,
            onClick = { viewModel.switchPage(currentPageIndex + 1) }
        )
        ToolButton(
            icon = Icons.Default.Add,
            contentDescription = "新增页",
            isEnabled = pages.size < CanvasViewModel.MAX_PAGES,
            onClick = { viewModel.addPage() }
        )
    }

}

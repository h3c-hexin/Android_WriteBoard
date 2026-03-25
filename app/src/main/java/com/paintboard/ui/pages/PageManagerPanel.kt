package com.paintboard.ui.pages

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.paintboard.domain.model.DrawingPage
import com.paintboard.domain.model.PageBackground
import com.paintboard.domain.model.StrokeTool
import com.paintboard.ui.canvas.ShapeRenderer
import com.paintboard.ui.canvas.StrokeRenderer
import com.paintboard.ui.theme.ActiveIconBackground
import com.paintboard.ui.theme.ActiveIconDark
import com.paintboard.ui.theme.BottomSheetBackground
import com.paintboard.ui.theme.CanvasBackground
import com.paintboard.ui.theme.ErrorRed
import com.paintboard.ui.theme.IconDefault
import com.paintboard.ui.theme.IconDisabled
import com.paintboard.ui.theme.PrimaryBlueDark
import com.paintboard.ui.theme.ToolbarBackground

/**
 * 页面管理 Popup
 *
 * 以页码按钮为锚点，弹出于按钮正上方（与橡皮擦/图形子面板一致）。
 * 内容：水平滚动的缩略图列表，支持切换、排序、删除、新增、背景类型切换。
 */
@Composable
fun PageManagerPopup(
    pages: List<DrawingPage>,
    currentPageIndex: Int,
    onSwitchPage: (Int) -> Unit,
    onAddPage: () -> Unit,
    onDeletePage: (Int) -> Unit,
    onMovePageUp: (Int) -> Unit,
    onMovePageDown: (Int) -> Unit,
    onSetBackground: (Int, PageBackground) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingDeleteIndex by remember { mutableStateOf(-1) }

    // 工具栏高度 72dp，用于把 Popup 底边对齐到工具栏顶边
    val density = LocalDensity.current
    val toolbarHeightPx = with(density) { 72.dp.roundToPx() }

    // 打开时将当前页滚动到列表中央
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        // 等待 LazyRow 完成首次布局（viewportSize > 0）
        snapshotFlow { listState.layoutInfo.viewportSize.width }
            .first { it > 0 }
            .also { viewportWidth ->
                val cardWidthPx  = with(density) { 160.dp.roundToPx() }
                // 负的 scrollOffset：让当前页卡片中心对齐视口中心
                val centerOffset = -((viewportWidth - cardWidthPx) / 2)
                listState.scrollToItem(
                    index = currentPageIndex.coerceIn(0, pages.size - 1),
                    scrollOffset = centerOffset
                )
            }
    }

    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                // Popup 底边 = 工具栏顶边（窗口底边 - 工具栏高度）
                val toolbarTop = windowSize.height - toolbarHeightPx
                val y = toolbarTop - popupContentSize.height
                // 横向：以锚点水平居中，但不超出屏幕
                val x = (anchorBounds.left + anchorBounds.width / 2 - popupContentSize.width / 2)
                    .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                return IntOffset(x, y.coerceAtLeast(0))
            }
        },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnClickOutside = true)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BottomSheetBackground,
            shadowElevation = 16.dp,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 648.dp)
                    .wrapContentWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                // 标题行
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "页面管理", color = Color.White, fontSize = 18.sp,
                        fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(32.dp))
                    Text(text = "${pages.size} 页", color = IconDefault, fontSize = 14.sp)
                }

                Spacer(Modifier.height(16.dp))

                // 缩略图横向滚动列表（打开时当前页居中）
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(pages) { index, page ->
                        PageCard(
                            page = page,
                            pageNumber = index + 1,
                            isSelected = index == currentPageIndex,
                            canMoveUp = index > 0,
                            canMoveDown = index < pages.size - 1,
                            canDelete = pages.size > 1,
                            isConfirmingDelete = index == pendingDeleteIndex,
                            onSelect = { onSwitchPage(index) },
                            onMoveUp = { onMovePageUp(index) },
                            onMoveDown = { onMovePageDown(index) },
                            onDelete = { pendingDeleteIndex = index },
                            onConfirmDelete = { onDeletePage(index); pendingDeleteIndex = -1 },
                            onCancelDelete = { pendingDeleteIndex = -1 },
                            onSetBackground = { bg -> onSetBackground(index, bg) }
                        )
                    }
                    item { AddPageCard(enabled = pages.size < 20, onClick = onAddPage) }
                }
            }
        }
    }

}

// ─── 内部子组件 ───────────────────────────────────────────────────────────────

@Composable
private fun PageCard(
    page: DrawingPage,
    pageNumber: Int,
    isSelected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    isConfirmingDelete: Boolean,
    onSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onSetBackground: (PageBackground) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 160.dp, height = 90.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) PrimaryBlueDark else Color(0xFF424242),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(enabled = !isConfirmingDelete, onClick = onSelect)
        ) {
            PageThumbnail(page = page, modifier = Modifier.matchParentSize())

            if (!isConfirmingDelete) {
                // 顶部右侧：左移 / 右移
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SmallIconButton(Icons.Default.KeyboardArrowLeft, canMoveUp, "上移", onClick = onMoveUp)
                    SmallIconButton(Icons.Default.KeyboardArrowRight, canMoveDown, "下移", onClick = onMoveDown)
                }

                // 右下：删除
                SmallIconButton(
                    icon = Icons.Default.Delete,
                    enabled = canDelete,
                    contentDescription = "删除",
                    tint = if (canDelete) ErrorRed else IconDisabled,
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                )
            } else {
                // 内嵌删除确认覆盖层
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("删除第 $pageNumber 页？", color = Color.White, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF3A3A3A))
                                    .clickable(onClick = onCancelDelete)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("取消", color = IconDefault, fontSize = 11.sp) }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x33EF9A9A))
                                    .clickable(onClick = onConfirmDelete)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("删除", color = ErrorRed, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }

        // 页码
        Text(
            text = "$pageNumber",
            color = if (isSelected) PrimaryBlueDark else IconDefault,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )

        // 背景类型切换
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PageBackground.entries.forEach { bg ->
                val active = page.background == bg
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (active) ActiveIconBackground else ToolbarBackground)
                        .border(1.dp,
                            if (active) PrimaryBlueDark else Color(0xFF424242),
                            RoundedCornerShape(4.dp))
                        .clickable { onSetBackground(bg) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (bg) {
                            PageBackground.BLANK -> "空白"
                            PageBackground.GRID  -> "网格"
                            PageBackground.LINES -> "横线"
                        },
                        color = if (active) ActiveIconDark else IconDefault,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AddPageCard(enabled: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 160.dp, height = 90.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ToolbarBackground)
                .border(1.dp,
                    if (enabled) Color(0xFF424242) else Color(0xFF2A2A2A),
                    RoundedCornerShape(8.dp))
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Add, "新增页",
                    tint = if (enabled) PrimaryBlueDark else IconDisabled,
                    modifier = Modifier.size(32.dp))
                Text("新增页",
                    color = if (enabled) PrimaryBlueDark else IconDisabled,
                    fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    contentDescription: String,
    tint: Color = if (enabled) Color(0xFFEEEEEE) else IconDisabled,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x73000000))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PageThumbnail(page: DrawingPage, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(CanvasBackground)) {
        val w = size.width; val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        when (page.background) {
            PageBackground.GRID -> {
                val step = w / 8f
                val p = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(80, 66, 66, 66)
                    strokeWidth = 0.5f; style = android.graphics.Paint.Style.STROKE
                }
                drawIntoCanvas { c ->
                    var x = step
                    while (x < w) { c.nativeCanvas.drawLine(x, 0f, x, h, p); x += step }
                    var y = step
                    while (y < h) { c.nativeCanvas.drawLine(0f, y, w, y, p); y += step }
                }
            }
            PageBackground.LINES -> {
                val step = h / 5f
                val p = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(80, 97, 97, 97)
                    strokeWidth = 0.5f; style = android.graphics.Paint.Style.STROKE
                }
                drawIntoCanvas { c ->
                    var y = step
                    while (y < h) { c.nativeCanvas.drawLine(0f, y, w, y, p); y += step }
                }
            }
            else -> Unit
        }

        if (page.strokes.isEmpty()) return@Canvas
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            page.strokes.forEach { stroke ->
                val paint = Paint().apply {
                    color = stroke.color
                    strokeWidth = (stroke.width * w).coerceAtLeast(1f)
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
                }
                if (stroke.tool == StrokeTool.SHAPE && stroke.shapeType != null && stroke.points.size >= 2) {
                    ShapeRenderer.drawShape(canvas, stroke.shapeType,
                        stroke.points[0].x, stroke.points[0].y,
                        stroke.points[1].x, stroke.points[1].y, w, h, paint)
                } else {
                    canvas.drawPath(StrokeRenderer.buildPath(stroke.points, w, h), paint)
                }
            }
        }
    }
}

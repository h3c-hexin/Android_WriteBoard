package com.h3c.writeboard.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.h3c.writeboard.ui.common.ToolbarPopup
import com.h3c.writeboard.ui.theme.IconDefault

/**
 * "更多"二级菜单 Popup
 *
 * 遵循 §0 二级子面板统一规范：底边对齐工具栏顶边，水平居中于锚点按钮。
 * 包含：新建白板 / 打开白板 / 保存白板
 *
 * 点击"新建白板"时切换为内联确认视图，避免误操作丢失内容。
 */
@Composable
fun MoreMenuPopup(
    onNewBoard: () -> Unit,
    onOpenBoard: () -> Unit,
    onSaveBoard: () -> Unit,
    onDismiss: () -> Unit,
    isCollabActive: Boolean = false
) {
    var confirmingNew by remember { mutableStateOf(false) }

    ToolbarPopup(onDismiss = onDismiss) {
        if (confirmingNew) {
                // 确认新建视图
                Column(
                    modifier = Modifier
                        .widthIn(min = 200.dp)
                        .width(IntrinsicSize.Max)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "新建将清空当前内容",
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.size(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { confirmingNew = false }) {
                            Text("取消", color = IconDefault)
                        }
                        TextButton(onClick = { onNewBoard(); onDismiss() }) {
                            Text("确认新建", color = Color(0xFFFF6B6B))
                        }
                    }
                }
            } else {
                // 正常菜单视图
                Column(
                    modifier = Modifier
                        .widthIn(min = 160.dp)
                        .width(IntrinsicSize.Max)
                        .padding(vertical = 8.dp)
                ) {
                    MenuItem(
                        icon = Icons.Default.Add,
                        label = "新建白板",
                        onClick = { confirmingNew = true },
                        enabled = !isCollabActive,
                        subtitle = if (isCollabActive) "协同中不可用" else null
                    )
                    HorizontalDivider(color = Color(0xFF3A3A3A), thickness = 0.5.dp)
                    MenuItem(
                        icon = Icons.Default.FolderOpen,
                        label = "打开白板",
                        onClick = { onOpenBoard(); onDismiss() },
                        enabled = !isCollabActive,
                        subtitle = if (isCollabActive) "协同中不可用" else null
                    )
                    MenuItem(
                        icon = Icons.Default.Save,
                        label = "保存白板",
                        onClick = { onSaveBoard(); onDismiss() }
                    )
                }
        }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    subtitle: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = if (subtitle != null) 10.dp else 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (enabled) IconDefault else IconDefault.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(14.dp))
            Text(label, color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
        }
        if (subtitle != null) {
            Text(
                subtitle,
                color = IconDefault.copy(alpha = 0.5f),
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 34.dp, top = 2.dp)
            )
        }
    }
}

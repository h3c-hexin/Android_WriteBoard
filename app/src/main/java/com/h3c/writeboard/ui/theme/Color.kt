package com.h3c.writeboard.ui.theme

import androidx.compose.ui.graphics.Color

// ===== 品牌主色 =====
val PrimaryBlue = Color(0xFF1976D2)
val PrimaryBlueDark = Color(0xFF90CAF9)    // 深色主题下的主色
val PrimaryBlueDarker = Color(0xFF1565C0)  // 激活按钮背景

// ===== 画布背景 =====
val CanvasBackground = Color(0xFF1E1E1E)   // 深色主题画布背景

// ===== 工具栏 =====
val ToolbarBackground = Color(0xFF2C2C2C)  // 工具栏背景
val ToolbarDivider = Color(0xFF3A3A3A)     // 分组分隔线

// ===== 图标状态 =====
val IconDefault = Color(0xFF9E9E9E)        // 默认（未激活）图标
val IconActive = Color(0xFFFFFFFF)         // 激活状态图标
val IconDisabled = Color(0xFF616161)       // 禁用状态图标

// ===== 激活背景 =====
val ActiveBackground = Color(0xFF1565C0)   // 工具激活背景色（保留兼容）
val ActiveIconBackground = Color(0xFF90CAF9)  // 工具激活背景（浅蓝，对照设计稿 icon-inner）
val ActiveIconDark = Color(0xFF003C8F)        // 激活状态图标色（深蓝，浅蓝背景上）

// ===== Bottom Sheet =====
val BottomSheetBackground = Color(0xFF2D2D2D)
val BottomSheetHandle = Color(0xFF5C5C5C)  // 拖拽条

// ===== 语义颜色 =====
val ErrorRed = Color(0xFFCF6679)
val WarningAmber = Color(0xFFFFB74D)
val SuccessGreen = Color(0xFF81C784)
val InfoBlue = Color(0xFF64B5F6)

// ===== 工具栏 4 个快捷色 =====
val QuickColorBlack = Color(0xFF212121)
val QuickColorRed = Color(0xFFE53935)
val QuickColorBlue = Color(0xFF1E88E5)
val QuickColorGreen = Color(0xFF43A047)

// ===== 20 种预设颜色（5×4 宫格）=====
val PresetColors = listOf(
    // 第1行：深色系
    Color(0xFF212121), Color(0xFF37474F), Color(0xFF4E342E), Color(0xFF4A148C),
    // 第2行：暖色系
    Color(0xFFE53935), Color(0xFFF4511E), Color(0xFFF57C00), Color(0xFFFDD835),
    // 第3行：冷色系
    Color(0xFF43A047), Color(0xFF00897B), Color(0xFF1E88E5), Color(0xFF3949AB),
    // 第4行：亮色系
    Color(0xFFE91E63), Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFF039BE5),
    // 第5行：浅色系
    Color(0xFFFFFFFF), Color(0xFFEEEEEE), Color(0xFFBCAAA4), Color(0xFFB0BEC5)
)

// ===== 协同设备标识颜色（最多 6 台设备）=====
val CollabDeviceColors = listOf(
    Color(0xFF4CAF50), // 绿
    Color(0xFF2196F3), // 蓝
    Color(0xFFF44336), // 红
    Color(0xFFFF9800), // 橙
    Color(0xFF9C27B0), // 紫
    Color(0xFF00BCD4), // 青
)

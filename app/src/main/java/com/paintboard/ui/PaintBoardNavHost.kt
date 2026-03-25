package com.paintboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.paintboard.ui.canvas.CanvasScreen

/**
 * 导航图
 * 当前只有一个页面：画布页（直接进入，无首页）
 */
@Composable
fun PaintBoardNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "canvas",
        modifier = modifier
    ) {
        composable("canvas") {
            CanvasScreen()
        }
    }
}

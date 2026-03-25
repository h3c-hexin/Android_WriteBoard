package com.paintboard.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.paintboard.ui.theme.PaintBoardTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 唯一的 Activity，承载整个 Compose 导航栈
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaintBoardTheme {
                PaintBoardNavHost(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

package com.paintboard.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application 入口，启用 Hilt 依赖注入
 */
@HiltAndroidApp
class PaintBoardApplication : Application()

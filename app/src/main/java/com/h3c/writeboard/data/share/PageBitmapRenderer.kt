package com.h3c.writeboard.data.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.h3c.writeboard.domain.model.DrawingPage
import com.h3c.writeboard.domain.model.PageBackground
import com.h3c.writeboard.domain.model.StrokeTool
import com.h3c.writeboard.ui.canvas.ShapeRenderer
import com.h3c.writeboard.ui.canvas.StrokeRenderer

/**
 * 将 DrawingPage 离屏渲染为 Bitmap（复用 DrawingCanvas 的渲染逻辑）
 */
object PageBitmapRenderer {

    fun render(page: DrawingPage, widthPx: Int, heightPx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val w = widthPx.toFloat()
        val h = heightPx.toFloat()

        // 画布背景色
        canvas.drawColor(Color.parseColor("#1E1E1E"))

        // 页面背景图案
        when (page.background) {
            PageBackground.GRID -> {
                val step = w / 25f
                val p = Paint().apply {
                    color = Color.argb(60, 66, 66, 66)
                    strokeWidth = 1f
                    style = Paint.Style.STROKE
                }
                var x = step
                while (x < w) { canvas.drawLine(x, 0f, x, h, p); x += step }
                var y = step
                while (y < h) { canvas.drawLine(0f, y, w, y, p); y += step }
            }
            PageBackground.LINES -> {
                val step = h / 18f
                val p = Paint().apply {
                    color = Color.argb(60, 97, 97, 97)
                    strokeWidth = 1f
                    style = Paint.Style.STROKE
                }
                var y = step
                while (y < h) { canvas.drawLine(0f, y, w, y, p); y += step }
            }
            PageBackground.BLANK -> Unit
        }

        // 笔画与图形
        page.strokes.forEach { stroke ->
            val paint = buildPaint(stroke.color, stroke.width * w)
            if (stroke.tool == StrokeTool.SHAPE && stroke.shapeType != null && stroke.points.size >= 2) {
                ShapeRenderer.drawShape(
                    canvas, stroke.shapeType,
                    stroke.points[0].x, stroke.points[0].y,
                    stroke.points[1].x, stroke.points[1].y,
                    w, h, paint
                )
            } else {
                canvas.drawPath(StrokeRenderer.buildPath(stroke.points, w, h), paint)
            }
        }

        return bmp
    }

    private fun buildPaint(color: Int, widthPx: Float) = Paint().apply {
        this.color = color
        strokeWidth = widthPx.coerceAtLeast(3f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
}

package com.h3c.writeboard.domain.model

import kotlinx.serialization.Serializable

/**
 * 单张白板页
 */
@Serializable
data class DrawingPage(
    val id: String,
    val strokes: List<Stroke> = emptyList(),
    val background: PageBackground = PageBackground.BLANK
)

@Serializable
enum class PageBackground { BLANK, GRID, LINES }

package com.h3c.writeboard.domain.model

/**
 * Undo/Redo 栈中的操作单元
 */
sealed class UndoAction {
    /** 添加一笔笔画（书写） */
    data class AddStroke(val stroke: Stroke) : UndoAction()

    /** 擦除了若干笔画（整条删除，无分割） */
    data class EraseStrokes(val strokes: List<Stroke>) : UndoAction()

    /**
     * 橡皮擦分割操作：部分擦除笔画
     * removedOriginals：本次手势中被触碰的原始笔画
     * addedSplits：橡皮擦分割后保留的笔画片段（可能是 0 或多条）
     *
     * Undo：从页面移除 addedSplits，恢复 removedOriginals
     * Redo：从页面移除 removedOriginals，加入 addedSplits
     */
    data class SplitEraseStrokes(
        val removedOriginals: List<Stroke>,
        val addedSplits: List<Stroke>
    ) : UndoAction()

    /** 清除当前页全部内容 */
    data class ClearPage(val strokes: List<Stroke>) : UndoAction()
}

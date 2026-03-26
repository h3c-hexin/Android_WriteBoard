package com.h3c.writeboard.ui.toolbar

import androidx.compose.runtime.Composable
import com.h3c.writeboard.ui.common.ToolbarPopup

@Composable
fun EraserPopup(
    onClearPage: () -> Unit,
    onDismiss: () -> Unit
) {
    ToolbarPopup(onDismiss = onDismiss) {
        EraserPanelContent(
            onClearPage = onClearPage,
            onDismiss = onDismiss
        )
    }
}

package com.andcodedit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A horizontal resizable pane splitter.
 * Left pane has fixed/resizable width, right takes remaining space.
 * Drag the handle to resize.
 */
@Composable
fun ResizableHorizontalPane(
    modifier: Modifier = Modifier,
    initialLeftWidth: Dp = 280.dp,
    minLeftWidth: Dp = 180.dp,
    maxLeftWidth: Dp = 600.dp,
    leftContent: @Composable (Modifier) -> Unit,
    rightContent: @Composable (Modifier) -> Unit,
    onLeftWidthChange: ((Dp) -> Unit)? = null
) {
    val density = LocalDensity.current
    var leftWidth by remember { mutableStateOf(initialLeftWidth) }
    var leftWidthPx by remember { mutableStateOf(with(density) { initialLeftWidth.toPx() }) }

    // Sync if external change needed, but internal state for self-contained usage
    LaunchedEffect(leftWidth) {
        onLeftWidthChange?.invoke(leftWidth)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val availableWidth = constraints.maxWidth.toFloat()
        val minPx = with(density) { minLeftWidth.toPx() }
        val maxPx = (availableWidth * 0.65f).coerceAtLeast(minPx) // safety max 65%

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left pane
            Box(
                modifier = Modifier
                    .width(with(density) { leftWidthPx.toDp() })
                    .fillMaxHeight()
            ) {
                leftContent(Modifier.fillMaxSize())
            }

            // Resize handle
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { /* optional haptic */ },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val newPx = (leftWidthPx + dragAmount.x).coerceIn(minPx, maxPx)
                                leftWidthPx = newPx
                                leftWidth = with(density) { newPx.toDp() }
                            }
                        )
                    }
            ) {
                // Visual grip dots (optional polish)
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    RoundedCornerShape(50)
                                )
                        )
                    }
                }
            }

            // Right pane - takes remaining space
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                rightContent(Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * Vertical resizable pane splitter (for bottom panels like Terminal).
 * Top content takes remaining space, bottom has controlled height.
 */
@Composable
fun ResizableVerticalPane(
    modifier: Modifier = Modifier,
    initialBottomHeight: Dp = 220.dp,
    minBottomHeight: Dp = 120.dp,
    maxBottomHeight: Dp = 500.dp,
    topContent: @Composable (Modifier) -> Unit,
    bottomContent: @Composable (Modifier) -> Unit,
    onBottomHeightChange: ((Dp) -> Unit)? = null
) {
    val density = LocalDensity.current
    var bottomHeight by remember { mutableStateOf(initialBottomHeight) }
    var bottomHeightPx by remember { mutableStateOf(with(density) { initialBottomHeight.toPx() }) }

    LaunchedEffect(bottomHeight) {
        onBottomHeightChange?.invoke(bottomHeight)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val availableHeight = constraints.maxHeight.toFloat()
        val minPx = with(density) { minBottomHeight.toPx() }
        val maxPx = (availableHeight * 0.7f).coerceAtLeast(minPx)

        Column(modifier = Modifier.fillMaxSize()) {
            // Top content (e.g. Editor) - flexible
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                topContent(Modifier.fillMaxSize())
            }

            // Resize handle (horizontal bar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val newPx = (bottomHeightPx - dragAmount.y).coerceIn(minPx, maxPx)
                                bottomHeightPx = newPx
                                bottomHeight = with(density) { newPx.toDp() }
                            }
                        )
                    }
            ) {
                // Grip line
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(40.dp)
                        .height(3.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(50)
                        )
                )
            }

            // Bottom pane (e.g. Terminal)
            Box(
                modifier = Modifier
                    .height(with(density) { bottomHeightPx.toDp() })
                    .fillMaxWidth()
            ) {
                bottomContent(Modifier.fillMaxSize())
            }
        }
    }
}

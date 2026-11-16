package com.aistudio.kineticmedia.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DraggableDownloadButton(
    candidateCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val buttonDataStore = remember { ButtonDataStore(context) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val savedX by buttonDataStore.buttonX.collectAsState(initial = -1f)
    val savedY by buttonDataStore.buttonY.collectAsState(initial = -1f)

    var isDragging by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val maxWidthPx = constraints.maxWidth.toFloat() - with(density) { 72.dp.toPx() }
        val maxHeightPx = constraints.maxHeight.toFloat() - with(density) { 140.dp.toPx() }

        val initX = if (savedX < 0f) maxWidthPx - with(density) { 16.dp.toPx() } else savedX.coerceIn(0f, maxWidthPx)
        val initY = if (savedY < 0f) maxHeightPx - with(density) { 16.dp.toPx() } else savedY.coerceIn(0f, maxHeightPx)

        var currentX by remember(initX) { mutableStateOf(initX) }
        var currentY by remember(initY) { mutableStateOf(initY) }

        LaunchedEffect(savedX, savedY) {
            if (savedX >= 0f) currentX = savedX.coerceIn(0f, maxWidthPx)
            if (savedY >= 0f) currentY = savedY.coerceIn(0f, maxHeightPx)
        }

        val buttonColor = if (candidateCount > 0) Color(0xFFFFCC00) else Color(0xFF333333)
        val iconTint = if (candidateCount > 0) Color.Black else Color.White

        Box(
            modifier = Modifier
                .offset { IntOffset(currentX.roundToInt(), currentY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            scope.launch {
                                buttonDataStore.savePosition(currentX, currentY)
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            currentX = (currentX + dragAmount.x).coerceIn(0f, maxWidthPx)
                            currentY = (currentY + dragAmount.y).coerceIn(0f, maxHeightPx)
                        }
                    )
                }
        ) {
            FloatingActionButton(
                onClick = {
                    if (!isDragging) {
                        onClick()
                    }
                },
                containerColor = buttonColor,
                contentColor = iconTint,
                shape = CircleShape,
                modifier = Modifier.size(60.dp)
            ) {
                BadgedBox(
                    badge = {
                        if (candidateCount > 0) {
                            Badge(
                                containerColor = Color.Red,
                                contentColor = Color.White
                            ) {
                                Text(text = if (candidateCount > 99) "99+" else candidateCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Download Video",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

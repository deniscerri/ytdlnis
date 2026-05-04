package com.deniscerri.ytdl.ui.downloadcard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CropOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private enum class DragMode {
        NONE, MOVE,
        RESIZE_TL, RESIZE_TR, RESIZE_BL, RESIZE_BR,
        RESIZE_TOP, RESIZE_BOTTOM, RESIZE_LEFT, RESIZE_RIGHT
    }

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val cornerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private var cropLeft = 0f
    private var cropTop = 0f
    private var cropRight = 0f
    private var cropBottom = 0f

    private var dragMode = DragMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val handleRadius = 48f
    private val minCropSize = 20f

    var aspectRatio: Float? = null

    var onCropChanged: (() -> Unit)? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    val cropLeftValue: Float get() = cropLeft
    val cropTopValue: Float get() = cropTop
    val cropRightValue: Float get() = cropRight
    val cropBottomValue: Float get() = cropBottom

    fun setCrop(left: Float, top: Float, right: Float, bottom: Float) {
        cropLeft = max(0f, min(left, width.toFloat()))
        cropTop = max(0f, min(top, height.toFloat()))
        cropRight = max(0f, min(right, width.toFloat()))
        cropBottom = max(0f, min(bottom, height.toFloat()))
        invalidate()
    }

    private fun ensureMinimumSize() {
        val w = cropRight - cropLeft
        val h = cropBottom - cropTop
        if (w < minCropSize && dragMode != DragMode.NONE) {
            val midX = (cropLeft + cropRight) / 2f
            cropLeft = midX - minCropSize / 2f
            cropRight = midX + minCropSize / 2f
        }
        if (h < minCropSize && dragMode != DragMode.NONE) {
            val midY = (cropTop + cropBottom) / 2f
            cropTop = midY - minCropSize / 2f
            cropBottom = midY + minCropSize / 2f
        }
        clampToBounds()
    }

    private fun clampToBounds() {
        val w = width.toFloat()
        val h = height.toFloat()
        val ar = aspectRatio
        if (cropLeft < 0) {
            val shift = -cropLeft
            cropRight += shift
            cropLeft = 0f
        }
        if (cropTop < 0) {
            val shift = -cropTop
            cropBottom += shift
            cropTop = 0f
        }
        if (cropRight > w) {
            val excess = cropRight - w
            if (ar != null) {
                val newW = cropRight - cropLeft - excess
                val newH = newW / ar
                cropBottom = cropTop + newH
            }
            cropLeft -= excess
            cropRight = w
        }
        if (cropBottom > h) {
            val excess = cropBottom - h
            if (ar != null) {
                val newH = cropBottom - cropTop - excess
                val newW = newH * ar
                cropRight = cropLeft + newW
            }
            cropTop -= excess
            cropBottom = h
        }
        cropLeft = max(0f, cropLeft)
        cropTop = max(0f, cropTop)
        cropRight = min(w, cropRight)
        cropBottom = min(h, cropBottom)
    }

    private fun detectDragMode(x: Float, y: Float): DragMode {
        val leftDist = abs(x - cropLeft)
        val rightDist = abs(x - cropRight)
        val topDist = abs(y - cropTop)
        val bottomDist = abs(y - cropBottom)

        val nearLeft = leftDist <= handleRadius
        val nearRight = rightDist <= handleRadius
        val nearTop = topDist <= handleRadius
        val nearBottom = bottomDist <= handleRadius

        val insideX = x > cropLeft && x < cropRight
        val insideY = y > cropTop && y < cropBottom

        if (nearTop && nearLeft) return DragMode.RESIZE_TL
        if (nearTop && nearRight) return DragMode.RESIZE_TR
        if (nearBottom && nearLeft) return DragMode.RESIZE_BL
        if (nearBottom && nearRight) return DragMode.RESIZE_BR
        if (nearTop && insideX) return DragMode.RESIZE_TOP
        if (nearBottom && insideX) return DragMode.RESIZE_BOTTOM
        if (nearLeft && insideY) return DragMode.RESIZE_LEFT
        if (nearRight && insideY) return DragMode.RESIZE_RIGHT
        if (insideX && insideY) return DragMode.MOVE
        return DragMode.NONE
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragMode = detectDragMode(x, y)
                if (dragMode != DragMode.NONE) {
                    lastTouchX = x
                    lastTouchY = y
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                when (dragMode) {
                    DragMode.MOVE -> {
                        val cropW = cropRight - cropLeft
                        val cropH = cropBottom - cropTop
                        var newLeft = cropLeft + dx
                        var newTop = cropTop + dy
                        newLeft = max(0f, min(newLeft, width.toFloat() - cropW))
                        newTop = max(0f, min(newTop, height.toFloat() - cropH))
                        cropLeft = newLeft
                        cropTop = newTop
                        cropRight = newLeft + cropW
                        cropBottom = newTop + cropH
                    }
                    DragMode.RESIZE_TL -> resizeCorner(x, y, cropRight, cropBottom)
                    DragMode.RESIZE_TR -> resizeCorner(x, y, cropLeft, cropBottom)
                    DragMode.RESIZE_BL -> resizeCorner(x, y, cropRight, cropTop)
                    DragMode.RESIZE_BR -> resizeCorner(x, y, cropLeft, cropTop)
                    DragMode.RESIZE_TOP -> resizeEdgeVertical(x, y, cropBottom, true)
                    DragMode.RESIZE_BOTTOM -> resizeEdgeVertical(x, y, cropTop, false)
                    DragMode.RESIZE_LEFT -> resizeEdgeHorizontal(x, y, cropRight, true)
                    DragMode.RESIZE_RIGHT -> resizeEdgeHorizontal(x, y, cropLeft, false)
                    DragMode.NONE -> {}
                }

                if (dragMode != DragMode.NONE) {
                    if (cropLeft > cropRight) {
                        val tmp = cropLeft; cropLeft = cropRight; cropRight = tmp
                    }
                    if (cropTop > cropBottom) {
                        val tmp = cropTop; cropTop = cropBottom; cropBottom = tmp
                    }
                    ensureMinimumSize()
                    clampToBounds()
                    lastTouchX = x
                    lastTouchY = y
                    onCropChanged?.invoke()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragMode = DragMode.NONE
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun resizeCorner(tx: Float, ty: Float, anchorX: Float, anchorY: Float) {
        val ar = aspectRatio
        if (ar == null) {
            cropLeft = min(tx, anchorX)
            cropRight = max(tx, anchorX)
            cropTop = min(ty, anchorY)
            cropBottom = max(ty, anchorY)
            return
        }
        val dx = tx - anchorX
        val dy = ty - anchorY
        val maxW = if (dx < 0) anchorX else width.toFloat() - anchorX
        val maxH = if (dy < 0) anchorY else height.toFloat() - anchorY
        val maxByRatioW = minOf(maxW, maxH * ar)
        val maxByRatioH = minOf(maxH, maxW / ar)
        val newW: Float
        val newH: Float
        if (abs(dx) / max(abs(dy), 1f) > ar) {
            newW = minOf(abs(dx), maxByRatioW)
            newH = newW / ar
        } else {
            newH = minOf(abs(dy), maxByRatioH)
            newW = newH * ar
        }
        cropLeft = if (dx < 0) anchorX - newW else anchorX
        cropRight = if (dx < 0) anchorX else anchorX + newW
        cropTop = if (dy < 0) anchorY - newH else anchorY
        cropBottom = if (dy < 0) anchorY else anchorY + newH
    }

    private fun resizeEdgeVertical(tx: Float, ty: Float, anchorY: Float, isTop: Boolean) {
        val ar = aspectRatio
        if (ar == null) {
            cropTop = min(ty, anchorY)
            cropBottom = max(ty, anchorY)
            return
        }
        val dy = abs(ty - anchorY)
        val maxH = if (isTop) anchorY else height.toFloat() - anchorY
        val newH = minOf(dy, maxH)
        val newW = newH * ar
        val centerX = (cropLeft + cropRight) / 2f
        cropLeft = centerX - newW / 2f
        cropRight = centerX + newW / 2f
        if (isTop) {
            cropTop = anchorY - newH
            cropBottom = anchorY
        } else {
            cropTop = anchorY
            cropBottom = anchorY + newH
        }
    }

    private fun resizeEdgeHorizontal(tx: Float, ty: Float, anchorX: Float, isLeft: Boolean) {
        val ar = aspectRatio
        if (ar == null) {
            cropLeft = min(tx, anchorX)
            cropRight = max(tx, anchorX)
            return
        }
        val dx = abs(tx - anchorX)
        val maxW = if (isLeft) anchorX else width.toFloat() - anchorX
        val newW = minOf(dx, maxW)
        val newH = newW / ar
        val centerY = (cropTop + cropBottom) / 2f
        cropTop = centerY - newH / 2f
        cropBottom = centerY + newH / 2f
        if (isLeft) {
            cropLeft = anchorX - newW
            cropRight = anchorX
        } else {
            cropLeft = anchorX
            cropRight = anchorX + newW
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        if (w <= 0 || h <= 0) return

        // Initialize crop rect to full view if not set
        if (cropRight == 0f && cropBottom == 0f) {
            val margin = min(w, h) * 0.05f
            cropLeft = margin
            cropTop = margin
            cropRight = w - margin
            cropBottom = h - margin
        }

        // Dark overlay outside the crop rectangle
        if (cropTop > 0) {
            canvas.drawRect(0f, 0f, w, cropTop, overlayPaint)
        }
        if (cropBottom < h) {
            canvas.drawRect(0f, cropBottom, w, h, overlayPaint)
        }
        if (cropLeft > 0) {
            canvas.drawRect(0f, cropTop, cropLeft, cropBottom, overlayPaint)
        }
        if (cropRight < w) {
            canvas.drawRect(cropRight, cropTop, w, cropBottom, overlayPaint)
        }

        // Grid lines
        val thirdW = (cropRight - cropLeft) / 3f
        val thirdH = (cropBottom - cropTop) / 3f
        for (i in 1..2) {
            val gx = cropLeft + thirdW * i
            val gy = cropTop + thirdH * i
            canvas.drawLine(gx, cropTop, gx, cropBottom, gridPaint)
            canvas.drawLine(cropLeft, gy, cropRight, gy, gridPaint)
        }

        // Border
        val cropRect = RectF(cropLeft, cropTop, cropRight, cropBottom)
        canvas.drawRect(cropRect, borderPaint)

        // Corner handles
        val cornerSize = handleRadius * 0.6f
        val corners = arrayOf(
            RectF(cropLeft - cornerSize / 2, cropTop - cornerSize / 2,
                  cropLeft + cornerSize / 2, cropTop + cornerSize / 2),
            RectF(cropRight - cornerSize / 2, cropTop - cornerSize / 2,
                  cropRight + cornerSize / 2, cropTop + cornerSize / 2),
            RectF(cropLeft - cornerSize / 2, cropBottom - cornerSize / 2,
                  cropLeft + cornerSize / 2, cropBottom + cornerSize / 2),
            RectF(cropRight - cornerSize / 2, cropBottom - cornerSize / 2,
                  cropRight + cornerSize / 2, cropBottom + cornerSize / 2)
        )
        for (corner in corners) {
            canvas.drawRect(corner, cornerFillPaint)
            canvas.drawRect(corner, borderPaint)
        }

        // Edge handles
        val edgeSize = handleRadius * 0.4f
        val midX = (cropLeft + cropRight) / 2f
        val midY = (cropTop + cropBottom) / 2f
        val edgeRects = arrayOf(
            RectF(midX - edgeSize, cropTop - edgeSize / 2, midX + edgeSize, cropTop + edgeSize / 2),
            RectF(midX - edgeSize, cropBottom - edgeSize / 2, midX + edgeSize, cropBottom + edgeSize / 2),
            RectF(cropLeft - edgeSize / 2, midY - edgeSize, cropLeft + edgeSize / 2, midY + edgeSize),
            RectF(cropRight - edgeSize / 2, midY - edgeSize, cropRight + edgeSize / 2, midY + edgeSize)
        )
        for (edge in edgeRects) {
            canvas.drawRect(edge, cornerFillPaint)
            canvas.drawRect(edge, borderPaint)
        }
    }
}

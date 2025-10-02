package com.magicitengineer.digitaltarotandroidapp.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class DetectionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode { RECT, CIRCLE }

    var mode: Mode = Mode.RECT
    var message: String? = null
    var framePaddingRatio: Float = 0.08f // 8% padding from edges

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 0, 255, 0)
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val padX = w * framePaddingRatio
        val padY = h * framePaddingRatio

        if (mode == Mode.RECT) {
            val rectPath = Path().apply {
                moveTo(padX, padY)
                lineTo(w - padX, padY)
                lineTo(w - padX, h - padY)
                lineTo(padX, h - padY)
                close()
            }
            canvas.drawPath(rectPath, framePaint)
        } else {
            val cx = w / 2f
            val cy = h / 42f * 21f // slightly above center for UI affordance
            val r = (w.coerceAtMost(h)) * (0.5f - framePaddingRatio)
            canvas.drawCircle(cx, cy, r, framePaint)
        }

        message?.let {
            canvas.drawText(it, padX, h - padY, textPaint)
        }
    }
}


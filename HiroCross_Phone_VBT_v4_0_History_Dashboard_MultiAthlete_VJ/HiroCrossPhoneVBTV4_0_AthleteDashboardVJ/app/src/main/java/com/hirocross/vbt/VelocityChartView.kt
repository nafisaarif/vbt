package com.hirocross.vbt

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max

class VelocityChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val values = ArrayDeque<Float>()

    private var targetEnabled = false
    private var targetMinimum = 0.75f
    private var targetMaximum = 1.00f

    private val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.green)
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val belowTargetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent)
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val targetLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.muted)
        strokeWidth = 2f
        style = Paint.Style.STROKE
        alpha = 150
    }

    fun setTargetVelocity(enabled: Boolean, minimum: Float, maximum: Float) {
        targetEnabled = enabled
        targetMinimum = minimum.coerceAtLeast(0f)
        targetMaximum = maximum.coerceAtLeast(targetMinimum)
        invalidate()
    }

    fun setValues(data: List<Float>) {
        values.clear()
        data.forEach { values.addLast(it) }
        invalidate()
    }

    fun addValue(value: Float) {
        values.addLast(value.coerceAtLeast(0f))
        if (values.size > 180) values.removeFirst()
        invalidate()
    }

    fun clear() {
        values.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.size < 2) return

        val data = values.toList()
        val scaleMaximum = max(
            1f,
            max(
                data.maxOrNull() ?: 1f,
                if (targetEnabled) targetMaximum * 1.15f else 1f
            )
        )
        val chartTop = 12f
        val chartBottom = height - 12f
        val usableHeight = chartBottom - chartTop
        val step = width.toFloat() / (data.size - 1)

        fun yFor(value: Float): Float =
            chartBottom - (value / scaleMaximum) * usableHeight

        if (targetEnabled) {
            // The minimum line is the actual warning boundary.
            val minimumY = yFor(targetMinimum)
            canvas.drawLine(0f, minimumY, width.toFloat(), minimumY, targetLinePaint)

            // Maximum is displayed only as a reference. Exceeding it is not penalized.
            val maximumY = yFor(targetMaximum)
            canvas.drawLine(0f, maximumY, width.toFloat(), maximumY, targetLinePaint)
        }

        for (index in 1 until data.size) {
            val previous = data[index - 1]
            val current = data[index]
            val x1 = (index - 1) * step
            val x2 = index * step
            val y1 = yFor(previous)
            val y2 = yFor(current)

            // Only velocity below the minimum target becomes red.
            // Velocity within target or above the maximum remains normal.
            val segmentPaint =
                if (targetEnabled && current > 0f && current < targetMinimum)
                    belowTargetPaint
                else
                    normalPaint

            canvas.drawLine(x1, y1, x2, y2, segmentPaint)
        }
    }
}

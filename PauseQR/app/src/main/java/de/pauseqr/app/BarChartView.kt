package de.pauseqr.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Minimalistisches Balkendiagramm ohne externe Bibliothek: weiße Balken auf dunklem Grund, dezente
 * graue Tagesbeschriftung, keine Farbverläufe oder Animationen. Rendert auch bei ausschließlich
 * leeren (0-ms-)Werten korrekt – dann werden nur die Grundlinie und die Beschriftungen gezeigt,
 * ohne abzustürzen. Die gewünschte Breite (für die horizontal scrollbare Monatsansicht) wird über
 * [desiredWidthPx] gesteuert; bei MATCH_PARENT im Eltern-Layout füllt die Ansicht stattdessen die
 * verfügbare Breite (Wochenansicht).
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var values: List<Long> = emptyList()
    private var labels: List<String> = emptyList()
    private var desiredWidthPx: Int? = null

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A5A5A9")
        textAlign = Paint.Align.CENTER
        textSize = resources.displayMetrics.scaledDensity * 11f
    }

    private val barWidthDp = 18f
    private val barGapDp = 14f

    /** [desiredWidthPx]: feste Gesamtbreite für die scrollbare Monatsansicht, null für Wochenansicht (füllt die Breite). */
    fun setData(values: List<Long>, labels: List<String>, desiredWidthPx: Int? = null) {
        this.values = values
        this.labels = labels
        this.desiredWidthPx = desiredWidthPx
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val width = if (widthMode == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(widthMeasureSpec)
        } else {
            desiredWidthPx ?: MeasureSpec.getSize(widthMeasureSpec)
        }
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val density = resources.displayMetrics.density
        val height = if (heightMode == MeasureSpec.UNSPECIFIED) (160 * density).toInt() else MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty() || width <= 0 || height <= 0) return

        val density = resources.displayMetrics.density
        val barWidth = barWidthDp * density
        val gap = barGapDp * density
        val labelAreaHeight = 18f * density
        val chartHeight = (height - labelAreaHeight).coerceAtLeast(0f)
        val maxValue = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)

        val totalContentWidth = values.size * (barWidth + gap)
        val startX = if (totalContentWidth < width) (width - totalContentWidth) / 2f + gap / 2f else gap / 2f

        var x = startX
        for (i in values.indices) {
            val value = values[i].coerceAtLeast(0L)
            val barHeight = (value.toFloat() / maxValue.toFloat()) * chartHeight
            val top = (chartHeight - barHeight).coerceAtLeast(0f)
            canvas.drawRoundRect(x, top, x + barWidth, chartHeight.coerceAtLeast(1f), 4f, 4f, barPaint)
            if (i < labels.size) {
                canvas.drawText(labels[i], x + barWidth / 2f, height.toFloat() - 2f, labelPaint)
            }
            x += barWidth + gap
        }
    }
}

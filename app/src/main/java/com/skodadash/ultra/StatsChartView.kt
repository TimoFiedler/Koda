package com.skodadash.ultra

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class StatsChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var dailyStats: List<StatsHelper.DailyStat> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6ECFC3")
        style = Paint.Style.FILL
    }

    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2C2C2E")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E8E93")
        textSize = 20f
        textAlign = Paint.Align.CENTER
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        textSize = 18f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    fun setData(stats: List<StatsHelper.DailyStat>) {
        dailyStats = stats
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dailyStats.isEmpty()) {
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("Keine Daten", width/2f, height/2f, textPaint)
            return
        }

        val maxDist = dailyStats.maxOf { it.distanceKm }.coerceAtLeast(1.0)
        val barWidth = (width - 32f) / dailyStats.size * 0.6f
        val spacing = (width - 32f) / dailyStats.size
        val chartHeight = height - 50f
        val left = 16f

        for ((i, stat) in dailyStats.withIndex()) {
            val x = left + spacing * i + spacing/2
            val barHeight = (stat.distanceKm / maxDist * chartHeight * 0.75).toFloat()
            val yTop = chartHeight - barHeight + 20f

            canvas.drawRoundRect(
                x - barWidth/2, 20f, x + barWidth/2, chartHeight + 20f,
                8f, 8f, barBgPaint
            )

            if (stat.distanceKm > 0) {
                val color = when {
                    stat.distanceKm > 50 -> Color.parseColor("#6ECFC3")
                    stat.distanceKm > 20 -> Color.parseColor("#5DBDB5")
                    else -> Color.parseColor("#3A3A3C")
                }
                barPaint.color = color
                canvas.drawRoundRect(
                    x - barWidth/2, yTop, x + barWidth/2, chartHeight + 20f,
                    8f, 8f, barPaint
                )
                if (stat.distanceKm >= 1) {
                    canvas.drawText(String.format("%.0f", stat.distanceKm), x, yTop - 8f, valuePaint)
                }
            }
            canvas.drawText(stat.date, x, height - 6f, textPaint)
        }
    }
}

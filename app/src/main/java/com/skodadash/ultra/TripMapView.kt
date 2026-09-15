package com.skodadash.ultra

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class TripMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var points: List<TripPoint> = emptyList()
    private var events: List<TripEvent> = emptyList()
    private var selectedEvent: TripEvent? = null

    var onEventSelected: ((TripEvent) -> Unit)? = null
    var onPointSelected: ((TripPoint) -> Unit)? = null

    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#5D4037")
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val pathShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        color = Color.parseColor("#33_5D4037".replace("_",""))
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#4CAF50")
    }

    private val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F44336")
    }

    private val eventPaints = mapOf(
        "ACCEL" to Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#66BB6A"); style = Paint.Style.FILL },
        "HARD_ACCEL" to Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E7D32"); style = Paint.Style.FILL },
        "BRAKE" to Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFA726"); style = Paint.Style.FILL },
        "HARD_BRAKE" to Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF5350"); style = Paint.Style.FILL },
        "CORNER" to Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#42A5F5"); style = Paint.Style.FILL },
        "SHARP_CORNER" to Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1565C0"); style = Paint.Style.FILL },
        "SPEED" to Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#AB47BC"); style = Paint.Style.FILL }
    )

    private val whiteStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }

    private val selectedStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#FFEB3B")
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.parseColor("#E8DCC6")
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8D6E63")
        textSize = 24f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }

    private var minLat = 0.0
    private var maxLat = 0.0
    private var minLon = 0.0
    private var maxLon = 0.0
    private var boundsCalculated = false

    fun setTripData(tripPoints: List<TripPoint>, tripEvents: List<TripEvent>) {
        points = tripPoints
        events = tripEvents
        selectedEvent = null
        calculateBounds()
        invalidate()
    }

    fun setSelectedEvent(event: TripEvent) {
        selectedEvent = event
        invalidate()
    }

    private fun calculateBounds() {
        if (points.isEmpty()) {
            boundsCalculated = false
            return
        }
        minLat = points.minOf { it.lat }
        maxLat = points.maxOf { it.lat }
        minLon = points.minOf { it.lon }
        maxLon = points.maxOf { it.lon }

        // Add 5% padding
        val latPad = (maxLat - minLat) * 0.08
        val lonPad = (maxLon - minLon) * 0.08
        val minPad = 0.0005 // at least 50m
        minLat -= max(latPad, minPad)
        maxLat += max(latPad, minPad)
        minLon -= max(lonPad, minPad)
        maxLon += max(lonPad, minPad)

        // Handle zero range
        if (maxLat - minLat < 0.0001) {
            minLat -= 0.0005
            maxLat += 0.0005
        }
        if (maxLon - minLon < 0.0001) {
            minLon -= 0.0005
            maxLon += 0.0005
        }
        boundsCalculated = true
    }

    private fun latLonToXY(lat: Double, lon: Double): PointF {
        if (!boundsCalculated) return PointF(width / 2f, height / 2f)
        val padding = 40f
        val usableW = width - padding * 2
        val usableH = height - padding * 2

        // Keep aspect ratio
        val latRange = maxLat - minLat
        val lonRange = maxLon - minLon
        val latScale = usableH / latRange
        val lonScale = usableW / lonRange
        val scale = min(latScale, lonScale)

        val centerLat = (minLat + maxLat) / 2
        val centerLon = (minLon + maxLon) / 2

        val x = (width / 2f + (lon - centerLon) * scale).toFloat()
        val y = (height / 2f - (lat - centerLat) * scale).toFloat() // invert lat

        return PointF(x, y)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(Color.parseColor("#FFFEF9"))

        if (points.isEmpty()) {
            val msg = "Keine GPS Daten"
            val bounds = Rect()
            textPaint.getTextBounds(msg, 0, msg.length, bounds)
            canvas.drawText(msg, width/2f - bounds.width()/2f, height/2f, textPaint)
            return
        }

        // Grid
        for (i in 1..3) {
            val y = height * i / 4f
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            val x = width * i / 4f
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
        }

        // Path shadow
        val path = Path()
        val shadowPath = Path()
        var first = true
        for (p in points) {
            val xy = latLonToXY(p.lat, p.lon)
            if (first) {
                path.moveTo(xy.x, xy.y)
                shadowPath.moveTo(xy.x, xy.y)
                first = false
            } else {
                path.lineTo(xy.x, xy.y)
                shadowPath.lineTo(xy.x, xy.y)
            }
        }
        canvas.drawPath(shadowPath, pathShadowPaint)
        canvas.drawPath(path, pathPaint)

        // Events
        for (event in events) {
            val xy = latLonToXY(event.lat, event.lon)
            val paint = eventPaints[event.type] ?: eventPaints["CORNER"]!!
            val isSelected = selectedEvent?.time == event.time && selectedEvent?.type == event.type
            val radius = if (isSelected) 22f else 14f

            // White background
            canvas.drawCircle(xy.x, xy.y, radius + 3f, Paint().apply { color = Color.WHITE; style = Paint.Style.FILL })
            canvas.drawCircle(xy.x, xy.y, radius, paint)
            canvas.drawCircle(xy.x, xy.y, radius, whiteStroke)
            if (isSelected) {
                canvas.drawCircle(xy.x, xy.y, radius + 6f, selectedStroke)
            }

            // Speed text for SPEED events
            if (event.type == "SPEED" && event.speedKmh > 0) {
                val speedText = "${event.speedKmh.toInt()}"
                canvas.drawText(speedText, xy.x + radius + 6f, xy.y + 8f, textPaint.apply { textSize = 22f; color = Color.parseColor("#5D4037") })
            }
        }

        // Start marker
        if (points.isNotEmpty()) {
            val start = latLonToXY(points.first().lat, points.first().lon)
            canvas.drawCircle(start.x, start.y, 18f, Paint().apply { color = Color.WHITE; style = Paint.Style.FILL })
            canvas.drawCircle(start.x, start.y, 14f, startPaint)
            canvas.drawCircle(start.x, start.y, 14f, whiteStroke)
            // Label S
            val sPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
            canvas.drawText("S", start.x, start.y + 7f, sPaint)
        }

        // End marker
        if (points.size > 1) {
            val end = latLonToXY(points.last().lat, points.last().lon)
            canvas.drawCircle(end.x, end.y, 18f, Paint().apply { color = Color.WHITE; style = Paint.Style.FILL })
            canvas.drawCircle(end.x, end.y, 14f, endPaint)
            canvas.drawCircle(end.x, end.y, 14f, whiteStroke)
            val ePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
            canvas.drawText("Z", end.x, end.y + 7f, ePaint)
        }

        // Compass and scale
        val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8D6E63"); textSize = 20f }
        canvas.drawText("N ^", width - 60f, 30f, infoPaint)
        if (points.size >= 2) {
            val dist = calculateDistance(points.first(), points.last())
            canvas.drawText("${String.format("%.1f km Luftlinie", dist)}", 20f, height - 20f, infoPaint)
        }
    }

    private fun calculateDistance(a: TripPoint, b: TripPoint): Double {
        val R = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val x = sin(dLat/2)*sin(dLat/2) + cos(lat1)*cos(lat2)*sin(dLon/2)*sin(dLon/2)
        val c = 2 * atan2(sqrt(x), sqrt(1-x))
        return R * c
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y

            // Find nearest event
            var nearest: TripEvent? = null
            var minDist = Float.MAX_VALUE
            for (ev in events) {
                val xy = latLonToXY(ev.lat, ev.lon)
                val dist = hypot(x - xy.x, y - xy.y)
                if (dist < minDist && dist < 80f) {
                    minDist = dist
                    nearest = ev
                }
            }

            if (nearest != null) {
                selectedEvent = nearest
                onEventSelected?.invoke(nearest)
                invalidate()
                return true
            }

            // Find nearest point
            var nearestPoint: TripPoint? = null
            var minPointDist = Float.MAX_VALUE
            for (p in points) {
                val xy = latLonToXY(p.lat, p.lon)
                val dist = hypot(x - xy.x, y - xy.y)
                if (dist < minPointDist && dist < 60f) {
                    minPointDist = dist
                    nearestPoint = p
                }
            }
            nearestPoint?.let {
                onPointSelected?.invoke(it)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

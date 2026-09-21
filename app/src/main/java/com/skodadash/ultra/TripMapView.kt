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

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1C1E")
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2C2C2E")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6ECFC3")
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val pathBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2C2C2E")
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30D158")
        style = Paint.Style.FILL
    }

    private val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF3B30")
        style = Paint.Style.FILL
    }

    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2C2C2E")
        style = Paint.Style.FILL
    }

    private val whiteStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1C1E")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val selectedRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCC02")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val eventColors = mapOf(
        "ACCEL" to Color.parseColor("#30D158"),
        "HARD_ACCEL" to Color.parseColor("#1B7A3A"),
        "BRAKE" to Color.parseColor("#FF9F0A"),
        "HARD_BRAKE" to Color.parseColor("#FF3B30"),
        "CORNER" to Color.parseColor("#5856D6"),
        "SHARP_CORNER" to Color.parseColor("#AF52DE"),
        "SPEED" to Color.parseColor("#6ECFC3")
    )

    private var minLat = 0.0
    private var maxLat = 0.0
    private var minLon = 0.0
    private var maxLon = 0.0
    private var hasBounds = false

    // Replay feature
    private var replayIndex = -1
    private var isReplaying = false
    private val replayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6ECFC3")
        style = Paint.Style.FILL
    }
    private val replayWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2C2C2E")
        style = Paint.Style.FILL
    }

    fun setTripData(tripPoints: List<TripPoint>, tripEvents: List<TripEvent>) {
        points = tripPoints
        events = tripEvents
        selectedEvent = null
        replayIndex = -1
        isReplaying = false
        calcBounds()
        invalidate()
    }

    fun setSelectedEvent(event: TripEvent) {
        selectedEvent = event
        invalidate()
    }

    fun startReplay(onUpdate: ((Int, TripPoint) -> Unit)? = null): Boolean {
        if (points.size < 2) return false
        isReplaying = true
        replayIndex = 0
        Thread {
            for (i in points.indices) {
                if (!isReplaying) break
                replayIndex = i
                post { 
                    invalidate()
                    onUpdate?.invoke(i, points[i])
                }
                try { Thread.sleep(80) } catch (_: Exception) {}
            }
            isReplaying = false
            replayIndex = -1
            post { invalidate() }
        }.start()
        return true
    }

    fun stopReplay() {
        isReplaying = false
        replayIndex = -1
        invalidate()
    }

    fun isReplayActive(): Boolean = isReplaying

    private fun calcBounds() {
        if (points.isEmpty()) { hasBounds = false; return }
        minLat = points.minOf { it.lat }
        maxLat = points.maxOf { it.lat }
        minLon = points.minOf { it.lon }
        maxLon = points.maxOf { it.lon }

        val latPad = max((maxLat - minLat) * 0.15, 0.0008)
        val lonPad = max((maxLon - minLon) * 0.15, 0.0008)
        minLat -= latPad
        maxLat += latPad
        minLon -= lonPad
        maxLon += lonPad
        hasBounds = true
    }

    private fun toXY(lat: Double, lon: Double): PointF {
        if (!hasBounds) return PointF(width/2f, height/2f)
        val pad = 24f
        val w = width - pad*2
        val h = height - pad*2
        if (w <=0 || h <=0) return PointF(width/2f, height/2f)

        val latRange = maxLat - minLat
        val lonRange = maxLon - minLon
        if (latRange == 0.0 || lonRange == 0.0) return PointF(width/2f, height/2f)

        // Keep aspect ratio
        val centerLat = (minLat + maxLat)/2
        val centerLon = (minLon + maxLon)/2
        val latScale = h / latRange
        val lonScale = w / lonRange
        val scale = min(latScale, lonScale)

        val x = width/2f + (lon - centerLon)*scale
        val y = height/2f - (lat - centerLat)*scale
        return PointF(x.toFloat(), y.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background with rounded corners effect via clip
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 24f, 24f, bgPaint)

        if (points.isEmpty()) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8D6E63"); textSize = 32f; textAlign = Paint.Align.CENTER }
            canvas.drawText("Keine GPS Daten", width/2f, height/2f, p)
            return
        }

        // Subtle grid
        val gridStep = width / 4f
        for (i in 1..3) {
            canvas.drawLine(gridStep*i, 0f, gridStep*i, height.toFloat(), gridPaint)
            canvas.drawLine(0f, gridStep*i, width.toFloat(), gridStep*i, gridPaint)
        }

        // Path background
        if (points.size > 1) {
            val bgPath = Path()
            var first = true
            for (pt in points) {
                val xy = toXY(pt.lat, pt.lon)
                if (first) { bgPath.moveTo(xy.x, xy.y); first=false } else bgPath.lineTo(xy.x, xy.y)
            }
            canvas.drawPath(bgPath, pathBgPaint)
        }

        // Path
        if (points.size > 1) {
            val path = Path()
            var first = true
            for (pt in points) {
                val xy = toXY(pt.lat, pt.lon)
                if (first) { path.moveTo(xy.x, xy.y); first=false } else path.lineTo(xy.x, xy.y)
            }
            canvas.drawPath(path, pathPaint)
        }

        // Events - minimal small dots
        for (ev in events) {
            val xy = toXY(ev.lat, ev.lon)
            val color = eventColors[ev.type] ?: eventColors["CORNER"]!!
            val isSel = selectedEvent?.time == ev.time && selectedEvent?.type == ev.type
            val r = if (isSel) 18f else 10f

            // white halo
            canvas.drawCircle(xy.x, xy.y, r+3f, whitePaint)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
            canvas.drawCircle(xy.x, xy.y, r, paint)
            canvas.drawCircle(xy.x, xy.y, r, whiteStroke)
            if (isSel) canvas.drawCircle(xy.x, xy.y, r+6f, selectedRing)
        }

        // Start
        if (points.isNotEmpty()) {
            val s = toXY(points.first().lat, points.first().lon)
            canvas.drawCircle(s.x, s.y, 14f, whitePaint)
            canvas.drawCircle(s.x, s.y, 10f, startPaint)
        }
        // End
        if (points.size > 1) {
            val e = toXY(points.last().lat, points.last().lon)
            canvas.drawCircle(e.x, e.y, 14f, whitePaint)
            canvas.drawCircle(e.x, e.y, 10f, endPaint)
        }

        // Replay dot
        if (isReplaying && replayIndex in points.indices) {
            val rp = points[replayIndex]
            val xy = toXY(rp.lat, rp.lon)
            canvas.drawCircle(xy.x, xy.y, 20f, replayWhite)
            canvas.drawCircle(xy.x, xy.y, 14f, replayPaint)
            canvas.drawCircle(xy.x, xy.y, 14f, whiteStroke)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y
            var nearest: TripEvent? = null
            var minDist = Float.MAX_VALUE
            for (ev in events) {
                val xy = toXY(ev.lat, ev.lon)
                val d = hypot(x - xy.x, y - xy.y)
                if (d < minDist && d < 70f) { minDist = d; nearest = ev }
            }
            if (nearest != null) {
                selectedEvent = nearest
                onEventSelected?.invoke(nearest)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

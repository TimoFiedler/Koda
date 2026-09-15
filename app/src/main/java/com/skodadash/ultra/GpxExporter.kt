package com.skodadash.ultra

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object GpxExporter {

    fun exportTrip(context: Context, trip: TripData): File? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val fileName = "trip_${trip.id}_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMANY).format(Date(trip.startTime))}.gpx"
            val file = File(context.cacheDir, fileName)

            val sb = StringBuilder()
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            sb.append("<gpx version=\"1.1\" creator=\"SkodaDash Vollversion\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
            sb.append("  <metadata><name>Fahrt ${trip.id} - ${trip.getDrivingStyle()}</name><desc>Score ${trip.score} - ${String.format("%.1f km", trip.distanceMeters/1000)}</desc></metadata>\n")
            sb.append("  <trk><name>Fahrt ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(trip.startTime))}</name>\n")
            sb.append("    <trkseg>\n")
            for (p in trip.points) {
                sb.append("      <trkpt lat=\"${p.lat}\" lon=\"${p.lon}\"><ele>${p.altitude}</ele><time>${sdf.format(Date(p.time))}</time><speed>${p.speedKmh/3.6}</speed></trkpt>\n")
            }
            sb.append("    </trkseg>\n")
            sb.append("  </trk>\n")
            // Waypoints for events
            for (e in trip.events) {
                sb.append("  <wpt lat=\"${e.lat}\" lon=\"${e.lon}\"><name>${e.type} ${String.format("%.1f", e.value)}</name><desc>${e.type} at ${e.speedKmh.toInt()} km/h</desc><time>${sdf.format(Date(e.time))}</time></wpt>\n")
            }
            sb.append("</gpx>\n")

            file.writeText(sb.toString())
            file
        } catch (e: Exception) {
            null
        }
    }

    fun shareGpx(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "GPX teilen"))
        } catch (e: Exception) {
            // Fallback
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(file), "application/gpx+xml")
            }
            context.startActivity(intent)
        }
    }
}

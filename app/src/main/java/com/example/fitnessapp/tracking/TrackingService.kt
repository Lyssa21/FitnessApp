package com.example.fitnessapp.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/** Foreground GPS tracker; distance is accumulated between successive location points. */
class TrackingService : Service() {
    private lateinit var client: FusedLocationProviderClient
    private var previous: android.location.Location? = null
    private var distanceMeters = 0f
    private var startedAt = 0L
    private var activityType = "Running"
    private val routePoints = JSONArray()
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { location ->
                if (location.accuracy > 40f) return@forEach
                previous?.let { old ->
                    val segment = old.distanceTo(location)
                    if (segment <= 100f) distanceMeters += segment
                }
                previous = location
                routePoints.put(JSONObject().put("lat", location.latitude).put("lng", location.longitude))
                val minutes = ((System.currentTimeMillis() - startedAt) / 60000).coerceAtLeast(1)
                val stride = if (activityType == "Walking") 0.72f else 0.78f
                val caloriesPerMinute = if (activityType == "Walking") 4 else 9
                sendUpdate(distanceMeters / 1000.0, (distanceMeters / stride).roundToInt(), minutes,
                    (minutes * caloriesPerMinute).toInt(), location.latitude, location.longitude)
            }
        }
    }
    override fun onCreate() { super.onCreate(); client = LocationServices.getFusedLocationProviderClient(this); createChannel() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activityType = intent?.getStringExtra("activity_type") ?: activityType
        startForeground(42, notification("GPS tracking active")); startedAt = System.currentTimeMillis()
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).setMinUpdateDistanceMeters(5f).build()
        try { client.requestLocationUpdates(request, callback, Looper.getMainLooper()) } catch (_: SecurityException) { stopSelf() }
        return START_STICKY
    }
    override fun onDestroy() { client.removeLocationUpdates(callback); sendBroadcast(Intent(ACTION_STOPPED).setPackage(packageName)); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun sendUpdate(distanceKm: Double, steps: Int, minutes: Long, calories: Int, lat: Double, lng: Double) = sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName).apply { putExtra("distance_km", distanceKm); putExtra("steps", steps); putExtra("minutes", minutes); putExtra("calories", calories); putExtra("route_points", routePoints.toString()); putExtra("latitude", lat); putExtra("longitude", lng) })
    private fun notification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL).setContentTitle("BlushFit GPS tracking").setContentText(text).setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).build()
    private fun createChannel() { getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "GPS tracking", NotificationManager.IMPORTANCE_LOW)) }
    companion object { const val ACTION_UPDATE = "com.example.fitnessapp.TRACKING_UPDATE"; const val ACTION_STOPPED = "com.example.fitnessapp.TRACKING_STOPPED"; private const val CHANNEL = "gps_tracking" }
}

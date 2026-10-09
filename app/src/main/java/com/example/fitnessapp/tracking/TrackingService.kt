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
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** GPS-only route and step estimator, compatible with Android mock-location providers. */
class TrackingService : Service() {
    private lateinit var locationClient: FusedLocationProviderClient
    private var previous: android.location.Location? = null
    private var distanceMeters = 0f
    private var startedAt = 0L
    private var activityType = "Running"
    private val routePoints = JSONArray()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { location ->
                val mockLocation = location.isFromMockProvider
                if (!mockLocation && (location.accuracy <= 0f || location.accuracy > 100f)) return@forEach
                previous?.let { old ->
                    val elapsedSeconds = ((location.time - old.time).coerceAtLeast(1L)) / 1000.0
                    val segmentMeters = old.distanceTo(location)
                    val maxPlausibleSpeed = if (activityType == "Cycling") 45.0 else 15.0 // m/s
                    // Fake GPS may jump between points; accept mock segments for route demonstrations.
                    if (mockLocation || segmentMeters / elapsedSeconds <= maxPlausibleSpeed) distanceMeters += segmentMeters
                }
                previous = location
                routePoints.put(JSONObject().put("lat", location.latitude).put("lng", location.longitude))
                publish(location.latitude, location.longitude)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            publishFinalMetrics()
            stopSelf()
            return START_NOT_STICKY
        }
        activityType = intent?.getStringExtra("activity_type") ?: activityType
        startForeground(NOTIFICATION_ID, notification("Waiting for GPS route updates"))
        startedAt = System.currentTimeMillis()
        previous = null
        distanceMeters = 0f
        while (routePoints.length() > 0) routePoints.remove(routePoints.length() - 1)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateDistanceMeters(1f).build()
        try { locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper()) }
        catch (_: SecurityException) { stopSelf() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        locationClient.removeLocationUpdates(locationCallback)
        sendBroadcast(Intent(ACTION_STOPPED).setPackage(packageName))
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun publish(lat: Double, lng: Double) {
        val elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(1L)
        val minutes = (elapsedSeconds / 60.0).roundToInt().coerceAtLeast(1)
        val strideMeters = when (activityType) { "Walking" -> 0.72f; "Running" -> 0.78f; else -> 0.72f }
        val calories = estimateDistanceCalories(distanceMeters / 1000.0)
        val steps = (distanceMeters / strideMeters).roundToInt()
        val speedKmh = if (elapsedSeconds > 0) distanceMeters / elapsedSeconds * 3.6 else 0.0
        val paceMinKm = if (speedKmh > 0.1) 60.0 / speedKmh else 0.0
        sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName).apply {
            putExtra("distance_km", distanceMeters / 1000.0)
            putExtra("steps", steps)
            putExtra("steps_available", true) // GPS-derived estimate, not the hardware pedometer.
            putExtra("minutes", minutes.toLong())
            putExtra("calories", calories)
            putExtra("speed_kmh", speedKmh)
            putExtra("pace_min_km", paceMinKm)
            putExtra("route_points", routePoints.toString())
            putExtra("latitude", lat)
            putExtra("longitude", lng)
        })
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID, notification("${"%.2f".format(distanceMeters / 1000.0)} km • $steps estimated steps")
        )
    }

    private fun publishFinalMetrics() {
        val elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(1L)
        val minutes = ((elapsedSeconds + 59L) / 60L).toInt().coerceAtLeast(1)
        val strideMeters = if (activityType == "Running") 0.78f else 0.72f
        val lastPoint = routePoints.optJSONObject(routePoints.length() - 1)
        sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName).apply {
            putExtra("distance_km", distanceMeters / 1000.0)
            putExtra("steps", (distanceMeters / strideMeters).roundToInt())
            putExtra("minutes", minutes.toLong())
            putExtra("calories", estimateDistanceCalories(distanceMeters / 1000.0))
            putExtra("route_points", routePoints.toString())
            if (lastPoint != null) {
                putExtra("latitude", lastPoint.optDouble("lat"))
                putExtra("longitude", lastPoint.optDouble("lng"))
            }
        })
    }

    /** Rough distance-based estimate. Calories vary by body mass and effort; this is a demo estimate. */
    private fun estimateDistanceCalories(distanceKm: Double): Int {
        val kcalPerKm = if (activityType == "Walking") 45.0 else 60.0
        return (distanceKm * kcalPerKm).roundToInt().coerceAtLeast(if (distanceKm > 0.0) 1 else 0)
    }

    private fun notification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL)
        .setContentTitle("BlushFit GPS tracking").setContentText(text)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).build()

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "GPS tracking", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val ACTION_UPDATE = "com.example.fitnessapp.TRACKING_UPDATE"
        const val ACTION_STOPPED = "com.example.fitnessapp.TRACKING_STOPPED"
        const val ACTION_STOP = "com.example.fitnessapp.TRACKING_STOP"
        private const val CHANNEL = "gps_tracking"
        private const val NOTIFICATION_ID = 42
    }
}

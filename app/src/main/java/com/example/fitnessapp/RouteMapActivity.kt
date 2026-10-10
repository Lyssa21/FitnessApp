package com.example.fitnessapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fitnessapp.tracking.TrackingService
import com.google.android.gms.location.LocationServices
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Native street map: the route and pins work without a WebView or remote JavaScript. */
class RouteMapActivity : BaseActivity() {
    private lateinit var map: MapView
    private lateinit var route: Polyline
    private lateinit var status: TextView
    private val points = mutableListOf<GeoPoint>()
    private var startPin: Marker? = null
    private var currentPin: Marker? = null
    private var finishPin: Marker? = null
    private var finished = false
    private var tileProblem: String? = null
    private var tileLoaded = false
    private var waitedForGps = false
    private val live by lazy { intent.getBooleanExtra(EXTRA_LIVE, false) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, update: Intent?) {
            when (update?.action) {
                TrackingService.ACTION_UPDATE -> {
                    if (!update.hasExtra("latitude") || !update.hasExtra("longitude")) return
                    val lat = update.getDoubleExtra("latitude", Double.NaN)
                    val lng = update.getDoubleExtra("longitude", Double.NaN)
                    if (lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0) addPoint(GeoPoint(lat, lng), true)
                }
                TrackingService.ACTION_STOPPED -> finishRoute()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().setUserAgentValue("BlushFit/1.0 (+https://github.com/Lyssa21/FitnessApp)")
        Configuration.getInstance().osmdroidBasePath = filesDir
        Configuration.getInstance().osmdroidTileCache = java.io.File(cacheDir, "map_tiles")

        map = MapView(this).apply {
            setTileSource(XYTileSource(
                "BlushFitStreetMap", 0, 19, 256, ".png",
                arrayOf("https://tile.openstreetmap.org/"),
                "© OpenStreetMap contributors",
                TileSourcePolicy(2,
                    TileSourcePolicy.FLAG_NO_BULK or
                    TileSourcePolicy.FLAG_NO_PREVENTIVE or
                    TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL)
            ))
            setMultiTouchControls(true)
            controller.setZoom(11.0)
            controller.setCenter(GeoPoint(16.8661, 96.1951))
            setBackgroundColor(android.graphics.Color.rgb(248, 242, 245))
        }
        route = Polyline().apply {
            outlinePaint.color = android.graphics.Color.rgb(255, 101, 132)
            outlinePaint.strokeWidth = 12f * resources.displayMetrics.density
        }
        map.overlays.add(route)
        status = TextView(this).apply {
            text = "Waiting for GPS location · checking street map…"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            setPadding(18.dp, 12.dp, 18.dp, 12.dp)
            setBackgroundColor(android.graphics.Color.rgb(255, 101, 132))
        }
        val attribution = TextView(this).apply {
            text = "© OpenStreetMap contributors"
            setTextColor(Color.rgb(35, 35, 35))
            textSize = 11f
            setPadding(8.dp, 4.dp, 8.dp, 4.dp)
            setBackgroundColor(Color.argb(230, 255, 255, 255))
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))) }
        }
        val root = FrameLayout(this).apply {
            addView(map, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
            addView(attribution, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(root)
        centerOnLastKnownLocation()
        checkStreetTiles()

        val saved = runCatching { JSONArray(intent.getStringExtra(EXTRA_POINTS).orEmpty()) }.getOrNull() ?: JSONArray()
        for (i in 0 until saved.length()) {
            val point = saved.optJSONObject(i) ?: continue
            val lat = point.optDouble("lat", Double.NaN)
            val lng = point.optDouble("lng", Double.NaN)
            if (lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0) addPoint(GeoPoint(lat, lng), false)
        }
        if (points.size > 1) map.post { map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), true, 48.dp) }
        if (!live && points.isNotEmpty()) finishRoute()
        if (live) ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
            addAction(TrackingService.ACTION_UPDATE)
            addAction(TrackingService.ACTION_STOPPED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        updateStatus()
        if (live) Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed && points.isEmpty()) {
                waitedForGps = true
                updateStatus()
            }
        }, 20_000L)
    }

    private fun centerOnLastKnownLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { location ->
                if (location != null && points.isEmpty() && !isFinishing && !isDestroyed) {
                    map.controller.setZoom(15.0)
                    map.controller.animateTo(GeoPoint(location.latitude, location.longitude))
                }
            }
        } catch (_: SecurityException) { /* Permission can change while opening the map. */ }
    }

    private fun checkStreetTiles() {
        Thread {
            val problem = try {
                val connection = (java.net.URL("https://tile.openstreetmap.org/0/0/0.png").openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "BlushFit/1.0 (+https://github.com/Lyssa21/FitnessApp)")
                }
                try {
                    val code = connection.responseCode
                    if (code == 200) {
                        connection.inputStream.use { it.read() }
                        null
                    } else if (code == 403) "Street map access blocked (HTTP 403)"
                    else "Street map unavailable (HTTP $code)"
                } finally { connection.disconnect() }
            } catch (_: Exception) { "Street map cannot connect. Check internet or VPN." }
            Handler(Looper.getMainLooper()).post {
                if (!isFinishing && !isDestroyed) {
                    tileLoaded = problem == null
                    tileProblem = problem
                    updateStatus()
                }
            }
        }.start()
    }

    private fun updateStatus() {
        val gps = when {
            finished -> if (points.isEmpty()) "No GPS route was recorded" else "Finished route · ${points.size} GPS points"
            points.isNotEmpty() -> "${points.size} GPS ${if (points.size == 1) "point" else "points"}"
            !live -> "No GPS route recorded"
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED -> "Precise location permission needed"
            !LocationManagerCompat.isLocationEnabled(getSystemService(LOCATION_SERVICE) as LocationManager) -> "Turn on phone Location"
            waitedForGps -> "No GPS fix yet; try outdoors"
            else -> "Waiting for GPS location"
        }
        status.text = when {
            tileProblem != null -> "$gps · $tileProblem"
            !tileLoaded -> "$gps · loading streets…"
            else -> "$gps · street server reachable"
        }
    }

    private fun addPoint(point: GeoPoint, follow: Boolean) {
        if (finished || points.lastOrNull()?.distanceToAsDouble(point)?.let { it < 0.5 } == true) return
        points.add(point)
        route.setPoints(points)
        if (startPin == null) startPin = pin(point, "Start", android.graphics.Color.rgb(52, 169, 110))
        if (currentPin == null) currentPin = pin(point, "You are here", android.graphics.Color.rgb(255, 101, 132))
        else currentPin?.position = point
        updateStatus()
        if (follow || points.size == 1) map.controller.animateTo(point)
        map.invalidate()
    }

    private fun finishRoute() {
        if (finished) return
        finished = true
        points.lastOrNull()?.let { finishPin = pin(it, "Finish", android.graphics.Color.rgb(92, 52, 64)) }
        currentPin?.let { map.overlays.remove(it) }
        currentPin = null
        updateStatus()
        map.invalidate()
    }

    private fun pin(point: GeoPoint, title: String, color: Int): Marker = Marker(map).apply {
        position = point
        this.title = title
        icon = BitmapDrawable(resources, circleIcon(color))
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        map.overlays.add(this)
    }

    private fun circleIcon(color: Int): Bitmap {
        val size = 30.dp
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = color
        canvas.drawCircle(size / 2f, size / 2f, size * 0.37f, paint)
        return bitmap
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    override fun onResume() { super.onResume(); if (::map.isInitialized) map.onResume() }
    override fun onPause() { if (::map.isInitialized) map.onPause(); super.onPause() }
    override fun onDestroy() { if (live) unregisterReceiver(receiver); super.onDestroy() }

    companion object {
        const val EXTRA_POINTS = "route_points"
        const val EXTRA_LIVE = "live_route"
    }
}

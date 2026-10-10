package com.example.fitnessapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fitnessapp.tracking.TrackingService
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
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
        Configuration.getInstance().setUserAgentValue(packageName)
        Configuration.getInstance().osmdroidBasePath = filesDir
        Configuration.getInstance().osmdroidTileCache = java.io.File(cacheDir, "map_tiles")

        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            setBackgroundColor(android.graphics.Color.rgb(248, 242, 245))
        }
        route = Polyline().apply {
            outlinePaint.color = android.graphics.Color.rgb(255, 101, 132)
            outlinePaint.strokeWidth = 12f * resources.displayMetrics.density
        }
        map.overlays.add(route)
        status = TextView(this).apply {
            text = "Waiting for GPS location • streets need internet"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            setPadding(18.dp, 12.dp, 18.dp, 12.dp)
            setBackgroundColor(android.graphics.Color.rgb(255, 101, 132))
        }
        val root = FrameLayout(this).apply {
            addView(map, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(root)

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
    }

    private fun addPoint(point: GeoPoint, follow: Boolean) {
        if (finished || points.lastOrNull()?.distanceToAsDouble(point)?.let { it < 0.5 } == true) return
        points.add(point)
        route.setPoints(points)
        if (startPin == null) startPin = pin(point, "Start", android.graphics.Color.rgb(52, 169, 110))
        if (currentPin == null) currentPin = pin(point, "You are here", android.graphics.Color.rgb(255, 101, 132))
        else currentPin?.position = point
        status.text = "Live route • ${points.size} GPS ${if (points.size == 1) "point" else "points"}"
        if (follow || points.size == 1) map.controller.animateTo(point)
        map.invalidate()
    }

    private fun finishRoute() {
        if (finished) return
        finished = true
        points.lastOrNull()?.let { finishPin = pin(it, "Finish", android.graphics.Color.rgb(92, 52, 64)) }
        currentPin?.let { map.overlays.remove(it) }
        currentPin = null
        status.text = if (points.isEmpty()) "No GPS route was recorded" else "Finished route • ${points.size} GPS points"
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

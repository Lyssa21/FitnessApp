package com.example.fitnessapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
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
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Live route on OpenFreeMap vector streets; no Google Maps API key is needed. */
class RouteMapActivity : BaseActivity() {
    private lateinit var mapView: MapView
    private lateinit var status: TextView
    private var map: MapLibreMap? = null
    private val points = mutableListOf<LatLng>()
    private var finished = false
    private var styleReady = false
    private var streetProblem: String? = null
    private var waitedForGps = false
    private val live by lazy { intent.getBooleanExtra(EXTRA_LIVE, false) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, update: Intent?) {
            when (update?.action) {
                TrackingService.ACTION_UPDATE -> {
                    if (!update.hasExtra("latitude") || !update.hasExtra("longitude")) return
                    val lat = update.getDoubleExtra("latitude", Double.NaN)
                    val lng = update.getDoubleExtra("longitude", Double.NaN)
                    if (valid(lat, lng)) addPoint(LatLng(lat, lng), true)
                }
                TrackingService.ACTION_STOPPED -> finishRoute()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        mapView = MapView(this).apply { onCreate(savedInstanceState) }
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(18.dp, 12.dp, 18.dp, 12.dp)
            setBackgroundColor(Color.rgb(255, 101, 132))
        }
        val attribution = TextView(this).apply {
            text = "© OpenMapTiles · © OpenStreetMap contributors"
            setTextColor(Color.rgb(35, 35, 35))
            textSize = 11f
            setPadding(8.dp, 4.dp, 8.dp, 4.dp)
            setBackgroundColor(Color.argb(230, 255, 255, 255))
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://openfreemap.org/"))) }
        }
        val root = FrameLayout(this).apply {
            addView(mapView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
            addView(attribution, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(root)

        val saved = runCatching { JSONArray(intent.getStringExtra(EXTRA_POINTS).orEmpty()) }.getOrNull() ?: JSONArray()
        for (i in 0 until saved.length()) {
            val item = saved.optJSONObject(i) ?: continue
            val lat = item.optDouble("lat", Double.NaN)
            val lng = item.optDouble("lng", Double.NaN)
            if (valid(lat, lng)) addPoint(LatLng(lat, lng), false)
        }
        if (!live && points.isNotEmpty()) finished = true
        if (live) ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
            addAction(TrackingService.ACTION_UPDATE)
            addAction(TrackingService.ACTION_STOPPED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)

        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.cameraPosition = CameraPosition.Builder()
                .target(points.lastOrNull() ?: LatLng(16.8661, 96.1951))
                .zoom(if (points.isEmpty()) 11.0 else 16.0).build()
            readyMap.setStyle(STYLE_URL) { style ->
                style.addSource(GeoJsonSource(ROUTE_SOURCE, emptyFeatures()))
                style.addLayer(LineLayer("workout-route", ROUTE_SOURCE).withProperties(
                    lineColor(Color.rgb(255, 101, 132)), lineWidth(6f), lineOpacity(0.9f)
                ))
                addPinLayer(style, START_SOURCE, Color.rgb(52, 169, 110))
                addPinLayer(style, CURRENT_SOURCE, Color.rgb(255, 101, 132))
                addPinLayer(style, FINISH_SOURCE, Color.rgb(92, 52, 64))
                styleReady = true
                if (streetProblem?.startsWith("Street map timed out") == true) streetProblem = null
                refreshRoute(false)
                if (points.size > 1) fitRoute()
                updateStatus()
            }
        }
        centerOnLastKnownLocation()
        checkStreetService()
        updateStatus()
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed && !styleReady && streetProblem == null) {
                streetProblem = "Street map timed out. Check internet or VPN."
                updateStatus()
            }
        }, 15_000L)
        if (live) Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed && points.isEmpty()) {
                waitedForGps = true
                updateStatus()
            }
        }, 20_000L)
    }

    private fun addPinLayer(style: org.maplibre.android.maps.Style, sourceId: String, color: Int) {
        style.addSource(GeoJsonSource(sourceId, emptyFeatures()))
        style.addLayer(CircleLayer("$sourceId-layer", sourceId).withProperties(
            circleColor(color), circleRadius(9f), circleStrokeColor(Color.WHITE), circleStrokeWidth(3f)
        ))
    }

    private fun emptyFeatures() = FeatureCollection.fromFeatures(emptyArray<Feature>())

    private fun pointFeatures(point: LatLng?) = if (point == null) emptyFeatures() else
        FeatureCollection.fromFeatures(arrayOf(Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude))))

    private fun refreshRoute(follow: Boolean) {
        val readyMap = map ?: return
        if (!styleReady) return
        val style = readyMap.style ?: return
        val line = if (points.size < 2) emptyFeatures() else FeatureCollection.fromFeatures(arrayOf(
            Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) }))
        ))
        style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(line)
        style.getSourceAs<GeoJsonSource>(START_SOURCE)?.setGeoJson(pointFeatures(points.firstOrNull()))
        style.getSourceAs<GeoJsonSource>(CURRENT_SOURCE)?.setGeoJson(pointFeatures(if (finished) null else points.lastOrNull()))
        style.getSourceAs<GeoJsonSource>(FINISH_SOURCE)?.setGeoJson(pointFeatures(if (finished) points.lastOrNull() else null))
        if (follow) points.lastOrNull()?.let { readyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 16.0)) }
    }

    private fun fitRoute() {
        val readyMap = map ?: return
        if (points.size < 2) return
        val bounds = LatLngBounds.Builder().includes(points).build()
        mapView.post { readyMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 48.dp)) }
    }

    private fun addPoint(point: LatLng, follow: Boolean) {
        if (finished || points.lastOrNull()?.let { it.latitude == point.latitude && it.longitude == point.longitude } == true) return
        points.add(point)
        refreshRoute(follow)
        updateStatus()
    }

    private fun finishRoute() {
        if (finished) return
        finished = true
        refreshRoute(false)
        updateStatus()
    }

    private fun centerOnLastKnownLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { location ->
                if (location != null && points.isEmpty() && !isFinishing && !isDestroyed) {
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15.0))
                }
            }
        } catch (_: SecurityException) { /* Permission can change while opening the map. */ }
    }

    private fun checkStreetService() {
        Thread {
            val problem = try {
                val connection = (java.net.URL(STYLE_URL).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                try {
                    val code = connection.responseCode
                    if (code == 200) {
                        connection.inputStream.use { it.read() }
                        null
                    } else "Street map server returned HTTP $code"
                } finally { connection.disconnect() }
            } catch (_: Exception) { "Street map cannot connect. Check internet or VPN." }
            Handler(Looper.getMainLooper()).post {
                if (!isFinishing && !isDestroyed) {
                    streetProblem = problem
                    updateStatus()
                }
            }
        }.start()
    }

    private fun updateStatus() {
        if (!::status.isInitialized) return
        val gps = when {
            finished -> if (points.isEmpty()) "No GPS route recorded" else "Finished route · ${points.size} GPS points"
            points.isNotEmpty() -> "${points.size} GPS ${if (points.size == 1) "point" else "points"}"
            !live -> "No GPS route recorded"
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED -> "Precise location permission needed"
            !LocationManagerCompat.isLocationEnabled(getSystemService(LOCATION_SERVICE) as LocationManager) -> "Turn on phone Location"
            waitedForGps -> "No GPS fix yet; try outdoors"
            else -> "Waiting for GPS location"
        }
        status.text = when {
            streetProblem != null -> "$gps · $streetProblem"
            !styleReady -> "$gps · loading street map…"
            else -> "$gps · live street map"
        }
    }

    private fun valid(lat: Double, lng: Double) = lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onDestroy() { if (live) unregisterReceiver(receiver); mapView.onDestroy(); super.onDestroy() }

    companion object {
        const val EXTRA_POINTS = "route_points"
        const val EXTRA_LIVE = "live_route"
        private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        private const val ROUTE_SOURCE = "workout-route-source"
        private const val START_SOURCE = "workout-start-source"
        private const val CURRENT_SOURCE = "workout-current-source"
        private const val FINISH_SOURCE = "workout-finish-source"
    }
}

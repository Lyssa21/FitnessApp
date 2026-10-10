package com.example.fitnessapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import com.example.fitnessapp.tracking.TrackingService
import org.json.JSONArray

/** Live street map backed by OpenStreetMap tiles and a pink route overlay. */
class RouteMapActivity : BaseActivity() {
    private lateinit var map: WebView
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TrackingService.ACTION_UPDATE) {
                val lat = intent.getDoubleExtra("latitude", Double.NaN)
                val lng = intent.getDoubleExtra("longitude", Double.NaN)
                if (lat.isFinite() && lng.isFinite()) map.evaluateJavascript("addPoint($lat,$lng)", null)
            } else if (intent?.action == TrackingService.ACTION_STOPPED) {
                map.evaluateJavascript("finishRoute()", null)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        map = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.blockNetworkImage = false
            webViewClient = WebViewClient()
        }
        setContentView(map)
        val live = intent.getBooleanExtra(EXTRA_LIVE, false)
        val points = runCatching { JSONArray(intent.getStringExtra(EXTRA_POINTS).orEmpty()) }.getOrNull() ?: JSONArray()
        val coordinates = buildList {
            for (i in 0 until points.length()) points.optJSONObject(i)?.let {
                val lat = it.optDouble("lat", Double.NaN); val lng = it.optDouble("lng", Double.NaN)
                if (lat.isFinite() && lng.isFinite()) add("[$lat,$lng]")
            }
        }.joinToString(",")
        val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"><script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><style>html,body,#map{height:100%;margin:0}#loading{position:absolute;z-index:1000;top:18px;left:18px;right:18px;padding:14px 18px;border-radius:16px;background:#ff6584;color:#fff;font:600 15px Arial;box-shadow:0 3px 12px #0003}.pin{border-radius:50%;border:3px solid #fff;box-shadow:0 2px 7px #555;width:22px!important;height:22px!important}.start{background:#40b77a}.moving{background:#ff6584}.finish{background:#5c3440}</style></head><body><div id="map"></div><div id="loading">Loading street map…</div><script>const points=[$coordinates],defaultCenter=[16.8661,96.1951];const map=L.map('map').setView(points.length?points[0]:defaultCenter,points.length?16:12);L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors',crossOrigin:true}).addTo(map);const line=L.polyline(points,{color:'#ff6584',weight:6,lineCap:'round',lineJoin:'round'}).addTo(map);const icon=(kind)=>L.divIcon({className:'pin '+kind,iconSize:[22,22],iconAnchor:[11,11]});let startMarker=points.length?L.marker(points[0],{icon:icon('start')}).addTo(map).bindPopup('Start'):null;let movingMarker=points.length?L.marker(points[points.length-1],{icon:icon('moving')}).addTo(map).bindPopup('You are here'):null;let finishMarker=null;if(points.length>1){finishMarker=L.marker(points[points.length-1],{icon:icon('finish')}).addTo(map).bindPopup('Finish');finishMarker.setOpacity(0)}if(points.length>1)map.fitBounds(line.getBounds(),{padding:[30,30]});if(points.length)document.getElementById('loading').style.display='none';function addPoint(a,b){const p=[a,b];line.addLatLng(p);if(!startMarker)startMarker=L.marker(p,{icon:icon('start')}).addTo(map).bindPopup('Start');if(!movingMarker)movingMarker=L.marker(p,{icon:icon('moving')}).addTo(map).bindPopup('You are here');else movingMarker.setLatLng(p);if(finishMarker)finishMarker.setLatLng(p);map.setView(p,17);document.getElementById('loading').style.display='none')}function finishRoute(){if(movingMarker){if(finishMarker)finishMarker.setLatLng(movingMarker.getLatLng()).setOpacity(1);else finishMarker=L.marker(movingMarker.getLatLng(),{icon:icon('finish')}).addTo(map).bindPopup('Finish');movingMarker.setOpacity(0)}}setTimeout(()=>document.getElementById('loading').style.display='none',5000);</script></body></html>"""
        map.loadDataWithBaseURL("https://www.openstreetmap.org", html, "text/html", "UTF-8", null)
        if (live) ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
            addAction(TrackingService.ACTION_UPDATE)
            addAction(TrackingService.ACTION_STOPPED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() { if (intent.getBooleanExtra(EXTRA_LIVE, false)) unregisterReceiver(receiver); super.onDestroy() }
    companion object { const val EXTRA_POINTS = "route_points"; const val EXTRA_LIVE = "live_route" }
}

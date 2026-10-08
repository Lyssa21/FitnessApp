package com.example.fitnessapp

import android.os.Bundle
import android.webkit.WebView
import org.json.JSONArray

/** Online OpenStreetMap view. Numeric coordinates are parsed before injecting a polyline. */
class RouteMapActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        setContentView(webView)
        val source = intent.getStringExtra(EXTRA_POINTS).orEmpty()
        val points = runCatching { JSONArray(source) }.getOrNull() ?: JSONArray()
        val coordinates = buildList {
            for (i in 0 until points.length()) {
                val point = points.optJSONObject(i) ?: continue
                val lat = point.optDouble("lat", Double.NaN); val lng = point.optDouble("lng", Double.NaN)
                if (lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0) add("[$lat,$lng]")
            }
        }.joinToString(",")
        val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"><script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><style>html,body,#map{height:100%;margin:0}</style></head><body><div id="map"></div><script>const p=[$coordinates];const map=L.map('map').setView(p.length?p[0]:[16.8661,96.1951],p.length?15:6);L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);if(p.length){L.polyline(p,{color:'#ff6584',weight:5}).addTo(map);L.marker(p[0]).addTo(map).bindPopup('Start');L.marker(p[p.length-1]).addTo(map).bindPopup('Finish');map.fitBounds(L.polyline(p).getBounds(),{padding:[25,25]});}</script></body></html>"""
        webView.settings.javaScriptEnabled = true
        webView.loadDataWithBaseURL("https://www.openstreetmap.org", html, "text/html", "UTF-8", null)
    }
    companion object { const val EXTRA_POINTS = "route_points" }
}

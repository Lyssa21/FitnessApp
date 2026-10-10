package com.example.fitnessapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.webkit.WebView
import androidx.core.content.ContextCompat
import com.example.fitnessapp.tracking.TrackingService
import org.json.JSONArray

/** Fast local route view. It does not wait for online map tiles before drawing GPS points. */
class RouteMapActivity : BaseActivity() {
    private lateinit var map: WebView
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TrackingService.ACTION_UPDATE) {
                val lat = intent.getDoubleExtra("latitude", Double.NaN)
                val lng = intent.getDoubleExtra("longitude", Double.NaN)
                if (lat.isFinite() && lng.isFinite()) map.evaluateJavascript("addPoint($lat,$lng)", null)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        map = WebView(this).apply { settings.javaScriptEnabled = true }
        setContentView(map)
        val live = intent.getBooleanExtra(EXTRA_LIVE, false)
        val points = runCatching { JSONArray(intent.getStringExtra(EXTRA_POINTS).orEmpty()) }.getOrNull() ?: JSONArray()
        val json = buildList {
            for (i in 0 until points.length()) points.optJSONObject(i)?.let {
                val lat = it.optDouble("lat", Double.NaN); val lng = it.optDouble("lng", Double.NaN)
                if (lat.isFinite() && lng.isFinite()) add("[$lat,$lng]")
            }
        }.joinToString(",")
        val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>
            *{box-sizing:border-box}html,body{height:100%;margin:0;font-family:Arial,sans-serif;background:#fff0f3;color:#5c3440}
            #top{height:145px;background:#ff6584;color:white;padding:24px 24px 16px;border-radius:0 0 28px 28px}
            #top small{letter-spacing:1.4px;font-weight:bold}#top h1{margin:8px 0 4px;font-size:28px}#status{opacity:.92}
            #map{height:calc(100% - 145px);position:relative;overflow:hidden;background:#f3f5f5}
            #grid{position:absolute;inset:0;background:linear-gradient(35deg,transparent 48%,#dfe7e5 49%,#dfe7e5 51%,transparent 52%),linear-gradient(-35deg,transparent 48%,#dfe7e5 49%,#dfe7e5 51%,transparent 52%);background-size:110px 90px;opacity:.9}
            svg{position:absolute;inset:0;width:100%;height:100%}.route{fill:none;stroke:#ff6584;stroke-width:7;stroke-linecap:round;stroke-linejoin:round;filter:drop-shadow(0 2px 2px #9a6d78)}
            .dot{fill:#fff;stroke:#ff6584;stroke-width:5}.pin{fill:#5c3440}.card{position:absolute;left:18px;right:18px;bottom:18px;background:white;border-radius:18px;padding:14px 18px;box-shadow:0 3px 14px #0002;font-size:14px}.empty{text-align:center;margin-top:35%;color:#9a6d78}
        </style></head><body><div id="top"><small>LIVE WORKOUT</small><h1>Real-time route</h1><div id="status">Waiting for your first GPS location…</div></div><div id="map"><div id="grid"></div><svg id="route" viewBox="0 0 100 100" preserveAspectRatio="none"><polyline id="line" class="route" points=""></polyline><circle id="start" class="dot" r="2.8" cx="0" cy="0" visibility="hidden"></circle><circle id="current" class="pin" r="3.3" cx="0" cy="0" visibility="hidden"></circle></svg><div id="empty" class="empty">Move outdoors to receive a GPS route</div><div class="card"><b id="count">0 GPS points</b><br><span>Route updates appear here as your location changes.</span></div></div><script>
        const pts=[$json];let minA,maxA,minB,maxB;function draw(){if(!pts.length)return;minA=Math.min(...pts.map(x=>x[0]));maxA=Math.max(...pts.map(x=>x[0]));minB=Math.min(...pts.map(x=>x[1]));maxB=Math.max(...pts.map(x=>x[1]));let da=Math.max(maxA-minA,.0002),db=Math.max(maxB-minB,.0002);let xy=pts.map(x=>[((x[1]-minB)/db)*84+8,(1-(x[0]-minA)/da)*78+8]);document.getElementById('line').setAttribute('points',xy.map(x=>x.join(',')).join(' '));let s=xy[0],c=xy[xy.length-1];Object.entries({start:s,current:c}).forEach(([id,p])=>{let e=document.getElementById(id);e.setAttribute('cx',p[0]);e.setAttribute('cy',p[1]);e.setAttribute('visibility','visible')});document.getElementById('empty').style.display='none';document.getElementById('count').textContent=pts.length+' GPS points';document.getElementById('status').textContent='GPS route is updating live'}function addPoint(a,b){pts.push([a,b]);draw()}draw();</script></body></html>"""
        map.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        if (live) ContextCompat.registerReceiver(this, receiver, IntentFilter(TrackingService.ACTION_UPDATE), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() { if (intent.getBooleanExtra(EXTRA_LIVE, false)) unregisterReceiver(receiver); super.onDestroy() }
    companion object { const val EXTRA_POINTS = "route_points"; const val EXTRA_LIVE = "live_route" }
}

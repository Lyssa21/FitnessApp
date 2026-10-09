package com.example.fitnessapp
import android.content.*
import android.os.Bundle
import android.webkit.WebView
import androidx.core.content.ContextCompat
import com.example.fitnessapp.tracking.TrackingService
import org.json.JSONArray
class RouteMapActivity : BaseActivity() {
 private lateinit var map: WebView
 private val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){if(i?.action==TrackingService.ACTION_UPDATE){val a=i.getDoubleExtra("latitude",Double.NaN);val b=i.getDoubleExtra("longitude",Double.NaN);if(a.isFinite()&&b.isFinite())map.evaluateJavascript("addPoint($a,$b)",null)}}}
 override fun onCreate(s:Bundle?){super.onCreate(s);map=WebView(this).apply{settings.javaScriptEnabled=true};setContentView(map);val live=intent.getBooleanExtra(EXTRA_LIVE,false);val j=runCatching{JSONArray(intent.getStringExtra(EXTRA_POINTS).orEmpty())}.getOrNull()?:JSONArray();val p=buildList{for(x in 0 until j.length())j.optJSONObject(x)?.let{add("[${it.optDouble("lat")},${it.optDouble("lng")}]")}}.joinToString(",");val h="""<html><head><meta name="viewport" content="width=device-width"><link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"><script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><style>html,body,#map{height:100%;margin:0}</style></head><body><div id="map"></div><script>const p=[$p],m=L.map('map').setView(p[0]||[16.8661,96.1951],p.length?15:6);L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(m);const l=L.polyline(p,{color:'#ff6584',weight:5}).addTo(m);let q=p.length?L.marker(p[p.length-1]).addTo(m):null;function addPoint(a,b){let x=[a,b];l.addLatLng(x);if(!q)q=L.marker(x).addTo(m);else q.setLatLng(x);m.setView(x,16)}</script></body></html>""";map.loadDataWithBaseURL("https://www.openstreetmap.org",h,"text/html","UTF-8",null);if(live)ContextCompat.registerReceiver(this,receiver,IntentFilter(TrackingService.ACTION_UPDATE),ContextCompat.RECEIVER_NOT_EXPORTED)}
 override fun onDestroy(){if(intent.getBooleanExtra(EXTRA_LIVE,false))unregisterReceiver(receiver);super.onDestroy()}
 companion object{const val EXTRA_POINTS="route_points";const val EXTRA_LIVE="live_route"}
}

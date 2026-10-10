package com.example.fitnessapp

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fitnessapp.data.SessionManager
import com.example.fitnessapp.data.WorkoutRepository
import com.example.fitnessapp.model.Workout

class HistoryActivity : BaseActivity() {
    private lateinit var repository: WorkoutRepository
    private lateinit var session: SessionManager
    private lateinit var items: MutableList<Workout>
    private lateinit var adapter: WorkoutAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        session = SessionManager(this); repository = WorkoutRepository(this); items = mutableListOf()
        findViewById<TextView>(R.id.historyBackButton).setOnClickListener { finish() }
        adapter = WorkoutAdapter(items, { showDelete(it) }, { showDetails(it) })
        findViewById<RecyclerView>(R.id.historyRecyclerView).apply { layoutManager = LinearLayoutManager(this@HistoryActivity); adapter = this@HistoryActivity.adapter }
        repository.loadFromServer(session.userId, { list -> items.clear(); items.addAll(list); adapter.notifyDataSetChanged(); findViewById<TextView>(R.id.historyEmptyTextView).visibility = if (items.isEmpty()) TextView.VISIBLE else TextView.GONE }) { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
    }
    private fun showDelete(workout: Workout) { AlertDialog.Builder(this).setTitle("Delete workout?").setMessage("Remove this ${workout.activityName.lowercase()} workout?").setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> repository.deleteWorkout(workout, session.userId) { ok, msg -> if (ok) { items.remove(workout); adapter.notifyDataSetChanged() }; Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() } }.show() }
    private fun showDetails(workout: Workout) { AlertDialog.Builder(this).setTitle("${workout.activityName} details").setMessage("Date: ${workout.date}\nDuration: ${workout.durationMinutes} min\nDistance: ${"%.2f".format(workout.distanceKm ?: 0.0)} km\nSteps: ${workout.stepsCount ?: "Not available"}\nCalories: ${workout.calories} kcal").apply { if (!workout.routePointsJson.isNullOrBlank()) setNegativeButton("View route") { _, _ -> startActivity(Intent(this@HistoryActivity, RouteMapActivity::class.java).putExtra(RouteMapActivity.EXTRA_POINTS, workout.routePointsJson)) } }.setPositiveButton("Close", null).show() }
}

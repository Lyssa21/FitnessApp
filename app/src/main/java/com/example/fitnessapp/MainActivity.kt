package com.example.fitnessapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.CancellationSignal
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fitnessapp.data.SessionManager
import com.example.fitnessapp.data.WorkoutRepository
import com.example.fitnessapp.model.Workout
import com.example.fitnessapp.model.WorkoutFactory
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var repository: WorkoutRepository
    private lateinit var session: SessionManager
    private lateinit var adapter: WorkoutAdapter
    private lateinit var workouts: MutableList<Workout>

    private lateinit var stepsText: TextView
    private lateinit var caloriesText: TextView
    private lateinit var locationText: TextView
    private lateinit var goalText: TextView
    private lateinit var goalProgress: ProgressBar

    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) readCurrentLocation()
        else Toast.makeText(this, "Location permission was not granted", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        session = SessionManager(this)
        repository = WorkoutRepository(this)
        workouts = repository.getWorkouts()

        stepsText = findViewById(R.id.stepsTextView)
        caloriesText = findViewById(R.id.caloriesTextView)
        locationText = findViewById(R.id.locationTextView)
        goalText = findViewById(R.id.goalTextView)
        goalProgress = findViewById(R.id.calorieGoalProgressBar)

        setupWorkoutList()
        updateSummary()

        findViewById<FloatingActionButton>(R.id.btnAddWorkout).setOnClickListener {
            showAddWorkoutDialog()
        }
        findViewById<Button>(R.id.locationButton).setOnClickListener { requestLocation() }
        findViewById<Button>(R.id.editGoalButton).setOnClickListener { showGoalDialog() }
        findViewById<Button>(R.id.analyticsButton).setOnClickListener {
            startActivity(Intent(this, AnalyticsActivity::class.java))
        }
        findViewById<Button>(R.id.logoutButton).setOnClickListener { logOut() }

        // Refresh local information with records from MySQL when signed in online.
        repository.loadFromServer(session.userId) { serverWorkouts ->
            workouts.clear()
            workouts.addAll(serverWorkouts)
            adapter.notifyDataSetChanged()
            updateSummary()
        }
        repository.loadGoalFromServer(session.userId) { updateSummary() }
    }

    private fun setupWorkoutList() {
        val recyclerView = findViewById<RecyclerView>(R.id.workoutRecyclerView)
        adapter = WorkoutAdapter(workouts) { workout -> showDeleteDialog(workout) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun showAddWorkoutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_workout, null)
        val activitySpinner = dialogView.findViewById<Spinner>(R.id.activitySpinner)
        val durationInput = dialogView.findViewById<EditText>(R.id.durationEditText)
        val caloriesInput = dialogView.findViewById<EditText>(R.id.caloriesEditText)
        val dialogLocation = dialogView.findViewById<TextView>(R.id.dialogLocationTextView)

        val activityTypes = listOf("Running", "Cycling", "Weightlifting", "Yoga")
        activitySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            activityTypes
        )
        dialogLocation.text = if (currentLatitude != null && currentLongitude != null) {
            "Location: %.4f, %.4f".format(currentLatitude, currentLongitude)
        } else {
            "No location selected (optional)"
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add a workout")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val duration = durationInput.text.toString().toIntOrNull()
                val calories = caloriesInput.text.toString().toIntOrNull()
                if (duration == null || duration <= 0 || calories == null || calories <= 0) {
                    Toast.makeText(this, "Enter a valid duration and calories", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val workout = WorkoutFactory.create(
                    type = activitySpinner.selectedItem.toString(),
                    duration = duration,
                    calories = calories,
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                    latitude = currentLatitude,
                    longitude = currentLongitude
                )
                workouts.add(0, workout)
                adapter.notifyItemInserted(0)
                updateSummary()
                repository.addWorkout(workout, session.userId) { _, message ->
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showDeleteDialog(workout: Workout) {
        AlertDialog.Builder(this)
            .setTitle("Delete workout?")
            .setMessage("Remove this ${workout.activityName.lowercase()} workout from the list?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                val position = workouts.indexOfFirst { it.id == workout.id }
                if (position >= 0) {
                    workouts.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    repository.deleteWorkout(workout, session.userId) { success, message ->
                        if (!success) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                    updateSummary()
                }
            }
            .show()
    }

    private fun showGoalDialog() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(repository.getDailyGoal().toString())
            selectAll()
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Daily calorie goal")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val goal = input.text.toString().toIntOrNull()
                if (goal == null || goal <= 0) {
                    input.error = "Enter a goal greater than zero"
                } else {
                    repository.saveDailyGoal(goal, session.userId) { _, message ->
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                    updateSummary()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun updateSummary() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val todaysWorkouts = workouts.filter { it.date == today }
        val calories = todaysWorkouts.sumOf { it.calories }
        val estimatedSteps = todaysWorkouts
            .filter { it.activityName == "Running" }
            .sumOf { it.durationMinutes * 100 }
        val goal = repository.getDailyGoal()

        stepsText.text = "%,d".format(estimatedSteps)
        caloriesText.text = "$calories kcal"
        goalProgress.max = goal
        goalProgress.progress = calories.coerceAtMost(goal)
        goalText.text = "Daily goal: $goal kcal • ${((calories * 100.0 / goal).toInt())}% complete"
    }

    private fun requestLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            readCurrentLocation()
        } else {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @Suppress("MissingPermission")
    private fun readCurrentLocation() {
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> {
                Toast.makeText(this, "Please turn on device location", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val lastLocation = manager.getLastKnownLocation(provider)
        if (lastLocation != null) {
            showLocation(lastLocation)
        } else {
            locationText.text = "Finding your location…"
            LocationManagerCompat.getCurrentLocation(
                manager,
                provider,
                CancellationSignal(),
                ContextCompat.getMainExecutor(this)
            ) { location ->
                if (location != null) showLocation(location)
                else locationText.text = "Location is currently unavailable"
            }
        }
    }

    private fun showLocation(location: Location) {
        currentLatitude = location.latitude
        currentLongitude = location.longitude
        locationText.text = "Current location: %.4f, %.4f".format(
            location.latitude,
            location.longitude
        )
    }

    private fun logOut() {
        session.logout()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}

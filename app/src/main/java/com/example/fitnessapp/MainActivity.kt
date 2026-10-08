package com.example.fitnessapp

import android.Manifest
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.app.DatePickerDialog
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
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.core.location.LocationManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fitnessapp.data.SessionManager
import com.example.fitnessapp.data.WorkoutRepository
import com.example.fitnessapp.model.Workout
import com.example.fitnessapp.model.WorkoutFactory
import com.example.fitnessapp.tracking.TrackingService
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : BaseActivity() {
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
    private var trackingDistanceKm = 0.0
    private var trackingMinutes = 0L
    private var trackingSteps = 0
    private var trackingActivityType = "Running"

    private val trackingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == TrackingService.ACTION_UPDATE) {
                trackingDistanceKm = intent.getDoubleExtra("distance_km", 0.0)
                trackingMinutes = intent.getLongExtra("minutes", 0L)
                trackingSteps = intent.getIntExtra("steps", 0)
                findViewById<TextView>(R.id.trackingStatusTextView).text =
                    "Tracking: %.2f km • %d steps • %d min".format(trackingDistanceKm, trackingSteps, trackingMinutes)
            }
        }
    }

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
        findViewById<Spinner>(R.id.trackingTypeSpinner).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Running", "Walking"))
        updateSummary()

        findViewById<FloatingActionButton>(R.id.btnAddWorkout).setOnClickListener {
            showAddWorkoutDialog()
        }
        findViewById<Button>(R.id.locationButton).setOnClickListener { requestLocation() }
        findViewById<Button>(R.id.startTrackingButton).setOnClickListener { startGpsTracking() }
        findViewById<Button>(R.id.stopTrackingButton).setOnClickListener { stopGpsTracking() }
        findViewById<Button>(R.id.editGoalButton).setOnClickListener { showGoalDialog() }
        findViewById<Button>(R.id.analyticsButton).setOnClickListener {
            startActivity(Intent(this, AnalyticsActivity::class.java))
        }
        findViewById<Button>(R.id.logoutButton).setOnClickListener { logOut() }

        repository.loadFromServer(session.userId, { serverWorkouts ->
            workouts.clear()
            workouts.addAll(serverWorkouts)
            adapter.notifyDataSetChanged()
            updateSummary()
        }) { message -> Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
        repository.loadGoalFromServer(session.userId) { updateSummary() }
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(this, trackingReceiver, IntentFilter(TrackingService.ACTION_UPDATE), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        unregisterReceiver(trackingReceiver)
        super.onPause()
    }

    private fun startGpsTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocation(); return
        }
        trackingDistanceKm = 0.0; trackingMinutes = 0; trackingSteps = 0
        trackingActivityType = findViewById<Spinner>(R.id.trackingTypeSpinner).selectedItem.toString()
        startForegroundService(this, Intent(this, TrackingService::class.java))
        findViewById<Button>(R.id.startTrackingButton).isEnabled = false
        findViewById<Button>(R.id.stopTrackingButton).isEnabled = true
        findViewById<TextView>(R.id.trackingStatusTextView).text = "Starting GPS tracking..."
    }

    private fun stopGpsTracking() {
        stopService(Intent(this, TrackingService::class.java))
        findViewById<Button>(R.id.startTrackingButton).isEnabled = true
        findViewById<Button>(R.id.stopTrackingButton).isEnabled = false
        if (trackingMinutes > 0 && trackingDistanceKm > 0.0) {
            val workout = WorkoutFactory.create(trackingActivityType, duration = trackingMinutes.toInt(), calories = (trackingMinutes * 8).toInt(),
                date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()), distanceKm = trackingDistanceKm,
                latitude = currentLatitude, longitude = currentLongitude, notes = "GPS tracked; estimated steps: $trackingSteps")
            repository.addWorkout(workout, session.userId) { success, message ->
                if (success) { workouts.add(0, workout); adapter.notifyItemInserted(0); updateSummary() }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<TextView>(R.id.trackingStatusTextView).text = "GPS tracking stopped"
    }

    private fun setupWorkoutList() {
        val recyclerView = findViewById<RecyclerView>(R.id.workoutRecyclerView)
        adapter = WorkoutAdapter(workouts, { workout -> showDeleteDialog(workout) }, { workout -> showWorkoutDetails(workout) })
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun showAddWorkoutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_workout, null)
        val activitySpinner = dialogView.findViewById<Spinner>(R.id.activitySpinner)
        val durationInput = dialogView.findViewById<EditText>(R.id.durationEditText)
        val caloriesInput = dialogView.findViewById<EditText>(R.id.caloriesEditText)
        val distanceInput = dialogView.findViewById<EditText>(R.id.distanceEditText)
        val exerciseInput = dialogView.findViewById<EditText>(R.id.exerciseNameEditText)
        val weightInput = dialogView.findViewById<EditText>(R.id.weightEditText)
        val setsInput = dialogView.findViewById<EditText>(R.id.setsEditText)
        val repsInput = dialogView.findViewById<EditText>(R.id.repsEditText)
        val notesInput = dialogView.findViewById<EditText>(R.id.notesEditText)
        val dateButton = dialogView.findViewById<Button>(R.id.dateButton)
        var selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        dateButton.text = "Date: $selectedDate"
        dateButton.setOnClickListener {
            val calendar = java.util.Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                selectedDate = "%04d-%02d-%02d".format(year, month + 1, day)
                dateButton.text = "Date: $selectedDate"
            }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
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
                val enteredCalories = caloriesInput.text.toString().toIntOrNull()
                if (duration == null || duration <= 0) {
                    Toast.makeText(this, "Enter a valid duration", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val type = activitySpinner.selectedItem.toString()
                val calories = enteredCalories ?: (duration * when (type) { "Running", "Walking" -> 9; "Cycling" -> 8; "Weightlifting" -> 6; else -> 4 })
                val distance = distanceInput.text.toString().toDoubleOrNull()
                val weight = weightInput.text.toString().toDoubleOrNull()
                val sets = setsInput.text.toString().toIntOrNull()
                val reps = repsInput.text.toString().toIntOrNull()
                if (type in listOf("Running", "Cycling") && (distance == null || distance <= 0)) {
                    Toast.makeText(this, "Enter distance for cardio activities", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (type == "Weightlifting" && (exerciseInput.text.toString().isBlank() || weight == null || sets == null || reps == null || weight <= 0 || sets <= 0 || reps <= 0)) {
                    Toast.makeText(this, "Enter exercise, weight, sets and reps", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val workout = WorkoutFactory.create(
                    type = type,
                    duration = duration,
                    calories = calories,
                    date = selectedDate,
                    latitude = currentLatitude,
                    longitude = currentLongitude
                    , distanceKm = distance
                    , exerciseName = exerciseInput.text.toString().trim().ifBlank { null }
                    , weightKg = weight
                    , sets = sets
                    , reps = reps
                    , notes = notesInput.text.toString().trim().ifBlank { null }
                )
                repository.addWorkout(workout, session.userId) { success, message ->
                    if (success) {
                        workouts.add(0, workout)
                        adapter.notifyItemInserted(0)
                        updateSummary()
                    }
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
                    repository.deleteWorkout(workout, session.userId) { success, message ->
                        if (success) {
                            workouts.removeAt(position)
                            adapter.notifyItemRemoved(position)
                            updateSummary()
                        } else Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    private fun showWorkoutDetails(workout: Workout) {
        val speed = if ((workout.durationMinutes) > 0) (workout.distanceKm ?: 0.0) / (workout.durationMinutes / 60.0) else 0.0
        val volume = (workout.weightKg ?: 0.0) * (workout.sets ?: 0) * (workout.reps ?: 0)
        AlertDialog.Builder(this)
            .setTitle("${workout.activityName} details")
            .setMessage("Date: ${workout.date}\nDuration: ${workout.durationMinutes} min\nDistance: ${"%.2f".format(workout.distanceKm ?: 0.0)} km\nAverage speed: ${"%.2f".format(speed)} km/h\nCalories: ${workout.calories} kcal\nLifting volume: ${"%.1f".format(volume)} kg\nNotes: ${workout.notes ?: "None"}")
            .setPositiveButton("Close", null)
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

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
import android.view.View
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
import com.example.fitnessapp.network.ApiClient
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
    private var trackingCalories = 0
    private var trackingActivityType = "Running"
    private var trackingRouteJson = "[]"
    private var pendingTrackingWorkout: Workout? = null
    private var pendingGpsStart = false

    private val trackingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == TrackingService.ACTION_UPDATE) {
                trackingDistanceKm = intent.getDoubleExtra("distance_km", 0.0)
                trackingMinutes = intent.getLongExtra("minutes", 0L)
                trackingSteps = intent.getIntExtra("steps", 0)
                trackingCalories = intent.getIntExtra("calories", 0)
                trackingRouteJson = intent.getStringExtra("route_points") ?: trackingRouteJson
                currentLatitude = intent.getDoubleExtra("latitude", 0.0)
                currentLongitude = intent.getDoubleExtra("longitude", 0.0)
                findViewById<TextView>(R.id.trackingStatusTextView).text =
                    "Tracking: %.2f km • %d estimated GPS steps • %d min • %d kcal".format(trackingDistanceKm, trackingSteps, trackingMinutes, trackingCalories)
            }
        }
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingGpsStart) { pendingGpsStart = false; startGpsTracking() }
        else if (granted) readCurrentLocation()
        else Toast.makeText(this, "Location permission was not granted", Toast.LENGTH_SHORT).show()
    }
    private val notificationPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        session = SessionManager(this)
        ApiClient.authToken = session.token
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
        findViewById<Button>(R.id.saveTrackingButton).setOnClickListener { saveTrackingProgress() }
        findViewById<Button>(R.id.editGoalButton).setOnClickListener { showGoalDialog() }
        findViewById<Button>(R.id.analyticsButton).setOnClickListener {
            startActivity(Intent(this, AnalyticsActivity::class.java))
        }
        findViewById<Button>(R.id.logoutButton).setOnClickListener { logOut() }
        findViewById<Button>(R.id.profileButton).setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.workoutSwipeRefresh).setOnRefreshListener {
            repository.loadFromServer(session.userId, { list ->
                workouts.clear(); workouts.addAll(list); adapter.notifyDataSetChanged(); updateSummary()
                findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.workoutSwipeRefresh).isRefreshing = false
            }) { message ->
                findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.workoutSwipeRefresh).isRefreshing = false
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }

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
            pendingGpsStart = true
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        trackingDistanceKm = 0.0; trackingMinutes = 0; trackingSteps = 0; trackingCalories = 0; trackingRouteJson = "[]"; pendingTrackingWorkout = null
        trackingActivityType = findViewById<Spinner>(R.id.trackingTypeSpinner).selectedItem.toString()
        findViewById<Button>(R.id.saveTrackingButton).visibility = android.view.View.GONE
        startForegroundService(this, Intent(this, TrackingService::class.java).putExtra("activity_type", trackingActivityType))
        findViewById<Button>(R.id.startTrackingButton).isEnabled = false
        findViewById<Button>(R.id.stopTrackingButton).isEnabled = true
        findViewById<TextView>(R.id.trackingStatusTextView).text = "Starting GPS tracking..."
    }

    private fun stopGpsTracking() {
        stopService(Intent(this, TrackingService::class.java))
        findViewById<Button>(R.id.startTrackingButton).isEnabled = true
        findViewById<Button>(R.id.stopTrackingButton).isEnabled = false
        if (trackingMinutes > 0) {
            pendingTrackingWorkout = WorkoutFactory.create(trackingActivityType, duration = trackingMinutes.toInt(), calories = trackingCalories,
                date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()), distanceKm = trackingDistanceKm,
                latitude = currentLatitude, longitude = currentLongitude, notes = "GPS tracked; estimated steps: $trackingSteps").apply {
                    routePointsJson = trackingRouteJson
                    stepsCount = trackingSteps
                }
            findViewById<Button>(R.id.saveTrackingButton).visibility = android.view.View.VISIBLE
            findViewById<TextView>(R.id.trackingStatusTextView).text = "Ready: %.2f km • %d estimated GPS steps • %d kcal. Tap Save progress.".format(trackingDistanceKm, trackingSteps, trackingCalories)
        } else findViewById<TextView>(R.id.trackingStatusTextView).text = "No GPS fix received. Enable location and try outdoors."
    }

    private fun saveTrackingProgress() {
        val workout = pendingTrackingWorkout ?: return
        val button = findViewById<Button>(R.id.saveTrackingButton)
        button.isEnabled = false
        repository.addWorkout(workout, session.userId) { success, message ->
            if (success) {
                workouts.add(0, workout); adapter.notifyItemInserted(0); updateSummary(); pendingTrackingWorkout = null
                button.visibility = android.view.View.GONE
                ProgressNotifier.show(this, "GPS workout saved", "%.2f km • %d estimated steps".format(trackingDistanceKm, trackingSteps))
            }
            button.isEnabled = true
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupWorkoutList() {
        val recyclerView = findViewById<RecyclerView>(R.id.workoutRecyclerView)
        adapter = WorkoutAdapter(workouts, { workout -> showDeleteDialog(workout) }, { workout -> showWorkoutDetails(workout) })
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun showAddWorkoutDialog(editWorkout: Workout? = null) {
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
        val cardioFields = listOf(distanceInput)
        val strengthFields = listOf(exerciseInput, weightInput, setsInput, repsInput)
        fun updateActivityFields(type: String) {
            cardioFields.forEach { it.visibility = if (type == "Running" || type == "Cycling") View.VISIBLE else View.GONE }
            strengthFields.forEach { it.visibility = if (type == "Weightlifting") View.VISIBLE else View.GONE }
            caloriesInput.visibility = View.GONE // Calories are estimated automatically from activity and duration.
        }
        activitySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateActivityFields(activitySpinner.getItemAtPosition(position).toString())
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
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
        editWorkout?.let { existing ->
            val index = activityTypes.indexOf(existing.activityName)
            if (index >= 0) activitySpinner.setSelection(index)
            durationInput.setText(existing.durationMinutes.toString())
            caloriesInput.setText(existing.calories.toString())
            distanceInput.setText(existing.distanceKm?.toString().orEmpty())
            exerciseInput.setText(existing.exerciseName.orEmpty())
            weightInput.setText(existing.weightKg?.toString().orEmpty())
            setsInput.setText(existing.sets?.toString().orEmpty())
            repsInput.setText(existing.reps?.toString().orEmpty())
            notesInput.setText(existing.notes.orEmpty())
            selectedDate = existing.date
            dateButton.text = "Date: $selectedDate"
        }
        updateActivityFields(editWorkout?.activityName ?: activityTypes.first())
        dialogLocation.text = if (currentLatitude != null && currentLongitude != null) {
            "Location: %.4f, %.4f".format(currentLatitude, currentLongitude)
        } else {
            "No location selected (optional)"
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (editWorkout == null) "Add a workout" else "Edit workout")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (editWorkout == null) "Save" else "Update", null)
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
                val save: ((Boolean, String) -> Unit) -> Unit = if (editWorkout == null) {
                    { callback -> repository.addWorkout(workout, session.userId, callback) }
                } else {
                    workout.id = editWorkout.id
                    { callback -> repository.updateWorkout(workout, session.userId, callback) }
                }
                save { success, message ->
                    if (success) {
                        if (editWorkout == null) workouts.add(0, workout)
                        else {
                            val editIndex = workouts.indexOfFirst { it.id == editWorkout.id }
                            if (editIndex >= 0) workouts[editIndex] = workout
                        }
                        adapter.notifyDataSetChanged()
                        updateSummary()
                        ProgressNotifier.show(this, if (editWorkout == null) "Workout saved" else "Workout updated", "Your ${workout.activityName.lowercase()} progress was saved online.")
                        notifyGoalAchievement()
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
            .setMessage("Date: ${workout.date}\nDuration: ${workout.durationMinutes} min\nDistance: ${"%.2f".format(workout.distanceKm ?: 0.0)} km\nAverage speed: ${"%.2f".format(speed)} km/h\nEstimated GPS steps: ${workout.stepsCount ?: "Not available"}\nCalories: ${workout.calories} kcal\nLifting volume: ${"%.1f".format(volume)} kg\nNotes: ${workout.notes ?: "None"}")
            .setNeutralButton("Edit") { _, _ -> showAddWorkoutDialog(workout) }
            .apply { if (!workout.routePointsJson.isNullOrBlank()) setNegativeButton("View route") { _, _ -> startActivity(Intent(this@MainActivity, RouteMapActivity::class.java).putExtra(RouteMapActivity.EXTRA_POINTS, workout.routePointsJson)) } }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showGoalDialog() {
        val container = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(36, 8, 36, 0) }
        val typePicker = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Daily calorie goal", "Weekly distance goal")) }
        typePicker.setSelection(if (repository.goalType == "distance") 1 else 0)
        val input = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL; setText(repository.goalTarget.toString()); hint = "Target value" }
        val deadlineButton = Button(this).apply { text = repository.goalDeadline?.let { "Deadline: $it" } ?: "Optional deadline" }
        var deadline: String? = repository.goalDeadline
        deadlineButton.setOnClickListener {
            val c = java.util.Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d -> deadline = "%04d-%02d-%02d".format(y, m + 1, d); deadlineButton.text = "Deadline: $deadline" }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        container.addView(typePicker); container.addView(input); container.addView(deadlineButton)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Fitness goal")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val target = input.text.toString().toDoubleOrNull()
                if (target == null || target <= 0) {
                    input.error = "Enter a target greater than zero"
                } else {
                    val type = if (typePicker.selectedItemPosition == 1) "distance" else "calories"
                    repository.saveStructuredGoal(type, target, deadline, session.userId) { success, message ->
                        if (success) updateSummary()
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
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
        val estimatedSteps = todaysWorkouts.sumOf { it.stepsCount ?: 0 }
        val goal = repository.goalTarget
        val progressValue = if (repository.goalType == "distance") {
            val weekStart = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -6) }.time
            workouts.filter { it.activityName in listOf("Running", "Walking", "Cycling") && runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it.date) }.getOrNull()?.after(weekStart) == true }.sumOf { it.distanceKm ?: 0.0 }
        } else calories.toDouble()

        stepsText.text = "%,d".format(estimatedSteps)
        caloriesText.text = "$calories kcal"
        goalProgress.max = 100
        val percent = if (goal > 0) (progressValue * 100.0 / goal).toInt().coerceIn(0, 100) else 0
        goalProgress.progress = percent
        val unit = if (repository.goalType == "distance") "km this week" else "kcal today"
        goalText.text = "Goal: ${"%.1f".format(goal)} $unit • $percent% complete${repository.goalDeadline?.let { " • due $it" } ?: ""}"
    }

    private fun notifyGoalAchievement() {
        val target = repository.goalTarget
        val progress = if (repository.goalType == "distance") {
            val weekStart = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -6) }.time
            workouts.filter { it.activityName in listOf("Running", "Walking", "Cycling") && runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it.date) }.getOrNull()?.after(weekStart) == true }.sumOf { it.distanceKm ?: 0.0 }
        } else {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            workouts.filter { it.date == today }.sumOf { it.calories }.toDouble()
        }
        if (target > 0 && progress >= target) {
            ProgressNotifier.show(this, "Fitness goal reached!", "You reached your ${repository.goalType} target.")
        }
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

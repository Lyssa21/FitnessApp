package com.example.fitnessapp

import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import com.example.fitnessapp.data.WorkoutRepository
import com.example.fitnessapp.data.SessionManager
import com.example.fitnessapp.model.Workout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AnalyticsActivity : BaseActivity() {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var allWorkouts: List<Workout> = emptyList()
    private var selectedPeriod = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
        val periodSpinner = findViewById<Spinner>(R.id.periodSpinner)
        periodSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Last 7 days", "This month", "All time"))
        periodSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { selectedPeriod = position; showAnalytics() }
        }
        val session = SessionManager(this)
        WorkoutRepository(this).loadFromServer(session.userId, { items -> allWorkouts = items; showAnalytics() }) {
            findViewById<TextView>(R.id.insightTextView).text = it
        }
    }

    private fun showAnalytics() {
        val now = Calendar.getInstance()
        val start = Calendar.getInstance().apply {
            if (selectedPeriod == 0) add(Calendar.DAY_OF_YEAR, -6) else if (selectedPeriod == 1) set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val periodWorkouts = allWorkouts.filter { workout ->
            if (selectedPeriod == 2) return@filter true
            runCatching { dateFormat.parse(workout.date) }
                .getOrNull()
                ?.let { !it.before(start) } == true
        }

        val totalMinutes = periodWorkouts.sumOf { it.durationMinutes }
        val totalCalories = periodWorkouts.sumOf { it.calories }
        val totalDistance = periodWorkouts.sumOf { it.distanceKm ?: 0.0 }
        val liftingVolume = periodWorkouts.sumOf { (it.weightKg ?: 0.0) * (it.sets ?: 0) * (it.reps ?: 0) }
        val activeDays = periodWorkouts.map { it.date }.distinct().size
        val breakdown = periodWorkouts
            .groupBy { it.activityName }
            .mapValues { (_, items) -> items.sumOf { it.durationMinutes } }

        findViewById<TextView>(R.id.weeklyMinutesTextView).text = "$totalMinutes min"
        findViewById<TextView>(R.id.weeklyCaloriesTextView).text = "$totalCalories kcal"
        findViewById<TextView>(R.id.activeDaysTextView).text = "$activeDays days"
        val activeDaysLabel = when (selectedPeriod) {
            0 -> "Active days / 7"
            1 -> "Active days / ${now.getActualMaximum(Calendar.DAY_OF_MONTH)}"
            else -> "Active days total"
        }
        findViewById<TextView>(R.id.activeDaysLabelTextView).text = activeDaysLabel
        findViewById<TextView>(R.id.periodSubtitleTextView).text = when (selectedPeriod) {
            0 -> "Your activity from the last seven days"
            1 -> "Your activity so far this month"
            else -> "Your activity across all recorded time"
        }
        findViewById<TextView>(R.id.detailMetricsTextView).text =
            "${periodWorkouts.size} workouts     ·     %.2f km\n%.1f kg lifting volume     ·     %.2f km/h avg. speed".format(
                totalDistance, liftingVolume,
                if (totalMinutes > 0) totalDistance / (totalMinutes / 60.0) else 0.0
            )

        setActivityProgress(R.id.runningProgressBar, R.id.runningValueTextView, breakdown["Running"] ?: 0)
        setActivityProgress(R.id.walkingProgressBar, R.id.walkingValueTextView, breakdown["Walking"] ?: 0)
        setActivityProgress(R.id.cyclingProgressBar, R.id.cyclingValueTextView, breakdown["Cycling"] ?: 0)
        setActivityProgress(
            R.id.weightliftingProgressBar,
            R.id.weightliftingValueTextView,
            breakdown["Weightlifting"] ?: 0
        )
        setActivityProgress(R.id.yogaProgressBar, R.id.yogaValueTextView, breakdown["Yoga"] ?: 0)

        val favourite = breakdown.maxByOrNull { it.value }?.key
        val insight = when {
            periodWorkouts.isEmpty() ->
                "No workouts in this period yet. Log an activity to see your progress."
            totalMinutes >= 150 ->
                "Amazing consistency! You recorded $totalMinutes active minutes" +
                    if (favourite != null) ", with $favourite as your leading activity." else "."
            else -> {
                val remaining = 150 - totalMinutes
                "You are $remaining minutes away from 150 active minutes" +
                    if (favourite != null) ". Your strongest activity is $favourite." else "."
            }
        }
        findViewById<TextView>(R.id.insightTextView).text = insight
    }

    private fun setActivityProgress(progressId: Int, valueId: Int, minutes: Int) {
        findViewById<ProgressBar>(progressId).progress = minutes.coerceAtMost(150)
        findViewById<TextView>(valueId).text = "$minutes min"
    }
}

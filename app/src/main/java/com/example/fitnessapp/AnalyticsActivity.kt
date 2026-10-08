package com.example.fitnessapp

import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.example.fitnessapp.data.WorkoutRepository
import com.example.fitnessapp.data.SessionManager
import com.example.fitnessapp.model.Workout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AnalyticsActivity : BaseActivity() {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
        val session = SessionManager(this)
        WorkoutRepository(this).loadFromServer(session.userId, ::showWeeklyAnalytics) {
            findViewById<TextView>(R.id.insightTextView).text = it
        }
    }

    private fun showWeeklyAnalytics(allWorkouts: List<Workout>) {
        val start = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -6)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val weeklyWorkouts = allWorkouts.filter { workout ->
            runCatching { dateFormat.parse(workout.date) }
                .getOrNull()
                ?.let { !it.before(start) } == true
        }

        val totalMinutes = weeklyWorkouts.sumOf { it.durationMinutes }
        val totalCalories = weeklyWorkouts.sumOf { it.calories }
        val totalDistance = weeklyWorkouts.sumOf { it.distanceKm ?: 0.0 }
        val liftingVolume = weeklyWorkouts.sumOf { (it.weightKg ?: 0.0) * (it.sets ?: 0) * (it.reps ?: 0) }
        val activeDays = weeklyWorkouts.map { it.date }.distinct().size
        val breakdown = weeklyWorkouts
            .groupBy { it.activityName }
            .mapValues { (_, items) -> items.sumOf { it.durationMinutes } }

        findViewById<TextView>(R.id.weeklyMinutesTextView).text = "$totalMinutes min"
        findViewById<TextView>(R.id.weeklyCaloriesTextView).text = "$totalCalories kcal"
        findViewById<TextView>(R.id.activeDaysTextView).text = "$activeDays of 7 days"
        findViewById<TextView>(R.id.detailMetricsTextView).text =
            "Distance: %.2f km\nLifting volume: %.1f kg\nAverage cardio speed: %.2f km/h".format(
                totalDistance, liftingVolume,
                if (totalMinutes > 0) totalDistance / (totalMinutes / 60.0) else 0.0
            )

        setActivityProgress(R.id.runningProgressBar, R.id.runningValueTextView, breakdown["Running"] ?: 0)
        setActivityProgress(R.id.cyclingProgressBar, R.id.cyclingValueTextView, breakdown["Cycling"] ?: 0)
        setActivityProgress(
            R.id.weightliftingProgressBar,
            R.id.weightliftingValueTextView,
            breakdown["Weightlifting"] ?: 0
        )
        setActivityProgress(R.id.yogaProgressBar, R.id.yogaValueTextView, breakdown["Yoga"] ?: 0)

        val favourite = breakdown.maxByOrNull { it.value }?.key
        val insight = when {
            weeklyWorkouts.isEmpty() ->
                "Log your first workout this week to unlock a personalised insight."
            totalMinutes >= 150 ->
                "Amazing consistency! You recorded $totalMinutes active minutes this week" +
                    if (favourite != null) ", with $favourite as your leading activity." else "."
            else -> {
                val remaining = 150 - totalMinutes
                "You are $remaining minutes away from 150 active minutes this week" +
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

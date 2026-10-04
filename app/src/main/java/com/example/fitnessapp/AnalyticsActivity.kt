package com.example.fitnessapp

import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.fitnessapp.data.WorkoutRepository
import com.example.fitnessapp.model.Workout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AnalyticsActivity : AppCompatActivity() {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
        showWeeklyAnalytics(WorkoutRepository(this).getWorkouts())
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
        val activeDays = weeklyWorkouts.map { it.date }.distinct().size
        val breakdown = weeklyWorkouts
            .groupBy { it.activityName }
            .mapValues { (_, items) -> items.sumOf { it.durationMinutes } }

        findViewById<TextView>(R.id.weeklyMinutesTextView).text = "$totalMinutes min"
        findViewById<TextView>(R.id.weeklyCaloriesTextView).text = "$totalCalories kcal"
        findViewById<TextView>(R.id.activeDaysTextView).text = "$activeDays of 7 days"

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

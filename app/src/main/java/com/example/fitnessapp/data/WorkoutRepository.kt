package com.example.fitnessapp.data

import android.content.Context
import com.example.fitnessapp.model.Workout
import com.example.fitnessapp.model.WorkoutFactory
import com.example.fitnessapp.network.ApiClient
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WorkoutRepository(context: Context) {
    private val preferences = context.getSharedPreferences("fitness_workouts", Context.MODE_PRIVATE)

    fun getWorkouts(): MutableList<Workout> {
        val saved = preferences.getString("workouts", null)
        if (saved == null) {
            val starterList = createStarterWorkouts()
            saveWorkouts(starterList)
            return starterList
        }

        val workouts = mutableListOf<Workout>()
        val jsonArray = JSONArray(saved)
        for (index in 0 until jsonArray.length()) {
            workouts.add(WorkoutFactory.fromJson(jsonArray.getJSONObject(index)))
        }
        return workouts
    }

    fun addWorkout(workout: Workout, userId: Int, callback: (Boolean, String) -> Unit) {
        val workouts = getWorkouts()
        workouts.add(0, workout)
        saveWorkouts(workouts)

        if (userId == 0) {
            callback(true, "Workout saved offline")
            return
        }

        val values = mutableMapOf(
            "user_id" to userId.toString(),
            "activity_type" to workout.activityName,
            "duration" to workout.durationMinutes.toString(),
            "calories" to workout.calories.toString(),
            "workout_date" to workout.date
        )
        workout.latitude?.let { values["latitude"] = it.toString() }
        workout.longitude?.let { values["longitude"] = it.toString() }

        ApiClient.post("add_workout.php", values) { result ->
            if (result.success) {
                val localId = workout.id
                workout.id = result.data.optLong("workout_id", localId)
                val savedItems = getWorkouts()
                savedItems.find { it.id == localId }?.id = workout.id
                saveWorkouts(savedItems)
            }
            callback(result.success, if (result.success) "Workout saved and synced" else result.message)
        }
    }

    fun deleteWorkout(workout: Workout, userId: Int, callback: (Boolean, String) -> Unit) {
        val updated = getWorkouts().filterNot { it.id == workout.id }
        saveWorkouts(updated)
        if (userId == 0) {
            callback(true, "Workout removed offline")
            return
        }
        ApiClient.post(
            "delete_workout.php",
            mapOf("user_id" to userId.toString(), "workout_id" to workout.id.toString())
        ) { result -> callback(result.success, result.message) }
    }

    fun loadFromServer(userId: Int, callback: (List<Workout>) -> Unit) {
        if (userId == 0) return
        ApiClient.post("get_workouts.php", mapOf("user_id" to userId.toString())) { result ->
            if (!result.success) return@post
            val serverItems = result.data.optJSONArray("workouts") ?: return@post
            val workouts = mutableListOf<Workout>()
            for (index in 0 until serverItems.length()) {
                workouts.add(WorkoutFactory.fromJson(serverItems.getJSONObject(index)))
            }
            saveWorkouts(workouts)
            callback(workouts)
        }
    }

    fun getDailyGoal(): Int = preferences.getInt("daily_goal", 500)

    fun saveDailyGoal(calories: Int, userId: Int, callback: (Boolean, String) -> Unit) {
        preferences.edit().putInt("daily_goal", calories).apply()
        if (userId == 0) {
            callback(true, "Goal saved offline")
            return
        }
        ApiClient.post(
            "save_goal.php",
            mapOf("user_id" to userId.toString(), "daily_calorie_goal" to calories.toString())
        ) { result -> callback(result.success, result.message) }
    }

    fun loadGoalFromServer(userId: Int, callback: (Int) -> Unit) {
        if (userId == 0) return
        ApiClient.post("get_goal.php", mapOf("user_id" to userId.toString())) { result ->
            if (!result.success) return@post
            val goal = result.data.optInt("daily_calorie_goal", getDailyGoal())
            preferences.edit().putInt("daily_goal", goal).apply()
            callback(goal)
        }
    }

    private fun saveWorkouts(workouts: List<Workout>) {
        val jsonArray = JSONArray()
        workouts.forEach { jsonArray.put(it.toJson()) }
        preferences.edit().putString("workouts", jsonArray.toString()).apply()
    }

    private fun createStarterWorkouts(): MutableList<Workout> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return mutableListOf(
            WorkoutFactory.create("Running", 1, 30, 220, today),
            WorkoutFactory.create("Cycling", 2, 40, 280, today),
            WorkoutFactory.create("Weightlifting", 3, 35, 180, today)
        )
    }
}

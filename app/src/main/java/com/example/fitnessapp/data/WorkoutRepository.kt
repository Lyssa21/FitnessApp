package com.example.fitnessapp.data

import android.content.Context
import com.example.fitnessapp.model.Workout
import com.example.fitnessapp.model.WorkoutFactory
import com.example.fitnessapp.network.ApiClient

/** Online-only repository: the PHP/MySQL server is the source of truth. */
class WorkoutRepository(context: Context) {
    private var dailyGoal = 500
    fun getWorkouts(): MutableList<Workout> = mutableListOf()

    fun addWorkout(workout: Workout, userId: Int, callback: (Boolean, String) -> Unit) {
        if (userId == 0) { callback(false, "Please log in before adding a workout"); return }
        val values = mutableMapOf("user_id" to userId.toString(), "activity_type" to workout.activityName,
            "duration" to workout.durationMinutes.toString(), "calories" to workout.calories.toString(), "workout_date" to workout.date)
        workout.latitude?.let { values["latitude"] = it.toString() }
        workout.longitude?.let { values["longitude"] = it.toString() }
        ApiClient.post("add_workout.php", values) { result ->
            if (result.success) workout.id = result.data.optLong("workout_id", workout.id)
            callback(result.success, if (result.success) "Workout saved to server" else result.message)
        }
    }

    fun deleteWorkout(workout: Workout, userId: Int, callback: (Boolean, String) -> Unit) {
        if (userId == 0) { callback(false, "Please log in before deleting a workout"); return }
        ApiClient.post("delete_workout.php", mapOf("user_id" to userId.toString(), "workout_id" to workout.id.toString())) { result -> callback(result.success, result.message) }
    }

    fun loadFromServer(userId: Int, callback: (List<Workout>) -> Unit, onError: (String) -> Unit = {}) {
        if (userId == 0) { onError("Please log in to load workouts"); return }
        ApiClient.post("get_workouts.php", mapOf("user_id" to userId.toString())) { result ->
            if (!result.success) { onError(result.message); return@post }
            val items = result.data.optJSONArray("workouts")
            if (items == null) { onError("The server returned no workout list"); return@post }
            callback(MutableList(items.length()) { index -> WorkoutFactory.fromJson(items.getJSONObject(index)) })
        }
    }

    fun getDailyGoal(): Int = dailyGoal

    fun saveDailyGoal(calories: Int, userId: Int, callback: (Boolean, String) -> Unit) {
        if (userId == 0) { callback(false, "Please log in before saving a goal"); return }
        ApiClient.post("save_goal.php", mapOf("user_id" to userId.toString(), "daily_calorie_goal" to calories.toString())) { result ->
            if (result.success) dailyGoal = calories
            callback(result.success, result.message)
        }
    }

    fun loadGoalFromServer(userId: Int, callback: (Int) -> Unit) {
        if (userId == 0) return
        ApiClient.post("get_goal.php", mapOf("user_id" to userId.toString())) { result ->
            if (result.success) { dailyGoal = result.data.optInt("daily_calorie_goal", dailyGoal); callback(dailyGoal) }
        }
    }
}

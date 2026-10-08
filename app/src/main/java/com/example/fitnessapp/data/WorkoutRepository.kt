package com.example.fitnessapp.data

import android.content.Context
import com.example.fitnessapp.model.Workout
import com.example.fitnessapp.model.WorkoutFactory
import com.example.fitnessapp.network.ApiClient

/** Online-only repository: the PHP/MySQL server is the source of truth. */
class WorkoutRepository(context: Context) {
    private var dailyGoal = 500
    var goalType: String = "calories"; private set
    var goalTarget: Double = 500.0; private set
    var goalDeadline: String? = null; private set
    fun getWorkouts(): MutableList<Workout> = mutableListOf()

    fun addWorkout(workout: Workout, userId: Int, callback: (Boolean, String) -> Unit) {
        if (userId == 0) { callback(false, "Please log in before adding a workout"); return }
        val values = mutableMapOf("user_id" to userId.toString(), "activity_type" to workout.activityName,
            "duration" to workout.durationMinutes.toString(), "calories" to workout.calories.toString(), "workout_date" to workout.date)
        workout.latitude?.let { values["latitude"] = it.toString() }
        workout.longitude?.let { values["longitude"] = it.toString() }
        workout.distanceKm?.let { values["distance_km"] = it.toString() }
        workout.exerciseName?.let { values["exercise_name"] = it }
        workout.weightKg?.let { values["weight_kg"] = it.toString() }
        workout.sets?.let { values["sets"] = it.toString() }
        workout.reps?.let { values["reps"] = it.toString() }
        workout.notes?.let { values["notes"] = it }
        workout.routePointsJson?.let { values["route_points"] = it }
        ApiClient.post("add_workout.php", values) { result ->
            if (result.success) workout.id = result.data.optLong("workout_id", workout.id)
            callback(result.success, if (result.success) "Workout saved to server" else result.message)
        }
    }

    fun updateWorkout(workout: Workout, userId: Int, callback: (Boolean, String) -> Unit) {
        if (userId == 0) { callback(false, "Please log in to edit workouts"); return }
        val values = workoutParameters(workout, userId).toMutableMap()
        values["workout_id"] = workout.id.toString()
        ApiClient.post("update_workout.php", values) { result -> callback(result.success, result.message) }
    }

    private fun workoutParameters(workout: Workout, userId: Int): Map<String, String> = mutableMapOf(
        "user_id" to userId.toString(), "activity_type" to workout.activityName,
        "duration" to workout.durationMinutes.toString(), "calories" to workout.calories.toString(),
        "workout_date" to workout.date
    ).apply {
        workout.distanceKm?.let { put("distance_km", it.toString()) }
        workout.exerciseName?.let { put("exercise_name", it) }
        workout.weightKg?.let { put("weight_kg", it.toString()) }
        workout.sets?.let { put("sets", it.toString()) }; workout.reps?.let { put("reps", it.toString()) }
        workout.notes?.let { put("notes", it) }; workout.latitude?.let { put("latitude", it.toString()) }
        workout.longitude?.let { put("longitude", it.toString()) }; workout.routePointsJson?.let { put("route_points", it) }
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

    fun saveStructuredGoal(type: String, target: Double, deadline: String?, userId: Int, callback: (Boolean, String) -> Unit) {
        if (userId == 0) { callback(false, "Please log in to save a goal"); return }
        val values = mutableMapOf("user_id" to userId.toString(), "goal_type" to type, "target_value" to target.toString())
        deadline?.let { values["deadline_date"] = it }
        ApiClient.post("save_goal.php", values) { result ->
            if (result.success) { goalType = type; goalTarget = target; goalDeadline = deadline; if (type == "calories") dailyGoal = target.toInt() }
            callback(result.success, result.message)
        }
    }

    fun loadGoalFromServer(userId: Int, callback: (Int) -> Unit) {
        if (userId == 0) return
        ApiClient.post("get_goal.php", mapOf("user_id" to userId.toString())) { result ->
            if (result.success) {
                dailyGoal = result.data.optInt("daily_calorie_goal", dailyGoal)
                goalType = result.data.optString("goal_type", "calories")
                goalTarget = result.data.optDouble("target_value", dailyGoal.toDouble())
                goalDeadline = result.data.optString("deadline_date").takeIf { it.isNotBlank() && it != "null" }
                callback(dailyGoal)
            }
        }
    }
}

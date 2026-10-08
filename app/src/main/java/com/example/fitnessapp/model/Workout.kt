package com.example.fitnessapp.model

import org.json.JSONObject

// Base class shared by every workout type.
open class Workout(
    var id: Long,
    val activityName: String,
    val durationMinutes: Int,
    val calories: Int,
    val date: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val distanceKm: Double? = null,
    val exerciseName: String? = null,
    val weightKg: Double? = null,
    val sets: Int? = null,
    val reps: Int? = null,
    val notes: String? = null
) {
    var routePointsJson: String? = null
    var stepsCount: Int? = null
    open val icon: String = "💪"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("activity_type", activityName)
        put("duration", durationMinutes)
        put("calories", calories)
        put("workout_date", date)
        latitude?.let { put("latitude", it) }
        longitude?.let { put("longitude", it) }
        distanceKm?.let { put("distance_km", it) }
        exerciseName?.let { put("exercise_name", it) }
        weightKg?.let { put("weight_kg", it) }
        sets?.let { put("sets", it) }
        reps?.let { put("reps", it) }
        notes?.let { put("notes", it) }
        routePointsJson?.let { put("route_points", it) }
        stepsCount?.let { put("steps_count", it) }
    }
}

class RunningWorkout(
    id: Long,
    durationMinutes: Int,
    calories: Int,
    date: String,
    latitude: Double? = null,
    longitude: Double? = null,
    distanceKm: Double? = null,
    notes: String? = null
) : Workout(id, "Running", durationMinutes, calories, date, latitude, longitude, distanceKm = distanceKm, notes = notes) {
    override val icon = "🏃‍♀️"
}

class CyclingWorkout(
    id: Long,
    durationMinutes: Int,
    calories: Int,
    date: String,
    latitude: Double? = null,
    longitude: Double? = null,
    distanceKm: Double? = null,
    notes: String? = null
) : Workout(id, "Cycling", durationMinutes, calories, date, latitude, longitude, distanceKm = distanceKm, notes = notes) {
    override val icon = "🚴‍♀️"
}

class WalkingWorkout(
    id: Long, durationMinutes: Int, calories: Int, date: String,
    latitude: Double? = null, longitude: Double? = null,
    distanceKm: Double? = null, notes: String? = null
) : Workout(id, "Walking", durationMinutes, calories, date, latitude, longitude, distanceKm = distanceKm, notes = notes) {
    override val icon = "🚶"
}

class WeightliftingWorkout(
    id: Long,
    durationMinutes: Int,
    calories: Int,
    date: String,
    latitude: Double? = null,
    longitude: Double? = null,
    exerciseName: String? = null,
    weightKg: Double? = null,
    sets: Int? = null,
    reps: Int? = null,
    notes: String? = null
) : Workout(id, "Weightlifting", durationMinutes, calories, date, latitude, longitude, exerciseName = exerciseName, weightKg = weightKg, sets = sets, reps = reps, notes = notes) {
    override val icon = "🏋️‍♀️"
}

class YogaWorkout(
    id: Long,
    durationMinutes: Int,
    calories: Int,
    date: String,
    latitude: Double? = null,
    longitude: Double? = null,
    notes: String? = null
) : Workout(id, "Yoga", durationMinutes, calories, date, latitude, longitude, notes = notes) {
    override val icon = "🧘‍♀️"
}

object WorkoutFactory {
    fun create(
        type: String,
        id: Long = System.currentTimeMillis(),
        duration: Int,
        calories: Int,
        date: String,
        latitude: Double? = null,
        longitude: Double? = null,
        distanceKm: Double? = null,
        exerciseName: String? = null,
        weightKg: Double? = null,
        sets: Int? = null,
        reps: Int? = null,
        notes: String? = null
    ): Workout = when (type.lowercase()) {
        "running" -> RunningWorkout(id, duration, calories, date, latitude, longitude, distanceKm, notes)
        "cycling" -> CyclingWorkout(id, duration, calories, date, latitude, longitude, distanceKm, notes)
        "walking" -> WalkingWorkout(id, duration, calories, date, latitude, longitude, distanceKm, notes)
        "weightlifting" -> WeightliftingWorkout(id, duration, calories, date, latitude, longitude, exerciseName, weightKg, sets, reps, notes)
        else -> YogaWorkout(id, duration, calories, date, latitude, longitude, notes)
    }

    fun fromJson(json: JSONObject): Workout = create(
        type = json.optString("activity_type", "Weightlifting"),
        id = json.optLong("id", System.currentTimeMillis()),
        duration = json.optInt("duration"),
        calories = json.optInt("calories"),
        date = json.optString("workout_date"),
        latitude = json.optDoubleOrNull("latitude"),
        longitude = json.optDoubleOrNull("longitude")
        , distanceKm = json.optDoubleOrNull("distance_km")
        , exerciseName = json.optString("exercise_name").ifBlank { null }
        , weightKg = json.optDoubleOrNull("weight_kg")
        , sets = json.optIntOrNull("sets")
        , reps = json.optIntOrNull("reps")
        , notes = json.optString("notes").ifBlank { null }
    ).apply {
        routePointsJson = json.optString("route_points").takeIf { it.isNotBlank() && it != "null" }
        stepsCount = if (json.has("steps_count") && !json.isNull("steps_count")) json.optInt("steps_count") else null
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        return if (has(name) && !isNull(name)) optDouble(name) else null
    }

    private fun JSONObject.optIntOrNull(name: String): Int? = if (has(name) && !isNull(name)) optInt(name) else null
}

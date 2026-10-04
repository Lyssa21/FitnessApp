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
    val longitude: Double? = null
) {
    open val icon: String = "💪"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("activity_type", activityName)
        put("duration", durationMinutes)
        put("calories", calories)
        put("workout_date", date)
        latitude?.let { put("latitude", it) }
        longitude?.let { put("longitude", it) }
    }
}

class RunningWorkout(
    id: Long,
    durationMinutes: Int,
    calories: Int,
    date: String,
    latitude: Double? = null,
    longitude: Double? = null
) : Workout(id, "Running", durationMinutes, calories, date, latitude, longitude) {
    override val icon = "🏃‍♀️"
}

class CyclingWorkout(
    id: Long,
    durationMinutes: Int,
    calories: Int,
    date: String,
    latitude: Double? = null,
    longitude: Double? = null
) : Workout(id, "Cycling", durationMinutes, calories, date, latitude, longitude) {
    override val icon = "🚴‍♀️"
}

class WeightliftingWorkout(
    id: Long,
    durationMinutes: Int,
    calories: Int,
    date: String,
    latitude: Double? = null,
    longitude: Double? = null
) : Workout(id, "Weightlifting", durationMinutes, calories, date, latitude, longitude) {
    override val icon = "🏋️‍♀️"
}

class YogaWorkout(
    id: Long,
    durationMinutes: Int,
    calories: Int,
    date: String,
    latitude: Double? = null,
    longitude: Double? = null
) : Workout(id, "Yoga", durationMinutes, calories, date, latitude, longitude) {
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
        longitude: Double? = null
    ): Workout = when (type.lowercase()) {
        "running" -> RunningWorkout(id, duration, calories, date, latitude, longitude)
        "cycling" -> CyclingWorkout(id, duration, calories, date, latitude, longitude)
        "weightlifting" -> WeightliftingWorkout(id, duration, calories, date, latitude, longitude)
        else -> YogaWorkout(id, duration, calories, date, latitude, longitude)
    }

    fun fromJson(json: JSONObject): Workout = create(
        type = json.optString("activity_type", "Weightlifting"),
        id = json.optLong("id", System.currentTimeMillis()),
        duration = json.optInt("duration"),
        calories = json.optInt("calories"),
        date = json.optString("workout_date"),
        latitude = json.optDoubleOrNull("latitude"),
        longitude = json.optDoubleOrNull("longitude")
    )

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        return if (has(name) && !isNull(name)) optDouble(name) else null
    }
}

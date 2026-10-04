package com.example.fitnessapp

import com.example.fitnessapp.model.CyclingWorkout
import com.example.fitnessapp.model.RunningWorkout
import com.example.fitnessapp.model.WeightliftingWorkout
import com.example.fitnessapp.model.WorkoutFactory
import com.example.fitnessapp.model.YogaWorkout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutFactoryTest {
    @Test
    fun createsTheCorrectSubclassForEachActivity() {
        assertTrue(WorkoutFactory.create("Running", duration = 20, calories = 150, date = "2026-10-04") is RunningWorkout)
        assertTrue(WorkoutFactory.create("Cycling", duration = 20, calories = 150, date = "2026-10-04") is CyclingWorkout)
        assertTrue(WorkoutFactory.create("Weightlifting", duration = 20, calories = 150, date = "2026-10-04") is WeightliftingWorkout)
        assertTrue(WorkoutFactory.create("Yoga", duration = 20, calories = 150, date = "2026-10-04") is YogaWorkout)
    }

    @Test
    fun factoryKeepsWorkoutValues() {
        val workout = WorkoutFactory.create(
            type = "Running",
            id = 42,
            duration = 35,
            calories = 260,
            date = "2026-10-04",
            latitude = 16.8409,
            longitude = 96.1735
        )

        assertEquals(42, workout.id)
        assertEquals("Running", workout.activityName)
        assertEquals(35, workout.durationMinutes)
        assertEquals(260, workout.calories)
        assertEquals(16.8409, workout.latitude)
    }
}

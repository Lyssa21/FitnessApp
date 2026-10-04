<?php
declare(strict_types=1);

require_once __DIR__ . '/api_helpers.php';
require_once __DIR__ . '/db_connect.php';

require_post();

$userId = filter_var($_POST['user_id'] ?? null, FILTER_VALIDATE_INT);
$activityType = post_value('activity_type');
$duration = filter_var($_POST['duration'] ?? null, FILTER_VALIDATE_INT);
$calories = filter_var($_POST['calories'] ?? null, FILTER_VALIDATE_INT);
$workoutDate = post_value('workout_date') ?: date('Y-m-d');
$latitude = post_value('latitude');
$longitude = post_value('longitude');
$allowedActivities = ['Running', 'Cycling', 'Weightlifting', 'Yoga'];

if (!$userId || !in_array($activityType, $allowedActivities, true)) {
    send_json(false, 'A valid user and activity type are required.', [], 422);
}
if (!$duration || $duration < 1 || !$calories || $calories < 1) {
    send_json(false, 'Duration and calories must be positive numbers.', [], 422);
}
$dateObject = DateTime::createFromFormat('Y-m-d', $workoutDate);
if (!$dateObject || $dateObject->format('Y-m-d') !== $workoutDate) {
    send_json(false, 'Workout date must use YYYY-MM-DD format.', [], 422);
}

$statement = $pdo->prepare(
    'INSERT INTO workouts
        (user_id, activity_type, duration, calories, workout_date, latitude, longitude)
     VALUES
        (:user_id, :activity_type, :duration, :calories, :workout_date, :latitude, :longitude)'
);
$statement->execute([
    'user_id' => $userId,
    'activity_type' => $activityType,
    'duration' => $duration,
    'calories' => $calories,
    'workout_date' => $workoutDate,
    'latitude' => $latitude === '' ? null : (float)$latitude,
    'longitude' => $longitude === '' ? null : (float)$longitude,
]);

send_json(true, 'Workout saved successfully.', [
    'workout_id' => (int)$pdo->lastInsertId(),
], 201);

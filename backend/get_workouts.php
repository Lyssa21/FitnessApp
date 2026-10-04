<?php
declare(strict_types=1);

require_once __DIR__ . '/api_helpers.php';
require_once __DIR__ . '/db_connect.php';

require_post();

$userId = filter_var($_POST['user_id'] ?? null, FILTER_VALIDATE_INT);
if (!$userId) {
    send_json(false, 'A valid user ID is required.', [], 422);
}

$statement = $pdo->prepare(
    'SELECT id, activity_type, duration, calories, workout_date, latitude, longitude
     FROM workouts
     WHERE user_id = :user_id
     ORDER BY workout_date DESC, id DESC'
);
$statement->execute(['user_id' => $userId]);

$workouts = array_map(static function (array $row): array {
    return [
        'id' => (int)$row['id'],
        'activity_type' => $row['activity_type'],
        'duration' => (int)$row['duration'],
        'calories' => (int)$row['calories'],
        'workout_date' => $row['workout_date'],
        'latitude' => $row['latitude'] === null ? null : (float)$row['latitude'],
        'longitude' => $row['longitude'] === null ? null : (float)$row['longitude'],
    ];
}, $statement->fetchAll());

send_json(true, 'Workouts loaded.', ['workouts' => $workouts]);

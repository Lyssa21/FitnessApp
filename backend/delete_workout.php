<?php
declare(strict_types=1);

require_once __DIR__ . '/api_helpers.php';
require_once __DIR__ . '/db_connect.php';

require_post();

$userId = filter_var($_POST['user_id'] ?? null, FILTER_VALIDATE_INT);
$workoutId = filter_var($_POST['workout_id'] ?? null, FILTER_VALIDATE_INT);
if (!$userId || !$workoutId) {
    send_json(false, 'A valid user ID and workout ID are required.', [], 422);
}

// Including user_id prevents one signed-in user deleting another user's workout.
$statement = $pdo->prepare(
    'DELETE FROM workouts WHERE id = :workout_id AND user_id = :user_id'
);
$statement->execute([
    'workout_id' => $workoutId,
    'user_id' => $userId,
]);

if ($statement->rowCount() === 0) {
    send_json(false, 'Workout was not found or did not belong to this user.', [], 404);
}

send_json(true, 'Workout deleted successfully.');

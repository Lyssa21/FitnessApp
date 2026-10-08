<?php
declare(strict_types=1);

require_once __DIR__ . '/api_helpers.php';
require_once __DIR__ . '/db_connect.php';

require_post();

$authenticatedUserId = require_auth_user($pdo);
$userId = filter_var($_POST['user_id'] ?? null, FILTER_VALIDATE_INT);
if (!$userId || $userId !== $authenticatedUserId) {
    send_json(false, 'A valid user ID is required.', [], 422);
}

$statement = $pdo->prepare(
    'SELECT daily_calorie_goal, goal_type, target_value, deadline_date FROM fitness_goals WHERE user_id = :user_id LIMIT 1'
);
$statement->execute(['user_id' => $userId]);
$goal = $statement->fetch();

send_json(true, 'Goal loaded.', [
    'daily_calorie_goal' => $goal === false ? 500 : (int)$goal['daily_calorie_goal'],
    'goal_type' => $goal === false ? 'calories' : $goal['goal_type'],
    'target_value' => $goal === false ? 500 : (float)$goal['target_value'],
    'deadline_date' => $goal === false ? null : $goal['deadline_date'],
]);

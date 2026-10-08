<?php
declare(strict_types=1);

require_once __DIR__ . '/api_helpers.php';
require_once __DIR__ . '/db_connect.php';

require_post();

$authenticatedUserId = require_auth_user($pdo);
$userId = filter_var($_POST['user_id'] ?? null, FILTER_VALIDATE_INT);
$goal = filter_var($_POST['daily_calorie_goal'] ?? null, FILTER_VALIDATE_INT);
if (!$userId || $userId !== $authenticatedUserId || !$goal || $goal < 1 || $goal > 10000) {
    send_json(false, 'Enter a valid calorie goal between 1 and 10,000.', [], 422);
}

$statement = $pdo->prepare(
    'INSERT INTO fitness_goals (user_id, daily_calorie_goal)
     VALUES (:user_id, :goal)
     ON DUPLICATE KEY UPDATE daily_calorie_goal = VALUES(daily_calorie_goal)'
);
$statement->execute(['user_id' => $userId, 'goal' => $goal]);

send_json(true, 'Goal saved and synced.');

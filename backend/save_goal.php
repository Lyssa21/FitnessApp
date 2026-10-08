<?php
declare(strict_types=1);

require_once __DIR__ . '/api_helpers.php';
require_once __DIR__ . '/db_connect.php';

require_post();

$authenticatedUserId = require_auth_user($pdo);
$userId = filter_var($_POST['user_id'] ?? null, FILTER_VALIDATE_INT);
$goal = filter_var($_POST['daily_calorie_goal'] ?? null, FILTER_VALIDATE_INT);
$goalType = post_value('goal_type') ?: 'calories';
$target = filter_var($_POST['target_value'] ?? ($_POST['daily_calorie_goal'] ?? null), FILTER_VALIDATE_FLOAT);
$deadline = post_value('deadline_date');
if (!$userId || $userId !== $authenticatedUserId || !in_array($goalType, ['calories', 'distance'], true) || $target === false || $target <= 0 || $target > 10000) {
    send_json(false, 'Enter a valid goal target.', [], 422);
}
$dailyGoal = $goalType === 'calories' ? (int)$target : 500;
$deadlineValue = null;
if ($deadline !== '') { $parsed = DateTime::createFromFormat('Y-m-d', $deadline); if (!$parsed || $parsed->format('Y-m-d') !== $deadline) send_json(false, 'Invalid goal deadline.', [], 422); $deadlineValue = $deadline; }

$statement = $pdo->prepare(
    'INSERT INTO fitness_goals (user_id, daily_calorie_goal, goal_type, target_value, deadline_date)
     VALUES (:user_id, :goal, :goal_type, :target, :deadline)
     ON DUPLICATE KEY UPDATE daily_calorie_goal = VALUES(daily_calorie_goal), goal_type=VALUES(goal_type), target_value=VALUES(target_value), deadline_date=VALUES(deadline_date)'
);
$statement->execute(['user_id' => $userId, 'goal' => $dailyGoal, 'goal_type' => $goalType, 'target' => $target, 'deadline' => $deadlineValue]);

send_json(true, 'Goal saved and synced.');

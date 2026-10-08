<?php
declare(strict_types=1);
require_once __DIR__ . '/api_helpers.php';
require_once __DIR__ . '/db_connect.php';
require_post();
$authUserId = require_auth_user($pdo);
$userId = filter_var($_POST['user_id'] ?? null, FILTER_VALIDATE_INT);
$workoutId = filter_var($_POST['workout_id'] ?? null, FILTER_VALIDATE_INT);
$activity = post_value('activity_type');
$duration = filter_var($_POST['duration'] ?? null, FILTER_VALIDATE_INT);
$calories = filter_var($_POST['calories'] ?? null, FILTER_VALIDATE_INT);
$date = post_value('workout_date');
$allowed = ['Running', 'Walking', 'Cycling', 'Weightlifting', 'Yoga'];
if (!$userId || $userId !== $authUserId || !$workoutId || !in_array($activity, $allowed, true) || !$duration || !$calories) send_json(false, 'Invalid workout data.', [], 422);
$dateValue = DateTime::createFromFormat('Y-m-d', $date);
if (!$dateValue || $dateValue->format('Y-m-d') !== $date) send_json(false, 'Invalid workout date.', [], 422);
$stmt = $pdo->prepare('UPDATE workouts SET activity_type=:type,duration=:duration,calories=:calories,workout_date=:date,distance_km=:distance,exercise_name=:exercise,weight_kg=:weight,sets=:sets,reps=:reps,notes=:notes,latitude=:lat,longitude=:lng,route_points=:route WHERE id=:id AND user_id=:uid');
$stmt->execute(['type'=>$activity,'duration'=>$duration,'calories'=>$calories,'date'=>$date,'distance'=>post_value('distance_km') ?: null,'exercise'=>post_value('exercise_name') ?: null,'weight'=>post_value('weight_kg') ?: null,'sets'=>post_value('sets') ?: null,'reps'=>post_value('reps') ?: null,'notes'=>post_value('notes') ?: null,'lat'=>post_value('latitude') ?: null,'lng'=>post_value('longitude') ?: null,'route'=>post_value('route_points') ?: null,'id'=>$workoutId,'uid'=>$userId]);
send_json(true, 'Workout updated successfully.');

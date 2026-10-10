<?php
declare(strict_types=1);
require_once __DIR__ . '/api_helpers.php';
require_once __DIR__ . '/db_connect.php';
require_post();
$userId = require_auth_user($pdo);
$name = post_value('name');
$email = strtolower(post_value('email'));
if ($name === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) send_json(false, 'Enter a valid name and email.', [], 422);
$check = $pdo->prepare('SELECT id FROM users WHERE email = :email AND id <> :id LIMIT 1');
$check->execute(['email' => $email, 'id' => $userId]);
if ($check->fetch()) send_json(false, 'That email is already in use.', [], 409);
$update = $pdo->prepare('UPDATE users SET name = :name, email = :email WHERE id = :id');
$update->execute(['name' => $name, 'email' => $email, 'id' => $userId]);
send_json(true, 'Profile updated.', ['name' => $name, 'email' => $email]);

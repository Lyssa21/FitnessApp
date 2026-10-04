<?php
declare(strict_types=1);

require_once __DIR__ . '/api_helpers.php';
require_once __DIR__ . '/db_connect.php';

require_post();

$email = strtolower(post_value('email'));
$password = (string)($_POST['password'] ?? '');

if (!filter_var($email, FILTER_VALIDATE_EMAIL) || $password === '') {
    send_json(false, 'Enter a valid email and password.', [], 422);
}

$statement = $pdo->prepare(
    'SELECT id, name, password_hash FROM users WHERE email = :email LIMIT 1'
);
$statement->execute(['email' => $email]);
$user = $statement->fetch();

if (!$user || !password_verify($password, $user['password_hash'])) {
    send_json(false, 'Incorrect email or password.', [], 401);
}

send_json(true, 'Login successful.', [
    'user_id' => (int)$user['id'],
    'name' => $user['name'],
]);

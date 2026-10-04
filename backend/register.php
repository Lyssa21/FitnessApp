<?php
declare(strict_types=1);

require_once __DIR__ . '/api_helpers.php';
require_once __DIR__ . '/db_connect.php';

require_post();

$name = post_value('name');
$email = strtolower(post_value('email'));
$password = (string)($_POST['password'] ?? '');

if ($name === '' || !filter_var($email, FILTER_VALIDATE_EMAIL) || strlen($password) < 6) {
    send_json(false, 'Enter a name, a valid email and a password of at least 6 characters.', [], 422);
}

$check = $pdo->prepare('SELECT id FROM users WHERE email = ? LIMIT 1');
$check->execute([$email]);
if ($check->fetch()) {
    send_json(false, 'An account already exists for this email.', [], 409);
}

$statement = $pdo->prepare(
    'INSERT INTO users (name, email, password_hash) VALUES (:name, :email, :password_hash)'
);
$statement->execute([
    'name' => $name,
    'email' => $email,
    'password_hash' => password_hash($password, PASSWORD_DEFAULT),
]);

send_json(true, 'Account created. You can now log in.', [
    'user_id' => (int)$pdo->lastInsertId(),
], 201);

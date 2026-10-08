<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');

function send_json(bool $success, string $message, array $extra = [], int $status = 200): never
{
    http_response_code($status);
    echo json_encode(array_merge([
        'success' => $success,
        'message' => $message,
    ], $extra));
    exit;
}

function require_post(): void
{
    if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
        send_json(false, 'Only POST requests are accepted.', [], 405);
    }
}

function post_value(string $name): string
{
    return trim((string)($_POST[$name] ?? ''));
}

function require_auth_user(PDO $pdo): int
{
    $token = post_value('token');
    if ($token === '') send_json(false, 'Authentication token is required.', [], 401);
    $statement = $pdo->prepare('SELECT id FROM users WHERE session_token = :token LIMIT 1');
    $statement->execute(['token' => $token]);
    $userId = $statement->fetchColumn();
    if (!$userId) send_json(false, 'Your session has expired. Please log in again.', [], 401);
    return (int)$userId;
}

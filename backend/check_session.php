<?php
declare(strict_types=1);
require_once __DIR__ . '/api_helpers.php';
require_once __DIR__ . '/db_connect.php';
require_post();
$userId = require_auth_user($pdo);
send_json(true, 'Session is valid.', ['user_id' => $userId]);

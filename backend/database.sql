CREATE DATABASE IF NOT EXISTS fitness_app
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE fitness_app;

CREATE TABLE IF NOT EXISTS users (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS workouts (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NOT NULL,
    activity_type ENUM('Running', 'Cycling', 'Weightlifting', 'Yoga') NOT NULL,
    duration INT UNSIGNED NOT NULL,
    calories INT UNSIGNED NOT NULL,
    workout_date DATE NOT NULL,
    latitude DECIMAL(10, 7) NULL,
    longitude DECIMAL(10, 7) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_workouts_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE,
    INDEX idx_workouts_user_date (user_id, workout_date)
);

-- This also upgrades an existing installation created before Yoga was added.
ALTER TABLE workouts
    MODIFY activity_type ENUM('Running', 'Cycling', 'Weightlifting', 'Yoga') NOT NULL;

CREATE TABLE IF NOT EXISTS fitness_goals (
    user_id INT UNSIGNED PRIMARY KEY,
    daily_calorie_goal INT UNSIGNED NOT NULL DEFAULT 500,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_goals_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
);

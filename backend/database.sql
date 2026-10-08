CREATE DATABASE IF NOT EXISTS fitness_app
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE fitness_app;

CREATE TABLE IF NOT EXISTS users (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    session_token CHAR(64) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS session_token CHAR(64) NULL;

CREATE TABLE IF NOT EXISTS workouts (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NOT NULL,
    activity_type ENUM('Running', 'Walking', 'Cycling', 'Weightlifting', 'Yoga') NOT NULL,
    duration INT UNSIGNED NOT NULL,
    calories INT UNSIGNED NOT NULL,
    distance_km DECIMAL(8, 2) NULL,
    exercise_name VARCHAR(120) NULL,
    weight_kg DECIMAL(8, 2) NULL,
    sets INT UNSIGNED NULL,
    reps INT UNSIGNED NULL,
    notes TEXT NULL,
    route_points LONGTEXT NULL,
    steps_count INT UNSIGNED NULL,
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
    MODIFY activity_type ENUM('Running', 'Walking', 'Cycling', 'Weightlifting', 'Yoga') NOT NULL;

ALTER TABLE workouts ADD COLUMN IF NOT EXISTS distance_km DECIMAL(8, 2) NULL;
ALTER TABLE workouts ADD COLUMN IF NOT EXISTS exercise_name VARCHAR(120) NULL;
ALTER TABLE workouts ADD COLUMN IF NOT EXISTS weight_kg DECIMAL(8, 2) NULL;
ALTER TABLE workouts ADD COLUMN IF NOT EXISTS sets INT UNSIGNED NULL;
ALTER TABLE workouts ADD COLUMN IF NOT EXISTS reps INT UNSIGNED NULL;
ALTER TABLE workouts ADD COLUMN IF NOT EXISTS notes TEXT NULL;
ALTER TABLE workouts ADD COLUMN IF NOT EXISTS route_points LONGTEXT NULL;
ALTER TABLE workouts ADD COLUMN IF NOT EXISTS steps_count INT UNSIGNED NULL;

CREATE TABLE IF NOT EXISTS fitness_goals (
    user_id INT UNSIGNED PRIMARY KEY,
    daily_calorie_goal INT UNSIGNED NOT NULL DEFAULT 500,
    goal_type VARCHAR(20) NOT NULL DEFAULT 'calories',
    target_value DECIMAL(10,2) NOT NULL DEFAULT 500,
    deadline_date DATE NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_goals_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
);

ALTER TABLE fitness_goals ADD COLUMN IF NOT EXISTS goal_type VARCHAR(20) NOT NULL DEFAULT 'calories';
ALTER TABLE fitness_goals ADD COLUMN IF NOT EXISTS target_value DECIMAL(10,2) NOT NULL DEFAULT 500;
ALTER TABLE fitness_goals ADD COLUMN IF NOT EXISTS deadline_date DATE NULL;
